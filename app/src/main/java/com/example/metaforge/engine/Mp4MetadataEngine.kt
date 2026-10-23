package com.example.metaforge.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.metaforge.model.MediaFormat
import com.example.metaforge.model.MetadataCategory
import com.example.metaforge.model.MetadataItem
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class Mp4MetadataEngine : MetadataEngine {

    override fun canHandle(format: MediaFormat): Boolean {
        return format == MediaFormat.MP4
    }

    override fun extractMetadata(context: Context, file: File, format: MediaFormat): List<MetadataItem> {
        val items = mutableListOf<MetadataItem>()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)

            // 1. Sensitive Data (GPS location embedded by cameras in MP4)
            val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            if (!location.isNullOrBlank()) {
                items.add(
                    MetadataItem(
                        key = "MP4_LOCATION",
                        rawKey = "©xyz / udta.loc",
                        displayName = "Embedded Location",
                        value = location.trim(),
                        category = MetadataCategory.SENSITIVE,
                        isSensitive = true,
                        isEditable = false,
                        isRemovable = true
                    )
                )
            }

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (!artist.isNullOrBlank()) {
                items.add(
                    MetadataItem(
                        key = "MP4_ARTIST",
                        rawKey = "©ART",
                        displayName = "Artist / Author",
                        value = artist.trim(),
                        category = MetadataCategory.SENSITIVE,
                        isSensitive = true,
                        isEditable = false,
                        isRemovable = true
                    )
                )
            }

            // 2. Descriptive Tags
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!title.isNullOrBlank()) {
                items.add(
                    MetadataItem(
                        key = "MP4_TITLE",
                        rawKey = "©nam",
                        displayName = "Title",
                        value = title.trim(),
                        category = MetadataCategory.VIDEO,
                        isSensitive = false,
                        isEditable = false,
                        isRemovable = true
                    )
                )
            }

            val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            if (!date.isNullOrBlank()) {
                items.add(
                    MetadataItem(
                        key = "MP4_DATE",
                        rawKey = "©day",
                        displayName = "Creation Date",
                        value = date.trim(),
                        category = MetadataCategory.VIDEO,
                        isSensitive = false,
                        isEditable = false,
                        isRemovable = true
                    )
                )
            }

            // 3. Technical Video Dimensions & Encoding
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (!width.isNullOrBlank() && !height.isNullOrBlank()) {
                items.add(
                    MetadataItem(
                        key = "VIDEO_DIMENSIONS",
                        rawKey = "tkhd.width x tkhd.height",
                        displayName = "Resolution",
                        value = "$width × $height px",
                        category = MetadataCategory.TECHNICAL,
                        isSensitive = false,
                        isEditable = false,
                        isRemovable = false
                    )
                )
            }

            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            if (!rotation.isNullOrBlank()) {
                items.add(
                    MetadataItem(
                        key = "VIDEO_ROTATION",
                        rawKey = "tkhd.matrix",
                        displayName = "Rotation",
                        value = "$rotation°",
                        category = MetadataCategory.TECHNICAL,
                        isSensitive = false,
                        isEditable = false,
                        isRemovable = false
                    )
                )
            }

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durationMs != null && durationMs > 0) {
                val sec = (durationMs / 1000) % 60
                val min = (durationMs / (1000 * 60)) % 60
                val hrs = durationMs / (1000 * 60 * 60)
                val durationStr = if (hrs > 0) String.format(Locale.US, "%d:%02d:%02d", hrs, min, sec) else String.format(Locale.US, "%d:%02d", min, sec)
                items.add(
                    MetadataItem(
                        key = "VIDEO_DURATION",
                        rawKey = "mvhd.duration",
                        displayName = "Duration",
                        value = durationStr,
                        category = MetadataCategory.TECHNICAL,
                        isSensitive = false,
                        isEditable = false,
                        isRemovable = false
                    )
                )
            }

            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            if (bitrate != null && bitrate > 0) {
                items.add(
                    MetadataItem(
                        key = "VIDEO_BITRATE",
                        rawKey = "bitrate",
                        displayName = "Bitrate",
                        value = "${bitrate / 1000} kbps",
                        category = MetadataCategory.TECHNICAL,
                        isSensitive = false,
                        isEditable = false,
                        isRemovable = false
                    )
                )
            }

        } catch (_: Exception) {
            // Graceful handling
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return items
    }

    override fun sanitize(
        context: Context,
        sourceFile: File,
        destFile: File,
        format: MediaFormat,
        tagsToRemove: Set<String>,
        tagsToModify: Map<String, String>
    ): Boolean {
        // MP4 safe copy
        return try {
            sourceFile.inputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            destFile.delete()
            false
        }
    }
}
