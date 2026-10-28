package com.rawvision.camera.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range
import android.util.Size
import android.view.SurfaceHolder
import com.rawvision.camera.data.model.LensFacing
import kotlin.math.abs

/** Static description of one camera, read once from [CameraCharacteristics]. */
data class CameraInfo(
    val id: String,
    val facing: LensFacing,
    val characteristics: CameraCharacteristics,
    val supportsRaw: Boolean,
    val supportsManualSensor: Boolean,
    val supportsManualPostProcessing: Boolean,
    val isoRange: Range<Int>?,
    val exposureRange: Range<Long>?,
    val minFocusDistance: Float,
    val hasFlash: Boolean,
    val hasOis: Boolean,
    val focalLengths: List<Float>,
    val apertures: List<Float>,
    val minZoom: Float,
    val maxZoom: Float,
    val aeCompensationRange: Range<Int>,
    val aeCompensationStep: Float,
    val sensorOrientation: Int,
    val activeArray: Rect,
    val rawSize: Size?,
    val jpegSize: Size,
    val previewSize: Size,
    val analysisSize: Size?,
    val awbModes: Set<Int>,
    val afModes: Set<Int>,
    val maxAfRegions: Int,
    val maxAeRegions: Int,
    val physicalCameraIds: Set<String>,
    /** DNG calibration keys this camera does not report (empty = DngCreator should be happy). */
    val dngMissingKeys: List<String>,
    val hardwareLevel: Int,
) {
    /** Some HALs (MediaTek) report a minimum focus distance of 0 on lenses that do autofocus, so trust the AF modes too. */
    val hasAutoFocus: Boolean
        get() = minFocusDistance > 0f || afModes.any { it != CameraMetadata.CONTROL_AF_MODE_OFF }

    /** Upper bound (diopters) for the manual focus slider; falls back to 10 (= 10 cm) when the HAL reports 0. */
    val manualFocusMax: Float get() = if (minFocusDistance > 0f) minFocusDistance else 10f
    val primaryFocalLength: Float? get() = focalLengths.firstOrNull()
    val primaryAperture: Float? get() = apertures.firstOrNull()
    val supportsManualExposure: Boolean get() = supportsManualSensor && isoRange != null && exposureRange != null

    /** Short label such as "24 mm" used by the lens picker. */
    val label: String
        get() {
            val f = primaryFocalLength
            return if (f != null) String.format(java.util.Locale.US, "%.1f mm", f) else id
        }

    companion object {
        private val MAX_PREVIEW = Size(1920, 1440)

        fun enumerate(context: Context): List<CameraInfo> {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            return runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList())
                .mapNotNull { id -> runCatching { from(id, manager.getCameraCharacteristics(id)) }.getOrNull() }
                .sortedWith(compareBy({ it.facing != LensFacing.BACK }, { !it.supportsRaw }, { !it.hasAutoFocus }, { it.id.toIntOrNull() ?: Int.MAX_VALUE }))
        }

        fun from(id: String, c: CameraCharacteristics): CameraInfo {
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet() ?: emptySet()
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw IllegalStateException("No stream configuration map for camera $id")

            val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList().orEmpty()
            val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
            val previewSizes = map.getOutputSizes(SurfaceHolder::class.java)?.toList().orEmpty()
            val yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()

            // DngCreator throws "Missing metadata fields" when a lens advertises RAW but lacks the
            // colour calibration tags (common on secondary ultra-wide / macro modules).
            val dngMissing = DNG_KEYS.filter { (_, key) -> runCatching { c.get(key) }.getOrNull() == null }.map { it.first }
            // Only the illuminant is a hard requirement we know of; everything else is reported in diagnostics
            // and handled by the JPEG fallback at save time.
            val supportsRaw = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) && rawSizes.isNotEmpty() &&
                "ReferenceIlluminant1" !in dngMissing
            val rawSize = rawSizes.maxByOrNull { it.width.toLong() * it.height }
            val jpegSize = jpegSizes.maxByOrNull { it.width.toLong() * it.height } ?: Size(1920, 1080)
            val targetAspect = (rawSize ?: jpegSize).let { it.width.toFloat() / it.height }
            val previewSize = choosePreview(previewSizes, targetAspect)
            val analysisSize = yuvSizes
                .filter { it.width <= 640 && it.height <= 480 && abs(it.width.toFloat() / it.height - targetAspect) < 0.2f }
                .maxByOrNull { it.width.toLong() * it.height }
                ?: yuvSizes.filter { it.width <= 640 }.maxByOrNull { it.width.toLong() * it.height }

            val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
            val maxDigital = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f

            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
                CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
                else -> LensFacing.BACK
            }
            val physicalIds: Set<String> = c.physicalCameraIds

            return CameraInfo(
                id = id,
                facing = facing,
                characteristics = c,
                supportsRaw = supportsRaw,
                supportsManualSensor = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR),
                supportsManualPostProcessing = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING),
                isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
                exposureRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE),
                minFocusDistance = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
                hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false,
                hasOis = (c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.contains(
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                ) ?: false),
                focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
                apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty(),
                minZoom = zoomRange?.lower ?: 1f,
                maxZoom = zoomRange?.upper ?: maxDigital,
                aeCompensationRange = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(0, 0),
                aeCompensationStep = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 1f,
                sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
                activeArray = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, 4000, 3000),
                rawSize = rawSize,
                jpegSize = jpegSize,
                previewSize = previewSize,
                analysisSize = analysisSize,
                awbModes = c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toSet() ?: setOf(CameraMetadata.CONTROL_AWB_MODE_AUTO),
                afModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toSet() ?: emptySet(),
                maxAfRegions = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0,
                maxAeRegions = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0,
                physicalCameraIds = physicalIds,
                dngMissingKeys = dngMissing,
                hardwareLevel = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1,
            )
        }

        /** Static keys DngCreator reads; used for the diagnostics screen and the RAW check. */
        val DNG_KEYS: List<Pair<String, CameraCharacteristics.Key<*>>> = listOf(
            "ReferenceIlluminant1" to CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1,
            "ReferenceIlluminant2" to CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2,
            "ColorTransform1" to CameraCharacteristics.SENSOR_COLOR_TRANSFORM1,
            "ColorTransform2" to CameraCharacteristics.SENSOR_COLOR_TRANSFORM2,
            "CalibrationTransform1" to CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1,
            "CalibrationTransform2" to CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2,
            "ForwardMatrix1" to CameraCharacteristics.SENSOR_FORWARD_MATRIX1,
            "ForwardMatrix2" to CameraCharacteristics.SENSOR_FORWARD_MATRIX2,
            "ColorFilterArrangement" to CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT,
            "WhiteLevel" to CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL,
            "BlackLevelPattern" to CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN,
            "PreCorrectionActiveArray" to CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE,
            "PixelArraySize" to CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE,
        )

        /** Human-readable dump of every camera, for the diagnostics screen. */
        fun diagnostics(cameras: List<CameraInfo>): String = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            appendLine()
            cameras.forEach { cam ->
                val c = cam.characteristics
                val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.joinToString(",").orEmpty()
                appendLine("Camera ${cam.id}  ${cam.facing}  level=${cam.hardwareLevel}")
                appendLine("  focal=${cam.focalLengths}  aperture=${cam.apertures}  sensorOrientation=${cam.sensorOrientation}")
                appendLine("  capabilities=[$caps]")
                appendLine("  RAW capability=${caps.split(",").contains("3")}  rawSize=${cam.rawSize}  usable=${cam.supportsRaw}")
                appendLine("  dngMissingKeys=${cam.dngMissingKeys}")
                appendLine("  manualSensor=${cam.supportsManualSensor}  manualPostProcessing=${cam.supportsManualPostProcessing}")
                appendLine("  iso=${cam.isoRange}  exposure=${cam.exposureRange}")
                appendLine("  minFocusDistance=${cam.minFocusDistance}  afModes=${cam.afModes}  hasAutoFocus=${cam.hasAutoFocus}")
                appendLine("  awbModes=${cam.awbModes}  flash=${cam.hasFlash}  ois=${cam.hasOis}  zoom=${cam.minZoom}..${cam.maxZoom}")
                appendLine("  jpegSize=${cam.jpegSize}  previewSize=${cam.previewSize}  analysisSize=${cam.analysisSize}")
                appendLine("  physicalIds=${cam.physicalCameraIds}  activeArray=${cam.activeArray}")
                appendLine()
            }
        }

        private fun choosePreview(sizes: List<Size>, aspect: Float): Size {
            if (sizes.isEmpty()) return Size(1440, 1080)
            val matching = sizes.filter { abs(it.width.toFloat() / it.height - aspect) < 0.03f }
            val pool = matching.ifEmpty { sizes }
            return pool.filter { it.width <= MAX_PREVIEW.width && it.height <= MAX_PREVIEW.height }
                .maxByOrNull { it.width.toLong() * it.height }
                ?: pool.minByOrNull { it.width.toLong() * it.height }
                ?: sizes.first()
        }
    }
}
