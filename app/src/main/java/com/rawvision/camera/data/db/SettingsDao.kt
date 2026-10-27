package com.rawvision.camera.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/** Generic key/value store backing [com.rawvision.camera.data.repo.SettingsRepository]. */
class SettingsDao(private val helper: AppDatabase) {

    fun get(key: String): String? = helper.readableDatabase.rawQuery(
        "SELECT value FROM settings WHERE key = ?", arrayOf(key)
    ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun all(): Map<String, String> = helper.readableDatabase.rawQuery(
        "SELECT key, value FROM settings", null
    ).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
    }

    fun put(key: String, value: String) {
        helper.writableDatabase.insertWithOnConflict(
            "settings", null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun putAll(values: Map<String, String>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            values.forEach { (k, v) -> put(k, v) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getPreset(cameraId: String): String? = helper.readableDatabase.rawQuery(
        "SELECT params FROM camera_presets WHERE camera_id = ?", arrayOf(cameraId)
    ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun putPreset(cameraId: String, json: String) {
        helper.writableDatabase.insertWithOnConflict(
            "camera_presets", null,
            ContentValues().apply {
                put("camera_id", cameraId)
                put("params", json)
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }
}
