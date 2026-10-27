package com.rawvision.camera.camera

import android.hardware.camera2.params.RggbChannelVector
import kotlin.math.ln
import kotlin.math.pow

/** Pure helper maths for exposure and white balance. */
object ExposureMath {

    private const val NS = 1_000_000_000.0

    /** Exposure value for ISO 100 (EV100) from shutter + ISO; aperture is fixed on phones so it is ignored. */
    fun ev100(exposureNs: Long, iso: Int): Double {
        val t = exposureNs / NS
        return -ln(t * iso / 100.0) / ln(2.0)
    }

    /** Scene brightness metric kept constant when swapping ISO vs shutter: exposure * ISO. */
    fun exposureProduct(exposureNs: Long, iso: Int): Double = exposureNs.toDouble() * iso

    fun nearestShutterStop(ns: Long): Long = CameraParams.SHUTTER_STOPS.minByOrNull { kotlin.math.abs(ln(it.toDouble() / ns)) } ?: ns

    fun nearestIsoStop(iso: Int): Int = CameraParams.ISO_STOPS.minByOrNull { kotlin.math.abs(ln(it.toDouble() / iso)) } ?: iso

    /**
     * Approximate black-body illuminant colour (Tanner Helland fit) normalised to 0..1.
     * Used to derive white-balance gains for a Kelvin value when AWB is off.
     */
    fun illuminantRgb(kelvin: Int): FloatArray {
        val t = kelvin.coerceIn(1000, 40000) / 100.0
        val r: Double
        val g: Double
        val b: Double
        if (t <= 66) {
            r = 255.0
            g = (99.4708025861 * ln(t) - 161.1195681661).coerceIn(0.0, 255.0)
            b = if (t <= 19) 0.0 else (138.5177312231 * ln(t - 10) - 305.0447927307).coerceIn(0.0, 255.0)
        } else {
            r = (329.698727446 * (t - 60).pow(-0.1332047592)).coerceIn(0.0, 255.0)
            g = (288.1221695283 * (t - 60).pow(-0.0755148492)).coerceIn(0.0, 255.0)
            b = 255.0
        }
        return floatArrayOf((r / 255.0).toFloat(), (g / 255.0).toFloat(), (b / 255.0).toFloat())
    }

    /**
     * Converts Kelvin + tint into RGGB gains that neutralise the illuminant.
     * Green stays at 1.0; tint shifts the red/blue pair against green.
     */
    fun gainsForKelvin(kelvin: Int, tint: Float): RggbChannelVector {
        val ill = illuminantRgb(kelvin)
        val neutral = illuminantRgb(6500)
        var r = (ill[1] / ill[0].coerceAtLeast(0.05f)) * (neutral[0] / neutral[1])
        var b = (ill[1] / ill[2].coerceAtLeast(0.05f)) * (neutral[2] / neutral[1])
        val tintFactor = 1f + (tint / 100f) * 0.35f   // magenta (>0) raises R and B relative to G
        r *= tintFactor
        b *= tintFactor
        return RggbChannelVector(r.coerceIn(0.3f, 6f), 1f, 1f, b.coerceIn(0.3f, 6f))
    }

    /** Rough inverse of [gainsForKelvin] so we can show the camera's auto WB as a Kelvin figure. */
    fun kelvinFromGains(gains: RggbChannelVector): Int {
        val ratio = gains.blue / gains.red.coerceAtLeast(0.01f)
        var best = 5500
        var bestErr = Double.MAX_VALUE
        var k = 2000
        while (k <= 12000) {
            val g = gainsForKelvin(k, 0f)
            val err = kotlin.math.abs(ln((g.blue / g.red).toDouble()) - ln(ratio.toDouble()))
            if (err < bestErr) { bestErr = err; best = k }
            k += 100
        }
        return best
    }
}
