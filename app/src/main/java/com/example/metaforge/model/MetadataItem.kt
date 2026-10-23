package com.example.metaforge.model

enum class MetadataCategory(val displayName: String, val order: Int) {
    SYSTEM("System & Storage", 0),
    SENSITIVE("Privacy & Location", 1),
    EXIF("Exif & Camera", 2),
    AUDIO("Audio & Tags", 3),
    VIDEO("Video & Media", 4),
    TECHNICAL("Technical & Encoding", 5)
}

enum class SanitizationProfileType {
    REMOVE_GPS,
    REMOVE_PERSONAL,
    MAX_CLEAN,
    CUSTOM
}

data class MetadataItem(
    val key: String,
    val rawKey: String,
    val displayName: String,
    val value: String,
    val category: MetadataCategory,
    val isSensitive: Boolean,
    val isEditable: Boolean = true,
    val isRemovable: Boolean = true
)

data class DiffItem(
    val key: String,
    val displayName: String,
    val originalValue: String,
    val newValue: String?,
    val status: DiffStatus
)

enum class DiffStatus {
    KEPT,
    MODIFIED,
    REMOVED
}

data class FileAnalysis(
    val fileName: String,
    val detectedFormat: MediaFormat,
    val declaredMimeType: String?,
    val sizeBytes: Long,
    val magicBytesHex: String,
    val sha256: String? = null,
    val items: List<MetadataItem>,
    val capabilities: FormatCapabilities,
    val localFilePath: String
)
