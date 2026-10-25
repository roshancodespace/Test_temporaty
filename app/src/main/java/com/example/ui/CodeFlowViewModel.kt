package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothHidKeyboardManager
import com.example.bluetooth.HumanTypingSimulator
import com.example.data.model.FileNode
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.data.model.PlaybackSpeed
import com.example.data.model.Project
import com.example.data.storage.GitHubRepoDownloader
import com.example.data.storage.SafeZipExtractor
import com.example.data.storage.SampleProjectProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class CodeFlowViewModel(application: Application) : AndroidViewModel(application) {

    // Bluetooth HID Keyboard & Human Typing Simulator
    val bluetoothHidManager = BluetoothHidKeyboardManager(application, viewModelScope)
    val hidState: StateFlow<BluetoothHidKeyboardManager.HidState> = bluetoothHidManager.hidState
    val humanTypingSimulator = HumanTypingSimulator(bluetoothHidManager, viewModelScope)
    val typingState: StateFlow<HumanTypingSimulator.TypingState> = humanTypingSimulator.typingState

    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()

    private val _fileTree = MutableStateFlow<List<FileNode>>(emptyList())
    val fileTree: StateFlow<List<FileNode>> = _fileTree.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    // Real-time Event Logging Console
    private val _eventLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val eventLogs: StateFlow<List<LogEntry>> = _eventLogs.asStateFlow()

    // Configurable Typing Cadence
    val typingCps = MutableStateFlow(32)
    val linePauseMs = MutableStateFlow(180L)
    val humanJitter = MutableStateFlow(true)
    val startCountdownSec = MutableStateFlow(3)

    private val safeZipExtractor = SafeZipExtractor()
    private val gitHubRepoDownloader = GitHubRepoDownloader(application)

    init {
        addLog(LogEntry(level = LogLevel.INFO, message = "CodeFlow Studio Remote Controller initialized."))
        loadDefaultSampleProject()
        observeBluetoothHostEvents()
    }

    private fun observeBluetoothHostEvents() {
        viewModelScope.launch {
            hidState.collect { state ->
                when (state) {
                    is BluetoothHidKeyboardManager.HidState.Connected -> {
                        addLog(LogEntry(
                            level = LogLevel.SUCCESS,
                            message = "PC Connected: ${state.deviceName} (${state.deviceAddress}). Ready to type into VS Code."
                        ))
                    }
                    is BluetoothHidKeyboardManager.HidState.WaitingForHost -> {
                        addLog(LogEntry(
                            level = LogLevel.INFO,
                            message = "Bluetooth HID active. Waiting for PC connection ('CodeFlow Studio Keyboard')."
                        ))
                    }
                    is BluetoothHidKeyboardManager.HidState.BluetoothDisabled -> {
                        addLog(LogEntry(
                            level = LogLevel.WARN,
                            message = "Bluetooth is disabled on Android device. Please turn on Bluetooth."
                        ))
                    }
                    is BluetoothHidKeyboardManager.HidState.SimulationMode -> {
                        addLog(LogEntry(
                            level = LogLevel.INFO,
                            message = "Simulation / Preview Mode active: ${state.reason}"
                        ))
                    }
                    is BluetoothHidKeyboardManager.HidState.Unsupported -> {
                        addLog(LogEntry(
                            level = LogLevel.WARN,
                            message = "HID Profile notice: ${state.reason}"
                        ))
                    }
                    else -> {}
                }
            }
        }
    }

    fun addLog(entry: LogEntry) {
        _eventLogs.update { current ->
            // Keep recent 200 logs
            (listOf(entry) + current).take(200)
        }
    }

    fun clearLogs() {
        _eventLogs.value = emptyList()
        addLog(LogEntry(level = LogLevel.INFO, message = "Console logs cleared."))
    }

    private fun loadDefaultSampleProject() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val sample = SampleProjectProvider.getSampleProject(app.filesDir)
            val sampleFiles = SampleProjectProvider.getSampleFileNodes()

            _currentProject.value = sample
            _fileTree.value = sampleFiles

            addLog(LogEntry(
                level = LogLevel.INFO,
                message = "Loaded bundled project: '${sample.name}' (${sampleFiles.size} files ready)."
            ))
        }
    }

    fun toggleFileInclusion(fileId: String) {
        fun toggleRecursive(nodes: List<FileNode>): List<FileNode> {
            return nodes.map { node ->
                if (node.id == fileId) {
                    val updated = !node.isIncluded
                    addLog(LogEntry(
                        level = LogLevel.INFO,
                        message = "File '${node.name}' marked as ${if (updated) "INCLUDED" else "EXCLUDED"}."
                    ))
                    node.copy(isIncluded = updated)
                } else if (node.isDirectory) {
                    node.copy(children = toggleRecursive(node.children))
                } else {
                    node
                }
            }
        }
        _fileTree.update { toggleRecursive(it) }
    }

    fun toggleAllFiles(includeAll: Boolean) {
        fun updateRecursive(nodes: List<FileNode>): List<FileNode> {
            return nodes.map { node ->
                if (node.isDirectory) {
                    node.copy(isIncluded = includeAll, children = updateRecursive(node.children))
                } else {
                    node.copy(isIncluded = includeAll)
                }
            }
        }
        _fileTree.update { updateRecursive(it) }
        addLog(LogEntry(
            level = LogLevel.INFO,
            message = if (includeAll) "Marked all files as INCLUDED." else "Deselected all files."
        ))
    }

    fun updateTypingCadence(cps: Int, linePause: Long, jitter: Boolean, countdown: Int) {
        typingCps.value = cps.coerceIn(5, 120)
        linePauseMs.value = linePause.coerceIn(0L, 1000L)
        humanJitter.value = jitter
        startCountdownSec.value = countdown.coerceIn(1, 10)

        humanTypingSimulator.charsPerSecond = typingCps.value
        humanTypingSimulator.linePauseMs = linePauseMs.value
        humanTypingSimulator.humanJitter = humanJitter.value

        addLog(LogEntry(
            level = LogLevel.INFO,
            message = "Typing settings updated: ${typingCps.value} cps, ${linePauseMs.value}ms line pause, countdown ${startCountdownSec.value}s."
        ))
    }

    // Bluetooth Output typing into PC VS Code
    fun startBluetoothTypingIntoPc() {
        val project = _currentProject.value ?: return
        val rootDir = File(project.rootPath)

        fun collectIncludedFiles(nodes: List<FileNode>, list: MutableList<HumanTypingSimulator.FilePayload>) {
            for (node in nodes) {
                if (!node.isDirectory && node.isIncluded) {
                    val fileOnDisk = File(rootDir, node.relativePath)
                    val content = if (fileOnDisk.exists()) fileOnDisk.readText() else ""
                    if (content.isNotBlank()) {
                        list.add(HumanTypingSimulator.FilePayload(node.relativePath, content))
                    }
                } else if (node.isDirectory) {
                    collectIncludedFiles(node.children, list)
                }
            }
        }

        val payloads = mutableListOf<HumanTypingSimulator.FilePayload>()
        collectIncludedFiles(_fileTree.value, payloads)

        if (payloads.isEmpty()) {
            _importStatusMessage.value = "No files selected to type. Check included files."
            addLog(LogEntry(level = LogLevel.WARN, message = "Start failed: No files are marked as included."))
            return
        }

        humanTypingSimulator.charsPerSecond = typingCps.value
        humanTypingSimulator.linePauseMs = linePauseMs.value
        humanTypingSimulator.humanJitter = humanJitter.value

        humanTypingSimulator.startTypingSequence(
            files = payloads,
            startDelayMs = (startCountdownSec.value * 1000).toLong(),
            onLog = { addLog(it) }
        )
    }

    fun pauseBluetoothTyping() {
        humanTypingSimulator.pause { addLog(it) }
    }

    fun resumeBluetoothTyping() {
        humanTypingSimulator.resume { addLog(it) }
    }

    fun stopBluetoothTyping() {
        humanTypingSimulator.stop { addLog(it) }
    }

    fun testSampleKeystroke() {
        viewModelScope.launch {
            val testLine = "// CodeFlow Studio: Bluetooth HID Keyboard verified!\n"
            addLog(LogEntry(level = LogLevel.INFO, message = "Sending quick test keystroke: '$testLine'"))
            for (ch in testLine) {
                bluetoothHidManager.sendChar(ch)
            }
            addLog(LogEntry(level = LogLevel.SUCCESS, message = "Test keystroke transmitted."))
        }
    }

    // ZIP Import via Storage Access Framework
    fun importProjectZip(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            _importStatusMessage.value = "Reading ZIP archive..."
            addLog(LogEntry(level = LogLevel.INFO, message = "Importing local ZIP archive from SAF picker..."))
            try {
                val context = getApplication<Application>()
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open stream for selected file.")

                val projectsDir = File(context.filesDir, "projects")
                val timestamp = System.currentTimeMillis()
                val targetDir = File(projectsDir, "imported_$timestamp")

                val result = safeZipExtractor.extract(inputStream, targetDir) { count ->
                    _importStatusMessage.value = "Extracted $count files safely..."
                }

                if (!result.success) {
                    _importStatusMessage.value = "Import failed: ${result.errorMessage}"
                    addLog(LogEntry(level = LogLevel.ERROR, message = "ZIP extraction error: ${result.errorMessage}"))
                    _isImporting.value = false
                    return@launch
                }

                val tree = safeZipExtractor.buildFileTree(result.rootDir)
                val projectName = uri.lastPathSegment?.substringAfterLast('/')?.removeSuffix(".zip")
                    ?: "Imported Project"

                val project = Project(
                    id = "proj_$timestamp",
                    name = projectName,
                    rootPath = result.rootDir.absolutePath,
                    isBundledSample = false,
                    totalFiles = result.totalFiles,
                    includedFilesCount = result.totalFiles
                )

                _currentProject.value = project
                _fileTree.value = tree

                _importStatusMessage.value = "Imported ${result.totalFiles} files successfully!"
                addLog(LogEntry(
                    level = LogLevel.SUCCESS,
                    message = "ZIP imported successfully: '$projectName' (${result.totalFiles} files, ${result.totalBytes / 1024} KB)."
                ))
            } catch (e: Exception) {
                _importStatusMessage.value = "Error: ${e.localizedMessage ?: "Unknown error"}"
                addLog(LogEntry(level = LogLevel.ERROR, message = "Import exception: ${e.localizedMessage}"))
            } finally {
                _isImporting.value = false
            }
        }
    }

    // GitHub Repo Import
    fun importGitHubRepo(repoUrl: String, branch: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            _importStatusMessage.value = "Fetching repository from GitHub..."
            addLog(LogEntry(level = LogLevel.INFO, message = "Connecting to GitHub for repository '$repoUrl' ($branch)..."))
            try {
                val result = gitHubRepoDownloader.downloadRepo(repoUrl, branch) { status ->
                    _importStatusMessage.value = status
                }

                if (!result.success || result.project == null) {
                    _importStatusMessage.value = "GitHub import error: ${result.errorMessage}"
                    addLog(LogEntry(level = LogLevel.ERROR, message = "GitHub download failed: ${result.errorMessage}"))
                    return@launch
                }

                _currentProject.value = result.project
                _fileTree.value = result.fileTree

                _importStatusMessage.value = "Successfully imported ${result.project.name}!"
                addLog(LogEntry(
                    level = LogLevel.SUCCESS,
                    message = "GitHub repository unpacked: '${result.project.name}' (${result.project.totalFiles} files ready for PC typing)."
                ))
            } catch (e: Exception) {
                _importStatusMessage.value = "GitHub import failed: ${e.localizedMessage}"
                addLog(LogEntry(level = LogLevel.ERROR, message = "GitHub fetch error: ${e.localizedMessage}"))
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun dismissImportStatus() {
        _importStatusMessage.value = null
    }

    fun resetToSampleProject() {
        humanTypingSimulator.stop { addLog(it) }
        loadDefaultSampleProject()
    }
}
