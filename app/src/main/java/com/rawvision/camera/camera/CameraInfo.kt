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
) {
    val hasAutoFocus: Boolean get() = minFocusDistance > 0f
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
                .sortedWith(compareBy({ it.facing != LensFacing.BACK }, { !it.supportsRaw }, { it.id.toIntOrNull() ?: Int.MAX_VALUE }))
        }

        fun from(id: String, c: CameraCharacteristics): CameraInfo {
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet() ?: emptySet()
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw IllegalStateException("No stream configuration map for camera $id")

            val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList().orEmpty()
            val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
            val previewSizes = map.getOutputSizes(SurfaceHolder::class.java)?.toList().orEmpty()
            val yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty()

            val supportsRaw = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) && rawSizes.isNotEmpty()
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
            )
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
