package com.example.metaforge.repository

import android.content.Context
import com.example.metaforge.detector.FormatDetector
import com.example.metaforge.engine.MetadataEngineRegistry
import com.example.metaforge.model.DiffItem
import com.example.metaforge.model.DiffStatus
import com.example.metaforge.model.FileAnalysis
import com.example.metaforge.model.MetadataItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MetadataVerifier(private val context: Context) {

    data class VerificationResult(
        val isVerified: Boolean,
        val sanitizedAnalysis: FileAnalysis,
        val diffItems: List<DiffItem>,
        val removedCount: Int,
        val modifiedCount: Int,
        val keptCount: Int,
        val verificationNotes: String
    )

    suspend fun verify(
        originalAnalysis: FileAnalysis,
        sanitizedFile: File,
        targetedForRemoval: Set<String>,
        targetedForModification: Map<String, String>
    ): VerificationResult = withContext(Dispatchers.IO) {
        val detection = FormatDetector.detect(sanitizedFile)
        val engine = MetadataEngineRegistry.getEngine(detection.format)
        val newItems = engine.extractMetadata(context, sanitizedFile, detection.format)

        val newItemsByKey = newItems.associateBy { it.key }
        val originalItemsByKey = originalAnalysis.items.associateBy { it.key }

        val diffs = mutableListOf<DiffItem>()
        var removedCount = 0
        var modifiedCount = 0
        var keptCount = 0

        // Process all original items
        for (orig in originalAnalysis.items) {
            val newItem = newItemsByKey[orig.key]
            if (newItem == null) {
                // Item was removed
                diffs.add(
                    DiffItem(
                        key = orig.key,
                        displayName = orig.displayName,
                        originalValue = orig.value,
                        newValue = null,
                        status = DiffStatus.REMOVED
                    )
                )
                removedCount++
            } else if (newItem.value != orig.value) {
                // Item was modified
                diffs.add(
                    DiffItem(
                        key = orig.key,
                        displayName = orig.displayName,
                        originalValue = orig.value,
                        newValue = newItem.value,
                        status = DiffStatus.MODIFIED
                    )
                )
                modifiedCount++
            } else {
                // Item remained unchanged
                diffs.add(
                    DiffItem(
                        key = orig.key,
                        displayName = orig.displayName,
                        originalValue = orig.value,
                        newValue = orig.value,
                        status = DiffStatus.KEPT
                    )
                )
                keptCount++
            }
        }

        // Check if any targeted tag failed to be removed
        var verificationSuccess = true
        val failedTags = mutableListOf<String>()
        for (tag in targetedForRemoval) {
            if (tag != "GPS_COORDINATES" && newItemsByKey.containsKey(tag)) {
                verificationSuccess = false
                failedTags.add(tag)
            }
        }

        val notes = if (verificationSuccess) {
            "All targeted tags were verified as cleanly removed or updated in the independent re-analysis."
        } else {
            "Warning: Some tags could not be completely removed by the container parser: ${failedTags.joinToString()}"
        }

        val sanitizedAnalysis = FileAnalysis(
            fileName = sanitizedFile.name,
            detectedFormat = detection.format,
            declaredMimeType = originalAnalysis.declaredMimeType,
            sizeBytes = sanitizedFile.length(),
            magicBytesHex = detection.magicHex,
            sha256 = null,
            items = newItems,
            capabilities = detection.capabilities,
            localFilePath = sanitizedFile.absolutePath
        )

        VerificationResult(
            isVerified = verificationSuccess,
            sanitizedAnalysis = sanitizedAnalysis,
            diffItems = diffs,
            removedCount = removedCount,
            modifiedCount = modifiedCount,
            keptCount = keptCount,
            verificationNotes = notes
        )
    }
}
