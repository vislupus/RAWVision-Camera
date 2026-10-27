package com.rawvision.camera.camera

import android.media.Image

/** Cheap luminance statistics from a small YUV_420_888 frame. */
class FrameAnalyzer {

    class Stats(val histogram: FloatArray, val meanLuma: Float)

    private var frameCounter = 0
    private val counts = IntArray(BINS)

    fun analyze(image: Image): Stats? {
        if (++frameCounter % 2 != 0) return null   // every other frame is plenty
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        counts.fill(0)
        var sum = 0L
        var n = 0
        val step = 2
        var y = 0
        while (y < height) {
            val rowStart = y * rowStride
            var x = 0
            while (x < width) {
                val idx = rowStart + x * pixelStride
                if (idx >= buffer.limit()) break
                val v = buffer.get(idx).toInt() and 0xFF
                counts[v * BINS / 256]++
                sum += v
                n++
                x += step
            }
            y += step
        }
        if (n == 0) return null
        val max = counts.max().coerceAtLeast(1)
        val hist = FloatArray(BINS) { counts[it].toFloat() / max }
        return Stats(hist, sum.toFloat() / n / 255f)
    }

    companion object {
        const val BINS = 64
    }
}
