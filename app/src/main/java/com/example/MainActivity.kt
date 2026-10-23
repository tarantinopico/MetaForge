package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.metaforge.ui.screens.DiffPreviewScreen
import com.example.metaforge.ui.screens.HomeScreen
import com.example.metaforge.ui.screens.InspectorScreen
import com.example.metaforge.ui.screens.ResultScreen
import com.example.metaforge.ui.screens.SanitizeProfileScreen
import com.example.metaforge.ui.screens.SettingsScreen
import com.example.metaforge.viewmodel.AppScreen
import com.example.metaforge.viewmodel.MainViewModel
import com.example.ui.theme.MetaForgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }

            // Document Pickers
            val openDocumentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let { viewModel.analyzeFile(it) }
            }

            val createDocumentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument(
                    uiState.currentAnalysis?.detectedFormat?.mimeType ?: "application/octet-stream"
                )
            ) { uri ->
                uri?.let { destUri ->
                    viewModel.exportToDestination(destUri) {
                        Toast.makeText(this, "Vyčištěný soubor byl úspěšně uložen!", Toast.LENGTH_LONG).show()
                    }
                }
            }

            // Error / Status Message Listener
            LaunchedEffect(uiState.errorMessage) {
                uiState.errorMessage?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearMessages()
                }
            }

            LaunchedEffect(uiState.statusMessage) {
                uiState.statusMessage?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearMessages()
                }
            }

            MetaForgeTheme(themeMode = themeMode) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AnimatedContent(
                            targetState = uiState.currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                AppScreen.HOME -> {
                                    HomeScreen(
                                        isAnalyzing = uiState.isAnalyzing,
                                        onSelectFile = {
                                            openDocumentLauncher.launch(arrayOf("*/*"))
                                        },
                                        onOpenSettings = {
                                            viewModel.navigateTo(AppScreen.SETTINGS)
                                        }
                                    )
                                }

                                AppScreen.INSPECTOR -> {
                                    val analysis = uiState.currentAnalysis
                                    if (analysis != null) {
                                        InspectorScreen(
                                            analysis = analysis,
                                            searchQuery = uiState.searchQuery,
                                            selectedCategory = uiState.selectedCategory,
                                            showRawTags = uiState.showRawTags,
                                            isComputingSha256 = uiState.isComputingSha256,
                                            onSearchChange = viewModel::setSearchQuery,
                                            onSelectCategory = viewModel::selectCategory,
                                            onToggleRawTags = viewModel::toggleRawTags,
                                            onComputeSha256 = viewModel::computeSha256,
                                            onExportJson = {
                                                shareText(viewModel.exportMetadataAsJson(), "MetaForge_report.json")
                                            },
                                            onExportText = {
                                                shareText(viewModel.exportMetadataAsText(), "MetaForge_report.txt")
                                            },
                                            onProceedToSanitize = {
                                                viewModel.navigateTo(AppScreen.SANITIZE_PROFILES)
                                            },
                                            onBack = {
                                                viewModel.navigateTo(AppScreen.HOME)
                                            }
                                        )
                                    } else {
                                        viewModel.navigateTo(AppScreen.HOME)
                                    }
                                }

                                AppScreen.SANITIZE_PROFILES -> {
                                    val analysis = uiState.currentAnalysis
                                    if (analysis != null) {
                                        SanitizeProfileScreen(
                                            analysis = analysis,
                                            selectedProfile = uiState.selectedProfile,
                                            selectedTagsForRemoval = uiState.selectedTagsForRemoval,
                                            onSelectProfile = viewModel::selectProfile,
                                            onToggleTag = viewModel::toggleTagRemoval,
                                            onProceedToDiff = {
                                                viewModel.navigateTo(AppScreen.DIFF_PREVIEW)
                                            },
                                            onBack = {
                                                viewModel.navigateTo(AppScreen.INSPECTOR)
                                            }
                                        )
                                    } else {
                                        viewModel.navigateTo(AppScreen.HOME)
                                    }
                                }

                                AppScreen.DIFF_PREVIEW -> {
                                    val analysis = uiState.currentAnalysis
                                    if (analysis != null) {
                                        DiffPreviewScreen(
                                            analysis = analysis,
                                            tagsForRemoval = uiState.selectedTagsForRemoval,
                                            isProcessing = uiState.isProcessingSanitization,
                                            onConfirmAndSanitize = viewModel::executeSanitization,
                                            onBack = {
                                                viewModel.navigateTo(AppScreen.SANITIZE_PROFILES)
                                            }
                                        )
                                    } else {
                                        viewModel.navigateTo(AppScreen.HOME)
                                    }
                                }

                                AppScreen.RESULT -> {
                                    val verification = uiState.verificationResult
                                    if (verification != null) {
                                        ResultScreen(
                                            verificationResult = verification,
                                            onSaveAs = {
                                                val origName = uiState.currentAnalysis?.fileName ?: "cleaned_file"
                                                val ext = uiState.currentAnalysis?.detectedFormat?.extension ?: "bin"
                                                val defaultName = if (origName.contains(".")) {
                                                    val dot = origName.lastIndexOf('.')
                                                    "${origName.substring(0, dot)}_cleaned.$ext"
                                                } else {
                                                    "${origName}_cleaned.$ext"
                                                }
                                                createDocumentLauncher.launch(defaultName)
                                            },
                                            onShare = {
                                                val shareUri = viewModel.getShareUri()
                                                if (shareUri != null) {
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = uiState.currentAnalysis?.detectedFormat?.mimeType ?: "*/*"
                                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    startActivity(Intent.createChooser(shareIntent, "Sdílet vyčištěný soubor"))
                                                } else {
                                                    Toast.makeText(this@MainActivity, "Chyba při přípravě souboru ke sdílení", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onDone = {
                                                viewModel.navigateTo(AppScreen.HOME)
                                            }
                                        )
                                    } else {
                                        viewModel.navigateTo(AppScreen.HOME)
                                    }
                                }

                                AppScreen.SETTINGS -> {
                                    SettingsScreen(
                                        currentTheme = themeMode,
                                        onSelectTheme = viewModel::setThemeMode,
                                        onClearCache = viewModel::clearWorkingCache,
                                        onBack = {
                                            viewModel.navigateTo(AppScreen.HOME)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shareText(content: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(intent, "Exportovat metadata"))
    }
}
