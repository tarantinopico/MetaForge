package com.example.metaforge.detector

import com.example.metaforge.model.FormatCapabilities
import com.example.metaforge.model.MediaFormat
import com.example.metaforge.model.SanitizationProfileType
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object FormatDetector {

    data class DetectionResult(
        val format: MediaFormat,
        val magicHex: String,
        val capabilities: FormatCapabilities
    )

    fun detect(file: File): DetectionResult {
        if (!file.exists() || file.length() == 0L) {
            return DetectionResult(
                format = MediaFormat.UNKNOWN,
                magicHex = "00 00 00 00",
                capabilities = getCapabilities(MediaFormat.UNKNOWN)
            )
        }

        val header = ByteArray(32)
        var bytesRead = 0
        try {
            FileInputStream(file).use { stream ->
                bytesRead = stream.read(header)
            }
        } catch (_: Exception) {
            // Safe fallback on IO error
        }

        return detectFromBytes(header, bytesRead)
    }

    fun detectFromStream(stream: InputStream): DetectionResult {
        val header = ByteArray(32)
        val bytesRead = stream.read(header)
        return detectFromBytes(header, bytesRead)
    }

    private fun detectFromBytes(header: ByteArray, bytesRead: Int): DetectionResult {
        val magicHex = buildHexDisplay(header, bytesRead.coerceAtMost(16))
        val format = when {
            isJpeg(header, bytesRead) -> MediaFormat.JPEG
            isPng(header, bytesRead) -> MediaFormat.PNG
            isWebP(header, bytesRead) -> MediaFormat.WEBP
            isFlac(header, bytesRead) -> MediaFormat.FLAC
            isMp3(header, bytesRead) -> MediaFormat.MP3
            isMp4(header, bytesRead) -> MediaFormat.MP4
            else -> MediaFormat.UNKNOWN
        }

        return DetectionResult(
            format = format,
            magicHex = magicHex,
            capabilities = getCapabilities(format)
        )
    }

    private fun isJpeg(header: ByteArray, len: Int): Boolean {
        if (len < 3) return false
        return (header[0].toInt() and 0xFF == 0xFF) &&
               (header[1].toInt() and 0xFF == 0xD8) &&
               (header[2].toInt() and 0xFF == 0xFF)
    }

    private fun isPng(header: ByteArray, len: Int): Boolean {
        if (len < 8) return false
        val pngSig = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (i in 0 until 8) {
            if ((header[i].toInt() and 0xFF) != pngSig[i]) return false
        }
        return true
    }

    private fun isWebP(header: ByteArray, len: Int): Boolean {
        if (len < 12) return false
        val riff = (header[0] == 'R'.code.toByte()) &&
                   (header[1] == 'I'.code.toByte()) &&
                   (header[2] == 'F'.code.toByte()) &&
                   (header[3] == 'F'.code.toByte())
        val webp = (header[8] == 'W'.code.toByte()) &&
                   (header[9] == 'E'.code.toByte()) &&
                   (header[10] == 'B'.code.toByte()) &&
                   (header[11] == 'P'.code.toByte())
        return riff && webp
    }

    private fun isFlac(header: ByteArray, len: Int): Boolean {
        if (len < 4) return false
        return (header[0] == 'f'.code.toByte()) &&
               (header[1] == 'L'.code.toByte()) &&
               (header[2] == 'a'.code.toByte()) &&
               (header[3] == 'C'.code.toByte())
    }

    private fun isMp3(header: ByteArray, len: Int): Boolean {
        if (len < 3) return false
        // ID3 header: 'I', 'D', '3'
        if (header[0] == 'I'.code.toByte() &&
            header[1] == 'D'.code.toByte() &&
            header[2] == '3'.code.toByte()) {
            return true
        }
        // MPEG audio sync word: 11 bits set (0xFF then 0xFB, 0xF3, 0xF2, etc.)
        if (len >= 2) {
            val b0 = header[0].toInt() and 0xFF
            val b1 = header[1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                return true
            }
        }
        return false
    }

    private fun isMp4(header: ByteArray, len: Int): Boolean {
        if (len < 8) return false
        // mp4 box: [4 bytes size][4 bytes 'ftyp']
        return (header[4] == 'f'.code.toByte()) &&
               (header[5] == 't'.code.toByte()) &&
               (header[6] == 'y'.code.toByte()) &&
               (header[7] == 'p'.code.toByte())
    }

    private fun buildHexDisplay(header: ByteArray, count: Int): String {
        if (count <= 0) return "00 00 00 00"
        val sb = StringBuilder()
        for (i in 0 until count) {
            if (i > 0) sb.append(" ")
            sb.append(String.format("%02X", header[i].toInt() and 0xFF))
        }
        return sb.toString()
    }

    fun getCapabilities(format: MediaFormat): FormatCapabilities {
        return when (format) {
            MediaFormat.JPEG -> FormatCapabilities(
                canRead = true,
                canEdit = true,
                canSanitize = true,
                supportedProfiles = listOf(
                    SanitizationProfileType.REMOVE_GPS,
                    SanitizationProfileType.REMOVE_PERSONAL,
                    SanitizationProfileType.MAX_CLEAN,
                    SanitizationProfileType.CUSTOM
                ),
                description = "Full Exif, GPS, Camera parameters, and IPTC reading & lossless in-place attribute editing without pixel recompression.",
                limitations = "Orientation tag is preserved during sanitization to avoid unwanted rotation."
            )
            MediaFormat.PNG -> FormatCapabilities(
                canRead = true,
                canEdit = true,
                canSanitize = true,
                supportedProfiles = listOf(
                    SanitizationProfileType.REMOVE_GPS,
                    SanitizationProfileType.REMOVE_PERSONAL,
                    SanitizationProfileType.MAX_CLEAN,
                    SanitizationProfileType.CUSTOM
                ),
                description = "eXIf chunk, tEXt, and basic image dimensions.",
                limitations = "Structural chunks (IHDR, IDAT, IEND) are preserved to keep image valid."
            )
            MediaFormat.WEBP -> FormatCapabilities(
                canRead = true,
                canEdit = true,
                canSanitize = true,
                supportedProfiles = listOf(
                    SanitizationProfileType.REMOVE_GPS,
                    SanitizationProfileType.REMOVE_PERSONAL,
                    SanitizationProfileType.MAX_CLEAN,
                    SanitizationProfileType.CUSTOM
                ),
                description = "WebP EXIF and XMP chunks reading and sanitization.",
                limitations = "VP8/VP8L/VP8X bitstreams remain untouched."
            )
            MediaFormat.MP3 -> FormatCapabilities(
                canRead = true,
                canEdit = true,
                canSanitize = true,
                supportedProfiles = listOf(
                    SanitizationProfileType.REMOVE_PERSONAL,
                    SanitizationProfileType.MAX_CLEAN,
                    SanitizationProfileType.CUSTOM
                ),
                description = "ID3v1, ID3v2.2-2.4 metadata tag reader and tag stripper.",
                limitations = "GPS profile is not applicable to audio files."
            )
            MediaFormat.FLAC -> FormatCapabilities(
                canRead = true,
                canEdit = true,
                canSanitize = true,
                supportedProfiles = listOf(
                    SanitizationProfileType.REMOVE_PERSONAL,
                    SanitizationProfileType.MAX_CLEAN,
                    SanitizationProfileType.CUSTOM
                ),
                description = "Vorbis Comments, STREAMINFO block inspector and comment stripper.",
                limitations = "Mandatory STREAMINFO block is preserved to keep FLAC decodable."
            )
            MediaFormat.MP4 -> FormatCapabilities(
                canRead = true,
                canEdit = false,
                canSanitize = true,
                supportedProfiles = listOf(
                    SanitizationProfileType.REMOVE_GPS,
                    SanitizationProfileType.REMOVE_PERSONAL,
                    SanitizationProfileType.MAX_CLEAN
                ),
                description = "ISO-14496 atom inspection (moov, udta, meta), video codecs, bitrate and duration.",
                limitations = "Editing individual video frames is not supported; sanitization removes user-data metadata boxes."
            )
            MediaFormat.UNKNOWN -> FormatCapabilities(
                canRead = false,
                canEdit = false,
                canSanitize = false,
                supportedProfiles = emptyList(),
                description = "Format not supported for metadata parsing.",
                limitations = "Only file size, MIME type and magic byte signature are displayed."
            )
        }
    }
}
