package com.rawvision.camera.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs

/**
 * Builds a complete Material 3 colour scheme from a single seed colour using
 * HSL maths, so the user can pick any accent in Settings without shipping the
 * full material-color-utilities library.
 */
object ColorSchemes {

    fun light(seed: Color): ColorScheme {
        val primary = seed.withLightness(0.40f)
        val secondary = seed.shiftHue(30f).withSaturation(0.35f).withLightness(0.38f)
        val tertiary = seed.shiftHue(-50f).withSaturation(0.45f).withLightness(0.36f)
        val neutral = seed.withSaturation(0.06f)
        return lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = seed.withSaturation(0.85f).withLightness(0.90f),
            onPrimaryContainer = seed.withLightness(0.12f),
            inversePrimary = seed.withLightness(0.80f),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondary.withLightness(0.90f),
            onSecondaryContainer = secondary.withLightness(0.12f),
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = tertiary.withLightness(0.90f),
            onTertiaryContainer = tertiary.withLightness(0.12f),
            background = neutral.withLightness(0.985f),
            onBackground = neutral.withLightness(0.10f),
            surface = neutral.withLightness(0.985f),
            onSurface = neutral.withLightness(0.10f),
            surfaceVariant = neutral.withSaturation(0.12f).withLightness(0.90f),
            onSurfaceVariant = neutral.withLightness(0.30f),
            surfaceTint = primary,
            inverseSurface = neutral.withLightness(0.18f),
            inverseOnSurface = neutral.withLightness(0.95f),
            outline = neutral.withLightness(0.50f),
            outlineVariant = neutral.withLightness(0.80f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = neutral.withLightness(0.965f),
            surfaceContainer = neutral.withLightness(0.945f),
            surfaceContainerHigh = neutral.withLightness(0.92f),
            surfaceContainerHighest = neutral.withLightness(0.895f),
            surfaceBright = neutral.withLightness(0.985f),
            surfaceDim = neutral.withLightness(0.86f),
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            scrim = Color.Black,
        )
    }

    fun dark(seed: Color, amoled: Boolean): ColorScheme {
        val primary = seed.withSaturation(0.80f).withLightness(0.76f)
        val secondary = seed.shiftHue(30f).withSaturation(0.30f).withLightness(0.74f)
        val tertiary = seed.shiftHue(-50f).withSaturation(0.40f).withLightness(0.74f)
        val neutral = seed.withSaturation(0.05f)
        val base = if (amoled) 0f else 0.07f
        fun n(l: Float) = if (amoled && l <= base) Color.Black else neutral.withLightness(l)
        return darkColorScheme(
            primary = primary,
            onPrimary = seed.withLightness(0.14f),
            primaryContainer = seed.withSaturation(0.55f).withLightness(0.28f),
            onPrimaryContainer = seed.withLightness(0.92f),
            inversePrimary = seed.withLightness(0.40f),
            secondary = secondary,
            onSecondary = secondary.withLightness(0.14f),
            secondaryContainer = secondary.withLightness(0.26f),
            onSecondaryContainer = secondary.withLightness(0.92f),
            tertiary = tertiary,
            onTertiary = tertiary.withLightness(0.14f),
            tertiaryContainer = tertiary.withLightness(0.26f),
            onTertiaryContainer = tertiary.withLightness(0.92f),
            background = n(base),
            onBackground = neutral.withLightness(0.92f),
            surface = n(base),
            onSurface = neutral.withLightness(0.92f),
            surfaceVariant = neutral.withLightness(0.22f),
            onSurfaceVariant = neutral.withLightness(0.78f),
            surfaceTint = primary,
            inverseSurface = neutral.withLightness(0.90f),
            inverseOnSurface = neutral.withLightness(0.14f),
            outline = neutral.withLightness(0.55f),
            outlineVariant = neutral.withLightness(0.28f),
            surfaceContainerLowest = n(if (amoled) 0f else 0.045f),
            surfaceContainerLow = neutral.withLightness(if (amoled) 0.06f else 0.10f),
            surfaceContainer = neutral.withLightness(if (amoled) 0.09f else 0.125f),
            surfaceContainerHigh = neutral.withLightness(if (amoled) 0.12f else 0.16f),
            surfaceContainerHighest = neutral.withLightness(if (amoled) 0.15f else 0.19f),
            surfaceBright = neutral.withLightness(0.24f),
            surfaceDim = n(base),
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
            scrim = Color.Black,
        )
    }
}

// ---- Small HSL helpers -----------------------------------------------------

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun Color.toHsl(): Hsl {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return Hsl(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
        g -> ((b - r) / d + 2f) * 60f
        else -> ((r - g) / d + 4f) * 60f
    }
    return Hsl(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hh = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hh % 2f - 1f))
    val (r1, g1, b1) = when {
        hh < 1f -> Triple(c, x, 0f)
        hh < 2f -> Triple(x, c, 0f)
        hh < 3f -> Triple(0f, c, x)
        hh < 4f -> Triple(0f, x, c)
        hh < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f))
}

fun Color.withLightness(l: Float): Color { val hsl = toHsl(); return hslToColor(hsl.h, hsl.s, l.coerceIn(0f, 1f)) }
fun Color.withSaturation(s: Float): Color { val hsl = toHsl(); return hslToColor(hsl.h, s.coerceIn(0f, 1f), hsl.l) }
fun Color.shiftHue(degrees: Float): Color { val hsl = toHsl(); return hslToColor(hsl.h + degrees, hsl.s, hsl.l) }

/** Picks black or white text for a background colour. */
fun Color.contentColor(): Color = if (luminance() > 0.45f) Color(0xFF111111) else Color.White
