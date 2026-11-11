package com.me.mylauncher.sections

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.ContactsContract
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import com.me.mylauncher.data.AppRepository
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
// ======================================================================
// CONSTANTS
// ======================================================================

private const val CONTACT_PREFS = "quick_contacts_preferences"
private const val MAX_CONTACTS = 3

private const val WEATHER_PREFS = "dashboard_weather_prefs"
private const val WEATHER_TTL_MS = 30 * 60 * 1000L
private const val LOCATION_TTL_MS = 3 * 60 * 60 * 1000L
private const val LOCATION_FRESH_MS = 2 * 60 * 60 * 1000L

private const val STEPS_PREFS = "dashboard_steps_prefs"
private const val KEY_DAY = "day"
private const val KEY_BASELINE = "baseline"
private const val KEY_CARRIED = "carried"
private const val KEY_LAST = "last"
private const val KEY_GOAL = "goal"
private const val DEFAULT_GOAL = 10_000

private const val PAY_PREFS = "dashboard_payment_prefs"
private const val KEY_PAY_PACKAGE = "package"

// ======================================================================
// MEDIA LISTENER SERVICE
// ======================================================================

class DashboardMediaListenerService : NotificationListenerService()

// ======================================================================
// ROOT: CLEAN 4-TIER ARCHITECTURAL DASHBOARD
// ======================================================================

@Composable
fun PersonalDashboard(isActive: Boolean = true) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(LauncherColors.OuterFrameShape)
            .background(LauncherColors.Background)
            .border(1.dp, LauncherColors.Border, LauncherColors.OuterFrameShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // TIER 1: Unified Ambient Telemetry Console (Weather + Steps in one sleek surface)
        Row(
            modifier = Modifier
                .weight(1.22f)
                .fillMaxWidth()
                .clip(LauncherColors.ModuleShape)
                .background(LauncherColors.Surface)
                .border(1.dp, LauncherColors.Border, LauncherColors.ModuleShape)
        ) {
            WeatherPane(
                active = isActive,
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )

            // Crisp Architectural Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 14.dp)
                    .background(LauncherColors.Border)
            )

            StepsPane(
                active = isActive,
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }

        // TIER 2: Full-Width Speed Dial Strip (3 Horizontal Pill Cards)
        ContactsRowSection(
            modifier = Modifier
                .weight(0.82f)
                .fillMaxWidth()
        )

        // TIER 3: Full-Width UPI Express Dock
        ScanPayBarSection(
            modifier = Modifier
                .weight(0.76f)
                .fillMaxWidth()
        )

        // TIER 4: Bottom Now Playing Studio Deck
        MusicSection(
            active = isActive,
            modifier = Modifier
                .weight(0.95f)
                .fillMaxWidth()
        )
    }
}

// ======================================================================
// SHARED UI PRIMITIVES
// ======================================================================

@Composable
private fun SubtleTagButton(
    text: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LauncherColors.Elevated)
            .border(1.dp, LauncherColors.Border, RoundedCornerShape(6.dp))
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = LauncherColors.TextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun InlineStatePrompt(
    title: String,
    subtitle: String,
    action: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = action != null, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = LauncherColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = LauncherColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LauncherColors.Elevated)
                    .border(1.dp, LauncherColors.Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = action,
                    color = LauncherColors.Accent,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}

@Composable
private fun dashboardFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LauncherColors.TextPrimary,
    unfocusedTextColor = LauncherColors.TextPrimary,
    focusedBorderColor = LauncherColors.Accent,
    unfocusedBorderColor = LauncherColors.TextTertiary,
    cursorColor = LauncherColors.Accent
)

private fun todayEpochDay(): Long {
    val now = System.currentTimeMillis()
    return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
}

@Suppress("DEPRECATION")
private fun appInfo(pm: PackageManager, packageName: String): ApplicationInfo? = try {
    if (Build.VERSION.SDK_INT >= 33) {
        pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    } else {
        pm.getApplicationInfo(packageName, 0)
    }
} catch (e: Exception) {
    null
}

// ======================================================================
// 1A. WEATHER PANE (LEFT HALF OF TOP CONSOLE)
// ======================================================================

private data class WeatherData(
    val temp: Double,
    val feelsLike: Double,
    val high: Double,
    val low: Double,
    val code: Int,
    val isDay: Boolean,
    val place: String,
    val fetchedAt: Long
)

private data class SavedPlace(
    val lat: Double,
    val lon: Double,
    val name: String,
    val manual: Boolean,
    val savedAt: Long
)

private data class GeoResult(
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double
)

private sealed interface PlaceResult {
    class Found(val place: SavedPlace) : PlaceResult
    object NoPermission : PlaceResult
    object Unavailable : PlaceResult
}

