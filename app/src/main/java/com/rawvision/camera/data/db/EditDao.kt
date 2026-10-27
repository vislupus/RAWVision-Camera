package com.rawvision.camera.data.db

import android.content.ContentValues
import com.rawvision.camera.data.model.EditParams
import com.rawvision.camera.data.model.EditVersion

class EditDao(private val helper: AppDatabase) {

    fun current(photoId: Long): EditParams? = helper.readableDatabase.rawQuery(
        "SELECT params FROM edits WHERE photo_id = ?", arrayOf(photoId.toString())
    ).use { c -> if (c.moveToFirst()) EditParams.fromJson(c.getString(0)) else null }

    /** Saves the recipe and pushes the previous one into the history. */
    fun save(photoId: Long, params: EditParams, label: String = "", snapshot: Boolean = true) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val previous = current(photoId)
            val now = System.currentTimeMillis()
            if (snapshot && previous != null && previous != params) {
                db.insert("edit_history", null, ContentValues().apply {
                    put("photo_id", photoId)
                    put("params", previous.toJson())
                    put("label", label)
                    put("created_at", now)
                })
                // Keep the history bounded.
                db.execSQL(
                    "DELETE FROM edit_history WHERE photo_id = ? AND id NOT IN " +
                        "(SELECT id FROM edit_history WHERE photo_id = ? ORDER BY created_at DESC LIMIT 50)",
                    arrayOf(photoId, photoId)
                )
            }
            if (params.isIdentity) {
                db.delete("edits", "photo_id = ?", arrayOf(photoId.toString()))
            } else {
                db.insertWithOnConflict(
                    "edits", null,
                    ContentValues().apply {
                        put("photo_id", photoId)
                        put("params", params.toJson())
                        put("updated_at", now)
                    },
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun history(photoId: Long): List<EditVersion> = helper.readableDatabase.rawQuery(
        "SELECT id, photo_id, params, label, created_at FROM edit_history WHERE photo_id = ? ORDER BY created_at DESC",
        arrayOf(photoId.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                EditVersion(
                    id = c.getLong(0),
                    photoId = c.getLong(1),
                    params = EditParams.fromJson(c.getString(2)),
                    label = c.getString(3),
                    createdAt = c.getLong(4),
                )
            )
        }
    }

    fun clear(photoId: Long) {
        val db = helper.writableDatabase
        db.delete("edits", "photo_id = ?", arrayOf(photoId.toString()))
        db.delete("edit_history", "photo_id = ?", arrayOf(photoId.toString()))
    }
}
