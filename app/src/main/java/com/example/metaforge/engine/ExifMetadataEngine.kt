package com.example.metaforge.engine

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import com.example.metaforge.model.MediaFormat
import com.example.metaforge.model.MetadataCategory
import com.example.metaforge.model.MetadataItem
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ExifMetadataEngine : MetadataEngine {

    override fun canHandle(format: MediaFormat): Boolean {
        return format == MediaFormat.JPEG || format == MediaFormat.PNG || format == MediaFormat.WEBP
    }

    override fun extractMetadata(context: Context, file: File, format: MediaFormat): List<MetadataItem> {
        val items = mutableListOf<MetadataItem>()

        try {
            val exif = ExifInterface(file.absolutePath)

            // 1. GPS & Location (High Privacy Priority)
            val latLong = FloatArray(2)
            val hasGps = exif.getLatLong(latLong)
            if (hasGps) {
                val formattedCoords = String.format(Locale.US, "%.5f°, %.5f°", latLong[0], latLong[1])
                items.add(
                    MetadataItem(
                        key = "GPS_COORDINATES",
                        rawKey = "GPSLatitude/Longitude",
                        displayName = "GPS Location (Decimal)",
                        value = formattedCoords,
                        category = MetadataCategory.SENSITIVE,
                        isSensitive = true
                    )
                )
            }

            readTag(exif, ExifInterface.TAG_GPS_LATITUDE, "GPS Latitude", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_GPS_LONGITUDE, "GPS Longitude", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_GPS_ALTITUDE, "GPS Altitude", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_GPS_DATESTAMP, "GPS Date", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_GPS_TIMESTAMP, "GPS Time", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS Method", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }

            // 2. Personal & Device Identification (Sensitive)
            readTag(exif, ExifInterface.TAG_ARTIST, "Artist / Author", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_COPYRIGHT, "Copyright", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_MAKE, "Device Manufacturer", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_MODEL, "Camera Model", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_SOFTWARE, "Software / Firmware", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_BODY_SERIAL_NUMBER, "Device Serial Number", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_LENS_MODEL, "Lens Model", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_LENS_MAKE, "Lens Manufacturer", MetadataCategory.SENSITIVE, true)?.let { items.add(it) }

            // 3. Exif Timestamps
            readTag(exif, ExifInterface.TAG_DATETIME_ORIGINAL, "Date & Time Original", MetadataCategory.EXIF, true)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_DATETIME, "Date & Time Modified", MetadataCategory.EXIF, false)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_DATETIME_DIGITIZED, "Date & Time Digitized", MetadataCategory.EXIF, false)?.let { items.add(it) }

            // 4. Photographic Parameters
            readTag(exif, ExifInterface.TAG_EXPOSURE_TIME, "Exposure Time", MetadataCategory.TECHNICAL, false)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_F_NUMBER, "Aperture (F-Stop)", MetadataCategory.TECHNICAL, false)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "ISO Speed", MetadataCategory.TECHNICAL, false)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_FOCAL_LENGTH, "Focal Length", MetadataCategory.TECHNICAL, false)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_FLASH, "Flash", MetadataCategory.TECHNICAL, false)?.let { items.add(it) }
            readTag(exif, ExifInterface.TAG_WHITE_BALANCE, "White Balance", MetadataCategory.TECHNICAL, false)?.let { items.add(it) }

            // 5. Structural & Dimensions
            val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            if (width > 0 && height > 0) {
                items.add(
                    MetadataItem(
                        key = "IMAGE_DIMENSIONS",
                        rawKey = "ImageWidth x ImageLength",
                        displayName = "Dimensions",
                        value = "${width} × ${height} px",
                        category = MetadataCategory.TECHNICAL,
                        isSensitive = false,
                        isEditable = false,
                        isRemovable = false
                    )
                )
            }

            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            items.add(
                MetadataItem(
                    key = ExifInterface.TAG_ORIENTATION,
                    rawKey = "Orientation",
                    displayName = "Exif Orientation",
                    value = formatOrientation(orientation),
                    category = MetadataCategory.TECHNICAL,
                    isSensitive = false,
                    isEditable = false,
                    isRemovable = false // Never remove orientation automatically as it breaks display!
                )
            )

        } catch (e: Exception) {
            // Return collected items gracefully if partial parsing succeeds
        }

        return items
    }

    private fun readTag(
        exif: ExifInterface,
        tag: String,
        displayName: String,
        category: MetadataCategory,
        isSensitive: Boolean
    ): MetadataItem? {
        val value = exif.getAttribute(tag) ?: return null
        if (value.isBlank()) return null
        return MetadataItem(
            key = tag,
            rawKey = tag,
            displayName = displayName,
            value = value.trim(),
            category = category,
            isSensitive = isSensitive,
            isEditable = true,
            isRemovable = true
        )
    }

    private fun formatOrientation(orientation: Int): String {
        return when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> "1 (Normal 0°)"
            ExifInterface.ORIENTATION_ROTATE_90 -> "6 (Rotate 90° CW)"
            ExifInterface.ORIENTATION_ROTATE_180 -> "3 (Rotate 180°)"
            ExifInterface.ORIENTATION_ROTATE_270 -> "8 (Rotate 270° CW)"
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "2 (Flipped horizontal)"
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> "4 (Flipped vertical)"
            else -> "$orientation"
        }
    }

    override fun sanitize(
        context: Context,
        sourceFile: File,
        destFile: File,
        format: MediaFormat,
        tagsToRemove: Set<String>,
        tagsToModify: Map<String, String>
    ): Boolean {
        return try {
            // Step 1: Copy pristine source to destination in sandbox
            sourceFile.inputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Step 2: Open ExifInterface on the copy
            val exif = ExifInterface(destFile.absolutePath)

            // Step 3: Handle GPS bulk removal if GPS coordinates or any GPS tag is targeted
            val isGpsRemoval = tagsToRemove.any { it.startsWith("GPS") || it == "GPS_COORDINATES" }
            if (isGpsRemoval) {
                // Clear all GPS tags reliably
                listOf(
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_GPS_DATESTAMP,
                    ExifInterface.TAG_GPS_PROCESSING_METHOD,
                    ExifInterface.TAG_GPS_AREA_INFORMATION,
                    ExifInterface.TAG_GPS_DEST_BEARING,
                    ExifInterface.TAG_GPS_SPEED,
                    ExifInterface.TAG_GPS_TRACK
                ).forEach { gpsTag ->
                    exif.setAttribute(gpsTag, null)
                }
            }

            // Step 4: Remove other specified tags
            for (tag in tagsToRemove) {
                if (tag == "GPS_COORDINATES" || tag == "IMAGE_DIMENSIONS" || tag == ExifInterface.TAG_ORIENTATION) {
                    continue // Do not attempt to nullify non-removable or synthetic tags
                }
                exif.setAttribute(tag, null)
            }

            // Step 5: Apply modifications
            for ((tag, value) in tagsToModify) {
                if (tag != "IMAGE_DIMENSIONS" && tag != "GPS_COORDINATES") {
                    exif.setAttribute(tag, value)
                }
            }

            // Step 6: Save attributes in-place in destFile
            exif.saveAttributes()
            true
        } catch (e: Exception) {
            destFile.delete()
            false
        }
    }
}
