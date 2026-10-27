package com.rawvision.camera.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.rawvision.camera.data.model.AppSettings
import com.rawvision.camera.data.model.ThemeMode

/** Exposes whether the resolved theme is dark so screens can adapt overlays. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** Consistent spacing scale used by every screen. */
object Dimens {
    val xs = 4
    val sm = 8
    val md = 16
    val lg = 24
    val xl = 32
    val cornerSmall = 10
    val cornerMedium = 16
    val cornerLarge = 24
}

@Composable
fun RawVisionTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val seed = Color(settings.accent.seed)
    val useDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context).let { s ->
            if (settings.amoledBlack) s.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black) else s
        }
        useDynamic -> dynamicLightColorScheme(context)
        dark -> ColorSchemes.dark(seed, settings.amoledBlack)
        else -> ColorSchemes.light(seed)
    }
    val density = LocalDensity.current
    val scaledDensity = Density(density.density, density.fontScale * settings.fontScale.factor)

    CompositionLocalProvider(
        LocalIsDarkTheme provides dark,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = RawVisionTypography,
            content = content,
        )
    }
}
