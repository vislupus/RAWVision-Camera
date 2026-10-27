package com.rawvision.camera.data.model

/** Where the photo was taken from, mirrors CameraCharacteristics.LENS_FACING. */
enum class LensFacing { BACK, FRONT, EXTERNAL }

/** One captured photo together with everything we know about it. */
data class Photo(
    val id: Long = 0,
    val uuid: String,
    val createdAt: Long,
    val baseName: String,
    val rawPath: String?,
    val jpegPath: String?,
    val thumbPath: String?,
    val sidecarPath: String?,
    val width: Int,
    val height: Int,
    val orientation: Int,          // EXIF orientation constant
    val cameraId: String,
    val lensFacing: LensFacing,
    val iso: Int?,
    val exposureNs: Long?,
    val aperture: Float?,
    val focalLengthMm: Float?,
    val focusDistance: Float?,     // diopters, 0 = infinity
    val whiteBalanceMode: String,
    val whiteBalanceKelvin: Int?,
    val exposureCompensation: Float,
    val flashMode: String,
    val zoomRatio: Float,
    val captureMode: String,       // AUTO / MANUAL / mixed
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val gpsTime: Long?,
    val author: String?,
    val copyright: String?,
    val deviceMake: String?,
    val deviceModel: String?,
    val favorite: Boolean = false,
    val rating: Int = 0,
    val notes: String = "",
    val hasEdits: Boolean = false,
) {
    val displayName: String get() = baseName
    val isRaw: Boolean get() = rawPath != null
}

/** A rendered export of a photo (JPEG inside the sandbox). */
data class ExportRecord(
    val id: Long = 0,
    val photoId: Long,
    val path: String,
    val createdAt: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

/** A snapshot of an edit recipe kept for the "go back" history. */
data class EditVersion(
    val id: Long,
    val photoId: Long,
    val params: EditParams,
    val label: String,
    val createdAt: Long,
)