@Composable
private fun WeatherPane(active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) {
        context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    val jobHolder = remember { arrayOfNulls<Job>(1) }

    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var needsLocation by remember { mutableStateOf(false) }
    var locationUnavailable by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }

    fun refresh(force: Boolean) {
        if (jobHolder[0]?.isActive == true) return
        jobHolder[0] = scope.launch {
            loading = true
            try {
                val cached = withContext(Dispatchers.IO) { readCachedWeather(prefs) }
                if (weather == null) weather = cached

                val fresh = cached != null &&
                        System.currentTimeMillis() - cached.fetchedAt < WEATHER_TTL_MS
                if (!force && fresh) {
                    failed = false
                    return@launch
                }

                when (val result = withContext(Dispatchers.IO) { resolvePlace(context, prefs, force) }) {
                    is PlaceResult.Found -> {
                        needsLocation = false
                        locationUnavailable = false
                        val data = withContext(Dispatchers.IO) {
                            fetchWeather(result.place).also { writeCachedWeather(prefs, it) }
                        }
                        weather = data
                        failed = false
                    }
                    PlaceResult.NoPermission -> needsLocation = true
                    PlaceResult.Unavailable -> locationUnavailable = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            } finally {
                loading = false
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) {
            needsLocation = false
            refresh(true)
        }
    }

    DisposableEffect(lifecycleOwner, active) {
        val observer = LifecycleEventObserver { _, event ->
            if (active && event == Lifecycle.Event.ON_START) refresh(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val data = weather
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header: City Pill + Refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClickLabel = "Choose city") {
                        showCityDialog = true
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = data?.place?.ifBlank { "Location" } ?: "WEATHER",
                    color = LauncherColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "▾",
                    color = LauncherColors.Accent,
                    fontSize = 10.sp
                )
            }

            SubtleTagButton("↻", "Refresh weather") { refresh(true) }
        }

        // Main Body
        when {
            data != null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${data.temp.roundToInt()}°",
                        color = LauncherColors.TextPrimary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = LauncherColors.Display,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = weatherGlyph(data.code, data.isDay),
                        color = LauncherColors.Accent,
                        fontSize = 26.sp,
                        modifier = Modifier.semantics { contentDescription = weatherLabel(data.code) }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = weatherLabel(data.code),
                            color = LauncherColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (failed) {
                            Text(
                                text = "OFFLINE",
                                color = LauncherColors.Danger,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    val hi = data.high.takeUnless { it.isNaN() }?.let { "H:${it.roundToInt()}°" }
                    val lo = data.low.takeUnless { it.isNaN() }?.let { "L:${it.roundToInt()}°" }
                    val fl = data.feelsLike.takeUnless { it.isNaN() }?.let { "FL:${it.roundToInt()}°" }
                    val metrics = listOfNotNull(hi, lo, fl).joinToString("  ")

                    Text(
                        text = metrics,
                        color = LauncherColors.TextTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            needsLocation -> InlineStatePrompt(
                title = "Location",
                subtitle = if (permissionDenied) "Pick a city manually" else "Allow GPS or pick city",
                action = if (permissionDenied) "CITY" else "ALLOW"
            ) {
                if (permissionDenied) showCityDialog = true
                else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            locationUnavailable -> InlineStatePrompt(
                title = "No GPS fix",
                subtitle = "Select your city manually",
                action = "CITY"
            ) { showCityDialog = true }

            failed -> InlineStatePrompt(
                title = "Offline",
                subtitle = "Tap to retry weather",
                action = "RETRY"
            ) { refresh(true) }

            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (loading) "Updating…" else "—",
                    color = LauncherColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showCityDialog) {
        CityDialog(
            onDismiss = { showCityDialog = false },
            onPick = { picked ->
                showCityDialog = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        savePlace(
                            prefs,
                            SavedPlace(picked.lat, picked.lon, picked.name, true, System.currentTimeMillis())
                        )
                    }
                    refresh(true)
                }
            },
            onUseDevice = {
                showCityDialog = false
                scope.launch {
                    withContext(Dispatchers.IO) { clearSavedPlace(prefs) }
                    if (hasLocationPermission(context)) {
                        refresh(true)
                    } else {
                        locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }
            }
        )
    }
}

@Composable
private fun CityDialog(
    onDismiss: () -> Unit,
    onPick: (GeoResult) -> Unit,
    onUseDevice: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun search() {
        val q = query.trim()
        if (q.length < 2 || searching) return
        searching = true
        message = null
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { searchCities(q) }
                results = found
                if (found.isEmpty()) message = "No matches"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Search failed. Check your connection."
            } finally {
                searching = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LauncherColors.Surface,
        title = { Text("Select City", color = LauncherColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search city", color = LauncherColors.TextTertiary) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    colors = dashboardFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (searching) {
                    Text("Searching…", color = LauncherColors.TextSecondary, fontSize = 12.sp)
                }
                message?.let {
                    Text(it, color = LauncherColors.TextSecondary, fontSize = 12.sp)
                }

                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(results) { r ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onPick(r) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(r.name, color = LauncherColors.TextPrimary, fontSize = 14.sp)
                            if (r.detail.isNotEmpty()) {
                                Text(r.detail, color = LauncherColors.TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }

                TextButton(onClick = onUseDevice) {
                    Text("USE DEVICE LOCATION", color = LauncherColors.Accent, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { search() }) {
                Text("SEARCH", color = LauncherColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LauncherColors.TextSecondary)
            }
        }
    )
}

private fun weatherLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75 -> "Snow"
    77 -> "Snow grains"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm, hail"
    else -> "Unknown"
}

private fun weatherGlyph(code: Int, isDay: Boolean): String = when (code) {
    0, 1 -> if (isDay) "☀\uFE0E" else "☾\uFE0E"
    2, 3 -> "☁\uFE0E"
    45, 48 -> "≋"
    in 51..67, in 80..82 -> "☂\uFE0E"
    in 71..77, 85, 86 -> "❄\uFE0E"
    95, 96, 99 -> "ϟ"
    else -> "☁\uFE0E"
}

private fun readCachedWeather(p: SharedPreferences): WeatherData? {
    val raw = p.getString("weather_json", null) ?: return null
    return try {
        val o = JSONObject(raw)
        WeatherData(
            temp = o.getDouble("t"),
            feelsLike = o.optDouble("f", Double.NaN),
            high = o.optDouble("h", Double.NaN),
            low = o.optDouble("l", Double.NaN),
            code = o.getInt("c"),
            isDay = o.optBoolean("d", true),
            place = o.optString("p", ""),
            fetchedAt = o.getLong("at")
        )
    } catch (e: Exception) {
        null
    }
}

private fun writeCachedWeather(p: SharedPreferences, w: WeatherData) {
    try {
        val o = JSONObject()
            .put("t", w.temp)
            .put("c", w.code)
            .put("d", w.isDay)
            .put("p", w.place)
            .put("at", w.fetchedAt)
        if (!w.feelsLike.isNaN()) o.put("f", w.feelsLike)
        if (!w.high.isNaN()) o.put("h", w.high)
        if (!w.low.isNaN()) o.put("l", w.low)
        p.edit().putString("weather_json", o.toString()).apply()
    } catch (e: Exception) {
        // Cache write failure is non-fatal.
    }
}

private fun readSavedPlace(p: SharedPreferences): SavedPlace? {
    val lat = p.getString("p_lat", null)?.toDoubleOrNull() ?: return null
    val lon = p.getString("p_lon", null)?.toDoubleOrNull() ?: return null
    return SavedPlace(
        lat = lat,
        lon = lon,
        name = p.getString("p_name", "") ?: "",
        manual = p.getBoolean("p_manual", false),
        savedAt = p.getLong("p_time", 0L)
    )
}

private fun savePlace(p: SharedPreferences, place: SavedPlace) {
    p.edit()
        .putString("p_lat", place.lat.toString())
        .putString("p_lon", place.lon.toString())
        .putString("p_name", place.name)
        .putBoolean("p_manual", place.manual)
        .putLong("p_time", place.savedAt)
        .apply()
}

private fun clearSavedPlace(p: SharedPreferences) {
    p.edit()
        .remove("p_lat").remove("p_lon").remove("p_name")
        .remove("p_manual").remove("p_time")
        .apply()
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

private suspend fun resolvePlace(
    context: Context,
    prefs: SharedPreferences,
    force: Boolean
): PlaceResult {
    val saved = readSavedPlace(prefs)
    if (saved != null && saved.manual) return PlaceResult.Found(saved)

    if (!hasLocationPermission(context)) {
        return if (saved != null) PlaceResult.Found(saved) else PlaceResult.NoPermission
    }

    val now = System.currentTimeMillis()
    if (!force && saved != null && now - saved.savedAt < LOCATION_TTL_MS) {
        return PlaceResult.Found(saved)
    }

    val loc = getDeviceLocation(context)
        ?: return if (saved != null) PlaceResult.Found(saved) else PlaceResult.Unavailable

    val name = if (saved != null &&
        distanceMeters(saved.lat, saved.lon, loc.latitude, loc.longitude) < 3000f
    ) {
        saved.name
    } else {
        reverseGeocode(context, loc.latitude, loc.longitude) ?: "Current location"
    }

    val place = SavedPlace(loc.latitude, loc.longitude, name, false, now)
    savePlace(prefs, place)
    return PlaceResult.Found(place)
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val out = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, out)
    return out[0]
}

@SuppressLint("MissingPermission")
private suspend fun getDeviceLocation(context: Context): Location? {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    return try {
        val lastKnown = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < LOCATION_FRESH_MS) {
            lastKnown
        } else {
            val current = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                withTimeoutOrNull(15_000L) {
                    currentLocation(context, lm, LocationManager.NETWORK_PROVIDER)
                }
            } else {
                null
            }
            current ?: lastKnown
        }
    } catch (e: SecurityException) {
        null
    }
}

@SuppressLint("MissingPermission")
private suspend fun currentLocation(
    context: Context,
    lm: LocationManager,
    provider: String
): Location? = suspendCancellableCoroutine { cont ->
    val signal = CancellationSignal()
    cont.invokeOnCancellation { signal.cancel() }
    LocationManagerCompat.getCurrentLocation(
        lm,
        provider,
        signal,
        ContextCompat.getMainExecutor(context),
        Consumer<Location?> { location -> if (cont.isActive) cont.resume(location) }
    )
}

@Suppress("DEPRECATION")
private suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    return try {
        val list: List<Address>? = if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(8_000L) {
                suspendCancellableCoroutine<List<Address>> { cont ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(emptyList())
                        }
                    })
                }
            }
        } else {
            geocoder.getFromLocation(lat, lon, 1)
        }
        val a = list?.firstOrNull() ?: return null
        a.locality ?: a.subAdminArea ?: a.adminArea
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

private fun httpGet(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.requestMethod = "GET"
        c.setRequestProperty("Accept", "application/json")
        val code = c.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

private fun fetchWeather(place: SavedPlace): WeatherData {
    val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${String.format(Locale.US, "%.4f", place.lat)}" +
            "&longitude=${String.format(Locale.US, "%.4f", place.lon)}" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&forecast_days=1&timezone=auto"

    val root = JSONObject(httpGet(url))
    val current = root.getJSONObject("current")
    val daily = root.optJSONObject("daily")

    fun firstOfDaily(key: String): Double {
        val arr = daily?.optJSONArray(key) ?: return Double.NaN
        return if (arr.length() > 0 && !arr.isNull(0)) arr.getDouble(0) else Double.NaN
    }

    return WeatherData(
        temp = current.getDouble("temperature_2m"),
        feelsLike = current.optDouble("apparent_temperature", Double.NaN),
        high = firstOfDaily("temperature_2m_max"),
        low = firstOfDaily("temperature_2m_min"),
        code = current.getInt("weather_code"),
        isDay = current.optInt("is_day", 1) == 1,
        place = place.name,
        fetchedAt = System.currentTimeMillis()
    )
}

private fun searchCities(query: String): List<GeoResult> {
    val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${URLEncoder.encode(query, "UTF-8")}&count=6&language=en&format=json"
    val arr = JSONObject(httpGet(url)).optJSONArray("results") ?: return emptyList()
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        GeoResult(
            name = o.getString("name"),
            detail = listOfNotNull(
                o.optString("admin1").ifBlank { null },
                o.optString("country").ifBlank { null }
            ).joinToString(", "),
            lat = o.getDouble("latitude"),
            lon = o.getDouble("longitude")
        )
    }
}

// ======================================================================
// 1B. STEPS PANE (RIGHT HALF OF TOP CONSOLE)
// ======================================================================

private fun hasActivityRecognition(context: Context): Boolean =
    Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED

private fun readStoredSteps(p: SharedPreferences): Int {
    val baseline = p.getLong(KEY_BASELINE, -1L)
    if (p.getLong(KEY_DAY, -1L) != todayEpochDay() || baseline < 0) return 0
    val steps = p.getLong(KEY_CARRIED, 0L) + (p.getLong(KEY_LAST, baseline) - baseline)
    return steps.coerceAtLeast(0L).toInt()
}

private fun applyStepReading(p: SharedPreferences, total: Long): Int {
    val today = todayEpochDay()
    var baseline = p.getLong(KEY_BASELINE, -1L)
    var carried = p.getLong(KEY_CARRIED, 0L)
    val last = p.getLong(KEY_LAST, -1L)

    if (p.getLong(KEY_DAY, -1L) != today || baseline < 0) {
        baseline = total
        carried = 0L
    } else if (total < last) {
        carried += (last - baseline).coerceAtLeast(0L)
        baseline = 0L
    }

    val steps = (carried + (total - baseline)).coerceAtLeast(0L)

    p.edit()
        .putLong(KEY_DAY, today)
        .putLong(KEY_BASELINE, baseline)
        .putLong(KEY_CARRIED, carried)
        .putLong(KEY_LAST, total)
        .apply()

    return steps.toInt()
}

@Composable
private fun StepsPane(active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) {
        context.getSharedPreferences(STEPS_PREFS, Context.MODE_PRIVATE)
    }
    val sensorManager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val sensor = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    val numberFormat = remember { NumberFormat.getIntegerInstance() }

    var permissionGranted by remember { mutableStateOf(hasActivityRecognition(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var steps by remember { mutableIntStateOf(readStoredSteps(prefs)) }
    var goal by remember { mutableIntStateOf(prefs.getInt(KEY_GOAL, DEFAULT_GOAL)) }
    var showGoalDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = hasActivityRecognition(context)
                steps = readStoredSteps(prefs)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(lifecycleOwner, active, permissionGranted, sensor) {
        if (!active || !permissionGranted || sensor == null || sensorManager == null) {
            return@DisposableEffect onDispose {}
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.isNotEmpty()) {
                    steps = applyStepReading(prefs, event.values[0].toLong())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        var registered = false
        fun register() {
            if (!registered) {
                registered = try {
                    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                } catch (e: Exception) {
                    false
                }
            }
        }

        fun unregister() {
            if (registered) {
                try {
                    sensorManager.unregisterListener(listener)
                } catch (e: Exception) {}
                registered = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()
                Lifecycle.Event.ON_STOP -> unregister()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STEPS",
                color = LauncherColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            if (sensor != null && permissionGranted) {
                SubtleTagButton("GOAL", "Set daily step goal") { showGoalDialog = true }
            }
        }

        when {
            sensor == null -> InlineStatePrompt(
                title = "No Sensor",
                subtitle = "Step counter unavailable",
                action = null,
                onClick = {}
            )

            !permissionGranted -> InlineStatePrompt(
                title = "Pedometer",
                subtitle = if (permissionDenied) "Enable in app settings" else "Allow step counting",
                action = if (permissionDenied) "SET" else "ALLOW"
            ) {
                if (permissionDenied) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {}
                } else {
                    permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }

            else -> {
                val fraction = (steps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                val percent = (fraction * 100).roundToInt()

                Text(
                    text = numberFormat.format(steps),
                    color = LauncherColors.TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = LauncherColors.Display,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "/ ${numberFormat.format(goal)}",
                            color = LauncherColors.TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Text(
                            text = "$percent%",
                            color = LauncherColors.Accent,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Sleek Continuous Progress Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(LauncherColors.Elevated)
                            .semantics { contentDescription = "Step goal progress $percent percent" }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(LauncherColors.Accent)
                        )
                    }
                }
            }
        }
    }

    if (showGoalDialog) {
        GoalDialog(
            current = goal,
            onDismiss = { showGoalDialog = false },
            onSave = { newGoal ->
                goal = newGoal
                prefs.edit().putInt(KEY_GOAL, newGoal).apply()
                showGoalDialog = false
            }
        )
    }
}

@Composable
private fun GoalDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 1_000..100_000

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LauncherColors.Surface,
        title = { Text("Daily Step Goal", color = LauncherColors.TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() }.take(6) },
                singleLine = true,
                isError = !valid,
                supportingText = {
                    Text("1,000 – 100,000 steps", color = LauncherColors.TextTertiary)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = dashboardFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { parsed?.let(onSave) }) {
                Text("Save", color = if (valid) LauncherColors.Accent else LauncherColors.TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LauncherColors.TextSecondary)
            }
        }
    )
}

// ======================================================================
// 2. SPEED DIAL ROW (FULL-WIDTH 3-PILL HORIZONTAL STRIP)
// ======================================================================

private data class DashboardContact(
    val name: String,
    val number: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactsRowSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) {
        context.getSharedPreferences(CONTACT_PREFS, Context.MODE_PRIVATE)
    }

    var contacts by remember { mutableStateOf(loadDashboardContacts(preferences)) }
    var selectedContact by remember { mutableStateOf<DashboardContact?>(null) }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            scope.launch {
                val picked = withContext(Dispatchers.IO) { readPickedContact(context, uri) }
                if (picked != null &&
                    contacts.size < MAX_CONTACTS &&
                    contacts.none { it.number == picked.number }
                ) {
                    contacts = contacts + picked
                    saveDashboardContacts(preferences, contacts)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .clip(LauncherColors.ModuleShape)
            .background(LauncherColors.Surface)
            .border(1.dp, LauncherColors.Border, LauncherColors.ModuleShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SPEED DIAL",
                color = LauncherColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "HOLD TO REMOVE",
                color = LauncherColors.TextTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(MAX_CONTACTS) { index ->
                val contact = contacts.getOrNull(index)

                if (contact != null) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(LauncherColors.InteractiveTileShape)
                            .background(LauncherColors.Elevated)
                            .border(1.dp, LauncherColors.Border, LauncherColors.InteractiveTileShape)
                            .semantics { contentDescription = "Call ${contact.name}" }
                            .combinedClickable(
                                role = Role.Button,
                                onClickLabel = "Open dialer",
                                onLongClickLabel = "Remove contact",
                                onClick = {
                                    try {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_DIAL,
                                                Uri.fromParts("tel", contact.number, null)
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (e: ActivityNotFoundException) {}
                                },
                                onLongClick = { selectedContact = contact }
                            )
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(LauncherColors.Accent.copy(alpha = 0.16f))
                                .border(1.dp, LauncherColors.Accent.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.firstOrNull()?.uppercase() ?: "?",
                                color = LauncherColors.Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = contact.name,
                            color = LauncherColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(LauncherColors.InteractiveTileShape)
                            .background(LauncherColors.Elevated.copy(alpha = 0.45f))
                            .border(1.dp, LauncherColors.Border, LauncherColors.InteractiveTileShape)
                            .semantics { contentDescription = "Add favourite contact" }
                            .clickable(role = Role.Button) {
                                try {
                                    contactPicker.launch(
                                        Intent(
                                            Intent.ACTION_PICK,
                                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                        )
                                    )
                                } catch (e: ActivityNotFoundException) {}
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "+ Add",
                            color = LauncherColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    selectedContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { selectedContact = null },
            containerColor = LauncherColors.Surface,
            title = { Text(contact.name, color = LauncherColors.TextPrimary) },
            text = {
                Text(
                    "Remove this contact from Speed Dial?",
                    color = LauncherColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        contacts = contacts.filter { it.number != contact.number }
                        saveDashboardContacts(preferences, contacts)
                        selectedContact = null
                    }
                ) {
                    Text("Remove", color = LauncherColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedContact = null }) {
                    Text("Cancel", color = LauncherColors.TextSecondary)
                }
            }
        )
    }
}

private fun readPickedContact(context: Context, uri: Uri): DashboardContact? {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val number = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: return@use null
            val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "Contact"
            DashboardContact(name, number)
        }
    } catch (e: Exception) {
        null
    }
}

private fun loadDashboardContacts(preferences: SharedPreferences): List<DashboardContact> {
    return (0 until MAX_CONTACTS).mapNotNull { index ->
        val name = preferences.getString("name_$index", null)
        val number = preferences.getString("number_$index", null)
        if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
            DashboardContact(name, number)
        } else null
    }
}

private fun saveDashboardContacts(
    preferences: SharedPreferences,
    contacts: List<DashboardContact>
) {
    preferences.edit().apply {
        repeat(MAX_CONTACTS) { index ->
            if (index < contacts.size) {
                putString("name_$index", contacts[index].name)
                putString("number_$index", contacts[index].number)
            } else {
                remove("name_$index")
                remove("number_$index")
            }
        }
    }.apply()
}

// ======================================================================
// 3. SCAN & PAY EXPRESS DOCK (FULL-WIDTH ACTION BAR)
// ======================================================================

private data class PayApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

private data class ScanTarget(
    val shortcutId: String,
    val label: String
)

@Composable
private fun ScanPayBarSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) {
        AppRepository.get(context)
    }


    val prefs = remember(context) {
        context.getSharedPreferences(PAY_PREFS, Context.MODE_PRIVATE)
    }

    var selectedPackage by remember { mutableStateOf(prefs.getString(KEY_PAY_PACKAGE, null)) }
    val repositoryApps by repository.apps.collectAsState()

    val launcherRecord = remember(repositoryApps, selectedPackage) {
        repositoryApps.firstOrNull {
            it.packageName == selectedPackage
        }
    }

    val emptyIconFlow = remember {
        MutableStateFlow<Bitmap?>(null)
    }

    val iconFlow = launcherRecord?.icon ?: emptyIconFlow
    val launcherIcon by iconFlow.collectAsState()
    var payApp by remember { mutableStateOf<PayApp?>(null) }
    var scanTarget by remember { mutableStateOf<ScanTarget?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var showChooser by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPackage) {
        val pkg = selectedPackage
        unavailable = false
        if (pkg == null) {
            payApp = null
            scanTarget = null
            return@LaunchedEffect
        }
        val (app, scan) = withContext(Dispatchers.IO) {
            resolvePayApp(context, pkg) to findScanShortcut(context, pkg)
        }
        payApp = app
        scanTarget = if (app != null) scan else null
        unavailable = app == null
    }

    val pkg = selectedPackage
    val app = payApp

    Row(
        modifier = modifier
            .clip(LauncherColors.ModuleShape)
            .background(LauncherColors.Surface)
            .border(1.dp, LauncherColors.Border, LauncherColors.ModuleShape)
            .clickable(role = Role.Button) {
                if (pkg == null || app == null || unavailable) {
                    showChooser = true
                } else if (!launchPayApp(context, pkg, scanTarget)) {
                    unavailable = true
                    showChooser = true
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: App Icon + Title & Mode

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (launcherIcon != null) {
                Image(
                    bitmap = launcherIcon!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(
                        color = LauncherColors.Accent,
                        blendMode = BlendMode.SrcIn
                    )
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LauncherColors.Elevated)
                        .border(1.dp, LauncherColors.Border, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "₹",
                        color = LauncherColors.Accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = when {
                        pkg == null -> "Quick UPI Scanner"
                        app != null -> app.label
                        unavailable -> "Payment app unavailable"
                        else -> "Loading…"
                    },
                    color = LauncherColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        pkg == null -> "Tap to link your default UPI app"
                        unavailable -> "Tap to choose another UPI app"
                        scanTarget != null -> "Direct QR Scanner ready"
                        else -> "Opens app directly"
                    },
                    color = LauncherColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right: Switch App Pill + Primary Scan CTA
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pkg != null) {
                SubtleTagButton("APP", "Change payment app") { showChooser = true }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(LauncherColors.Accent)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (pkg == null) "LINK UPI" else "SCAN QR ↗",
                    color = LauncherColors.OnAccent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
            }
        }
    }

    if (showChooser) {
        PayChooserDialog(
            currentPackage = selectedPackage,
            onDismiss = { showChooser = false },
            onSelect = { chosen ->
                prefs.edit().putString(KEY_PAY_PACKAGE, chosen.packageName).apply()
                selectedPackage = chosen.packageName
                showChooser = false
            }
        )
    }
}

@Composable
private fun PayChooserDialog(
    currentPackage: String?,
    onDismiss: () -> Unit,
    onSelect: (PayApp) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<PayApp>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { discoverUpiApps(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LauncherColors.Surface,
        title = { Text("Select Payment App", color = LauncherColors.TextPrimary) },
        text = {
            val list = apps
            when {
                list == null -> Text("Searching…", color = LauncherColors.TextSecondary)
                list.isEmpty() -> Text("No UPI apps found.", color = LauncherColors.TextSecondary)
                else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(list, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { onSelect(app) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                            Text(
                                text = app.label,
                                color = if (app.packageName == currentPackage) LauncherColors.Accent
                                else LauncherColors.TextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LauncherColors.TextSecondary)
            }
        }
    )
}

@Suppress("DEPRECATION")
private fun discoverUpiApps(context: Context): List<PayApp> {
    val pm = context.packageManager
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=probe@upi&pn=probe&cu=INR"))
    val infos = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(probe, 0)
        }
    } catch (e: Exception) {
        emptyList()
    }

    return infos
        .mapNotNull { it.activityInfo?.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .mapNotNull { info ->
            try {
                PayApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                )
            } catch (e: Exception) {
                null
            }
        }
        .sortedBy { it.label.lowercase() }
}

private fun resolvePayApp(context: Context, packageName: String): PayApp? {
    val pm = context.packageManager
    val info = appInfo(pm, packageName) ?: return null
    if (!info.enabled) return null
    if (pm.getLaunchIntentForPackage(packageName) == null) return null
    return try {
        PayApp(
            packageName = packageName,
            label = info.loadLabel(pm).toString(),
            icon = info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
        )
    } catch (e: Exception) {
        PayApp(packageName, info.loadLabel(pm).toString(), null)
    }
}

private fun findScanShortcut(context: Context, packageName: String): ScanTarget? {
    if (Build.VERSION.SDK_INT < 25) return null
    return try {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        if (!launcherApps.hasShortcutHostPermission()) return null

        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
            ?.filter { it.isEnabled }
            .orEmpty()

        fun labelOf(s: android.content.pm.ShortcutInfo): String =
            (s.shortLabel ?: s.longLabel)?.toString().orEmpty()

        val match = shortcuts.firstOrNull { labelOf(it).contains("scan", ignoreCase = true) }
            ?: shortcuts.firstOrNull { labelOf(it).contains("qr", ignoreCase = true) }

        match?.let { ScanTarget(it.id, labelOf(it)) }
    } catch (e: Exception) {
        null
    }
}

private fun launchPayApp(context: Context, packageName: String, scan: ScanTarget?): Boolean {
    if (scan != null && Build.VERSION.SDK_INT >= 25) {
        try {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            if (launcherApps != null) {
                launcherApps.startShortcut(packageName, scan.shortcutId, null, null, Process.myUserHandle())
                return true
            }
        } catch (e: Exception) {
            // Fall through to the app's main activity.
        }
    }
    return try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}

// ======================================================================
// 4. NOW PLAYING STUDIO DECK (BOTTOM FULL-WIDTH CELL)
// ======================================================================

@Immutable
private data class MediaUi(
    val title: String,
    val artist: String,
    val appName: String?,
    val artwork: ImageBitmap?,
    val playing: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean
)

private fun isMediaAccessEnabled(context: Context): Boolean =
    try {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    } catch (e: Exception) {
        false
    }

@Composable
private fun MusicSection(active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAccess by remember { mutableStateOf(isMediaAccessEnabled(context)) }
    var ui by remember { mutableStateOf<MediaUi?>(null) }
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = isMediaAccessEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(lifecycleOwner, active, hasAccess) {
        if (!active || !hasAccess) {
            ui = null
            controller = null
            return@DisposableEffect onDispose {}
        }

        val tracker = MediaTracker(context) { newUi, newController ->
            ui = newUi
            controller = newController
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> tracker.start()
                Lifecycle.Event.ON_STOP -> tracker.stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tracker.stop()
        }
    }

    Box(
        modifier = modifier
            .clip(LauncherColors.ModuleShape)
            .background(LauncherColors.Surface)
            .border(1.dp, LauncherColors.Border, LauncherColors.ModuleShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        val state = ui
        when {
            !hasAccess -> InlineStatePrompt(
                title = "Media Access Needed",
                subtitle = "Enable notification access for live playback controls",
                action = "ENABLE"
            ) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {}
            }

            state == null -> InlineStatePrompt(
                title = "Nothing Playing",
                subtitle = "Tap to launch your default music player",
                action = "MUSIC ↗"
            ) { openMusicApp(context) }

            else -> NowPlayingStudioDeck(
                state = state,
                onOpenPlayer = { openMediaApp(context, controller) },
                onPrevious = { safeTransport { controller?.transportControls?.skipToPrevious() } },
                onToggle = {
                    safeTransport {
                        val controls = controller?.transportControls
                        if (state.playing) controls?.pause() else controls?.play()
                    }
                },
                onNext = { safeTransport { controller?.transportControls?.skipToNext() } }
            )
        }
    }
}

@Composable
private fun NowPlayingStudioDeck(
    state: MediaUi,
    onOpenPlayer: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left: Album Art + Track Info (Directly on surface, no nested border)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .semantics { contentDescription = "Open ${state.appName ?: "music app"}" }
                .clickable(role = Role.Button, onClick = onOpenPlayer),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LauncherColors.Elevated)
                    .border(1.dp, LauncherColors.Border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (state.artwork != null) {
                    Image(
                        bitmap = state.artwork,
                        contentDescription = "Album artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("♪", color = LauncherColors.Accent, fontSize = 20.sp)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                state.appName?.let { app ->
                    Text(
                        text = app.uppercase(),
                        color = LauncherColors.Accent,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = state.title,
                    color = LauncherColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.artist.isNotEmpty()) {
                    Text(
                        text = state.artist,
                        color = LauncherColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Right: Tactile Transport Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransportButton(Glyph.Previous, "Previous track", state.canPrevious, false, onPrevious)
            TransportButton(
                glyph = if (state.playing) Glyph.Pause else Glyph.Play,
                description = if (state.playing) "Pause" else "Play",
                enabled = true,
                prominent = true,
                onClick = onToggle
            )
            TransportButton(Glyph.Next, "Next track", state.canNext, false, onNext)
        }
    }
}

private enum class Glyph { Play, Pause, Next, Previous }

@Composable
private fun TransportButton(
    glyph: Glyph,
    description: String,
    enabled: Boolean,
    prominent: Boolean,
    onClick: () -> Unit
) {
    val tint = when {
        prominent -> LauncherColors.OnAccent
        enabled -> LauncherColors.TextPrimary
        else -> LauncherColors.TextTertiary
    }
    Box(
        modifier = Modifier
            .size(if (prominent) 44.dp else 36.dp)
            .clip(CircleShape)
            .background(if (prominent) LauncherColors.Accent else LauncherColors.Elevated)
            .border(
                width = 1.dp,
                color = if (prominent) LauncherColors.Accent else LauncherColors.Border,
                shape = CircleShape
            )
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(14.dp)) { drawGlyph(glyph, tint) }
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val w = size.width
    val h = size.height
    when (glyph) {
        Glyph.Play -> drawPath(
            Path().apply {
                moveTo(w * 0.15f, 0f)
                lineTo(w, h / 2f)
                lineTo(w * 0.15f, h)
                close()
            },
            color
        )

        Glyph.Pause -> {
            drawRoundRect(color, Offset(w * 0.1f, 0f), Size(w * 0.27f, h), CornerRadius(2f, 2f))
            drawRoundRect(color, Offset(w * 0.63f, 0f), Size(w * 0.27f, h), CornerRadius(2f, 2f))
        }

        Glyph.Next -> {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * 0.78f, h / 2f)
                    lineTo(0f, h)
                    close()
                },
                color
            )
            drawRoundRect(color, Offset(w * 0.84f, 0f), Size(w * 0.16f, h), CornerRadius(1.5f, 1.5f))
        }

        Glyph.Previous -> {
            drawPath(
                Path().apply {
                    moveTo(w, 0f)
                    lineTo(w * 0.22f, h / 2f)
                    lineTo(w, h)
                    close()
                },
                color
            )
            drawRoundRect(color, Offset(0f, 0f), Size(w * 0.16f, h), CornerRadius(1.5f, 1.5f))
        }
    }
}

private inline fun safeTransport(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        // Session may have died between the tap and the call.
    }
}

