package com.rawvision.camera.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.location.Location
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.rawvision.camera.data.model.Photo
import com.rawvision.camera.data.storage.AppStorage
import com.rawvision.camera.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

/** Turns a [CaptureOutput] into DNG / JPEG / thumbnail / sidecar files and a [Photo] row. */
class CaptureSaver(private val storage: AppStorage, private val context: android.content.Context) {

    class SaveRequest(
        val output: CaptureOutput,
        val location: Location?,
        val author: String,
        val copyright: String,
        val thumbnailSize: Int = 512,
    )

    class SaveResult(val photo: Photo, val rawError: String?)

    suspend fun save(req: SaveRequest): SaveResult = withContext(Dispatchers.IO) {
        var rawError: String? = null
        val out = req.output
        val info = out.info
        val result = out.result
        val now = System.currentTimeMillis()
        val baseName = storage.newBaseName(now)
        val exifOrientation = ImageUtils.orientationFromDegrees(out.orientationDegrees)
        val description = buildString {
            if (req.author.isNotBlank()) append(req.author)
            if (req.copyright.isNotBlank()) { if (isNotEmpty()) append(" · "); append(req.copyright) }
        }

        try {
            // ---- JPEG first (fast, gives us a thumbnail) ----
            var jpegFile: File? = null
            var thumbBitmap: Bitmap? = null
            out.jpeg?.let { image ->
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                val f = storage.jpegFile(baseName)
                FileOutputStream(f).use { it.write(bytes) }
                writeJpegExif(f, req, description)
                jpegFile = f
                thumbBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = ImageUtils.sampleSize(image.width, image.height, req.thumbnailSize, req.thumbnailSize)
                })?.let { ImageUtils.applyOrientation(it, readOrientation(f)) }
            }

            // ---- DNG ----
            var rawFile: File? = null
            out.raw?.let { image ->
                val f = storage.rawFile(baseName)
                val thumb = thumbBitmap?.let { tb ->
                    val t = ImageUtils.fitLongEdge(tb, 256)
                    if (t.config == Bitmap.Config.ARGB_8888) t else t.copy(Bitmap.Config.ARGB_8888, false)
                }
                // Progressive fallback: some HALs reject the DNG for reasons unrelated to the pixels
                // (GPS tags, thumbnail, stale characteristics). Try the full write first, then drop
                // one extra in turn so the RAW data is never lost; the failures are kept in the notes.
                val fresh = runCatching {
                    (context.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager)
                        .getCameraCharacteristics(info.id)
                }.getOrNull()
                val attempts = listOf(
                    DngAttempt("full", info.characteristics, thumb, req.location, description),
                    DngAttempt("fresh characteristics", fresh ?: info.characteristics, thumb, req.location, description),
                    DngAttempt("no thumbnail", info.characteristics, null, req.location, description),
                    DngAttempt("no location", info.characteristics, null, null, description),
                    DngAttempt("no description", info.characteristics, null, null, ""),
                )
                val failures = mutableListOf<String>()
                for (attempt in attempts) {
                    try {
                        val creator = DngCreator(attempt.characteristics, result)
                        creator.setOrientation(exifOrientation)
                        attempt.location?.let { creator.setLocation(it) }
                        if (attempt.description.isNotBlank()) creator.setDescription(attempt.description)
                        attempt.thumbnail?.let { creator.setThumbnail(it) }
                        image.planes[0].buffer.rewind()
                        FileOutputStream(f).use { creator.writeImage(it, image) }
                        creator.close()
                        rawFile = f
                        if (failures.isNotEmpty()) rawError = "DNG written (${attempt.label}) after: " + failures.joinToString(" | ")
                        break
                    } catch (e: Exception) {
                        f.delete()
                        val msg = "${attempt.label}: ${e.message ?: e.javaClass.simpleName}"
                        failures += msg
                        android.util.Log.e("CaptureSaver", "DNG attempt failed on camera ${info.id}: $msg", e)
                    }
                }
                if (rawFile == null) {
                    if (jpegFile == null) throw IllegalStateException(failures.joinToString(" | "))
                    rawError = "DNG failed: " + failures.joinToString(" | ")
                }
            }

            // ---- Thumbnail (fallback: decode the DNG) ----
            if (thumbBitmap == null && rawFile != null) {
                thumbBitmap = ImageUtils.decodeSampled(rawFile!!, req.thumbnailSize, req.thumbnailSize)
            }
            val thumbFile = thumbBitmap?.let { bmp ->
                val f = storage.thumbFile(baseName)
                ImageUtils.writeJpeg(ImageUtils.fitLongEdge(bmp, req.thumbnailSize), f, 85)
                f
            }

            val width = out.raw?.width ?: out.jpeg?.width ?: 0
            val height = out.raw?.height ?: out.jpeg?.height ?: 0
            val p = out.params
            val photo = Photo(
                uuid = UUID.randomUUID().toString(),
                createdAt = now,
                baseName = baseName,
                rawPath = rawFile?.absolutePath,
                jpegPath = jpegFile?.absolutePath,
                thumbPath = thumbFile?.absolutePath,
                sidecarPath = storage.sidecarFile(baseName).absolutePath,
                width = width,
                height = height,
                orientation = exifOrientation,
                cameraId = info.id,
                lensFacing = info.facing,
                iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
                exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                aperture = result.get(CaptureResult.LENS_APERTURE) ?: info.primaryAperture,
                focalLengthMm = result.get(CaptureResult.LENS_FOCAL_LENGTH) ?: info.primaryFocalLength,
                focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
                whiteBalanceMode = p.whiteBalance.name,
                whiteBalanceKelvin = when (p.whiteBalance) {
                    WhiteBalance.AUTO -> null
                    WhiteBalance.MANUAL -> p.kelvin
                    else -> p.whiteBalance.kelvin
                },
                exposureCompensation = p.exposureCompensation,
                flashMode = p.flash.name,
                zoomRatio = p.zoom,
                captureMode = p.modeLabel,
                latitude = req.location?.latitude,
                longitude = req.location?.longitude,
                altitude = req.location?.takeIf { it.hasAltitude() }?.altitude,
                gpsTime = req.location?.time,
                author = req.author.ifBlank { null },
                copyright = req.copyright.ifBlank { null },
                notes = rawError ?: "",
                deviceMake = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                deviceModel = Build.MODEL,
            )
            runCatching { XmpSidecar.write(storage.sidecarFile(baseName), photo, null) }
            SaveResult(photo, rawError)
        } finally {
            out.close()
        }
    }

    private class DngAttempt(
        val label: String,
        val characteristics: android.hardware.camera2.CameraCharacteristics,
        val thumbnail: Bitmap?,
        val location: Location?,
        val description: String,
    )

    private fun readOrientation(file: File): Int = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun writeJpegExif(file: File, req: SaveRequest, description: String) {
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            if (req.author.isNotBlank()) exif.setAttribute(ExifInterface.TAG_ARTIST, req.author)
            if (req.copyright.isNotBlank()) exif.setAttribute(ExifInterface.TAG_COPYRIGHT, req.copyright)
            if (description.isNotBlank()) exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, description)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "RAWVision Camera")
            req.location?.let { exif.setGpsInfo(it) }
            exif.saveAttributes()
        }
    }
}

