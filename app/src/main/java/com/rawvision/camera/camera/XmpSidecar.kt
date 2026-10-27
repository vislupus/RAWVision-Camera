package com.rawvision.camera.camera

import com.rawvision.camera.data.model.EditParams
import com.rawvision.camera.data.model.Photo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Writes a small XMP sidecar next to each capture. It carries the creator,
 * copyright, GPS position and the non-destructive edit recipe, so the RAW file
 * itself is never modified and other software can still read the essentials.
 */
object XmpSidecar {

    fun write(file: File, photo: Photo, edits: EditParams?) {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = TimeZone.getDefault() }
        val created = fmt.format(Date(photo.createdAt))
        val sb = StringBuilder()
        sb.append("<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
        sb.append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"RAWVision Camera\">\n")
        sb.append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
        sb.append("  <rdf:Description rdf:about=\"\"\n")
        sb.append("    xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\"\n")
        sb.append("    xmlns:dc=\"http://purl.org/dc/elements/1.1/\"\n")
        sb.append("    xmlns:exif=\"http://ns.adobe.com/exif/1.0/\"\n")
        sb.append("    xmlns:tiff=\"http://ns.adobe.com/tiff/1.0/\"\n")
        sb.append("    xmlns:photoshop=\"http://ns.adobe.com/photoshop/1.0/\"\n")
        sb.append("    xmlns:rawvision=\"https://rawvision.app/ns/1.0/\"\n")
        sb.append("    xmp:CreatorTool=\"RAWVision Camera\"\n")
        sb.append("    xmp:CreateDate=\"").append(created).append("\"\n")
        sb.append("    xmp:ModifyDate=\"").append(fmt.format(Date())).append("\"\n")
        sb.append("    photoshop:DateCreated=\"").append(created).append("\"\n")
        photo.deviceMake?.let { sb.append("    tiff:Make=\"").append(esc(it)).append("\"\n") }
        photo.deviceModel?.let { sb.append("    tiff:Model=\"").append(esc(it)).append("\"\n") }
        sb.append("    tiff:Orientation=\"").append(photo.orientation).append("\"\n")
        photo.iso?.let { sb.append("    exif:ISOSpeedRatings=\"").append(it).append("\"\n") }
        photo.exposureNs?.let { sb.append("    exif:ExposureTime=\"").append(it).append("/1000000000\"\n") }
        photo.aperture?.let { sb.append("    exif:FNumber=\"").append((it * 100).toInt()).append("/100\"\n") }
        photo.focalLengthMm?.let { sb.append("    exif:FocalLength=\"").append((it * 100).toInt()).append("/100\"\n") }
        if (photo.latitude != null && photo.longitude != null) {
            sb.append("    exif:GPSLatitude=\"").append(gpsCoord(photo.latitude, true)).append("\"\n")
            sb.append("    exif:GPSLongitude=\"").append(gpsCoord(photo.longitude, false)).append("\"\n")
            photo.altitude?.let {
                sb.append("    exif:GPSAltitude=\"").append((abs(it) * 100).toInt()).append("/100\"\n")
                sb.append("    exif:GPSAltitudeRef=\"").append(if (it < 0) 1 else 0).append("\"\n")
            }
        }
        sb.append("    rawvision:captureMode=\"").append(esc(photo.captureMode)).append("\"\n")
        sb.append("    rawvision:whiteBalance=\"").append(esc(photo.whiteBalanceMode)).append("\">\n")
        if (!photo.author.isNullOrBlank()) {
            sb.append("   <dc:creator><rdf:Seq><rdf:li>").append(esc(photo.author)).append("</rdf:li></rdf:Seq></dc:creator>\n")
        }
        if (!photo.copyright.isNullOrBlank()) {
            sb.append("   <dc:rights><rdf:Alt><rdf:li xml:lang=\"x-default\">").append(esc(photo.copyright)).append("</rdf:li></rdf:Alt></dc:rights>\n")
        }
        if (edits != null && !edits.isIdentity) {
            sb.append("   <rawvision:edit>").append(esc(edits.toJson())).append("</rawvision:edit>\n")
        }
        sb.append("  </rdf:Description>\n </rdf:RDF>\n</x:xmpmeta>\n<?xpacket end=\"w\"?>\n")
        file.parentFile?.mkdirs()
        file.writeText(sb.toString())
    }

    private fun gpsCoord(value: Double, latitude: Boolean): String {
        val ref = if (latitude) (if (value >= 0) "N" else "S") else (if (value >= 0) "E" else "W")
        val v = abs(value)
        val deg = v.toInt()
        val min = (v - deg) * 60
        return String.format(Locale.US, "%d,%.6f%s", deg, min, ref)
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
