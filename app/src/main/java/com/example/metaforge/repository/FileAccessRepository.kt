package com.example.metaforge.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class FileAccessRepository(private val context: Context) {

    private val workDir: File by lazy {
        File(context.cacheDir, "metaforge_work").apply { if (!exists()) mkdirs() }
    }

    private val exportDir: File by lazy {
        File(context.cacheDir, "metaforge_export").apply { if (!exists()) mkdirs() }
    }

    data class StagedFile(
        val file: File,
        val originalFileName: String,
        val declaredMimeType: String?,
        val sizeBytes: Long
    )

    suspend fun stageFileFromUri(uri: Uri): StagedFile = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        var fileName = "imported_file"
        var sizeBytes = 0L

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) fileName = name
                }
                if (sizeIndex >= 0) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        val declaredMime = contentResolver.getType(uri)

        // Sanitize filename to avoid path traversal or problematic characters
        val safeName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destFile = File(workDir, "work_${System.currentTimeMillis()}_$safeName")

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input stream from URI: $uri")

        val actualSize = destFile.length()
        StagedFile(
            file = destFile,
            originalFileName = fileName,
            declaredMimeType = declaredMime,
            sizeBytes = if (sizeBytes > 0) sizeBytes else actualSize
        )
    }

    suspend fun calculateSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { stream ->
            var bytesRead = stream.read(buffer)
            while (bytesRead > 0) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = stream.read(buffer)
            }
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder()
        for (b in hashBytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        sb.toString()
    }

    suspend fun exportToDestinationUri(sourceFile: File, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            } ?: return@withContext false
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun prepareShareableFile(sourceFile: File, suggestedName: String): Uri = withContext(Dispatchers.IO) {
        val shareFile = File(exportDir, suggestedName)
        sourceFile.inputStream().use { input ->
            FileOutputStream(shareFile).use { output ->
                input.copyTo(output)
            }
        }
        val authority = "${context.packageName}.fileprovider"
        FileProvider.getUriForFile(context, authority, shareFile)
    }

    suspend fun createWorkingCopy(sourceFile: File, prefix: String = "clean_"): File = withContext(Dispatchers.IO) {
        val newFile = File(workDir, "${prefix}${System.currentTimeMillis()}_${sourceFile.name}")
        sourceFile.inputStream().use { input ->
            FileOutputStream(newFile).use { output ->
                input.copyTo(output)
            }
        }
        newFile
    }

    suspend fun clearWorkingCache() = withContext(Dispatchers.IO) {
        workDir.listFiles()?.forEach { it.delete() }
        exportDir.listFiles()?.forEach { it.delete() }
    }
}
