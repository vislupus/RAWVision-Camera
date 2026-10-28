package com.rawvision.camera.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import com.rawvision.camera.data.model.EditParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CPU image pipeline used both for the live preview (downscaled) and for the
 * final export (full resolution). It never touches the source files: it takes
 * a decoded bitmap and a recipe and returns a new bitmap.
 *
 * Order of operations:
 *  1. white balance + exposure in linear light
 *  2. blacks / whites, shadows / highlights, contrast, dehaze (luminance curve)
 *  3. saturation / vibrance
 *  4. clarity (wide unsharp mask), sharpness (narrow unsharp mask)
 *  5. vignette
 *  6. geometry: rotation, flip, straighten, crop
 */
object ImagePipeline {

    suspend fun render(source: Bitmap, p: EditParams): Bitmap = withContext(Dispatchers.Default) {
        val colour = if (hasColourWork(p)) applyColour(source, p) else source
        if (p.hasGeometry) applyGeometry(colour, p).also { if (colour !== source && it !== colour) colour.recycle() } else colour
    }

    private fun hasColourWork(p: EditParams): Boolean =
        p.exposure != 0f || p.contrast != 0f || p.highlights != 0f || p.shadows != 0f || p.whites != 0f ||
            p.blacks != 0f || p.temperature != 0f || p.tint != 0f || p.saturation != 0f || p.vibrance != 0f ||
            p.sharpness != 0f || p.clarity != 0f || p.vignette != 0f || p.dehaze != 0f

    // ---- Colour -----------------------------------------------------------------

    private class Luts(p: EditParams) {
        val toLinear = FloatArray(256) { (it / 255f).let { v -> if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f) } }
        val toSrgb = FloatArray(4097) { i ->
            val v = i / 4096f
            if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
        }
        val gainR: Float
        val gainG: Float
        val gainB: Float
        val exposure: Float = 2f.pow(p.exposure)
        /** Luminance curve in gamma space, 1025 entries. */
        val tone = FloatArray(1025)
        val saturation = 1f + p.saturation / 100f
        val vibrance = p.vibrance / 100f
        val vignette = p.vignette / 100f
        val sharpen = p.sharpness / 100f * 1.4f
        val clarity = p.clarity / 100f * 0.8f

