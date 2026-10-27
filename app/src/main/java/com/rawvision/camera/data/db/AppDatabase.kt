package com.rawvision.camera.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.rawvision.camera.data.storage.AppStorage

/**
 * Plain SQLite (no ORM) so the schema is explicit and the database file is a
 * normal file inside the sandbox: `files/db/rawvision.db`.
 *
 * Tables:
 *  - photos        one row per capture with all shooting metadata
 *  - edits         current non-destructive recipe per photo
 *  - edit_history  previous recipes (for "go back")
 *  - exports       rendered JPEGs produced from a photo
 *  - settings      key/value store for every user preference
 *  - camera_presets last used manual values per camera
 */
class AppDatabase(context: Context, storage: AppStorage) :
    SQLiteOpenHelper(context, storage.databaseFile.absolutePath, null, VERSION) {

    val photos = PhotoDao(this)
    val edits = EditDao(this)
    val exports = ExportDao(this)
    val settings = SettingsDao(this)

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE photos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL,
                base_name TEXT NOT NULL,
                raw_path TEXT,
                jpeg_path TEXT,
                thumb_path TEXT,
                sidecar_path TEXT,
                width INTEGER NOT NULL DEFAULT 0,
                height INTEGER NOT NULL DEFAULT 0,
                orientation INTEGER NOT NULL DEFAULT 1,
                camera_id TEXT NOT NULL DEFAULT '',
                lens_facing TEXT NOT NULL DEFAULT 'BACK',
                iso INTEGER,
                exposure_ns INTEGER,
                aperture REAL,
                focal_length REAL,
                focus_distance REAL,
                wb_mode TEXT NOT NULL DEFAULT 'AUTO',
                wb_kelvin INTEGER,
                ev_comp REAL NOT NULL DEFAULT 0,
                flash_mode TEXT NOT NULL DEFAULT 'OFF',
                zoom REAL NOT NULL DEFAULT 1,
                capture_mode TEXT NOT NULL DEFAULT 'AUTO',
                latitude REAL,
                longitude REAL,
                altitude REAL,
                gps_time INTEGER,
                author TEXT,
                copyright TEXT,
                device_make TEXT,
                device_model TEXT,
                favorite INTEGER NOT NULL DEFAULT 0,
                rating INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                deleted_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_photos_created ON photos(created_at DESC)")
        db.execSQL(
            """
            CREATE TABLE edits (
                photo_id INTEGER PRIMARY KEY REFERENCES photos(id) ON DELETE CASCADE,
                params TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE edit_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                photo_id INTEGER NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
                params TEXT NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_history_photo ON edit_history(photo_id, created_at DESC)")
        db.execSQL(
            """
            CREATE TABLE exports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                photo_id INTEGER NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
                path TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                width INTEGER NOT NULL DEFAULT 0,
                height INTEGER NOT NULL DEFAULT 0,
                size_bytes INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE camera_presets (
                camera_id TEXT PRIMARY KEY,
                params TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add ALTER TABLE migrations here as the schema evolves.
    }

    companion object {
        const val VERSION = 1
    }
}
