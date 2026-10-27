package com.rawvision.camera.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.rawvision.camera.data.model.EditParams
import com.rawvision.camera.data.model.ExportRecord
import com.rawvision.camera.data.model.Photo
import com.rawvision.camera.data.repo.PhotoRepository
import com.rawvision.camera.data.storage.AppStorage
import com.rawvision.camera.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Renders a photo with its recipe to a JPEG inside the sandbox and copies the important metadata. */
class Exporter(private val storage: AppStorage, private val repo: PhotoRepository) {

    class Result(val file: File, val record: ExportRecord)

    suspend fun export(photo: Photo, params: EditParams, quality: Int, maxLongEdge: Int = 6000): Result = withContext(Dispatchers.IO) {
        val sourceFile = File(photo.rawPath ?: photo.jpegPath ?: throw IllegalStateException("Photo has no file"))
        var decoded = ImageUtils.decodeSampled(sourceFile, maxLongEdge, maxLongEdge)
        if (decoded == null && photo.jpegPath != null) decoded = ImageUtils.decodeSampled(File(photo.jpegPath), maxLongEdge, maxLongEdge)
        val base = decoded ?: throw IllegalStateException("Could not decode ${sourceFile.name}")
        val rendered = ImagePipeline.render(base, params)
        if (rendered !== base) base.recycle()

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = storage.exportFile(photo.baseName, "edit_$stamp")
        ImageUtils.writeJpeg(rendered, file, quality)
        writeExif(file, photo, sourceFile)
        val record = repo.recordExport(
            ExportRecord(
                photoId = photo.id,
                path = file.absolutePath,
                createdAt = System.currentTimeMillis(),
                width = rendered.width,
                height = rendered.height,
                sizeBytes = file.length(),
            )
        ).let { id ->
            ExportRecord(id, photo.id, file.absolutePath, System.currentTimeMillis(), rendered.width, rendered.height, file.length())
        }
        rendered.recycle()
        Result(file, record)
    }

    private fun writeExif(target: File, photo: Photo, source: File) {
        runCatching {
            val out = ExifInterface(target.absolutePath)
            val src = runCatching { ExifInterface(source.absolutePath) }.getOrNull()
            val copy = listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MODEL,
                ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH,
                ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            )
            if (src != null) copy.forEach { tag -> src.getAttribute(tag)?.let { out.setAttribute(tag, it) } }
            out.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            out.setAttribute(ExifInterface.TAG_SOFTWARE, "RAWVision Camera")
            out.setAttribute(ExifInterface.TAG_MAKE, photo.deviceMake ?: src?.getAttribute(ExifInterface.TAG_MAKE))
            out.setAttribute(ExifInterface.TAG_MODEL, photo.deviceModel ?: src?.getAttribute(ExifInterface.TAG_MODEL))
            photo.iso?.let { out.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, it.toString()) }
            photo.exposureNs?.let { out.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, (it / 1_000_000_000.0).toString()) }
            photo.aperture?.let { out.setAttribute(ExifInterface.TAG_F_NUMBER, it.toString()) }
            photo.focalLengthMm?.let { out.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "${(it * 100).toInt()}/100") }
            if (out.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) {
                out.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(photo.createdAt)))
            }
            photo.author?.let { out.setAttribute(ExifInterface.TAG_ARTIST, it) }
            photo.copyright?.let { out.setAttribute(ExifInterface.TAG_COPYRIGHT, it) }
            if (photo.latitude != null && photo.longitude != null) {
                out.setLatLong(photo.latitude, photo.longitude)
                photo.altitude?.let { alt -> out.setAltitude(alt) }
            }
            out.saveAttributes()
        }
    }

    companion object {
        fun shareIntent(context: Context, file: File, mime: String = "image/jpeg"): Intent {
            val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            return Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