        init {
            val t = p.temperature / 100f
            val ti = p.tint / 100f
            var r = 1f + t * 0.6f
            var b = 1f - t * 0.6f
            var g = 1f - ti * 0.3f
            r *= 1f + ti * 0.1f
            b *= 1f + ti * 0.1f
            // Keep overall brightness roughly constant.
            val mean = (r + g + b) / 3f
            gainR = r / mean; gainG = g / mean; gainB = b / mean

            val blacks = p.blacks / 100f
            val whites = p.whites / 100f
            val shadows = p.shadows / 100f
            val highlights = p.highlights / 100f
            val contrast = p.contrast / 100f + p.dehaze / 100f * 0.35f
            val blackPt = -blacks * 0.08f + p.dehaze / 100f * 0.04f
            val whitePt = 1f - whites * 0.12f
            for (i in 0..1024) {
                var l = i / 1024f
                l = ((l - blackPt) / (whitePt - blackPt).coerceAtLeast(0.05f)).coerceIn(0f, 1f)
                l += shadows * 0.7f * (1f - l).pow(2) * l
                l += highlights * 0.7f * l.pow(2) * (1f - l)
                l = l.coerceIn(0f, 1f)
                val k = if (contrast >= 0f) 1f + contrast * 1.0f else 1f + contrast * 0.7f
                l = 0.5f + (l - 0.5f) * k
                tone[i] = l.coerceIn(0f, 1f)
            }
        }
    }

    private suspend fun applyColour(src: Bitmap, p: EditParams): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val luts = Luts(p)

        // Pass 1: per-pixel colour, parallel over row bands.
        val bands = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val rowsPerBand = (h + bands - 1) / bands
        coroutineScope {
            (0 until bands).map { band ->
                async(Dispatchers.Default) {
                    val y0 = band * rowsPerBand
                    val y1 = min(h, y0 + rowsPerBand)
                    if (y0 < y1) processRows(pixels, w, h, y0, y1, luts)
                }
            }.forEach { it.await() }
        }

        // Pass 2: local contrast / sharpening (needs neighbours).
        if (luts.clarity != 0f) unsharp(pixels, w, h, radius = max(3, (min(w, h) * 0.02f).roundToInt()), amount = luts.clarity, midtoneOnly = true)
        if (luts.sharpen != 0f) unsharp(pixels, w, h, radius = 1, amount = luts.sharpen, midtoneOnly = false)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun processRows(px: IntArray, w: Int, h: Int, y0: Int, y1: Int, l: Luts) {
        val cx = w / 2f
        val cy = h / 2f
        val maxDist = sqrt(cx * cx + cy * cy)
        val hasVignette = l.vignette != 0f
        for (y in y0 until y1) {
            val row = y * w
            for (x in 0 until w) {
                val c = px[row + x]
                val a = c ushr 24
                var r = l.toLinear[(c shr 16) and 0xFF] * l.gainR * l.exposure
                var g = l.toLinear[(c shr 8) and 0xFF] * l.gainG * l.exposure
                var b = l.toLinear[c and 0xFF] * l.gainB * l.exposure
                // Back to gamma space via LUT.
                r = l.toSrgb[(r.coerceIn(0f, 1f) * 4096f).toInt()]
                g = l.toSrgb[(g.coerceIn(0f, 1f) * 4096f).toInt()]
                b = l.toSrgb[(b.coerceIn(0f, 1f) * 4096f).toInt()]

                // Tone curve on luminance, applied as a ratio to preserve hue.
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val newLum = l.tone[(lum * 1024f).toInt().coerceIn(0, 1024)]
                if (lum > 0.0005f) {
                    val ratio = newLum / lum
                    r *= ratio; g *= ratio; b *= ratio
                } else {
                    r = newLum; g = newLum; b = newLum
                }

                // Saturation + vibrance around the new luminance.
                if (l.saturation != 1f || l.vibrance != 0f) {
                    val lum2 = 0.299f * r + 0.587f * g + 0.114f * b
                    val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
                    val pixSat = (mx - mn).coerceIn(0f, 1f)
                    val s = l.saturation * (1f + l.vibrance * (1f - pixSat))
                    r = lum2 + (r - lum2) * s
                    g = lum2 + (g - lum2) * s
                    b = lum2 + (b - lum2) * s
                }

                if (hasVignette) {
                    val dx = x - cx; val dy = y - cy
                    val d = sqrt(dx * dx + dy * dy) / maxDist
                    val f = smoothstep(0.35f, 1.05f, d)
                    val factor = if (l.vignette < 0f) 1f + l.vignette * f else 1f + l.vignette * f * 0.6f
                    r *= factor; g *= factor; b *= factor
                }

                px[row + x] = (a shl 24) or (to255(r) shl 16) or (to255(g) shl 8) or to255(b)
            }
        }
    }

    private fun to255(v: Float): Int = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Unsharp mask on luminance using a separable box blur. */
    private fun unsharp(px: IntArray, w: Int, h: Int, radius: Int, amount: Float, midtoneOnly: Boolean) {
        val luma = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            luma[i] = 0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)
        }
        val blurred = boxBlur(luma, w, h, radius)
        for (i in px.indices) {
            val l = luma[i]
            var delta = (l - blurred[i]) * amount
            if (midtoneOnly) {
                val m = l / 255f
                delta *= 1f - abs(2f * m - 1f).pow(2)
            }
            if (delta == 0f) continue
            val c = px[i]
            val r = (((c shr 16) and 0xFF) + delta).roundToInt().coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) + delta).roundToInt().coerceIn(0, 255)
            val b = ((c and 0xFF) + delta).roundToInt().coerceIn(0, 255)
            px[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        val size = radius * 2 + 1
        // Horizontal
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            for (x in -radius..radius) sum += src[row + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum / size
                val addX = (x + radius + 1).coerceAtMost(w - 1)
                val subX = (x - radius).coerceAtLeast(0)
                sum += src[row + addX] - src[row + subX]
            }
        }
        // Vertical
        for (x in 0 until w) {
            var sum = 0f
            for (y in -radius..radius) sum += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / size
                val addY = (y + radius + 1).coerceAtMost(h - 1)
                val subY = (y - radius).coerceAtLeast(0)
                sum += tmp[addY * w + x] - tmp[subY * w + x]
            }
        }
        return out
    }

    // ---- Geometry --------------------------------------------------------------------

    fun applyGeometry(src: Bitmap, p: EditParams): Bitmap {
        var bmp = src
        // 1. Lossless 90° rotation and flips.
        if (p.rotation != 0 || p.flipHorizontal || p.flipVertical) {
            val m = Matrix()
            if (p.flipHorizontal) m.postScale(-1f, 1f)
            if (p.flipVertical) m.postScale(1f, -1f)
            if (p.rotation != 0) m.postRotate(p.rotation.toFloat())
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (bmp !== src) bmp.recycle()
            bmp = rotated
        }
        // 2. Straighten with auto-crop so no empty corners remain.
        if (p.straighten != 0f) {
            val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
            val rad = Math.toRadians(p.straighten.toDouble())
            val cosA = abs(cos(rad)).toFloat(); val sinA = abs(sin(rad)).toFloat()
            val scale = min(w / (w * cosA + h * sinA), h / (w * sinA + h * cosA))
            val outW = (w * scale).roundToInt().coerceAtLeast(1)
            val outH = (h * scale).roundToInt().coerceAtLeast(1)
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            val m = Matrix()
            m.postTranslate(-w / 2f, -h / 2f)
            m.postRotate(p.straighten)
            m.postTranslate(outW / 2f, outH / 2f)
            canvas.drawBitmap(bmp, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG))
            if (bmp !== src) bmp.recycle()
            bmp = out
        }
        // 3. Crop.
        if (p.hasCrop) {
            val left = (p.cropLeft * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
            val top = (p.cropTop * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
            val right = (p.cropRight * bmp.width).roundToInt().coerceIn(left + 1, bmp.width)
            val bottom = (p.cropBottom * bmp.height).roundToInt().coerceIn(top + 1, bmp.height)
            val cropped = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
            if (bmp !== src && cropped !== bmp) bmp.recycle()
            bmp = cropped
        }
        return bmp
    }

    /** Centre crop rectangle (normalised) for an aspect ratio, relative to a w×h image after rotation. */
    fun centeredCrop(width: Int, height: Int, aspect: Float?): FloatArray {
        if (aspect == null || width <= 0 || height <= 0) return floatArrayOf(0f, 0f, 1f, 1f)
        val current = width.toFloat() / height
        return if (current > aspect) {
            val w = aspect / current
            floatArrayOf((1f - w) / 2f, 0f, (1f + w) / 2f, 1f)
        } else {
            val h = current / aspect
            floatArrayOf(0f, (1f - h) / 2f, 1f, (1f + h) / 2f)
        }
    }
}
