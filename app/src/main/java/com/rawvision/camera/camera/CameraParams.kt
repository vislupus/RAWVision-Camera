package com.rawvision.camera.camera

import android.hardware.camera2.CameraMetadata
import org.json.JSONObject

enum class FlashMode { OFF, AUTO, ON, TORCH }

enum class FocusMode { CONTINUOUS, LOCKED, MANUAL }

enum class TimerMode(val seconds: Int) { OFF(0), THREE(3), TEN(10) }

/** White-balance choices. [awbMode] is the Camera2 constant, [kelvin] the approximate temperature. */
enum class WhiteBalance(val awbMode: Int, val kelvin: Int) {
    AUTO(CameraMetadata.CONTROL_AWB_MODE_AUTO, 0),
    INCANDESCENT(CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT, 2800),
    FLUORESCENT(CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT, 4000),
    DAYLIGHT(CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT, 5500),
    CLOUDY(CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, 6500),
    SHADE(CameraMetadata.CONTROL_AWB_MODE_SHADE, 7500),
    MANUAL(CameraMetadata.CONTROL_AWB_MODE_OFF, 5500),
}

/**
 * The user-facing shooting parameters. Each exposure value can be automatic
 * or manual independently, like a dedicated camera: ISO manual + shutter auto
 * is a valid combination (the app meters the missing value itself).
 */
data class CameraParams(
    val isoAuto: Boolean = true,
    val iso: Int = 100,
    val shutterAuto: Boolean = true,
    val exposureNs: Long = 16_666_667L,      // 1/60 s
    val focusMode: FocusMode = FocusMode.CONTINUOUS,
    val focusDistance: Float = 0f,          // diopters; 0 = infinity
    val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
    val kelvin: Int = 5500,
    val tint: Float = 0f,                   // -100..100, only with manual WB
    val exposureCompensation: Float = 0f,   // EV
    val flash: FlashMode = FlashMode.OFF,
    val zoom: Float = 1f,
    val timer: TimerMode = TimerMode.OFF,
    val aeLock: Boolean = false,
) {
    /** True when the camera's own 3A is in charge of exposure. */
    val autoExposure: Boolean get() = isoAuto && shutterAuto

    val isFullAuto: Boolean
        get() = autoExposure && focusMode == FocusMode.CONTINUOUS && whiteBalance == WhiteBalance.AUTO &&
            exposureCompensation == 0f && !aeLock

    val modeLabel: String
        get() = when {
            isFullAuto -> "AUTO"
            autoExposure -> "P"
            isoAuto && !shutterAuto -> "S"
            !isoAuto && shutterAuto -> "ISO"
            else -> "M"
        }

    fun resetToAuto() = CameraParams(flash = flash, zoom = zoom, timer = timer)

    fun toJson(): String = JSONObject().apply {
        put("isoAuto", isoAuto); put("iso", iso)
        put("shutterAuto", shutterAuto); put("exposureNs", exposureNs)
        put("focusMode", focusMode.name); put("focusDistance", focusDistance.toDouble())
        put("whiteBalance", whiteBalance.name); put("kelvin", kelvin); put("tint", tint.toDouble())
        put("exposureCompensation", exposureCompensation.toDouble())
        put("flash", flash.name); put("zoom", zoom.toDouble()); put("timer", timer.name)
    }.toString()

    companion object {
        fun fromJson(json: String?): CameraParams {
            if (json.isNullOrBlank()) return CameraParams()
            return runCatching {
                val o = JSONObject(json)
                val d = CameraParams()
                CameraParams(
                    isoAuto = o.optBoolean("isoAuto", d.isoAuto),
                    iso = o.optInt("iso", d.iso),
                    shutterAuto = o.optBoolean("shutterAuto", d.shutterAuto),
                    exposureNs = o.optLong("exposureNs", d.exposureNs),
                    focusMode = runCatching { FocusMode.valueOf(o.optString("focusMode")) }.getOrDefault(d.focusMode)
                        .let { if (it == FocusMode.LOCKED) FocusMode.CONTINUOUS else it },
                    focusDistance = o.optDouble("focusDistance", d.focusDistance.toDouble()).toFloat(),
                    whiteBalance = runCatching { WhiteBalance.valueOf(o.optString("whiteBalance")) }.getOrDefault(d.whiteBalance),
                    kelvin = o.optInt("kelvin", d.kelvin),
                    tint = o.optDouble("tint", d.tint.toDouble()).toFloat(),
                    exposureCompensation = o.optDouble("exposureCompensation", d.exposureCompensation.toDouble()).toFloat(),
                    flash = runCatching { FlashMode.valueOf(o.optString("flash")) }.getOrDefault(d.flash)
                        .let { if (it == FlashMode.TORCH) FlashMode.OFF else it },
                    zoom = o.optDouble("zoom", d.zoom.toDouble()).toFloat(),
                    timer = runCatching { TimerMode.valueOf(o.optString("timer")) }.getOrDefault(d.timer),
                )
            }.getOrDefault(CameraParams())
        }

        /** Standard 1/3-stop shutter speeds in nanoseconds, from 1/8000 s to 30 s. */
        val SHUTTER_STOPS: List<Long> = listOf(
            1.0 / 8000, 1.0 / 6400, 1.0 / 5000, 1.0 / 4000, 1.0 / 3200, 1.0 / 2500, 1.0 / 2000, 1.0 / 1600,
            1.0 / 1250, 1.0 / 1000, 1.0 / 800, 1.0 / 640, 1.0 / 500, 1.0 / 400, 1.0 / 320, 1.0 / 250,
            1.0 / 200, 1.0 / 160, 1.0 / 125, 1.0 / 100, 1.0 / 80, 1.0 / 60, 1.0 / 50, 1.0 / 40, 1.0 / 30,
            1.0 / 25, 1.0 / 20, 1.0 / 15, 1.0 / 13, 1.0 / 10, 1.0 / 8, 1.0 / 6, 1.0 / 5, 1.0 / 4, 0.3, 0.4,
            0.5, 0.6, 0.8, 1.0, 1.3, 1.6, 2.0, 2.5, 3.2, 4.0, 5.0, 6.0, 8.0, 10.0, 13.0, 15.0, 20.0, 25.0, 30.0,
        ).map { (it * 1_000_000_000L).toLong() }

        /** Standard 1/3-stop ISO values. */
        val ISO_STOPS: List<Int> = listOf(
            25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600,
            2000, 2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600, 32000, 40000, 51200,
        )
    }
}
