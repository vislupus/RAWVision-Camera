package com.rawvision.camera.data.repo

import com.rawvision.camera.data.db.AppDatabase
import com.rawvision.camera.data.model.EditParams
import com.rawvision.camera.data.model.EditVersion
import com.rawvision.camera.data.model.ExportRecord
import com.rawvision.camera.data.model.Photo
import com.rawvision.camera.data.storage.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single source of truth for photos. Keeps an in-memory [StateFlow] copy of
 * the gallery that is refreshed after every write so screens update instantly.
 */
class PhotoRepository(private val db: AppDatabase, private val storage: AppStorage) {

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _photos.value = db.photos.all()
    }

    suspend fun insert(photo: Photo): Photo = withContext(Dispatchers.IO) {
        val id = db.photos.insert(photo)
        refresh()
        photo.copy(id = id)
    }

    suspend fun get(id: Long): Photo? = withContext(Dispatchers.IO) { db.photos.byId(id) }

    suspend fun setFavorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        db.photos.setFavorite(id, favorite); refresh()
    }

    suspend fun setRating(id: Long, rating: Int) = withContext(Dispatchers.IO) {
        db.photos.setRating(id, rating); refresh()
    }

    suspend fun setNotes(id: Long, notes: String) = withContext(Dispatchers.IO) {
        db.photos.setNotes(id, notes); refresh()
    }

    suspend fun setAuthor(id: Long, author: String?, copyright: String?) = withContext(Dispatchers.IO) {
        db.photos.setAuthor(id, author, copyright); refresh()
    }

    /** Deletes the database row and every file that belongs to the photo. */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val photo = db.photos.byId(id) ?: return@withContext
        db.exports.forPhoto(id).forEach { File(it.path).delete() }
        listOfNotNull(photo.rawPath, photo.jpegPath, photo.thumbPath, photo.sidecarPath)
            .forEach { File(it).delete() }
        db.photos.delete(id)
        refresh()
    }

    // ---- Edits -------------------------------------------------------------

    suspend fun currentEdit(photoId: Long): EditParams = withContext(Dispatchers.IO) {
        db.edits.current(photoId) ?: EditParams()
    }

    /** Persists the recipe. With [snapshot] the previous recipe is kept in the history for "go back". */
    suspend fun saveEdit(photoId: Long, params: EditParams, label: String = "", snapshot: Boolean = true) = withContext(Dispatchers.IO) {
        db.edits.save(photoId, params, label, snapshot)
        refresh()
    }

    suspend fun editHistory(photoId: Long): List<EditVersion> = withContext(Dispatchers.IO) {
        db.edits.history(photoId)
    }

    suspend fun resetEdits(photoId: Long) = withContext(Dispatchers.IO) {
        db.edits.save(photoId, EditParams(), "reset")
        refresh()
    }

    // ---- Exports -----------------------------------------------------------

    suspend fun recordExport(record: ExportRecord): Long = withContext(Dispatchers.IO) { db.exports.insert(record) }

    suspend fun exports(photoId: Long): List<ExportRecord> = withContext(Dispatchers.IO) { db.exports.forPhoto(photoId) }

    suspend fun allExports(): List<Pair<ExportRecord, String>> = withContext(Dispatchers.IO) { db.exports.all() }

    suspend fun deleteExport(record: ExportRecord) = withContext(Dispatchers.IO) {
        File(record.path).delete()
        db.exports.delete(record.id)
    }

    val storageInfo: AppStorage get() = storage
}
