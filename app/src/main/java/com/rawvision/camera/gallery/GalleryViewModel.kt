package com.rawvision.camera.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rawvision.camera.data.AppContainer
import com.rawvision.camera.data.model.ExportRecord
import com.rawvision.camera.data.model.Photo
import com.rawvision.camera.editor.Exporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class GalleryFilter { ALL, FAVORITES, EDITED, RAW }

class GalleryViewModel(val container: AppContainer) : ViewModel() {

    val photos = container.photos.photos
    val thumbnails = container.thumbnails

    private val _filter = MutableStateFlow(GalleryFilter.ALL)
    val filter: StateFlow<GalleryFilter> = _filter.asStateFlow()

    private val _exports = MutableStateFlow<List<ExportRecord>>(emptyList())
    val exports: StateFlow<List<ExportRecord>> = _exports.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { container.photos.refresh() }
    }

    fun setFilter(f: GalleryFilter) { _filter.value = f }

    fun filtered(list: List<Photo>, f: GalleryFilter): List<Photo> = when (f) {
        GalleryFilter.ALL -> list
        GalleryFilter.FAVORITES -> list.filter { it.favorite }
        GalleryFilter.EDITED -> list.filter { it.hasEdits }
        GalleryFilter.RAW -> list.filter { it.isRaw }
    }

    fun loadExports(photoId: Long) {
        viewModelScope.launch { _exports.value = container.photos.exports(photoId) }
    }

    fun toggleFavorite(photo: Photo) {
        viewModelScope.launch { container.photos.setFavorite(photo.id, !photo.favorite) }
    }

    fun setRating(photo: Photo, rating: Int) {
        viewModelScope.launch { container.photos.setRating(photo.id, rating) }
    }

    fun setNotes(photo: Photo, notes: String) {
        viewModelScope.launch { container.photos.setNotes(photo.id, notes) }
    }

    fun setAuthor(photo: Photo, author: String, copyright: String) {
        viewModelScope.launch { container.photos.setAuthor(photo.id, author.ifBlank { null }, copyright.ifBlank { null }) }
    }

    fun delete(photo: Photo, onDone: () -> Unit) {
        viewModelScope.launch {
            photo.thumbPath?.let { thumbnails.evict(it) }
            container.photos.delete(photo.id)
            onDone()
        }
    }

    fun deleteExport(record: ExportRecord) {
        viewModelScope.launch {
            container.photos.deleteExport(record)
            _exports.value = container.photos.exports(record.photoId)
        }
    }

    /** Renders the photo with its current recipe (or as-is) and hands back the JPEG to share. */
    fun exportForShare(photo: Photo, onReady: (File) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val params = container.photos.currentEdit(photo.id)
                val exporter = Exporter(container.storage, container.photos)
                val result = exporter.export(photo, params, container.settings.current.jpegQuality)
                _exports.value = container.photos.exports(photo.id)
                onReady(result.file)
            } catch (e: Exception) {
                _message.value = e.message ?: e.javaClass.simpleName
            } finally {
                _busy.value = false
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GalleryViewModel(container) as T
    }
}
