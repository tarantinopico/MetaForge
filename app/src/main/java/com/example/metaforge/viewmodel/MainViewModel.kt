package com.example.metaforge.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metaforge.detector.FormatDetector
import com.example.metaforge.engine.MetadataEngineRegistry
import com.example.metaforge.model.FileAnalysis
import com.example.metaforge.model.MetadataCategory
import com.example.metaforge.model.MetadataItem
import com.example.metaforge.model.SanitizationProfileType
import com.example.metaforge.repository.FileAccessRepository
import com.example.metaforge.repository.MetadataVerifier
import com.example.metaforge.repository.PreferencesRepository
import com.example.metaforge.repository.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AppScreen {
    HOME,
    INSPECTOR,
    SANITIZE_PROFILES,
    DIFF_PREVIEW,
    RESULT,
    SETTINGS
}

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.HOME,
    val currentAnalysis: FileAnalysis? = null,
    val isAnalyzing: Boolean = false,
    val isComputingSha256: Boolean = false,
    val searchQuery: String = "",
    val selectedCategory: MetadataCategory? = null,
    val showRawTags: Boolean = false,
    val selectedProfile: SanitizationProfileType = SanitizationProfileType.REMOVE_GPS,
    val selectedTagsForRemoval: Set<String> = emptySet(),
    val tagsToModify: Map<String, String> = emptyMap(),
    val isProcessingSanitization: Boolean = false,
    val sanitizedFile: File? = null,
    val verificationResult: MetadataVerifier.VerificationResult? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepo = FileAccessRepository(application)
    private val verifier = MetadataVerifier(application)
    private val prefsRepo = PreferencesRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = prefsRepo.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.AMOLED
    )

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen, errorMessage = null) }
    }

    fun analyzeFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, errorMessage = null) }
            try {
                val staged = fileRepo.stageFileFromUri(uri)
                val detection = FormatDetector.detect(staged.file)
                val engine = MetadataEngineRegistry.getEngine(detection.format)
                val items = engine.extractMetadata(getApplication(), staged.file, detection.format)

                val analysis = FileAnalysis(
                    fileName = staged.originalFileName,
                    detectedFormat = detection.format,
                    declaredMimeType = staged.declaredMimeType,
                    sizeBytes = staged.sizeBytes,
                    magicBytesHex = detection.magicHex,
                    sha256 = null,
                    items = items,
                    capabilities = detection.capabilities,
                    localFilePath = staged.file.absolutePath
                )

                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        currentAnalysis = analysis,
                        currentScreen = AppScreen.INSPECTOR,
                        searchQuery = "",
                        selectedCategory = null,
                        selectedProfile = SanitizationProfileType.REMOVE_GPS,
                        selectedTagsForRemoval = getDefaultRemovalSet(analysis, SanitizationProfileType.REMOVE_GPS),
                        tagsToModify = emptyMap(),
                        sanitizedFile = null,
                        verificationResult = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = e.localizedMessage ?: "Failed to read file from storage"
                    )
                }
            }
        }
    }

    fun computeSha256() {
        val analysis = _uiState.value.currentAnalysis ?: return
        if (analysis.sha256 != null || _uiState.value.isComputingSha256) return

        viewModelScope.launch {
            _uiState.update { it.copy(isComputingSha256 = true) }
            try {
                val file = File(analysis.localFilePath)
                val hash = fileRepo.calculateSha256(file)
                _uiState.update {
                    it.copy(
                        isComputingSha256 = false,
                        currentAnalysis = it.currentAnalysis?.copy(sha256 = hash)
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isComputingSha256 = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: MetadataCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleRawTags() {
        _uiState.update { it.copy(showRawTags = !it.showRawTags) }
    }

    fun selectProfile(profile: SanitizationProfileType) {
        val analysis = _uiState.value.currentAnalysis ?: return
        val removalSet = getDefaultRemovalSet(analysis, profile)
        _uiState.update {
            it.copy(
                selectedProfile = profile,
                selectedTagsForRemoval = removalSet
            )
        }
    }

    fun toggleTagRemoval(key: String) {
        _uiState.update { state ->
            val set = state.selectedTagsForRemoval.toMutableSet()
            if (set.contains(key)) {
                set.remove(key)
            } else {
                set.add(key)
            }
            state.copy(
                selectedTagsForRemoval = set,
                selectedProfile = SanitizationProfileType.CUSTOM
            )
        }
    }

    private fun getDefaultRemovalSet(analysis: FileAnalysis, profile: SanitizationProfileType): Set<String> {
        val removableItems = analysis.items.filter { it.isRemovable }
        return when (profile) {
            SanitizationProfileType.REMOVE_GPS -> {
                removableItems.filter { it.key.startsWith("GPS") || it.key == "GPS_COORDINATES" || it.key == "MP4_LOCATION" }.map { it.key }.toSet()
            }
            SanitizationProfileType.REMOVE_PERSONAL -> {
                removableItems.filter { it.isSensitive }.map { it.key }.toSet()
            }
            SanitizationProfileType.MAX_CLEAN -> {
                removableItems.map { it.key }.toSet()
            }
            SanitizationProfileType.CUSTOM -> emptySet()
        }
    }

    fun executeSanitization() {
        val analysis = _uiState.value.currentAnalysis ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingSanitization = true, errorMessage = null) }
            try {
                val sourceFile = File(analysis.localFilePath)
                val destFile = fileRepo.createWorkingCopy(sourceFile, "sanitized_")
                val engine = MetadataEngineRegistry.getEngine(analysis.detectedFormat)

                val success = engine.sanitize(
                    context = getApplication(),
                    sourceFile = sourceFile,
                    destFile = destFile,
                    format = analysis.detectedFormat,
                    tagsToRemove = _uiState.value.selectedTagsForRemoval,
                    tagsToModify = _uiState.value.tagsToModify
                )

                if (!success) {
                    _uiState.update {
                        it.copy(
                            isProcessingSanitization = false,
                            errorMessage = "Engine could not apply sanitization changes to this file."
                        )
                    }
                    return@launch
                }

                // Run verification
                val verification = verifier.verify(
                    originalAnalysis = analysis,
                    sanitizedFile = destFile,
                    targetedForRemoval = _uiState.value.selectedTagsForRemoval,
                    targetedForModification = _uiState.value.tagsToModify
                )

                _uiState.update {
                    it.copy(
                        isProcessingSanitization = false,
                        sanitizedFile = destFile,
                        verificationResult = verification,
                        currentScreen = AppScreen.RESULT
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingSanitization = false,
                        errorMessage = e.localizedMessage ?: "Sanitization failed unexpectedly"
                    )
                }
            }
        }
    }

    fun exportToDestination(targetUri: Uri, onSuccess: () -> Unit) {
        val file = _uiState.value.sanitizedFile ?: return
        viewModelScope.launch {
            val ok = fileRepo.exportToDestinationUri(file, targetUri)
            if (ok) {
                _uiState.update { it.copy(statusMessage = "File saved successfully!") }
                onSuccess()
            } else {
                _uiState.update { it.copy(errorMessage = "Failed to write file to selected location") }
            }
        }
    }

    fun getShareUri(): Uri? {
        val file = _uiState.value.sanitizedFile ?: return null
        val originalName = _uiState.value.currentAnalysis?.fileName ?: "sanitized_file"
        val ext = _uiState.value.currentAnalysis?.detectedFormat?.extension ?: "bin"
        val cleanName = if (originalName.contains(".")) {
            val dot = originalName.lastIndexOf('.')
            "${originalName.substring(0, dot)}_cleaned.$ext"
        } else {
            "${originalName}_cleaned.$ext"
        }
        return kotlinx.coroutines.runBlocking {
            fileRepo.prepareShareableFile(file, cleanName)
        }
    }

    fun exportMetadataAsJson(): String {
        val analysis = _uiState.value.currentAnalysis ?: return "{}"
        val root = JSONObject()
        root.put("fileName", analysis.fileName)
        root.put("detectedFormat", analysis.detectedFormat.displayName)
        root.put("declaredMimeType", analysis.declaredMimeType ?: "unknown")
        root.put("sizeBytes", analysis.sizeBytes)
        root.put("magicBytesHex", analysis.magicBytesHex)
        if (analysis.sha256 != null) {
            root.put("sha256", analysis.sha256)
        }

        val itemsArray = JSONArray()
        for (item in analysis.items) {
            val obj = JSONObject()
            obj.put("key", item.key)
            obj.put("rawKey", item.rawKey)
            obj.put("displayName", item.displayName)
            obj.put("value", item.value)
            obj.put("category", item.category.name)
            obj.put("isSensitive", item.isSensitive)
            itemsArray.put(obj)
        }
        root.put("metadata", itemsArray)
        return root.toString(2)
    }

    fun exportMetadataAsText(): String {
        val analysis = _uiState.value.currentAnalysis ?: return ""
        val sb = StringBuilder()
        sb.append("=== METAFORGE METADATA REPORT ===\n")
        sb.append("File Name: ${analysis.fileName}\n")
        sb.append("Format: ${analysis.detectedFormat.displayName}\n")
        sb.append("Size: ${analysis.sizeBytes} bytes\n")
        sb.append("Magic Bytes: ${analysis.magicBytesHex}\n")
        if (analysis.sha256 != null) {
            sb.append("SHA-256: ${analysis.sha256}\n")
        }
        sb.append("\n--- METADATA TAGS ---\n")
        for (item in analysis.items) {
            val tagInfo = if (item.isSensitive) " [SENSITIVE]" else ""
            sb.append("${item.displayName} (${item.rawKey}): ${item.value}$tagInfo\n")
        }
        sb.append("=== END OF REPORT ===\n")
        return sb.toString()
    }

    fun clearWorkingCache() {
        viewModelScope.launch {
            fileRepo.clearWorkingCache()
            _uiState.update { it.copy(statusMessage = "Working cache cleared.") }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefsRepo.setThemeMode(mode)
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }
}
