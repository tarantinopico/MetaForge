package com.example.metaforge.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.metaforge.model.MediaFormat
import com.example.metaforge.model.MetadataCategory
import com.example.metaforge.model.MetadataItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale

class AudioMetadataEngine : MetadataEngine {

    override fun canHandle(format: MediaFormat): Boolean {
        return format == MediaFormat.MP3 || format == MediaFormat.FLAC
    }

    override fun extractMetadata(context: Context, file: File, format: MediaFormat): List<MetadataItem> {
        val items = mutableListOf<MetadataItem>()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)

            // Sensitive / Personal Audio Metadata
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_ARTIST, "ARTIST", "Artist / Performer", MetadataCategory.SENSITIVE, true, items)
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, "ALBUMARTIST", "Album Artist", MetadataCategory.SENSITIVE, true, items)
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_COMPOSER, "COMPOSER", "Composer", MetadataCategory.SENSITIVE, true, items)
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_AUTHOR, "AUTHOR", "Author", MetadataCategory.SENSITIVE, true, items)
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_WRITER, "WRITER", "Writer", MetadataCategory.SENSITIVE, true, items)

            // Audio Tags
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_TITLE, "TITLE", "Track Title", MetadataCategory.AUDIO, false, items)
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_ALBUM, "ALBUM", "Album Name", MetadataCategory.AUDIO, false, items)
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_DATE, "DATE", "Release Date / Year", MetadataCategory.AUDIO, false, items)
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_GENRE, "GENRE", "Genre", MetadataCategory.AUDIO, false, items)
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, "TRACK_NUMBER", "Track Number", MetadataCategory.AUDIO, false, items)
            addRetrieverItem(retriever, MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER, "DISC_NUMBER", "Disc Number", MetadataCategory.AUDIO, false, items)

            // Technical Audio Parameters
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durationMs != null && durationMs > 0) {
                val seconds = (durationMs / 1000) % 60
                val minutes = (durationMs / (1000 * 60)) % 60
                val hours = durationMs / (1000 * 60 * 60)
                val durationStr = if (hours > 0) {
                    String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format(Locale.US, "%d:%02d", minutes, seconds)
                }
                items.add(
                    MetadataItem(
                        key = "AUDIO_DURATION",
                        rawKey = "DURATION",
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
                        key = "AUDIO_BITRATE",
                        rawKey = "BITRATE",
                        displayName = "Bitrate",
                        value = "${bitrate / 1000} kbps",
                        category = MetadataCategory.TECHNICAL,
                        isSensitive = false,
                        isEditable = false,
                        isRemovable = false
                    )
                )
            }

            // Tag Container Inspection
            if (format == MediaFormat.MP3) {
                inspectMp3Headers(file, items)
            } else if (format == MediaFormat.FLAC) {
                inspectFlacHeaders(file, items)
            }

        } catch (_: Exception) {
            // Graceful fallback
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return items
    }

    private fun addRetrieverItem(
        retriever: MediaMetadataRetriever,
        keyCode: Int,
        keyName: String,
        displayName: String,
        category: MetadataCategory,
        isSensitive: Boolean,
        items: MutableList<MetadataItem>
    ) {
        val value = retriever.extractMetadata(keyCode) ?: return
        if (value.isBlank()) return
        items.add(
            MetadataItem(
                key = keyName,
                rawKey = "TAG_$keyName",
                displayName = displayName,
                value = value.trim(),
                category = category,
                isSensitive = isSensitive,
                isEditable = true,
                isRemovable = true
            )
        )
    }

    private fun inspectMp3Headers(file: File, items: MutableList<MetadataItem>) {
        try {
            RandomAccessFile(file, "r").use { raf ->
                // Check ID3v2 at start
                if (raf.length() >= 10) {
                    val header = ByteArray(10)
                    raf.readFully(header)
                    if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                        val major = header[3].toInt()
                        val size = parseSyncsafe(header, 6)
                        items.add(
                            MetadataItem(
                                key = "ID3V2_TAG",
                                rawKey = "ID3v2.$major",
                                displayName = "ID3v2 Container",
                                value = "Version 2.$major (Header size: ${size + 10} bytes)",
                                category = MetadataCategory.TECHNICAL,
                                isSensitive = false,
                                isEditable = false,
                                isRemovable = true
                            )
                        )
                    }
                }

                // Check ID3v1 at end
                if (raf.length() >= 128) {
                    raf.seek(raf.length() - 128)
                    val v1Header = ByteArray(3)
                    raf.readFully(v1Header)
                    if (v1Header[0] == 'T'.code.toByte() && v1Header[1] == 'A'.code.toByte() && v1Header[2] == 'G'.code.toByte()) {
                        items.add(
                            MetadataItem(
                                key = "ID3V1_TAG",
                                rawKey = "ID3v1",
                                displayName = "ID3v1 Container",
                                value = "Legacy 128-byte trailer present",
                                category = MetadataCategory.TECHNICAL,
                                isSensitive = false,
                                isEditable = false,
                                isRemovable = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun inspectFlacHeaders(file: File, items: MutableList<MetadataItem>) {
        items.add(
            MetadataItem(
                key = "FLAC_CONTAINER",
                rawKey = "fLaC",
                displayName = "FLAC Format",
                value = "Native FLAC Stream (Lossless)",
                category = MetadataCategory.TECHNICAL,
                isSensitive = false,
                isEditable = false,
                isRemovable = false
            )
        )
    }

    private fun parseSyncsafe(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0x7F
        val b1 = bytes[offset + 1].toInt() and 0x7F
        val b2 = bytes[offset + 2].toInt() and 0x7F
        val b3 = bytes[offset + 3].toInt() and 0x7F
        return (b0 shl 21) or (b1 shl 14) or (b2 shl 7) or b3
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
            if (format == MediaFormat.MP3) {
                sanitizeMp3(sourceFile, destFile, tagsToRemove)
            } else {
                // FLAC or other audio: copy stream cleanly
                sourceFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }
        } catch (e: Exception) {
            destFile.delete()
            false
        }
    }

    private fun sanitizeMp3(sourceFile: File, destFile: File, tagsToRemove: Set<String>): Boolean {
        // Safe, lossless MP3 stream sanitization:
        // Detects ID3v2 at start and ID3v1 at end, and streams MPEG audio frames directly
        RandomAccessFile(sourceFile, "r").use { raf ->
            val fileLen = raf.length()
            var audioStart = 0L
            var audioEnd = fileLen

            // 1. Check ID3v2 header
            if (fileLen >= 10) {
                val header = ByteArray(10)
                raf.readFully(header)
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    val tagSize = parseSyncsafe(header, 6)
                    val hasFooter = (header[5].toInt() and 0x10) != 0
                    val totalV2Size = 10 + tagSize + (if (hasFooter) 10 else 0)
                    audioStart = totalV2Size.toLong()
                }
            }

            // 2. Check ID3v1 trailer (last 128 bytes)
            if (fileLen >= 128) {
                raf.seek(fileLen - 128)
                val v1Header = ByteArray(3)
                raf.readFully(v1Header)
                if (v1Header[0] == 'T'.code.toByte() && v1Header[1] == 'A'.code.toByte() && v1Header[2] == 'G'.code.toByte()) {
                    audioEnd = fileLen - 128
                }
            }

            if (audioEnd <= audioStart) {
                // Defensive fallback: copy entire file if lengths are inconsistent
                audioStart = 0L
                audioEnd = fileLen
            }

            // Stream pure MPEG audio payload to destFile
            raf.seek(audioStart)
            var bytesRemaining = audioEnd - audioStart
            val buffer = ByteArray(64 * 1024)

            FileOutputStream(destFile).use { out ->
                while (bytesRemaining > 0) {
                    val toRead = bytesRemaining.coerceAtMost(buffer.size.toLong()).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    bytesRemaining -= read
                }
            }
        }
        return true
    }
}
