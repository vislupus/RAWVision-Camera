package com.rawvision.camera.camera

import android.media.MediaActionSound
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rawvision.camera.data.AppContainer
import com.rawvision.camera.data.model.CaptureFormat
import com.rawvision.camera.data.model.LensFacing
import com.rawvision.camera.data.model.Photo
import com.rawvision.camera.util.DeviceSaver
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-off messages shown by the camera screen. */
sealed class CameraEvent {
    data class Saved(val photo: Photo, val rawError: String? = null) : CameraEvent()
    data class Error(val message: String) : CameraEvent()
    data object Shutter : CameraEvent()
}

/** Which control panel is expanded under the viewfinder. */
enum class ControlPanel { NONE, ISO, SHUTTER, FOCUS, WB, EV, ZOOM }

class CameraViewModel(private val container: AppContainer) : ViewModel() {

    val controller = CameraController(container.appContext)
    private val saver = CaptureSaver(container.storage, container.appContext)
    val location = LocationTracker(container.appContext)
    val level = LevelSensor(container.appContext)
    val rotation = RotationTracker(container.appContext)
    private val sound = MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }

    val settings = container.settings.settings
    val photos = container.photos.photos

    private val _events = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CameraEvent> = _events.asSharedFlow()

    private val _panel = MutableStateFlow(ControlPanel.NONE)
    val panel: StateFlow<ControlPanel> = _panel.asStateFlow()

    private val _countdown = MutableStateFlow(0)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _lastPhoto = MutableStateFlow<Photo?>(null)
    val lastPhoto: StateFlow<Photo?> = _lastPhoto.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            container.photos.refresh()
            _lastPhoto.value = container.photos.photos.value.firstOrNull()
            val s = settings.value
            controller.captureFormat = s.captureFormat
            val preferred = controller.cameras.firstOrNull { it.id == s.lastCameraId }
                ?: controller.cameras.firstOrNull { it.facing == LensFacing.BACK && it.supportsRaw && it.hasAutoFocus }
                ?: controller.cameras.firstOrNull { it.facing == LensFacing.BACK && it.supportsRaw }
                ?: controller.cameras.firstOrNull { it.facing == LensFacing.BACK && it.hasAutoFocus }
                ?: controller.cameras.firstOrNull { it.facing == LensFacing.BACK }
                ?: controller.cameras.firstOrNull()
            preferred?.let { selectCamera(it.id, restorePreset = true) }
        }
        viewModelScope.launch {
            settings.collect { s ->
                controller.captureFormat = s.captureFormat
                if (s.saveLocation) location.start() else location.stop()
            }
        }
    }

    fun onResume() {
        level.start()
        rotation.start()
        if (settings.value.saveLocation) location.start()
        controller.resume()
    }

    fun onPause() {
        level.stop()
        rotation.stop()
        location.stop()
        timerJob?.cancel()
        _countdown.value = 0
        controller.close()
    }

    override fun onCleared() {
        controller.destroy()
        sound.release()
    }

    // ---- Camera selection -------------------------------------------------------

    fun selectCamera(id: String, restorePreset: Boolean = true) {
        viewModelScope.launch {
            // Remember the manual values of the camera we are leaving.
            controller.info.value?.let { old ->
                container.settings.saveCameraPreset(old.id, controller.params.value.toJson())
            }
            controller.selectCamera(id)
            if (restorePreset) {
                val preset = container.settings.loadCameraPreset(id)
                if (preset != null) controller.setParams { CameraParams.fromJson(preset).copy(flash = it.flash, timer = it.timer) }
            }
            container.settings.update { it.copy(lastCameraId = id) }
        }
    }

    fun switchFacing() {
        val current = controller.info.value ?: return
        val cameras = controller.cameras
        val next = cameras.firstOrNull { it.facing != current.facing && it.supportsRaw }
            ?: cameras.firstOrNull { it.facing != current.facing }
            ?: return
        selectCamera(next.id)
    }

    fun lensesForCurrentFacing(): List<CameraInfo> {
        val facing = controller.info.value?.facing ?: LensFacing.BACK
        return controller.cameras.filter { it.facing == facing }
    }

    // ---- Parameter helpers used by the UI ----------------------------------------------

    fun togglePanel(p: ControlPanel) { _panel.update { if (it == p) ControlPanel.NONE else p } }
    fun closePanel() { _panel.value = ControlPanel.NONE }

    fun setParams(transform: (CameraParams) -> CameraParams) = controller.setParams(transform)

    fun resetToAuto() {
        controller.clearFocusPoint()
        controller.setParams { it.resetToAuto() }
    }

    fun cycleFlash() = controller.setParams {
        val order = listOf(FlashMode.OFF, FlashMode.AUTO, FlashMode.ON, FlashMode.TORCH)
        it.copy(flash = order[(order.indexOf(it.flash) + 1) % order.size])
    }

    fun cycleTimer() = controller.setParams {
        val order = TimerMode.entries
        it.copy(timer = order[(order.indexOf(it.timer) + 1) % order.size])
    }

    fun focusAt(x: Float, y: Float) = controller.focusAt(x, y)

    fun setGrid(mode: com.rawvision.camera.data.model.GridMode) {
        viewModelScope.launch { container.settings.update { it.copy(grid = mode) } }
    }

    fun setZoom(ratio: Float) = controller.setZoom(ratio)

    // ---- Capture ----------------------------------------------------------------------

    fun onShutterPressed() {
        if (_saving.value || _countdown.value > 0) return
        val seconds = controller.params.value.timer.seconds
        if (seconds == 0) {
            capture()
        } else {
            timerJob = viewModelScope.launch {
                for (i in seconds downTo 1) {
                    _countdown.value = i
                    delay(1000)
                }
                _countdown.value = 0
                capture()
            }
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        _countdown.value = 0
    }

    private fun capture() {
        viewModelScope.launch {
            val s = settings.value
            val info = controller.info.value ?: return@launch
            _saving.value = true
            try {
                val wantRaw = info.supportsRaw && s.captureFormat != CaptureFormat.JPEG_ONLY
                val wantJpeg = s.captureFormat != CaptureFormat.RAW_ONLY || !info.supportsRaw
                val output = controller.capture(
                    CaptureRequestOptions(
                        wantRaw = wantRaw,
                        wantJpeg = wantJpeg,
                        jpegQuality = s.jpegQuality,
                        location = if (s.saveLocation) location.location.value else null,
                        deviceRotation = rotation.rotation.value,
                    )
                )
                if (s.shutterSound) sound.play(MediaActionSound.SHUTTER_CLICK)
                _events.tryEmit(CameraEvent.Shutter)
                val saved = saver.save(
                    CaptureSaver.SaveRequest(
                        output = output,
                        location = if (s.saveLocation) location.location.value else null,
                        author = s.authorName,
                        copyright = s.copyright,
                    )
                )
                val stored = container.photos.insert(saved.photo)
                _lastPhoto.value = stored
                _events.tryEmit(CameraEvent.Saved(stored, saved.rawError))
                if (s.copyToGallery) {
                    runCatching {
                        stored.jpegPath?.let { DeviceSaver.save(container.appContext, File(it), "image/jpeg") }
                        stored.rawPath?.let { DeviceSaver.save(container.appContext, File(it), "image/x-adobe-dng") }
                    }.onFailure { _events.tryEmit(CameraEvent.Error(it.message ?: it.javaClass.simpleName)) }
                }
            } catch (e: Exception) {
                _events.tryEmit(CameraEvent.Error(e.message ?: e.javaClass.simpleName))
            } finally {
                _saving.value = false
            }
        }
    }

    /** Waits until the camera is previewing (used after resume before the first capture). */
    suspend fun awaitPreview() {
        controller.status.first { it is CameraStatus.Previewing }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CameraViewModel(container) as T
    }
}

