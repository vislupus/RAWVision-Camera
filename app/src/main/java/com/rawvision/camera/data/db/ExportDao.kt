package com.rawvision.camera.data.db

import android.content.ContentValues
import com.rawvision.camera.data.model.ExportRecord

class ExportDao(private val helper: AppDatabase) {

    fun insert(record: ExportRecord): Long = helper.writableDatabase.insertOrThrow(
        "exports", null,
        ContentValues().apply {
            put("photo_id", record.photoId)
            put("path", record.path)
            put("created_at", record.createdAt)
            put("width", record.width)
            put("height", record.height)
            put("size_bytes", record.sizeBytes)
        }
    )

    fun forPhoto(photoId: Long): List<ExportRecord> = helper.readableDatabase.rawQuery(
        "SELECT id, photo_id, path, created_at, width, height, size_bytes FROM exports WHERE photo_id = ? ORDER BY created_at DESC",
        arrayOf(photoId.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                ExportRecord(c.getLong(0), c.getLong(1), c.getString(2), c.getLong(3), c.getInt(4), c.getInt(5), c.getLong(6))
            )
        }
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("exports", "id = ?", arrayOf(id.toString()))
    }
}
