package com.rawvision.camera.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {

    /** Decodes [file] so that neither dimension exceeds the requested size (power-of-two sampling). */
    fun decodeSampled(file: File, reqWidth: Int, reqHeight: Int, applyExif: Boolean = true): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        if (!applyExif) return bmp
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyOrientation(bmp, orientation)
    }

    /**
     * Decodes the best source of a [Photo] (DNG first, then the camera JPEG) and
     * returns it upright. DNG files written by DngCreator carry the orientation
     * in a TIFF tag that not every reader honours, so when the EXIF reader
     * reports nothing we fall back to the orientation stored in the database,
     * guarded by an aspect-ratio check so an already rotated decode is never
     * rotated twice.
     */
    fun decodePhoto(photo: com.rawvision.camera.data.model.Photo, maxEdge: Int): Bitmap? {
        val candidates = listOfNotNull(photo.rawPath, photo.jpegPath).map { File(it) }.filter { it.exists() }
        for (file in candidates) {
            val bmp = decodeSampled(file, maxEdge, maxEdge, applyExif = false) ?: continue
            val exif = runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val isDng = file.extension.equals("dng", ignoreCase = true)
            val sensorLandscape = photo.width >= photo.height
            val decodedLandscape = bmp.width >= bmp.height
            val orientation = when {
                exif != ExifInterface.ORIENTATION_NORMAL && exif != ExifInterface.ORIENTATION_UNDEFINED -> exif
                isDng && decodedLandscape == sensorLandscape -> photo.orientation
                else -> ExifInterface.ORIENTATION_NORMAL
            }
            return applyOrientation(bmp, orientation)
        }
        return null
    }

    fun sampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= reqWidth && height / (sample * 2) >= reqHeight) sample *= 2
        return sample
    }

    fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (out !== bitmap) bitmap.recycle()
        return out
    }

    /** Scales so the long edge equals [longEdge] (never upscales). */
    fun fitLongEdge(bitmap: Bitmap, longEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= longEdge) return bitmap
        val scale = longEdge.toFloat() / longest
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    fun writeJpeg(bitmap: Bitmap, file: File, quality: Int) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), it) }
    }

    /** Degrees of rotation encoded by an EXIF orientation value. */
    fun orientationDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }

    fun orientationFromDegrees(degrees: Int): Int = when (((degrees % 360) + 360) % 360) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }
}
