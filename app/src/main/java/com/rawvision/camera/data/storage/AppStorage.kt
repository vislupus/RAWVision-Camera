package com.rawvision.camera.data.storage

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every file the app creates lives inside its private sandbox
 * (`Context.filesDir`, the Android equivalent of the iOS
 * "Application Documents Directory"). Nothing is written to shared storage.
 *
 * Layout:
 * ```
 * files/
 *   db/rawvision.db          SQLite database
 *   photos/<name>.dng        RAW captures (DNG)
 *   photos/<name>.jpg        camera JPEG companion
 *   photos/<name>.xmp        sidecar with author / GPS / edit recipe
 *   thumbs/<name>.jpg        small previews for the gallery
 *   exports/<name>_edit.jpg  rendered edits ready to share
 * ```
 */
class AppStorage(context: Context) {

    val root: File = context.filesDir
    val dbDir: File = File(root, "db").also { it.mkdirs() }
    val photosDir: File = File(root, "photos").also { it.mkdirs() }
    val thumbsDir: File = File(root, "thumbs").also { it.mkdirs() }
    val exportsDir: File = File(root, "exports").also { it.mkdirs() }

    val databaseFile: File get() = File(dbDir, "rawvision.db")

    /** Creates a unique, sortable base name such as `RV_20260127_143501_123`. */
    fun newBaseName(timeMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(timeMillis))
        return "RV_$stamp"
    }

    fun rawFile(baseName: String) = File(photosDir, "$baseName.dng")
    fun jpegFile(baseName: String) = File(photosDir, "$baseName.jpg")
    fun sidecarFile(baseName: String) = File(photosDir, "$baseName.xmp")
    fun thumbFile(baseName: String) = File(thumbsDir, "$baseName.jpg")
    fun exportFile(baseName: String, suffix: String = "edit") = File(exportsDir, "${baseName}_$suffix.jpg")

    /** Total bytes used by the sandbox, for the storage card in settings. */
    fun usedBytes(): Long = listOf(photosDir, thumbsDir, exportsDir, dbDir).sumOf { dir ->
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun freeBytes(): Long = root.usableSpace
}
