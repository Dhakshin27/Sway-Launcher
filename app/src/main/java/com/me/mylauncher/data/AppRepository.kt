package com.me.mylauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.util.LruCache

import androidx.core.graphics.drawable.toBitmap

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build

import java.io.ByteArrayOutputStream

private const val ICON_SIZE = 128

class AppRecord(
    val packageName: String,
    val label: String,
    val componentName: ComponentName,
    val lastUpdateTime: Long,
    initialIcon: Bitmap?,
    initialLaunchCount: Int = 0
) {

    private val _icon = MutableStateFlow(initialIcon)

    val icon: StateFlow<Bitmap?> = _icon.asStateFlow()

    @Volatile
    var launchCount: Int = initialLaunchCount
        private set

    internal fun updateIcon(bitmap: Bitmap) {
        _icon.value = bitmap
    }

    @Synchronized
    internal fun recordLaunch() {
        launchCount++
    }
}

class AppRepository private constructor(
    private val context: Context
) {

    companion object {

        @Volatile
        private var INSTANCE: AppRepository? = null

        fun get(context: Context): AppRepository {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: AppRepository(
                    context.applicationContext
                ).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val database = AppDatabase(context)

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE)
                as LauncherApps

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val _apps =
        MutableStateFlow<List<AppRecord>>(emptyList())

    val apps: StateFlow<List<AppRecord>> = _apps.asStateFlow()

    /**
     * Maximum approximately 16 MB of decoded icon memory.
     */
    private val iconCache = object : LruCache<String, Bitmap>(
        16 * 1024 * 1024
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    private val appMap = mutableMapOf<String, AppRecord>()

    @Volatile
    private var started = false

    /**
     * Android package change callbacks.
     */
    private val callback = object : LauncherApps.Callback() {

        override fun onPackageAdded(
            packageName: String,
            user: UserHandle
        ) {
            if (user == Process.myUserHandle()) {
                refreshPackage(packageName)
            }
        }

        override fun onPackageChanged(
            packageName: String,
            user: UserHandle
        ) {
            if (user == Process.myUserHandle()) {
                refreshPackage(packageName)
            }
        }

        override fun onPackageRemoved(
            packageName: String,
            user: UserHandle
        ) {
            if (user == Process.myUserHandle()) {
                removePackage(packageName)
            }
        }

        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) {
            if (user == Process.myUserHandle()) {
                packageNames.forEach(::refreshPackage)
            }
        }

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) {
            // Keep cached entries. Packages may become available again.
        }
    }

    /**
     * Called once by LauncherApp.
     */
    @Synchronized
    fun start() {

        if (started) return

        started = true

        launcherApps.registerCallback(
            callback,
            Handler(Looper.getMainLooper())
        )

        scope.launch {

            invalidateOldIconCache()

            loadDatabaseIntoMemory()

            reconcileInstalledApps()
        }
    }

    //Display icons
    private fun getLauncherDisplayIcon(
        activity: LauncherActivityInfo
    ): Drawable {

        val originalIcon = activity.getIcon(0)

        // Android 13+ supports app-provided monochrome icons.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val monochrome = (
                    originalIcon as? AdaptiveIconDrawable
                    )?.monochrome

            if (monochrome != null) {
                return monochrome.mutate().apply {
                    setTint(Color.WHITE)
                }
            }
        }

        // Fallback for apps without a monochrome layer.
        return originalIcon
    }

    private fun invalidateOldIconCache() {

        val preferences = context.getSharedPreferences(
            "launcher_icon_cache",
            Context.MODE_PRIVATE
        )

        val currentVersion = 1

        if (preferences.getInt("style_version", 0) != currentVersion) {

            database.clearAllIcons()

            preferences.edit()
                .putInt("style_version", currentVersion)
                .apply()
        }
    }
    /**
     * Load previously saved apps before querying Android.
     */
    private suspend fun loadDatabaseIntoMemory() {

        val storedApps = database.getAllApps()

        for (stored in storedApps) {

            val bitmap = try {

                BitmapFactory.decodeByteArray(
                    stored.iconBytes,
                    0,
                    stored.iconBytes.size
                )

            } catch (_: Exception) {
                null
            }

            val component = ComponentName.unflattenFromString(
                stored.componentName
            ) ?: continue

            val record = AppRecord(
                packageName = stored.packageName,
                label = stored.label,
                componentName = component,
                lastUpdateTime = stored.lastUpdateTime,
                initialIcon = bitmap,
                initialLaunchCount = stored.launchCount
            )

            synchronized(appMap) {

                appMap[stored.packageName] = record

                if (bitmap != null) {
                    iconCache.put(stored.packageName, bitmap)
                }
            }
        }

        publishApps()
    }

    /**
     * One reconciliation after startup.
     *
     * Package changes afterwards are handled by LauncherApps.Callback.
     */
    private suspend fun reconcileInstalledApps() {

        val activities = try {

            launcherApps.getActivityList(
                null,
                Process.myUserHandle()
            )

        } catch (_: Exception) {
            return
        }

        val uniqueActivities = activities
            .filter {
                it.applicationInfo.packageName != context.packageName
            }
            .distinctBy {
                it.applicationInfo.packageName
            }
            .sortedBy {
                it.label.toString().lowercase()
            }

        val installedPackages = uniqueActivities
            .map {
                it.applicationInfo.packageName
            }
            .toSet()

        // Remove stale records from memory.
        synchronized(appMap) {

            val stalePackages = appMap.keys
                .filter { it !in installedPackages }

            stalePackages.forEach { packageName ->
                appMap.remove(packageName)
                iconCache.remove(packageName)
            }
        }

        // Remove stale records from SQLite.
        database.deleteAllExcept(installedPackages)

        for (activity in uniqueActivities) {
            refreshActivity(activity)
        }

        publishApps()
    }

    /**
     * Refresh one package only.
     */
    private fun refreshPackage(packageName: String) {

        scope.launch {



            try {

                val activity = launcherApps.getActivityList(
                    packageName,
                    Process.myUserHandle()
                ).firstOrNull()

                if (activity == null) {
                    removePackage(packageName)
                    return@launch
                }

                refreshActivity(activity)

                publishApps()

            } catch (_: Exception) {
                // Package may temporarily be unavailable.
            }
        }
    }

    /**
     * Update an individual app and its icon.
     */
    private suspend fun refreshActivity(
        activity: LauncherActivityInfo
    ) {

        val packageName = activity.applicationInfo.packageName

        val label = activity.label.toString()

        val component = activity.componentName

        val updateTime = try {

            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                packageName,
                0
            ).lastUpdateTime

        } catch (_: Exception) {
            0L
        }

        val existing = synchronized(appMap) {
            appMap[packageName]
        }

        val initialLaunchCount = existing?.launchCount ?: 0

        // Reuse the existing record if nothing changed.
        if (
            existing != null &&
            existing.lastUpdateTime == updateTime &&
            existing.label == label &&
            existing.componentName == component &&
            existing.icon.value != null
        ) {
            return
        }

        val bitmap = withContext(Dispatchers.IO) {

            try {

                val cached = iconCache.get(packageName)

                if (
                    cached != null &&
                    existing?.lastUpdateTime == updateTime
                ) {
                    cached
                } else {

                    getLauncherDisplayIcon(activity).toBitmap(
                        width = ICON_SIZE,
                        height = ICON_SIZE,
                        config = Bitmap.Config.ARGB_8888
                    )
                }

            } catch (_: Exception) {
                null
            }
        } ?: return

        val iconBytes = withContext(Dispatchers.IO) {

            ByteArrayOutputStream().use { output ->

                bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    output
                )

                output.toByteArray()
            }
        }

        val record = AppRecord(
            packageName = packageName,
            label = label,
            componentName = component,
            lastUpdateTime = updateTime,
            initialIcon = bitmap,
            initialLaunchCount = initialLaunchCount
        )

        synchronized(appMap) {

            appMap[packageName] = record

            iconCache.put(packageName, bitmap)
        }

        database.upsert(
            StoredApp(
                packageName = packageName,
                label = label,
                componentName = component.flattenToString(),
                lastUpdateTime = updateTime,
                launchCount = initialLaunchCount,
                iconBytes = iconBytes
            )
        )
    }

    /**
     * Remove an app from memory and database.
     */
    private fun removePackage(packageName: String) {

        scope.launch {

            synchronized(appMap) {

                appMap.remove(packageName)

                iconCache.remove(packageName)
            }

            database.delete(packageName)

            publishApps()
        }
    }

    /**
     * Publish apps sorted by most used.
     */
    private fun publishApps() {

        val snapshot = synchronized(appMap) {

            appMap.values.sortedWith(
                compareByDescending<AppRecord> {
                    it.launchCount
                }.thenBy {
                    it.label.lowercase()
                }
            )
        }

        _apps.value = snapshot
    }

    /**
     * Launch an app using its cached ComponentName.
     */
    fun launch(app: AppRecord) {

        // Update the ranking immediately.
        app.recordLaunch()

        publishApps()

        // Persist asynchronously.
        scope.launch {
            database.incrementLaunchCount(app.packageName)
        }

        val intent = Intent().apply {

            component = app.componentName

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {

            context.startActivity(intent)

        } catch (_: Exception) {

            refreshPackage(app.packageName)
        }
    }
}