package com.example.data.storage

import com.example.data.model.FileNode
import com.example.data.model.PlaybackAction
import com.example.data.model.PlaybackSession
import com.example.data.model.Project
import java.io.File

object SampleProjectProvider {

    const val SAMPLE_PROJECT_ID = "sample_android_kotlin"
    const val SAMPLE_PROJECT_NAME = "Android Kotlin Greeting App"

    val SAMPLE_VIEW_MODEL_CONTENT = """package com.example.demo

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GreetingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GreetingUiState())
    val uiState = _uiState.asStateFlow()

    fun onNameChanged(newName: String) {
        _uiState.value = _uiState.value.copy(
            name = newName,
            greeting = if (newName.isNotBlank()) "Hello, ${'$'}newName!" else "Welcome to CodeFlow!"
        )
    }

    fun onToggleFavorite() {
        _uiState.value = _uiState.value.copy(
            isFavorite = !_uiState.value.isFavorite
        )
    }
}

data class GreetingUiState(
    val name: String = "",
    val greeting: String = "Welcome to CodeFlow!",
    val isFavorite: Boolean = false
)
""".trimIndent()

    val SAMPLE_SCREEN_CONTENT = """package com.example.demo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GreetingScreen(
    viewModel: GreetingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = uiState.greeting,
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChanged,
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = viewModel::onToggleFavorite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uiState.isFavorite) "Favorited ★" else "Add to Favorites ☆")
        }
    }
}
""".trimIndent()

    val SAMPLE_README_CONTENT = """# Android Kotlin Greeting App

A reactive Jetpack Compose application built with Kotlin, Coroutines StateFlow, and Material 3.

## Architecture
- **GreetingViewModel**: Manages UI state with unidirectional data flow.
- **GreetingScreen**: Declarative UI layout with interactive state binding.
""".trimIndent()

    fun getSampleProject(baseDir: File): Project {
        val projectDir = File(baseDir, SAMPLE_PROJECT_ID)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
            File(projectDir, "GreetingViewModel.kt").writeText(SAMPLE_VIEW_MODEL_CONTENT)
            File(projectDir, "GreetingScreen.kt").writeText(SAMPLE_SCREEN_CONTENT)
            File(projectDir, "README.md").writeText(SAMPLE_README_CONTENT)
        }

        return Project(
            id = SAMPLE_PROJECT_ID,
            name = SAMPLE_PROJECT_NAME,
            rootPath = projectDir.absolutePath,
            isBundledSample = true,
            totalFiles = 3,
            includedFilesCount = 3
        )
    }

    fun getSampleFileNodes(): List<FileNode> {
        return listOf(
            FileNode(
                id = "GreetingViewModel.kt",
                name = "GreetingViewModel.kt",
                relativePath = "GreetingViewModel.kt",
                isDirectory = false,
                isIncluded = true,
                extension = "kt",
                sizeBytes = SAMPLE_VIEW_MODEL_CONTENT.length.toLong()
            ),
            FileNode(
                id = "GreetingScreen.kt",
                name = "GreetingScreen.kt",
                relativePath = "GreetingScreen.kt",
                isDirectory = false,
                isIncluded = true,
                extension = "kt",
                sizeBytes = SAMPLE_SCREEN_CONTENT.length.toLong()
            ),
            FileNode(
                id = "README.md",
                name = "README.md",
                relativePath = "README.md",
                isDirectory = false,
                isIncluded = true,
                extension = "md",
                sizeBytes = SAMPLE_README_CONTENT.length.toLong()
            )
        )
    }

    /**
     * Builds a rich, realistic deterministic session for screen recording
     */
    fun createDefaultSession(): PlaybackSession {
        val actions = mutableListOf<PlaybackAction>()

        // Step 1: Open GreetingViewModel.kt
        actions.add(PlaybackAction.OpenFile("GreetingViewModel.kt"))
        actions.add(PlaybackAction.Pause(400L))

        // Step 2: Incrementally type the ViewModel code
        actions.add(PlaybackAction.InsertText(SAMPLE_VIEW_MODEL_CONTENT))
        actions.add(PlaybackAction.Pause(800L))

        // Step 3: Switch to GreetingScreen.kt
        actions.add(PlaybackAction.SwitchTab("GreetingScreen.kt"))
        actions.add(PlaybackAction.Pause(500L))

        // Step 4: Type the Composable UI code
        actions.add(PlaybackAction.InsertText(SAMPLE_SCREEN_CONTENT))
        actions.add(PlaybackAction.Pause(600L))

        // Step 5: Switch back to GreetingViewModel.kt to inspect
        actions.add(PlaybackAction.SwitchTab("GreetingViewModel.kt"))
        actions.add(PlaybackAction.ScrollToLine(10))

        return PlaybackSession(
            id = "session_sample_1",
            name = "Compose UI & ViewModel Session",
            actions = actions,
            defaultCps = 32,
            lineEndPauseMs = 150L,
            randomJitter = true,
            randomSeed = 42L
        )
    }
}
