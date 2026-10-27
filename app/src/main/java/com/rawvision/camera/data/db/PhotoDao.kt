package com.rawvision.camera.data.db

import android.content.ContentValues
import android.database.Cursor
import com.rawvision.camera.data.model.LensFacing
import com.rawvision.camera.data.model.Photo

class PhotoDao(private val helper: AppDatabase) {

    fun insert(photo: Photo): Long {
        val cv = ContentValues().apply {
            put("uuid", photo.uuid)
            put("created_at", photo.createdAt)
            put("base_name", photo.baseName)
            put("raw_path", photo.rawPath)
            put("jpeg_path", photo.jpegPath)
            put("thumb_path", photo.thumbPath)
            put("sidecar_path", photo.sidecarPath)
            put("width", photo.width)
            put("height", photo.height)
            put("orientation", photo.orientation)
            put("camera_id", photo.cameraId)
            put("lens_facing", photo.lensFacing.name)
            put("iso", photo.iso)
            put("exposure_ns", photo.exposureNs)
            put("aperture", photo.aperture)
            put("focal_length", photo.focalLengthMm)
            put("focus_distance", photo.focusDistance)
            put("wb_mode", photo.whiteBalanceMode)
            put("wb_kelvin", photo.whiteBalanceKelvin)
            put("ev_comp", photo.exposureCompensation)
            put("flash_mode", photo.flashMode)
            put("zoom", photo.zoomRatio)
            put("capture_mode", photo.captureMode)
            put("latitude", photo.latitude)
            put("longitude", photo.longitude)
            put("altitude", photo.altitude)
            put("gps_time", photo.gpsTime)
            put("author", photo.author)
            put("copyright", photo.copyright)
            put("device_make", photo.deviceMake)
            put("device_model", photo.deviceModel)
            put("favorite", if (photo.favorite) 1 else 0)
            put("rating", photo.rating)
            put("notes", photo.notes)
        }
        return helper.writableDatabase.insertOrThrow("photos", null, cv)
    }

    fun all(includeDeleted: Boolean = false): List<Photo> {
        val where = if (includeDeleted) null else "p.deleted_at IS NULL"
        return query(where, null)
    }

    fun favorites(): List<Photo> = query("p.deleted_at IS NULL AND p.favorite = 1", null)

    fun byId(id: Long): Photo? = query("p.id = ?", arrayOf(id.toString())).firstOrNull()

    fun count(): Int = helper.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM photos WHERE deleted_at IS NULL", null
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun setFavorite(id: Long, favorite: Boolean) = update(id) { put("favorite", if (favorite) 1 else 0) }
    fun setRating(id: Long, rating: Int) = update(id) { put("rating", rating.coerceIn(0, 5)) }
    fun setNotes(id: Long, notes: String) = update(id) { put("notes", notes) }
    fun setAuthor(id: Long, author: String?, copyright: String?) = update(id) {
        put("author", author); put("copyright", copyright)
    }
    fun setThumb(id: Long, thumbPath: String?) = update(id) { put("thumb_path", thumbPath) }

    fun delete(id: Long) {
        helper.writableDatabase.delete("photos", "id = ?", arrayOf(id.toString()))
    }

    private fun update(id: Long, block: ContentValues.() -> Unit) {
        val cv = ContentValues().apply(block)
        helper.writableDatabase.update("photos", cv, "id = ?", arrayOf(id.toString()))
    }

    private fun query(where: String?, args: Array<String>?): List<Photo> {
        val sql = buildString {
            append(
                "SELECT p.*, (SELECT COUNT(*) FROM edits e WHERE e.photo_id = p.id) AS edit_count FROM photos p"
            )
            if (where != null) append(" WHERE ").append(where)
            append(" ORDER BY p.created_at DESC")
        }
        return helper.readableDatabase.rawQuery(sql, args).use { c ->
            buildList { while (c.moveToNext()) add(c.toPhoto()) }
        }
    }

    private fun Cursor.toPhoto(): Photo = Photo(
        id = long("id"),
        uuid = str("uuid"),
        createdAt = long("created_at"),
        baseName = str("base_name"),
        rawPath = getStringOrNull("raw_path"),
        jpegPath = getStringOrNull("jpeg_path"),
        thumbPath = getStringOrNull("thumb_path"),
        sidecarPath = getStringOrNull("sidecar_path"),
        width = int("width"),
        height = int("height"),
        orientation = int("orientation"),
        cameraId = str("camera_id"),
        lensFacing = runCatching { LensFacing.valueOf(str("lens_facing")) }.getOrDefault(LensFacing.BACK),
        iso = getIntOrNull("iso"),
        exposureNs = getLongOrNull("exposure_ns"),
        aperture = getFloatOrNull("aperture"),
        focalLengthMm = getFloatOrNull("focal_length"),
        focusDistance = getFloatOrNull("focus_distance"),
        whiteBalanceMode = str("wb_mode"),
        whiteBalanceKelvin = getIntOrNull("wb_kelvin"),
        exposureCompensation = float("ev_comp"),
        flashMode = str("flash_mode"),
        zoomRatio = float("zoom"),
        captureMode = str("capture_mode"),
        latitude = getDoubleOrNull("latitude"),
        longitude = getDoubleOrNull("longitude"),
        altitude = getDoubleOrNull("altitude"),
        gpsTime = getLongOrNull("gps_time"),
        author = getStringOrNull("author"),
        copyright = getStringOrNull("copyright"),
        deviceMake = getStringOrNull("device_make"),
        deviceModel = getStringOrNull("device_model"),
        favorite = bool("favorite"),
        rating = int("rating"),
        notes = str("notes"),
        hasEdits = int("edit_count") > 0,
    )
}
