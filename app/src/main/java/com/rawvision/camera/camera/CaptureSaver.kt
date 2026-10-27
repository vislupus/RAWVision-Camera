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
class CaptureSaver(private val storage: AppStorage) {

    class SaveRequest(
        val output: CaptureOutput,
        val location: Location?,
        val author: String,
        val copyright: String,
        val thumbnailSize: Int = 512,
    )

    suspend fun save(req: SaveRequest): Photo = withContext(Dispatchers.IO) {
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
                val creator = DngCreator(info.characteristics, result)
                creator.setOrientation(exifOrientation)
                req.location?.let { creator.setLocation(it) }
                if (description.isNotBlank()) creator.setDescription(description)
                thumbBitmap?.let { tb ->
                    val t = ImageUtils.fitLongEdge(tb, 256)
                    val argb = if (t.config == Bitmap.Config.ARGB_8888) t else t.copy(Bitmap.Config.ARGB_8888, false)
                    runCatching { creator.setThumbnail(argb) }
                }
                FileOutputStream(f).use { creator.writeImage(it, image) }
                creator.close()
                rawFile = f
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
                    WhiteBalance.AUTO -> result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let { ExposureMath.kelvinFromGains(it) }
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
                deviceMake = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                deviceModel = Build.MODEL,
            )
            runCatching { XmpSidecar.write(storage.sidecarFile(baseName), photo, null) }
            photo
        } finally {
            out.close()
        }
    }

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

