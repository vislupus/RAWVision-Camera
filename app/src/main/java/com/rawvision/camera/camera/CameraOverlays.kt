package com.rawvision.camera.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.rawvision.camera.data.model.GridMode
import kotlin.math.abs

@Composable
fun GridOverlay(mode: GridMode, modifier: Modifier = Modifier) {
    if (mode == GridMode.NONE) return
    Canvas(modifier.fillMaxSize()) {
        val color = Color.White.copy(alpha = 0.45f)
        val stroke = 1.dp.toPx()
        when (mode) {
            GridMode.THIRDS, GridMode.GOLDEN -> {
                val a = if (mode == GridMode.THIRDS) 1f / 3f else 0.382f
                val b = 1f - a
                listOf(a, b).forEach { f ->
                    drawLine(color, Offset(size.width * f, 0f), Offset(size.width * f, size.height), stroke)
                    drawLine(color, Offset(0f, size.height * f), Offset(size.width, size.height * f), stroke)
                }
            }
            GridMode.SQUARE -> {
                val side = minOf(size.width, size.height)
                val left = (size.width - side) / 2f
                val top = (size.height - side) / 2f
                drawRect(Color.Black.copy(alpha = 0.35f), Offset.Zero, Size(size.width, top))
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, top + side), Size(size.width, size.height - top - side))
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, top), Size(left, side))
                drawRect(Color.Black.copy(alpha = 0.35f), Offset(left + side, top), Size(size.width - left - side, side))
                drawRect(color, Offset(left, top), Size(side, side), style = Stroke(stroke))
            }
            GridMode.NONE -> Unit
        }
    }
}

/** Spirit level: a line that stays horizontal while the phone tilts. */
@Composable
fun LevelOverlay(roll: Float, modifier: Modifier = Modifier) {
    val level = abs(roll) < 1f
    Canvas(modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val half = size.width * 0.22f
        val color = if (level) Color(0xFF58E07A) else Color.White.copy(alpha = 0.8f)
        // Fixed reference ticks.
        drawLine(Color.White.copy(alpha = 0.5f), Offset(cx - half - 24.dp.toPx(), cy), Offset(cx - half - 8.dp.toPx(), cy), 2.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.5f), Offset(cx + half + 8.dp.toPx(), cy), Offset(cx + half + 24.dp.toPx(), cy), 2.dp.toPx(), StrokeCap.Round)
        rotate(-roll, pivot = Offset(cx, cy)) {
            drawLine(color, Offset(cx - half, cy), Offset(cx + half, cy), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
fun FocusRing(point: Pair<Float, Float>?, focused: Boolean, focusing: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (point == null) 0f else 1f, tween(200), label = "focusAlpha")
    if (point == null && alpha == 0f) return
    Canvas(modifier.fillMaxSize()) {
        val p = point ?: return@Canvas
        val center = Offset(p.first * size.width, p.second * size.height)
        val radius = 34.dp.toPx()
        val color = when {
            focusing -> Color.White
            focused -> Color(0xFF58E07A)
            else -> Color(0xFFFFC857)
        }.copy(alpha = alpha)
        drawCircle(color, radius, center, style = Stroke(2.dp.toPx()))
        val tick = 6.dp.toPx()
        listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f)).forEach { d ->
            drawLine(color, center + d * (radius - tick), center + d * (radius + tick), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
fun HistogramOverlay(bins: FloatArray, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(112.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(4.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (bins.isEmpty()) return@Canvas
            val barW = size.width / bins.size
            bins.forEachIndexed { i, v ->
                val h = v.coerceIn(0f, 1f) * size.height
                drawRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(i * barW, size.height - h),
                    size = Size(barW * 0.9f, h),
                )
            }
        }
    }
}
