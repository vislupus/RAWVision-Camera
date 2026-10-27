package com.rawvision.camera.data.model

import org.json.JSONObject

/**
 * Non-destructive edit recipe. The original RAW/JPEG is never touched; this
 * recipe is stored in SQLite (and in the XMP sidecar) and rendered on demand.
 *
 * All tonal values are in a -100..100 range except where noted.
 */
data class EditParams(
    val exposure: Float = 0f,       // EV, -3..3
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,    // negative = cooler, positive = warmer
    val tint: Float = 0f,           // negative = green, positive = magenta
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val sharpness: Float = 0f,      // 0..100
    val clarity: Float = 0f,
    val vignette: Float = 0f,       // negative = dark corners, positive = bright
    val dehaze: Float = 0f,
    val rotation: Int = 0,          // 0, 90, 180, 270
    val straighten: Float = 0f,     // degrees, -45..45
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropLeft: Float = 0f,       // normalized 0..1, applied after rotation
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
) {
    val isIdentity: Boolean
        get() = this == EditParams()

    val hasCrop: Boolean
        get() = cropLeft > 0.0005f || cropTop > 0.0005f || cropRight < 0.9995f || cropBottom < 0.9995f

    /** True when only pixel-colour operations are needed (no geometry). */
    val hasGeometry: Boolean
        get() = rotation != 0 || straighten != 0f || flipHorizontal || flipVertical || hasCrop

    fun toJson(): String = JSONObject().apply {
        put("exposure", exposure.toDouble())
        put("contrast", contrast.toDouble())
        put("highlights", highlights.toDouble())
        put("shadows", shadows.toDouble())
        put("whites", whites.toDouble())
        put("blacks", blacks.toDouble())
        put("temperature", temperature.toDouble())
        put("tint", tint.toDouble())
        put("saturation", saturation.toDouble())
        put("vibrance", vibrance.toDouble())
        put("sharpness", sharpness.toDouble())
        put("clarity", clarity.toDouble())
        put("vignette", vignette.toDouble())
        put("dehaze", dehaze.toDouble())
        put("rotation", rotation)
        put("straighten", straighten.toDouble())
        put("flipHorizontal", flipHorizontal)
        put("flipVertical", flipVertical)
        put("cropLeft", cropLeft.toDouble())
        put("cropTop", cropTop.toDouble())
        put("cropRight", cropRight.toDouble())
        put("cropBottom", cropBottom.toDouble())
    }.toString()

    companion object {
        fun fromJson(json: String?): EditParams {
            if (json.isNullOrBlank()) return EditParams()
            return runCatching {
                val o = JSONObject(json)
                val d = EditParams()
                EditParams(
                    exposure = o.optDouble("exposure", d.exposure.toDouble()).toFloat(),
                    contrast = o.optDouble("contrast", d.contrast.toDouble()).toFloat(),
                    highlights = o.optDouble("highlights", d.highlights.toDouble()).toFloat(),
                    shadows = o.optDouble("shadows", d.shadows.toDouble()).toFloat(),
                    whites = o.optDouble("whites", d.whites.toDouble()).toFloat(),
                    blacks = o.optDouble("blacks", d.blacks.toDouble()).toFloat(),
                    temperature = o.optDouble("temperature", d.temperature.toDouble()).toFloat(),
                    tint = o.optDouble("tint", d.tint.toDouble()).toFloat(),
                    saturation = o.optDouble("saturation", d.saturation.toDouble()).toFloat(),
                    vibrance = o.optDouble("vibrance", d.vibrance.toDouble()).toFloat(),
                    sharpness = o.optDouble("sharpness", d.sharpness.toDouble()).toFloat(),
                    clarity = o.optDouble("clarity", d.clarity.toDouble()).toFloat(),
                    vignette = o.optDouble("vignette", d.vignette.toDouble()).toFloat(),
                    dehaze = o.optDouble("dehaze", d.dehaze.toDouble()).toFloat(),
                    rotation = o.optInt("rotation", d.rotation),
                    straighten = o.optDouble("straighten", d.straighten.toDouble()).toFloat(),
                    flipHorizontal = o.optBoolean("flipHorizontal", d.flipHorizontal),
                    flipVertical = o.optBoolean("flipVertical", d.flipVertical),
                    cropLeft = o.optDouble("cropLeft", d.cropLeft.toDouble()).toFloat(),
                    cropTop = o.optDouble("cropTop", d.cropTop.toDouble()).toFloat(),
                    cropRight = o.optDouble("cropRight", d.cropRight.toDouble()).toFloat(),
                    cropBottom = o.optDouble("cropBottom", d.cropBottom.toDouble()).toFloat(),
                )
            }.getOrDefault(EditParams())
        }
    }
}
