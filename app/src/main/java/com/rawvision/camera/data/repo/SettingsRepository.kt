package com.rawvision.camera.data.repo

import com.rawvision.camera.data.db.AppDatabase
import com.rawvision.camera.data.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Loads [AppSettings] from the `settings` table once, exposes them as a
 * [StateFlow] and writes every change back immediately.
 */
class SettingsRepository(private val db: AppDatabase) {

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.update { next }
        withContext(Dispatchers.IO) { persist(next) }
    }

    private fun load(): AppSettings {
        val m = db.settings.all()
        val d = AppSettings()
        fun s(key: String, def: String) = m[key] ?: def
        fun b(key: String, def: Boolean) = m[key]?.toBooleanStrictOrNull() ?: def
        fun i(key: String, def: Int) = m[key]?.toIntOrNull() ?: def
        fun <E : Enum<E>> e(key: String, def: E): E = m.enumOr(key, def)
        return AppSettings(
            themeMode = e("theme_mode", d.themeMode),
            amoledBlack = b("amoled_black", d.amoledBlack),
            dynamicColor = b("dynamic_color", d.dynamicColor),
            accent = e("accent", d.accent),
            fontScale = e("font_scale", d.fontScale),
            authorName = s("author_name", d.authorName),
            copyright = s("copyright", d.copyright),
            saveLocation = b("save_location", d.saveLocation),
            grid = e("grid", d.grid),
            showLevel = b("show_level", d.showLevel),
            showHistogram = b("show_histogram", d.showHistogram),
            captureFormat = e("capture_format", d.captureFormat),
            jpegQuality = i("jpeg_quality", d.jpegQuality),
            shutterSound = b("shutter_sound", d.shutterSound),
            haptics = b("haptics", d.haptics),
            volumeShutter = b("volume_shutter", d.volumeShutter),
            mirrorFront = b("mirror_front", d.mirrorFront),
            keepScreenOn = b("keep_screen_on", d.keepScreenOn),
            copyToGallery = b("copy_to_gallery", d.copyToGallery),
            lastCameraId = s("last_camera_id", d.lastCameraId),
        )
    }

    private fun persist(s: AppSettings) {
        db.settings.putAll(
            mapOf(
                "theme_mode" to s.themeMode.name,
                "amoled_black" to s.amoledBlack.toString(),
                "dynamic_color" to s.dynamicColor.toString(),
                "accent" to s.accent.name,
                "font_scale" to s.fontScale.name,
                "author_name" to s.authorName,
                "copyright" to s.copyright,
                "save_location" to s.saveLocation.toString(),
                "grid" to s.grid.name,
                "show_level" to s.showLevel.toString(),
                "show_histogram" to s.showHistogram.toString(),
                "capture_format" to s.captureFormat.name,
                "jpeg_quality" to s.jpegQuality.toString(),
                "shutter_sound" to s.shutterSound.toString(),
                "haptics" to s.haptics.toString(),
                "volume_shutter" to s.volumeShutter.toString(),
                "mirror_front" to s.mirrorFront.toString(),
                "keep_screen_on" to s.keepScreenOn.toString(),
                "copy_to_gallery" to s.copyToGallery.toString(),
                "last_camera_id" to s.lastCameraId,
            )
        )
    }

    // Per-camera manual presets (ISO, shutter, focus, WB ...) stored as JSON.
    suspend fun loadCameraPreset(cameraId: String): String? =
        withContext(Dispatchers.IO) { db.settings.getPreset(cameraId) }

    suspend fun saveCameraPreset(cameraId: String, json: String) =
        withContext(Dispatchers.IO) { db.settings.putPreset(cameraId, json) }
}

/** Reads an enum constant by name, falling back to [def] (uses the default's declaring class). */
private fun <E : Enum<E>> Map<String, String>.enumOr(key: String, def: E): E {
    val name = this[key] ?: return def
    return def.declaringJavaClass.enumConstants?.firstOrNull { it.name == name } ?: def
}
