package com.example.metaforge.model

enum class MediaFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val categoryName: String
) {
    JPEG("jpg", "image/jpeg", "JPEG Image", "Image"),
    PNG("png", "image/png", "PNG Image", "Image"),
    WEBP("webp", "image/webp", "WebP Image", "Image"),
    MP3("mp3", "audio/mpeg", "MP3 Audio", "Audio"),
    FLAC("flac", "audio/flac", "FLAC Audio", "Audio"),
    MP4("mp4", "video/mp4", "MP4 Container", "Video"),
    UNKNOWN("bin", "application/octet-stream", "Unknown / Binary", "Other")
}

data class FormatCapabilities(
    val canRead: Boolean,
    val canEdit: Boolean,
    val canSanitize: Boolean,
    val supportedProfiles: List<SanitizationProfileType>,
    val description: String,
    val limitations: String
)
