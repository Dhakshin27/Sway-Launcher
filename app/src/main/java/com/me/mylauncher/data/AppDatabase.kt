package com.me.mylauncher.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class StoredApp(
    val packageName: String,
    val label: String,
    val componentName: String,
    val lastUpdateTime: Long,
    val launchCount: Int,
    val iconBytes: ByteArray
)

class AppDatabase(context: Context) :
    SQLiteOpenHelper(context, "app_icons.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE apps (
                package_name TEXT PRIMARY KEY,
                label TEXT NOT NULL,
                component_name TEXT NOT NULL,
                last_update_time INTEGER NOT NULL,
                icon BLOB NOT NULL,
                launch_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                ALTER TABLE apps
                ADD COLUMN launch_count INTEGER NOT NULL DEFAULT 0
                """.trimIndent()
            )
        }
    }

    fun getAllApps(): List<StoredApp> {
        val result = mutableListOf<StoredApp>()

        readableDatabase.rawQuery(
            """
            SELECT
                package_name,
                label,
                component_name,
                last_update_time,
                icon,
                launch_count
            FROM apps
            ORDER BY label COLLATE NOCASE
            """.trimIndent(),
            null
        ).use { cursor ->

            while (cursor.moveToNext()) {
                result.add(
                    StoredApp(
                        packageName = cursor.getString(0),
                        label = cursor.getString(1),
                        componentName = cursor.getString(2),
                        lastUpdateTime = cursor.getLong(3),
                        iconBytes = cursor.getBlob(4),
                        launchCount = cursor.getInt(5)
                    )
                )
            }
        }

        return result
    }

    fun clearAllIcons() {
        writableDatabase.execSQL(
            "UPDATE apps SET icon = X''"
        )
    }
    /**
     * Insert a new app or update its metadata.
     *
     * Existing launch_count is preserved when refreshing
     * an app's label, icon or component.
     */
    fun upsert(app: StoredApp) {
        val values = ContentValues().apply {
            put("label", app.label)
            put("component_name", app.componentName)
            put("last_update_time", app.lastUpdateTime)
            put("icon", app.iconBytes)
        }

        val updated = writableDatabase.update(
            "apps",
            values,
            "package_name = ?",
            arrayOf(app.packageName)
        )

        if (updated == 0) {
            val insertValues = ContentValues().apply {
                put("package_name", app.packageName)
                put("label", app.label)
                put("component_name", app.componentName)
                put("last_update_time", app.lastUpdateTime)
                put("icon", app.iconBytes)
                put("launch_count", app.launchCount)
            }

            writableDatabase.insertWithOnConflict(
                "apps",
                null,
                insertValues,
                SQLiteDatabase.CONFLICT_IGNORE
            )
        }
    }

    /**
     * Increment directly in SQLite.
     * This avoids overwriting a newer count with an older value.
     */
    fun incrementLaunchCount(packageName: String) {
        writableDatabase.execSQL(
            """
            UPDATE apps
            SET launch_count = launch_count + 1
            WHERE package_name = ?
            """.trimIndent(),
            arrayOf(packageName)
        )
    }

    fun delete(packageName: String) {
        writableDatabase.delete(
            "apps",
            "package_name = ?",
            arrayOf(packageName)
        )
    }

    /**
     * Remove database entries for packages no longer installed.
     * Deletes individually to avoid SQLite's bind-variable limit.
     */
    fun deleteAllExcept(installedPackages: Set<String>) {
        val db = writableDatabase

        db.beginTransaction()

        try {
            val existingPackages = mutableListOf<String>()

            db.rawQuery(
                "SELECT package_name FROM apps",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    existingPackages.add(cursor.getString(0))
                }
            }

            for (packageName in existingPackages) {
                if (packageName !in installedPackages) {
                    db.delete(
                        "apps",
                        "package_name = ?",
                        arrayOf(packageName)
                    )
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}