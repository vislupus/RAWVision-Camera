package com.rawvision.camera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.location.Location
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.rawvision.camera.data.model.CaptureFormat
import com.rawvision.camera.data.model.LensFacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** What the camera is doing right now. */
sealed class CameraStatus {
    data object Closed : CameraStatus()
    data object Opening : CameraStatus()
    data object Previewing : CameraStatus()
    data object Capturing : CameraStatus()
    data class Error(val message: String) : CameraStatus()
}

/** Live values reported by the camera for every preview frame. */
data class LiveMeter(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val focusDistance: Float? = null,
    val aperture: Float? = null,
    val focalLength: Float? = null,
    val afState: Int? = null,
    val aeState: Int? = null,
    val awbKelvin: Int? = null,
    val meanLuma: Float = 0f,
    val flashRequired: Boolean = false,
) {
    val isFocused: Boolean
        get() = afState == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED ||
            afState == CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED
    val isFocusing: Boolean
        get() = afState == CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN ||
            afState == CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN
}

/** Everything needed to turn one capture into files. */
class CaptureOutput(
    val info: CameraInfo,
    val result: TotalCaptureResult,
    val raw: Image?,
    val jpeg: Image?,
    val orientationDegrees: Int,
    val params: CameraParams,
) {
    fun close() {
        runCatching { raw?.close() }
        runCatching { jpeg?.close() }
    }
}

class CaptureRequestOptions(
    val wantRaw: Boolean,
    val wantJpeg: Boolean,
    val jpegQuality: Int,
    val location: Location?,
    val deviceRotation: Int,
)

/**
 * Thin, explicit Camera2 wrapper. One instance per app; it owns the camera
 * thread, the capture session and the readers, and exposes state as flows.
 *
 * Design notes:
 *  - Preview is a SurfaceView surface; stills go to RAW_SENSOR and JPEG readers.
 *  - A small YUV stream feeds the histogram and the app's own metering loop,
 *    which lets ISO and shutter be automatic *independently* (something the
 *    Camera2 AE cannot do).
 *  - Session creation falls back to fewer streams when the device rejects the
 *    four-stream configuration.
 */
