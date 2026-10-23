package com.example.metaforge.engine

import android.content.Context
import com.example.metaforge.model.MediaFormat
import com.example.metaforge.model.MetadataCategory
import com.example.metaforge.model.MetadataItem
import java.io.File

class FallbackMetadataEngine : MetadataEngine {

    override fun canHandle(format: MediaFormat): Boolean = true

    override fun extractMetadata(context: Context, file: File, format: MediaFormat): List<MetadataItem> {
        return listOf(
            MetadataItem(
                key = "FILE_SIZE_BYTES",
                rawKey = "stat.st_size",
                displayName = "Exact Size",
                value = "${file.length()} bytes",
                category = MetadataCategory.SYSTEM,
                isSensitive = false,
                isEditable = false,
                isRemovable = false
            ),
            MetadataItem(
                key = "FILE_STATUS",
                rawKey = "engine.status",
                displayName = "Parser Status",
                value = "Unrecognized or unsupported binary format",
                category = MetadataCategory.SYSTEM,
                isSensitive = false,
                isEditable = false,
                isRemovable = false
            )
        )
    }

    override fun sanitize(
        context: Context,
        sourceFile: File,
        destFile: File,
        format: MediaFormat,
        tagsToRemove: Set<String>,
        tagsToModify: Map<String, String>
    ): Boolean {
        return false // Unsupported
    }
}