private fun openMediaApp(context: Context, controller: MediaController?) {
    if (controller == null) return
    try {
        val pending = controller.sessionActivity
        if (pending != null) {
            pending.send()
            return
        }
    } catch (e: PendingIntent.CanceledException) {
    } catch (e: Exception) {}

    try {
        context.packageManager.getLaunchIntentForPackage(controller.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    } catch (e: Exception) {}
}

private fun openMusicApp(context: Context) {
    try {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {}
}

private class MediaTracker(
    private val context: Context,
    private val onUpdate: (MediaUi?, MediaController?) -> Unit
) {
    private val component = ComponentName(context, DashboardMediaListenerService::class.java)
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val appNames = HashMap<String, String?>()

    private var controllers: List<MediaController> = emptyList()
    private var started = false

    private var artKey: String? = null
    private var artCache: ImageBitmap? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> setControllers(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = reloadSessions()
    }

    fun start() {
        if (started || manager == null) return
        started = true
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            setControllers(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            started = false
            onUpdate(null, null)
        } catch (e: Exception) {
            started = false
            onUpdate(null, null)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {}
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
    }

    private fun reloadSessions() {
        if (!started) return
        try {
            setControllers(manager?.getActiveSessions(component).orEmpty())
        } catch (e: Exception) {
            onUpdate(null, null)
        }
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { runCatching { it.registerCallback(callback) } }
        refresh()
    }

    private fun isPlaying(state: PlaybackState?): Boolean =
        state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING

    private fun refresh() {
        if (!started) return
        try {
            val selected = controllers.firstOrNull { isPlaying(it.playbackState) }
                ?: controllers.firstOrNull { it.metadata != null }

            if (selected == null) {
                onUpdate(null, null)
                return
            }

            val md = selected.metadata
            val state = selected.playbackState
            val playing = isPlaying(state)
            if (md == null && !playing) {
                onUpdate(null, null)
                return
            }

            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            val actions = state?.actions ?: 0L

            onUpdate(
                MediaUi(
                    title = title?.takeIf { it.isNotBlank() } ?: "Unknown track",
                    artist = artist.orEmpty(),
                    appName = appLabel(selected.packageName),
                    artwork = artworkFor(selected.packageName, md, title, artist),
                    playing = playing,
                    canPrevious = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
                    canNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L
                ),
                selected
            )
        } catch (e: Exception) {
            onUpdate(null, null)
        }
    }

    private fun appLabel(packageName: String): String? {
        if (appNames.containsKey(packageName)) return appNames[packageName]
        val label = try {
            val pm = context.packageManager
            appInfo(pm, packageName)?.loadLabel(pm)?.toString()
        } catch (e: Exception) {
            null
        }
        appNames[packageName] = label
        return label
    }

    private fun artworkFor(
        packageName: String,
        md: MediaMetadata?,
        title: String?,
        artist: String?
    ): ImageBitmap? {
        if (md == null) return null
        val raw = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        val key = "$packageName|$title|$artist|${md.getString(MediaMetadata.METADATA_KEY_ALBUM)}|" +
                "${raw?.width}x${raw?.height}"
        if (key == artKey) return artCache

        artKey = key
        artCache = raw?.let { bitmap ->
            try {
                val longest = max(bitmap.width, bitmap.height)
                val limit = 256
                val scaled = if (longest > limit) {
                    val ratio = limit.toFloat() / longest
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                        (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    bitmap
                }
                scaled.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
        return artCache
    }
}

// ======================================================================
// KEPT FOR OTHER LAUNCHER SECTIONS
// ======================================================================

internal fun openDeviceClock(context: Context) {
    val packageName = "com.android.deskclock"

    try {
        val alarmIntent = Intent(
            android.provider.AlarmClock.ACTION_SHOW_ALARMS
        ).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(alarmIntent)
        return
    } catch (_: Exception) {}

    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    try {
        context.startActivity(
            Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        e.printStackTrace()
    }
}