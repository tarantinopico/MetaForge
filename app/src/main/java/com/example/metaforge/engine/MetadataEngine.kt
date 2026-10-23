package com.example.metaforge.engine

import android.content.Context
import com.example.metaforge.model.MediaFormat
import com.example.metaforge.model.MetadataItem
import java.io.File

interface MetadataEngine {
    fun canHandle(format: MediaFormat): Boolean

    fun extractMetadata(context: Context, file: File, format: MediaFormat): List<MetadataItem>

    fun sanitize(
        context: Context,
        sourceFile: File,
        destFile: File,
        format: MediaFormat,
        tagsToRemove: Set<String>,
        tagsToModify: Map<String, String>
    ): Boolean
}
