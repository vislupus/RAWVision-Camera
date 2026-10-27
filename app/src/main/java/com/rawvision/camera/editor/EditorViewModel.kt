package com.rawvision.camera.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rawvision.camera.camera.XmpSidecar
import com.rawvision.camera.data.AppContainer
import com.rawvision.camera.data.model.EditParams
import com.rawvision.camera.data.model.EditVersion
import com.rawvision.camera.data.model.Photo
import com.rawvision.camera.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class EditorTab { LIGHT, COLOR, DETAIL, EFFECTS, GEOMETRY }

sealed class EditorEvent {
    data class Exported(val file: File) : EditorEvent()
    data class Error(val message: String) : EditorEvent()
    data object Saved : EditorEvent()
}

/**
 * Holds the recipe being edited, renders the preview and persists changes.
 * The recipe is auto-saved (debounced) so nothing is ever lost; snapshots for
 * the history are taken when the editor is left or on explicit request.
 */
@OptIn(FlowPreview::class)
class EditorViewModel(private val container: AppContainer, private val photoId: Long) : ViewModel() {

    private val exporter = Exporter(container.storage, container.photos)

    private val _photo = MutableStateFlow<Photo?>(null)
    val photo: StateFlow<Photo?> = _photo.asStateFlow()

    private val _params = MutableStateFlow(EditParams())
    val params: StateFlow<EditParams> = _params.asStateFlow()

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    private val _original = MutableStateFlow<Bitmap?>(null)
    val original: StateFlow<Bitmap?> = _original.asStateFlow()

    private val _tab = MutableStateFlow(EditorTab.LIGHT)
    val tab: StateFlow<EditorTab> = _tab.asStateFlow()

    private val _history = MutableStateFlow<List<EditVersion>>(emptyList())
    val history: StateFlow<List<EditVersion>> = _history.asStateFlow()

    private val _busy = MutableStateFlow(true)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    private val undoStack = ArrayDeque<EditParams>()
    private val redoStack = ArrayDeque<EditParams>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var initialParams = EditParams()
    private var base: Bitmap? = null

    init {
        viewModelScope.launch {
            val p = container.photos.get(photoId)
            _photo.value = p
            if (p == null) { _busy.value = false; return@launch }
            val saved = container.photos.currentEdit(photoId)
            initialParams = saved
            _params.value = saved
            _history.value = container.photos.editHistory(photoId)
            val b = withContext(Dispatchers.IO) { loadBase(p) }
            base = b
            _original.value = b
            if (b != null && !saved.isIdentity) {
                _preview.value = runCatching { ImagePipeline.render(b, saved) }.getOrNull()
            }
            _busy.value = false
        }
        // Live preview.
        viewModelScope.launch {
            _params.debounce(24).collectLatest { p ->
                val src = base ?: return@collectLatest
                val rendered = runCatching { ImagePipeline.render(src, p) }.getOrNull() ?: return@collectLatest
                // Previous previews are left to the garbage collector: Compose may still be drawing them.
                _preview.value = rendered
            }
        }
        // Auto-save without history snapshots.
        viewModelScope.launch {
            _params.drop(1).debounce(600).collectLatest { p ->
                runCatching { container.photos.saveEdit(photoId, p, snapshot = false) }
            }
        }
    }

    private fun loadBase(p: Photo): Bitmap? {
        val candidates = listOfNotNull(p.rawPath, p.jpegPath).map { File(it) }.filter { it.exists() }
        for (f in candidates) {
            val bmp = ImageUtils.decodeSampled(f, PREVIEW_EDGE, PREVIEW_EDGE) ?: continue
            return ImageUtils.fitLongEdge(bmp, PREVIEW_EDGE)
        }
        return null
    }

    fun selectTab(t: EditorTab) { _tab.value = t }

    fun update(transform: (EditParams) -> EditParams) {
        val before = _params.value
        val after = transform(before)
        if (after == before) return
        _params.value = after
    }

    /** Called when a slider gesture ends so one drag equals one undo step. */
    fun commit() {
        val current = _params.value
        val last = undoStack.lastOrNull()
        if (last == current) return
        undoStack.addLast(current)
        if (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
        refreshUndoState()
    }

    /** Immediate change (toggle, rotation) that is its own undo step. */
    fun apply(transform: (EditParams) -> EditParams) {
        if (undoStack.isEmpty()) undoStack.addLast(_params.value)
        update(transform)
        commit()
    }

    fun undo() {
        if (undoStack.size < 2) {
            if (undoStack.size == 1) {
                redoStack.addLast(_params.value)
                undoStack.clear()
                _params.value = initialParams
            }
        } else {
            redoStack.addLast(undoStack.removeLast())
            _params.value = undoStack.last()
        }
        refreshUndoState()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(next)
        _params.value = next
        refreshUndoState()
    }

    private fun refreshUndoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun resetAll() = apply { EditParams() }

    fun restore(version: EditVersion) = apply { version.params }

    /** Stores the current recipe as a history snapshot. */
    fun saveSnapshot(label: String = "") {
        viewModelScope.launch {
            container.photos.saveEdit(photoId, _params.value, label, snapshot = true)
            _history.value = container.photos.editHistory(photoId)
            _photo.value?.let { p -> runCatching { XmpSidecar.write(File(p.sidecarPath ?: container.storage.sidecarFile(p.baseName).absolutePath), p, _params.value) } }
            _events.tryEmit(EditorEvent.Saved)
        }
    }

    /** Persist on exit: snapshot only if something changed since opening. */
    fun onLeave() {
        val p = _params.value
        if (p == initialParams) return
        viewModelScope.launch {
            container.photos.saveEdit(photoId, p, "", snapshot = true)
            _photo.value?.let { ph -> runCatching { XmpSidecar.write(File(ph.sidecarPath ?: container.storage.sidecarFile(ph.baseName).absolutePath), ph, p) } }
        }
    }

    fun export() {
        val p = _photo.value ?: return
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            try {
                container.photos.saveEdit(photoId, _params.value, "export", snapshot = true)
                val result = exporter.export(p, _params.value, container.settings.current.jpegQuality)
                _events.tryEmit(EditorEvent.Exported(result.file))
            } catch (e: Exception) {
                _events.tryEmit(EditorEvent.Error(e.message ?: e.javaClass.simpleName))
            } finally {
                _exporting.value = false
            }
        }
    }

    fun setCropAspect(aspect: Float?) {
        val b = base ?: return
        val rotated = _params.value.rotation % 180 != 0
        val w = if (rotated) b.height else b.width
        val h = if (rotated) b.width else b.height
        val r = ImagePipeline.centeredCrop(w, h, aspect)
        apply { it.copy(cropLeft = r[0], cropTop = r[1], cropRight = r[2], cropBottom = r[3]) }
    }

    class Factory(private val container: AppContainer, private val photoId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(container, photoId) as T
    }

    companion object {
        const val PREVIEW_EDGE = 1600
    }
}

