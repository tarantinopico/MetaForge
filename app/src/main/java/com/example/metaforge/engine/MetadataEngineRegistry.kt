package com.example.metaforge.engine

import com.example.metaforge.model.MediaFormat

object MetadataEngineRegistry {
    private val exifEngine = ExifMetadataEngine()
    private val audioEngine = AudioMetadataEngine()
    private val mp4Engine = Mp4MetadataEngine()
    private val fallbackEngine = FallbackMetadataEngine()

    fun getEngine(format: MediaFormat): MetadataEngine {
        return when {
            exifEngine.canHandle(format) -> exifEngine
            audioEngine.canHandle(format) -> audioEngine
            mp4Engine.canHandle(format) -> mp4Engine
            else -> fallbackEngine
        }
    }
}
