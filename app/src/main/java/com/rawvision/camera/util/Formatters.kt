package com.rawvision.camera.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    /** 1/125, 0.5", 2.0" style shutter speed text. */
    fun shutter(exposureNs: Long?): String {
        if (exposureNs == null || exposureNs <= 0) return "—"
        val seconds = exposureNs / 1_000_000_000.0
        return when {
            seconds >= 1.0 -> String.format(Locale.US, "%.1f\"", seconds)
            seconds >= 0.25 -> String.format(Locale.US, "%.2f", seconds).trimEnd('0').trimEnd('.') + "\""
            else -> "1/" + (1.0 / seconds).roundToInt()
        }
    }

    fun aperture(f: Float?): String = if (f == null || f <= 0f) "—" else String.format(Locale.US, "f/%.1f", f)

    fun focalLength(mm: Float?): String = if (mm == null || mm <= 0f) "—" else String.format(Locale.US, "%.1f mm", mm)

    fun iso(iso: Int?): String = if (iso == null || iso <= 0) "—" else "ISO $iso"

    fun ev(value: Float): String = if (value == 0f) "0" else String.format(Locale.US, "%+.1f", value)

    fun kelvin(k: Int?): String = if (k == null) "—" else "${k}K"

    fun zoom(ratio: Float): String = String.format(Locale.US, "%.1fx", ratio)

    fun focusDistance(diopters: Float?): String {
        if (diopters == null) return "—"
        if (diopters <= 0.01f) return "∞"
        val meters = 1f / diopters
        return if (meters >= 1f) String.format(Locale.US, "%.1f m", meters) else String.format(Locale.US, "%.0f cm", meters * 100)
    }

    fun coordinates(lat: Double?, lon: Double?): String {
        if (lat == null || lon == null) return "—"
        return String.format(Locale.US, "%.5f, %.5f", lat, lon)
    }

    fun bytes(size: Long): String {
        if (size < 1024) return "$size B"
        val kb = size / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun dateTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    fun date(millis: Long): String = DateFormat.getDateInstance(DateFormat.LONG).format(Date(millis))

    fun megapixels(width: Int, height: Int): String =
        String.format(Locale.US, "%.1f MP", width.toLong() * height / 1_000_000.0)
}
