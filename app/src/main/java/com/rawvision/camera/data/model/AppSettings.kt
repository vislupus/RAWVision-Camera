package com.rawvision.camera.data.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class FontScale(val factor: Float) {
    SMALL(0.875f), NORMAL(1f), LARGE(1.15f), EXTRA_LARGE(1.3f)
}

enum class GridMode { NONE, THIRDS, GOLDEN, SQUARE }

enum class CaptureFormat { RAW_JPEG, RAW_ONLY, JPEG_ONLY }

/** Accent colours offered in Settings. [seed] is the ARGB value used to build the Material scheme. */
enum class AccentColor(val seed: Long) {
    INDIGO(0xFF4F5BD5), BLUE(0xFF2F6FED), TEAL(0xFF0F9D8A), EMERALD(0xFF1E9E5A),
    AMBER(0xFFD9900B), ORANGE(0xFFE2632A), ROSE(0xFFD9426F), VIOLET(0xFF8B4FD9),
    SLATE(0xFF5B6B7C), MONO(0xFF2B2B2B),
}

/**
 * Everything the user can change in Settings. Persisted as key/value rows in
 * the `settings` table so that the whole state lives in SQLite.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoledBlack: Boolean = false,
    val dynamicColor: Boolean = false,
    val accent: AccentColor = AccentColor.INDIGO,
    val fontScale: FontScale = FontScale.NORMAL,
    val authorName: String = "",
    val copyright: String = "",
    val saveLocation: Boolean = true,
    val grid: GridMode = GridMode.THIRDS,
    val showLevel: Boolean = true,
    val showHistogram: Boolean = true,
    val captureFormat: CaptureFormat = CaptureFormat.RAW_JPEG,
    val jpegQuality: Int = 95,
    val shutterSound: Boolean = true,
    val haptics: Boolean = true,
    val volumeShutter: Boolean = true,
    val mirrorFront: Boolean = true,
    val keepScreenOn: Boolean = true,
    val lastCameraId: String = "",
)
