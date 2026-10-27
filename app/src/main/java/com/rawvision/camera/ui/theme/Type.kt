package com.rawvision.camera.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rawvision.camera.R

/**
 * Roboto (variable font, SIL OFL) bundled in res/font so the UI looks the
 * same on every device, regardless of the OEM system font.
 */
@OptIn(ExperimentalTextApi::class)
private fun robotoFont(weight: FontWeight) = Font(
    resId = R.font.roboto_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Roboto = FontFamily(
    robotoFont(FontWeight.Light),
    robotoFont(FontWeight.Normal),
    robotoFont(FontWeight.Medium),
    robotoFont(FontWeight.SemiBold),
    robotoFont(FontWeight.Bold),
)

private fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal, letterSpacing: Float = 0f) =
    TextStyle(
        fontFamily = Roboto,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
    )

val RawVisionTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Normal, -0.25f),
    displayMedium = style(45, 52),
    displaySmall = style(36, 44),
    headlineLarge = style(32, 40, FontWeight.Medium),
    headlineMedium = style(28, 36, FontWeight.Medium),
    headlineSmall = style(24, 32, FontWeight.Medium),
    titleLarge = style(22, 28, FontWeight.Medium),
    titleMedium = style(16, 24, FontWeight.Medium, 0.15f),
    titleSmall = style(14, 20, FontWeight.Medium, 0.1f),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.5f),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.25f),
    bodySmall = style(12, 16, FontWeight.Normal, 0.4f),
    labelLarge = style(14, 20, FontWeight.Medium, 0.1f),
    labelMedium = style(12, 16, FontWeight.Medium, 0.5f),
    labelSmall = style(11, 16, FontWeight.Medium, 0.5f),
)