class CameraController(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("RawVisionCamera").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val cameras: List<CameraInfo> = CameraInfo.enumerate(appContext)

    private val _info = MutableStateFlow<CameraInfo?>(null)
    val info: StateFlow<CameraInfo?> = _info.asStateFlow()

    private val _params = MutableStateFlow(CameraParams())
    val params: StateFlow<CameraParams> = _params.asStateFlow()

    private val _meter = MutableStateFlow(LiveMeter())
    val meter: StateFlow<LiveMeter> = _meter.asStateFlow()

    private val _status = MutableStateFlow<CameraStatus>(CameraStatus.Closed)
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _histogram = MutableStateFlow(FloatArray(FrameAnalyzer.BINS))
    val histogram: StateFlow<FloatArray> = _histogram.asStateFlow()

    private val _analysisActive = MutableStateFlow(false)
    val analysisActive: StateFlow<Boolean> = _analysisActive.asStateFlow()

    /** Metering region currently applied (normalised preview coordinates), for the focus ring overlay. */
    private val _focusPoint = MutableStateFlow<Pair<Float, Float>?>(null)
    val focusPoint: StateFlow<Pair<Float, Float>?> = _focusPoint.asStateFlow()

    var captureFormat: CaptureFormat = CaptureFormat.RAW_JPEG
        set(value) {
            if (field != value) {
                field = value
                handler.post { if (device != null) createSession(tryAnalysis = true) }
            }
        }

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var rawReader: ImageReader? = null
    private var jpegReader: ImageReader? = null
    private var analysisReader: ImageReader? = null
    private val analyzer = FrameAnalyzer()

    private val rawImages = Channel<Image>(capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST) { it.close() }
    private val jpegImages = Channel<Image>(capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST) { it.close() }
    private val previewResults = MutableSharedFlow<TotalCaptureResult>(replay = 1, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val captureMutex = Mutex()

    // Metering state for the app's own exposure loop.
    @Volatile private var sceneProduct: Double = 16_666_667.0 * 100   // exposure(ns) * ISO measured while AE was on
    @Volatile private var lastLoopNanos = 0L
    @Volatile private var lastColorTransform: ColorSpaceTransform? = null
    @Volatile private var lastAutoGains: RggbChannelVector? = null
    @Volatile private var meteringRect: MeteringRectangle? = null
    @Volatile private var afTriggerPending = false

    // ---- Public API -----------------------------------------------------------

    fun selectCamera(id: String) {
        val target = cameras.firstOrNull { it.id == id } ?: cameras.firstOrNull() ?: return
        if (_info.value?.id == target.id) return
        handler.post {
            closeCamera(keepSurface = true)
            _info.value = target
            meteringRect = null
            _focusPoint.value = null
            _params.update { it.copy(focusMode = if (it.focusMode == FocusMode.LOCKED) FocusMode.CONTINUOUS else it.focusMode, zoom = 1f) }
            // The preview view is re-created for the new camera; opening happens when its surface arrives.
        }
    }

    /** Called by the preview view when its surface is created. */
    fun setPreviewSurface(surface: Surface) {
        handler.post {
            if (previewSurface !== surface) {
                closeCamera(keepSurface = false)
                previewSurface = surface
            }
            openIfReady()
        }
    }

    /** Called when a preview surface is destroyed; ignored if it is not the active one. */
    fun removePreviewSurface(surface: Surface) {
        handler.post {
            if (previewSurface === surface) {
                closeCamera(keepSurface = false)
            }
        }
    }

    /** Re-opens the camera after [close] (e.g. when the activity resumes with the surface still alive). */
    fun resume() {
        handler.post { openIfReady() }
    }

    fun setParams(transform: (CameraParams) -> CameraParams) {
        val before = _params.value
        val after = transform(before)
        if (after == before) return
        _params.value = after
        if (after.autoExposure != before.autoExposure || after.exposureCompensation != before.exposureCompensation) {
            // Entering semi-auto: start from what AE was doing.
            lastLoopNanos = 0L
        }
        if (after.focusMode != FocusMode.LOCKED && before.focusMode == FocusMode.LOCKED) {
            meteringRect = null
            _focusPoint.value = null
        }
        handler.post { restartPreview() }
    }

    fun setZoom(ratio: Float) {
        val info = _info.value ?: return
        setParams { it.copy(zoom = ratio.coerceIn(info.minZoom, info.maxZoom)) }
    }

    /** Tap to focus / meter at a normalised preview position (0..1, portrait coordinates). */
    fun focusAt(xNorm: Float, yNorm: Float) {
        val info = _info.value ?: return
        if (!info.hasAutoFocus && info.maxAeRegions == 0) return
        val rect = meteringRectangle(info, xNorm, yNorm, _params.value.zoom)
        meteringRect = rect
        _focusPoint.value = xNorm to yNorm
        afTriggerPending = info.hasAutoFocus
        _params.update { if (it.focusMode == FocusMode.MANUAL) it else it.copy(focusMode = FocusMode.LOCKED) }
        handler.post { restartPreview() }
    }

    fun clearFocusPoint() {
        meteringRect = null
        _focusPoint.value = null
        _params.update { if (it.focusMode == FocusMode.LOCKED) it.copy(focusMode = FocusMode.CONTINUOUS) else it }
        handler.post { restartPreview() }
    }

    /** Releases the camera but remembers the surface so [resume] can reopen without a new view. */
    fun close() {
        handler.post { closeCamera(keepSurface = true) }
    }

    fun destroy() {
        close()
        scope.cancel()
        handler.post { thread.quitSafely() }
    }

    // ---- Opening / closing ----------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun openIfReady() {
        val info = _info.value ?: return
        if (previewSurface == null || device != null) return
        _status.value = CameraStatus.Opening
        try {
            manager.openCamera(info.id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    refreshInfo()
                    createSession(tryAnalysis = true)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (device === camera) device = null
                    session = null
                    _status.value = CameraStatus.Closed
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (device === camera) device = null
                    session = null
                    _status.value = CameraStatus.Error("Camera error $error")
                }
            }, handler)
        } catch (e: CameraAccessException) {
            _status.value = CameraStatus.Error(e.message ?: "Camera access failed")
        } catch (e: SecurityException) {
            _status.value = CameraStatus.Error("Camera permission missing")
        } catch (e: IllegalArgumentException) {
            _status.value = CameraStatus.Error(e.message ?: "Camera unavailable")
        }
    }

    /**
     * Re-reads the static metadata once the device is open. Some HALs (MIUI / MediaTek)
     * return an incomplete CameraCharacteristics before the first open, which makes
     * DngCreator fail with "Missing metadata fields"; the post-open copy is complete.
     */
    private fun refreshInfo() {
        val current = _info.value ?: return
        val fresh = runCatching { CameraInfo.from(current.id, manager.getCameraCharacteristics(current.id)) }.getOrNull() ?: return
        _info.value = fresh
    }

    private fun closeCamera(keepSurface: Boolean) {
        runCatching { session?.close() }
        session = null
        runCatching { device?.close() }
        device = null
        closeReaders()
        if (!keepSurface) previewSurface = null
        _status.value = CameraStatus.Closed
        _analysisActive.value = false
    }

    private fun closeReaders() {
        rawReader?.close(); rawReader = null
        jpegReader?.close(); jpegReader = null
        analysisReader?.close(); analysisReader = null
        while (true) { val r = rawImages.tryReceive().getOrNull() ?: break; r.close() }
        while (true) { val r = jpegImages.tryReceive().getOrNull() ?: break; r.close() }
    }

    private fun createSession(tryAnalysis: Boolean) {
        val camera = device ?: return
        val info = _info.value ?: return
        val surface = previewSurface ?: return
        runCatching { session?.close() }
        session = null
        closeReaders()

        val wantRaw = info.supportsRaw && captureFormat != CaptureFormat.JPEG_ONLY
        val wantJpeg = captureFormat != CaptureFormat.RAW_ONLY || !info.supportsRaw

        val outputs = mutableListOf(OutputConfiguration(surface))
        if (wantRaw) {
            val size = info.rawSize!!
            rawReader = ImageReader.newInstance(size.width, size.height, ImageFormat.RAW_SENSOR, 2).also { reader ->
                reader.setOnImageAvailableListener({ r ->
                    val img = runCatching { r.acquireNextImage() }.getOrNull() ?: return@setOnImageAvailableListener
                    if (rawImages.trySend(img).isFailure) img.close()
                }, handler)
                outputs += OutputConfiguration(reader.surface)
            }
        }
        if (wantJpeg) {
            val size = info.jpegSize
            jpegReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2).also { reader ->
                reader.setOnImageAvailableListener({ r ->
                    val img = runCatching { r.acquireNextImage() }.getOrNull() ?: return@setOnImageAvailableListener
                    if (jpegImages.trySend(img).isFailure) img.close()
                }, handler)
                outputs += OutputConfiguration(reader.surface)
            }
        }
        val analysisSize = info.analysisSize
        val withAnalysis = tryAnalysis && analysisSize != null
        if (withAnalysis) {
            analysisReader = ImageReader.newInstance(analysisSize!!.width, analysisSize.height, ImageFormat.YUV_420_888, 3).also { reader ->
                reader.setOnImageAvailableListener({ r ->
                    val img = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                    try {
                        val stats = analyzer.analyze(img)
                        if (stats != null) {
                            _histogram.value = stats.histogram
                            _meter.update { it.copy(meanLuma = stats.meanLuma) }
                            onLumaMeasured(stats.meanLuma)
                        }
                    } finally {
                        img.close()
                    }
                }, handler)
                outputs += OutputConfiguration(reader.surface)
            }
        }

        val config = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputs,
            executor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (device !== camera) { s.close(); return }
                    session = s
                    _analysisActive.value = withAnalysis
                    restartPreview()
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    if (withAnalysis) {
                        Log.w(TAG, "Session with analysis stream rejected; retrying without it")
                        createSession(tryAnalysis = false)
                    } else {
                        _status.value = CameraStatus.Error("Capture session configuration failed")
                    }
                }
            }
        )
        try {
            camera.createCaptureSession(config)
        } catch (e: Exception) {
            if (withAnalysis) createSession(tryAnalysis = false)
            else _status.value = CameraStatus.Error(e.message ?: "Session failed")
        }
    }

    // ---- Preview ----------------------------------------------------------------

    private fun restartPreview() {
        val camera = device ?: return
        val s = session ?: return
        val info = _info.value ?: return
        val surface = previewSurface ?: return
        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(surface)
            analysisReader?.let { builder.addTarget(it.surface) }
            applyParams(builder, info, _params.value, still = false)
            if (afTriggerPending) {
                afTriggerPending = false
                // One-shot AF trigger on top of the new repeating request.
                val trigger = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                trigger.addTarget(surface)
                analysisReader?.let { trigger.addTarget(it.surface) }
                applyParams(trigger, info, _params.value, still = false)
                trigger.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                s.setRepeatingRequest(builder.build(), previewCallback, handler)
                s.capture(trigger.build(), previewCallback, handler)
            } else {
                s.setRepeatingRequest(builder.build(), previewCallback, handler)
            }
            if (_status.value !is CameraStatus.Capturing) _status.value = CameraStatus.Previewing
        } catch (e: Exception) {
            Log.e(TAG, "restartPreview failed", e)
            _status.value = CameraStatus.Error(e.message ?: "Preview failed")
        }
    }

    private val previewCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            onPreviewResult(result)
        }
    }

    private fun onPreviewResult(result: TotalCaptureResult) {
        val p = _params.value
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        val exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
        val transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)
        if (p.autoExposure && iso != null && exposure != null && iso > 0 && exposure > 0) {
            sceneProduct = ExposureMath.exposureProduct(exposure, iso) / 2.0.pow(p.exposureCompensation.toDouble())
        }
        if (p.whiteBalance == WhiteBalance.AUTO) {
            if (gains != null) lastAutoGains = gains
            if (transform != null) lastColorTransform = transform
        }
        _meter.update {
            it.copy(
                iso = iso ?: it.iso,
                exposureNs = exposure ?: it.exposureNs,
                focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: it.focusDistance,
                aperture = result.get(CaptureResult.LENS_APERTURE) ?: it.aperture,
                focalLength = result.get(CaptureResult.LENS_FOCAL_LENGTH) ?: it.focalLength,
                afState = result.get(CaptureResult.CONTROL_AF_STATE),
                aeState = result.get(CaptureResult.CONTROL_AE_STATE),
                awbKelvin = when {
                    p.whiteBalance == WhiteBalance.MANUAL -> p.kelvin
                    p.whiteBalance != WhiteBalance.AUTO -> p.whiteBalance.kelvin
                    gains != null -> ExposureMath.kelvinFromGains(gains)
                    else -> it.awbKelvin
                },
                flashRequired = result.get(CaptureResult.CONTROL_AE_STATE) == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED,
            )
        }
        previewResults.tryEmit(result)
    }

    /** The app's own metering loop for semi-automatic exposure. */
    private fun onLumaMeasured(mean: Float) {
        val p = _params.value
        if (p.autoExposure || (!p.isoAuto && !p.shutterAuto) || p.aeLock) return
        if (mean <= 0.002f) return
        val now = System.nanoTime()
        if (now - lastLoopNanos < 200_000_000L) return
        val target = 0.42f
        val ratio = (target / mean).toDouble().coerceIn(0.4, 2.5)
        val factor = ratio.pow(0.4)
        if (abs(factor - 1.0) < 0.05) return
        val info = _info.value ?: return
        val minProduct = (info.exposureRange?.lower ?: 10_000L).toDouble() * (info.isoRange?.lower ?: 50)
        val maxProduct = MAX_AUTO_EXPOSURE_NS.toDouble() * (info.isoRange?.upper ?: 3200)
        sceneProduct = (sceneProduct * factor).coerceIn(minProduct, maxProduct)
        lastLoopNanos = now
        handler.post { restartPreview() }
    }

    // ---- Request construction ------------------------------------------------------

    private fun applyParams(b: CaptureRequest.Builder, info: CameraInfo, p: CameraParams, still: Boolean) {
        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        b.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
            if (still) CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE else CameraMetadata.CONTROL_CAPTURE_INTENT_PREVIEW)

        // ---- Zoom ----
        val crop = cropRegion(info, p.zoom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO, p.zoom.coerceIn(info.minZoom, info.maxZoom))
        } else {
            b.set(CaptureRequest.SCALER_CROP_REGION, crop)
        }

        // ---- Exposure ----
        val manualExposure = !p.autoExposure && info.supportsManualExposure
        if (manualExposure) {
            val (iso, exposure) = resolveManualExposure(info, p)
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure)
            val frame = maxOf(exposure, 33_333_333L)
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, frame)
            b.set(CaptureRequest.FLASH_MODE, when (p.flash) {
                FlashMode.TORCH -> CameraMetadata.FLASH_MODE_TORCH
                FlashMode.ON -> if (still) CameraMetadata.FLASH_MODE_SINGLE else CameraMetadata.FLASH_MODE_OFF
                FlashMode.AUTO -> if (still && _meter.value.meanLuma < 0.18f) CameraMetadata.FLASH_MODE_SINGLE else CameraMetadata.FLASH_MODE_OFF
                FlashMode.OFF -> CameraMetadata.FLASH_MODE_OFF
            })
        } else {
            val aeMode = when (p.flash) {
                FlashMode.AUTO -> if (info.hasFlash) CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH else CameraMetadata.CONTROL_AE_MODE_ON
                FlashMode.ON -> if (info.hasFlash) CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH else CameraMetadata.CONTROL_AE_MODE_ON
                else -> CameraMetadata.CONTROL_AE_MODE_ON
            }
            b.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
            b.set(CaptureRequest.FLASH_MODE, if (p.flash == FlashMode.TORCH && info.hasFlash) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
            val steps = (p.exposureCompensation / info.aeCompensationStep).roundToInt()
                .coerceIn(info.aeCompensationRange.lower, info.aeCompensationRange.upper)
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, steps)
            b.set(CaptureRequest.CONTROL_AE_LOCK, p.aeLock)
            b.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_AUTO)
        }
        val meter = meteringRect
        if (meter != null && info.maxAeRegions > 0) b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(meter))

        // ---- Focus ----
        when {
            !info.hasAutoFocus -> b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            p.focusMode == FocusMode.MANUAL -> {
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                b.set(CaptureRequest.LENS_FOCUS_DISTANCE, p.focusDistance.coerceIn(0f, info.manualFocusMax))
            }
            p.focusMode == FocusMode.LOCKED -> {
                b.set(CaptureRequest.CONTROL_AF_MODE,
                    if (info.afModes.contains(CameraMetadata.CONTROL_AF_MODE_AUTO)) CameraMetadata.CONTROL_AF_MODE_AUTO
                    else CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                if (meter != null && info.maxAfRegions > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meter))
            }
            else -> b.set(CaptureRequest.CONTROL_AF_MODE,
                if (info.afModes.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                else CameraMetadata.CONTROL_AF_MODE_AUTO)
        }

        // ---- White balance ----
        when {
            p.whiteBalance == WhiteBalance.AUTO -> b.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
            p.whiteBalance == WhiteBalance.MANUAL && info.supportsManualPostProcessing -> {
                b.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
                b.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                b.set(CaptureRequest.COLOR_CORRECTION_GAINS, ExposureMath.gainsForKelvin(p.kelvin, p.tint))
                b.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, lastColorTransform ?: IDENTITY_TRANSFORM)
            }
            info.awbModes.contains(p.whiteBalance.awbMode) -> b.set(CaptureRequest.CONTROL_AWB_MODE, p.whiteBalance.awbMode)
            else -> b.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
        }

        // ---- Quality ----
        if (info.hasOis) b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
        b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        if (still) {
            b.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            b.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY)
            b.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)
        } else {
            b.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
            b.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_FAST)
        }
    }

    /** Fills in the automatic half of a semi-manual exposure from the metered scene brightness. */
    private fun resolveManualExposure(info: CameraInfo, p: CameraParams): Pair<Int, Long> {
        val isoRange = info.isoRange!!
        val expRange = info.exposureRange!!
        val product = sceneProduct * 2.0.pow(p.exposureCompensation.toDouble())
        val iso: Int
        val exposure: Long
        when {
            !p.isoAuto && !p.shutterAuto -> {
                iso = p.iso.coerceIn(isoRange.lower, isoRange.upper)
                exposure = p.exposureNs.coerceIn(expRange.lower, expRange.upper)
            }
            p.isoAuto -> {
                exposure = p.exposureNs.coerceIn(expRange.lower, expRange.upper)
                iso = (product / exposure).roundToInt().coerceIn(isoRange.lower, isoRange.upper)
            }
            else -> {
                iso = p.iso.coerceIn(isoRange.lower, isoRange.upper)
                val maxAuto = minOf(expRange.upper, MAX_AUTO_EXPOSURE_NS)
                exposure = (product / iso).toLong().coerceIn(expRange.lower, maxAuto)
            }
        }
        return iso to exposure
    }

    private fun cropRegion(info: CameraInfo, zoom: Float): Rect {
        val a = info.activeArray
        val z = zoom.coerceAtLeast(1f)
        val w = (a.width() / z).roundToInt()
        val h = (a.height() / z).roundToInt()
        val left = a.left + (a.width() - w) / 2
        val top = a.top + (a.height() - h) / 2
        return Rect(left, top, left + w, top + h)
    }

    private fun meteringRectangle(info: CameraInfo, xNorm: Float, yNorm: Float, zoom: Float): MeteringRectangle {
        var x = xNorm.coerceIn(0f, 1f)
        val y = yNorm.coerceIn(0f, 1f)
        if (info.facing == LensFacing.FRONT) x = 1f - x       // preview is mirrored
        val (sx, sy) = when (info.sensorOrientation) {
            90 -> y to (1f - x)
            180 -> (1f - x) to (1f - y)
            270 -> (1f - y) to x
            else -> x to y
        }
        val crop = cropRegion(info, zoom)
        val cx = crop.left + sx * crop.width()
        val cy = crop.top + sy * crop.height()
        val half = crop.width() * 0.06f
        val rect = Rect(
            (cx - half).roundToInt().coerceIn(crop.left, crop.right - 1),
            (cy - half).roundToInt().coerceIn(crop.top, crop.bottom - 1),
            (cx + half).roundToInt().coerceIn(crop.left + 1, crop.right),
            (cy + half).roundToInt().coerceIn(crop.top + 1, crop.bottom),
        )
        return MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX - 1)
    }

    // ---- Still capture -------------------------------------------------------------

    /**
     * Runs the full capture sequence (focus lock → AE pre-capture → still) and
     * returns the images. The caller must call [CaptureOutput.close] when done.
     */
    suspend fun capture(options: CaptureRequestOptions): CaptureOutput = captureMutex.withLock {
        val camera = device ?: throw IllegalStateException("Camera closed")
        val s = session ?: throw IllegalStateException("No session")
        val info = _info.value ?: throw IllegalStateException("No camera")
        val p = _params.value
        val surface = previewSurface ?: throw IllegalStateException("No preview")
        _status.value = CameraStatus.Capturing
        try {
            // Drain stale images.
            while (true) { val r = rawImages.tryReceive().getOrNull() ?: break; r.close() }
            while (true) { val r = jpegImages.tryReceive().getOrNull() ?: break; r.close() }

            val wantRaw = options.wantRaw && rawReader != null
            val wantJpeg = options.wantJpeg && jpegReader != null
            if (!wantRaw && !wantJpeg) throw IllegalStateException("No capture target")

            // 1. Lock focus when the lens is in an automatic mode.
            if (info.hasAutoFocus && p.focusMode == FocusMode.CONTINUOUS) {
                val trigger = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                trigger.addTarget(surface)
                analysisReader?.let { trigger.addTarget(it.surface) }
                applyParams(trigger, info, p, still = false)
                trigger.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                s.capture(trigger.build(), previewCallback, handler)
                withTimeoutOrNull(1500) {
                    previewResults.drop(1).first { r ->
                        val st = r.get(CaptureResult.CONTROL_AF_STATE)
                        st == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            st == CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
                            st == CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED
                    }
                }
            }

            // 2. AE pre-capture (lets the camera settle exposure and flash).
            if (p.autoExposure || !info.supportsManualExposure) {
                val pre = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                pre.addTarget(surface)
                analysisReader?.let { pre.addTarget(it.surface) }
                applyParams(pre, info, p, still = false)
                pre.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                s.capture(pre.build(), previewCallback, handler)
                withTimeoutOrNull(2000) {
                    previewResults.drop(1).first { r ->
                        val st = r.get(CaptureResult.CONTROL_AE_STATE)
                        st == CameraMetadata.CONTROL_AE_STATE_CONVERGED ||
                            st == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED ||
                            st == CameraMetadata.CONTROL_AE_STATE_LOCKED ||
                            st == null
                    }
                }
            }

            // 3. The still itself.
            val still = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            if (wantRaw) still.addTarget(rawReader!!.surface)
            if (wantJpeg) still.addTarget(jpegReader!!.surface)
            applyParams(still, info, p, still = true)
            val orientation = jpegOrientation(info, options.deviceRotation)
            still.set(CaptureRequest.JPEG_ORIENTATION, orientation)
            still.set(CaptureRequest.JPEG_QUALITY, options.jpegQuality.coerceIn(1, 100).toByte())
            options.location?.let { still.set(CaptureRequest.JPEG_GPS_LOCATION, it) }

            val resultChannel = Channel<TotalCaptureResult>(1)
            val failure = Channel<String>(1)
            s.capture(still.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    resultChannel.trySend(result)
                }

                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, f: CaptureFailure) {
                    failure.trySend("Capture failed (reason ${f.reason})")
                }
            }, handler)

            val exposureNs = still.get(CaptureRequest.SENSOR_EXPOSURE_TIME) ?: 0L
            val timeoutMs = 8000L + exposureNs / 1_000_000L * 2
            val result = withTimeoutOrNull(timeoutMs) {
                kotlinx.coroutines.selects.select<TotalCaptureResult?> {
                    resultChannel.onReceive { it }
                    failure.onReceive { throw IllegalStateException(it) }
                }
            } ?: throw IllegalStateException("Capture timed out")

            val raw = if (wantRaw) withTimeoutOrNull(timeoutMs) { rawImages.receive() } ?: throw IllegalStateException("RAW frame missing") else null
            val jpeg = if (wantJpeg) withTimeoutOrNull(timeoutMs) { jpegImages.receive() } else null

            CaptureOutput(info, result, raw, jpeg, orientation, p)
        } finally {
            // 4. Release the AF lock and resume the preview.
            if (info.hasAutoFocus && p.focusMode == FocusMode.CONTINUOUS) {
                runCatching {
                    val cancel = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    cancel.addTarget(surface)
                    applyParams(cancel, info, p, still = false)
                    cancel.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                    s.capture(cancel.build(), null, handler)
                }
            }
            _status.value = if (device != null) CameraStatus.Previewing else CameraStatus.Closed
            handler.post { restartPreview() }
        }
    }

    private fun jpegOrientation(info: CameraInfo, deviceRotation: Int): Int {
        if (deviceRotation < 0) return 0
        var rotation = ((deviceRotation + 45) / 90 * 90) % 360
        if (info.facing == LensFacing.FRONT) rotation = -rotation
        return (info.sensorOrientation + rotation + 360) % 360
    }

    companion object {
        private const val TAG = "CameraController"
        /** Longest shutter the automatic half of a semi-manual exposure may choose (1/2 s). */
        const val MAX_AUTO_EXPOSURE_NS = 500_000_000L
        private val IDENTITY_TRANSFORM = ColorSpaceTransform(intArrayOf(1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
    }
}
