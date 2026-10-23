package com.example

import com.example.metaforge.detector.FormatDetector
import com.example.metaforge.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class FormatDetectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `detects JPEG by magic bytes even with deceptive txt extension`() {
        val file = tempFolder.newFile("photo.txt")
        file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10))

        val result = FormatDetector.detect(file)
        assertEquals(MediaFormat.JPEG, result.format)
        assertTrue(result.magicHex.startsWith("FF D8 FF"))
    }

    @Test
    fun `detects PNG by 8-byte signature even with mp3 extension`() {
        val file = tempFolder.newFile("audio.mp3")
        file.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00))

        val result = FormatDetector.detect(file)
        assertEquals(MediaFormat.PNG, result.format)
        assertTrue(result.magicHex.startsWith("89 50 4E 47"))
    }

    @Test
    fun `detects WebP by RIFF and WEBP signature`() {
        val file = tempFolder.newFile("image.bin")
        val bytes = ByteArray(16)
        bytes[0] = 'R'.code.toByte()
        bytes[1] = 'I'.code.toByte()
        bytes[2] = 'F'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[8] = 'W'.code.toByte()
        bytes[9] = 'E'.code.toByte()
        bytes[10] = 'B'.code.toByte()
        bytes[11] = 'P'.code.toByte()
        file.writeBytes(bytes)

        val result = FormatDetector.detect(file)
        assertEquals(MediaFormat.WEBP, result.format)
    }

    @Test
    fun `detects MP3 by ID3 header`() {
        val file = tempFolder.newFile("song.dat")
        file.writeBytes(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))

        val result = FormatDetector.detect(file)
        assertEquals(MediaFormat.MP3, result.format)
    }

    @Test
    fun `detects FLAC by signature`() {
        val file = tempFolder.newFile("track.flac")
        file.writeBytes(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte(), 0x00, 0x00, 0x22))

        val result = FormatDetector.detect(file)
        assertEquals(MediaFormat.FLAC, result.format)
    }

    @Test
    fun `detects MP4 by ftyp box`() {
        val file = tempFolder.newFile("video.mov")
        val bytes = ByteArray(12)
        bytes[4] = 'f'.code.toByte()
        bytes[5] = 't'.code.toByte()
        bytes[6] = 'y'.code.toByte()
        bytes[7] = 'p'.code.toByte()
        file.writeBytes(bytes)

        val result = FormatDetector.detect(file)
        assertEquals(MediaFormat.MP4, result.format)
    }

    @Test
    fun `handles empty zero-byte file gracefully without exception`() {
        val file = tempFolder.newFile("empty.bin")
        file.writeBytes(ByteArray(0))

        val result = FormatDetector.detect(file)
        assertEquals(MediaFormat.UNKNOWN, result.format)
        assertFalse(result.capabilities.canSanitize)
    }

    @Test
    fun `verifies original file hash is unchanged during inspection`() {
        val file = tempFolder.newFile("original.jpg")
        val content = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte(), 0x01, 0x02, 0x03)
        file.writeBytes(content)

        val digestBefore = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val result = FormatDetector.detect(file)
        val digestAfter = MessageDigest.getInstance("SHA-256").digest(file.readBytes())

        assertEquals(MediaFormat.JPEG, result.format)
        assertTrue(digestBefore.contentEquals(digestAfter))
    }
}
