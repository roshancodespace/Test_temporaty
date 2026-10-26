package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothHidKeyboardManager
import com.example.bluetooth.HumanTypingSimulator
import com.example.bluetooth.KeystrokeForegroundService
import com.example.data.model.FileNode
import com.example.data.model.ImportProgress
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.data.model.Project
import com.example.data.storage.GitHubRepoDownloader
import com.example.data.storage.ProjectPreferences
import com.example.data.storage.SafeZipExtractor
import com.example.data.storage.SampleProjectProvider
import com.example.data.storage.TypingCheckpoint
import com.example.ui.theme.PalettePreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class CodeFlowViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        @Volatile
        private var sharedHidManager: BluetoothHidKeyboardManager? = null
        @Volatile
        private var sharedTypingSimulator: HumanTypingSimulator? = null

        fun getHidManager(app: Application): BluetoothHidKeyboardManager {
            return sharedHidManager ?: synchronized(this) {
                sharedHidManager ?: BluetoothHidKeyboardManager(app.applicationContext, processScope).also {
                    sharedHidManager = it
                }
            }
        }

        fun getTypingSimulator(app: Application): HumanTypingSimulator {
            return sharedTypingSimulator ?: synchronized(this) {
                sharedTypingSimulator ?: HumanTypingSimulator(getHidManager(app), processScope).also {
                    sharedTypingSimulator = it
                }
            }
        }
    }

    private val preferences = ProjectPreferences(application)

    // Bluetooth HID Keyboard & Human Typing Simulator (Process-scoped so Recents/backgrounding never drops state)
    val bluetoothHidManager = getHidManager(application)
    val hidState: StateFlow<BluetoothHidKeyboardManager.HidState> = bluetoothHidManager.hidState
    val humanTypingSimulator = getTypingSimulator(application)
    val typingState: StateFlow<HumanTypingSimulator.TypingState> = humanTypingSimulator.typingState

    // VS Code Workspace Orchestrator & Target OS Automation Engine
    val targetWorkspaceOrchestrator = com.example.automation.TargetWorkspaceOrchestrator(
        keyboardManager = bluetoothHidManager,
        typingSimulator = humanTypingSimulator,
        scope = processScope
    )
    val orchestratorState = targetWorkspaceOrchestrator.orchestratorState
    val pluginRegistry = targetWorkspaceOrchestrator.pluginRegistry

    private val _targetOs = MutableStateFlow(com.example.automation.TargetOS.WINDOWS_POWERSHELL)
    val targetOs: StateFlow<com.example.automation.TargetOS> = _targetOs.asStateFlow()

    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()

    private val _fileTree = MutableStateFlow<List<FileNode>>(emptyList())
    val fileTree: StateFlow<List<FileNode>> = _fileTree.asStateFlow()

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    // Real-time Event Logging Console
    private val _eventLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val eventLogs: StateFlow<List<LogEntry>> = _eventLogs.asStateFlow()

    // Cadence Settings with persistent memory
    private val cadenceData = preferences.getCadence()
    val typingCps = MutableStateFlow(cadenceData.cps)
    val linePauseMs = MutableStateFlow(cadenceData.linePauseMs)
    val humanJitter = MutableStateFlow(cadenceData.jitter)
    val startCountdownSec = MutableStateFlow(cadenceData.countdownSec)

    // Theme Settings with persistent memory
    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(preferences.getThemeMode().uppercase()) }.getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _palettePreset = MutableStateFlow(
        runCatching { PalettePreset.valueOf(preferences.getThemePalette().uppercase()) }.getOrDefault(PalettePreset.VS_CODE)
    )
    val palettePreset: StateFlow<PalettePreset> = _palettePreset.asStateFlow()

    // Persistent Checkpoint Memory
    private val _checkpoint = MutableStateFlow<TypingCheckpoint?>(preferences.getCheckpoint())
    val checkpoint: StateFlow<TypingCheckpoint?> = _checkpoint.asStateFlow()

    // Bluetooth Device Name Spoofing
    val currentBtName = MutableStateFlow(bluetoothHidManager.getDeviceName())
    val isBtNameSpoofed = MutableStateFlow(bluetoothHidManager.isNameSpoofed())

    // Screen Awake / Power Policy
    val keepScreenAwake = MutableStateFlow(preferences.getKeepScreenOn())

    private val safeZipExtractor = SafeZipExtractor()
    private val gitHubRepoDownloader = GitHubRepoDownloader(application)

    init {
        humanTypingSimulator.charsPerSecond = typingCps.value
        humanTypingSimulator.linePauseMs = linePauseMs.value
        humanTypingSimulator.humanJitter = humanJitter.value

        addLog(LogEntry(level = LogLevel.INFO, message = "CodeFlow Studio Remote Controller initialized."))
        restoreOrLoadDefaultProject()
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
            (listOf(entry) + current).take(200)
        }
    }

    fun clearLogs() {
        _eventLogs.value = emptyList()
        addLog(LogEntry(level = LogLevel.INFO, message = "Console logs cleared."))
    }

    private fun restoreOrLoadDefaultProject() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val saved = preferences.loadSavedProject()

            if (saved != null && File(saved.rootPath).exists()) {
                val tree = safeZipExtractor.buildFileTree(File(saved.rootPath))
                // Apply saved inclusion states
                fun applyInclusions(nodes: List<FileNode>): List<FileNode> {
                    return nodes.map { node ->
                        if (node.isDirectory) {
                            val newChildren = applyInclusions(node.children)
                            fun allDescFilesIncluded(n: FileNode): Boolean {
                                if (!n.isDirectory) return n.isIncluded
                                if (n.children.isEmpty()) return true
                                return n.children.all { allDescFilesIncluded(it) }
                            }
                            val allIncluded = newChildren.isNotEmpty() && newChildren.all { allDescFilesIncluded(it) }
                            node.copy(
                                isIncluded = allIncluded,
                                children = newChildren
                            )
                        } else {
                            val isInc = saved.includedFilePaths.contains(node.relativePath)
                            node.copy(isIncluded = isInc)
                        }
                    }
                }
                val restoredTree = applyInclusions(tree)
                val project = Project(
                    id = saved.id,
                    name = saved.name,
                    rootPath = saved.rootPath,
                    isBundledSample = saved.isBundledSample,
                    totalFiles = restoredTree.size,
                    includedFilesCount = saved.includedFilePaths.size
                )
                _currentProject.value = project
                _fileTree.value = restoredTree
                addLog(LogEntry(
                    level = LogLevel.INFO,
                    message = "Restored saved project from persistent memory: '${saved.name}' (${saved.includedFilePaths.size} files marked)."
                ))
            } else {
                loadDefaultSampleProject()
            }
        }
    }

    private fun loadDefaultSampleProject() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val sample = SampleProjectProvider.getSampleProject(app.filesDir)
            val sampleFiles = SampleProjectProvider.getSampleFileNodes()

            _currentProject.value = sample
            _fileTree.value = sampleFiles

            persistProjectState(sample, sampleFiles)

            addLog(LogEntry(
                level = LogLevel.INFO,
                message = "Loaded bundled project: '${sample.name}' (${sampleFiles.size} files ready)."
            ))
        }
    }

    private fun persistProjectState(project: Project, tree: List<FileNode>) {
        val includedPaths = mutableSetOf<String>()
        fun collect(nodes: List<FileNode>) {
            for (node in nodes) {
                if (!node.isDirectory && node.isIncluded) {
                    includedPaths.add(node.relativePath)
                }
                if (node.isDirectory) collect(node.children)
            }
        }
        collect(tree)
        preferences.saveProject(
            id = project.id,
            name = project.name,
            rootPath = project.rootPath,
            isBundledSample = project.isBundledSample,
            includedFiles = includedPaths
        )
    }

    /**
     * Toggles inclusion of a file or entire directory tree.
     * When a folder is toggled, all descendant files/subfolders are updated to match.
     * When a child file is toggled, parent folders recompute their inclusion state.
     */
    fun toggleFileInclusion(fileId: String) {
        // Collect all descendant non-directory files
        fun collectFiles(node: FileNode, outList: MutableList<FileNode>) {
            if (!node.isDirectory) {
                outList.add(node)
            } else {
                node.children.forEach { collectFiles(it, outList) }
            }
        }

        // Recursively set inclusion state on target node and all its descendants
        fun setAllDescendants(node: FileNode, included: Boolean): FileNode {
            return node.copy(
                isIncluded = included,
                children = node.children.map { setAllDescendants(it, included) }
            )
        }

        // Traverse tree, locate target fileId, and apply toggle
        fun updateTree(nodes: List<FileNode>): List<FileNode> {
            return nodes.map { node ->
                if (node.id == fileId) {
                    if (node.isDirectory) {
                        val files = mutableListOf<FileNode>()
                        collectFiles(node, files)
                        val allCurrentlyIncluded = files.isNotEmpty() && files.all { it.isIncluded }
                        // If all children are selected, unselect all. Otherwise select all.
                        val newIncluded = !allCurrentlyIncluded
                        setAllDescendants(node, newIncluded)
                    } else {
                        node.copy(isIncluded = !node.isIncluded)
                    }
                } else if (node.isDirectory) {
                    node.copy(children = updateTree(node.children))
                } else {
                    node
                }
            }
        }

        // Recompute parent folder isIncluded state so it accurately reflects all child files
        fun recomputeFolderStates(node: FileNode): FileNode {
            if (!node.isDirectory) return node
            val updatedChildren = node.children.map { recomputeFolderStates(it) }
            val files = mutableListOf<FileNode>()
            node.copy(children = updatedChildren).let { collectFiles(it, files) }
            val allFilesIncluded = files.isNotEmpty() && files.all { it.isIncluded }
            return node.copy(
                isIncluded = allFilesIncluded,
                children = updatedChildren
            )
        }

        val updatedTree = updateTree(_fileTree.value).map { recomputeFolderStates(it) }
        _fileTree.value = updatedTree
        _currentProject.value?.let { persistProjectState(it, updatedTree) }
    }

    /**
     * Selects all or deselects all files and folders recursively.
     */
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
        val updatedTree = updateRecursive(_fileTree.value)
        _fileTree.value = updatedTree
        _currentProject.value?.let { persistProjectState(it, updatedTree) }
        addLog(LogEntry(
            level = LogLevel.INFO,
            message = if (includeAll) "Marked all files as INCLUDED." else "Deselected all files."
        ))
    }

    /**
     * Quick filter: Selects only source code & configuration files (.kt, .java, .xml, .gradle, .json, etc.),
     * ignoring build artifacts, binary images, and lockfiles.
     */
    fun selectCodeFilesOnly() {
        val codeExtensions = setOf(
            "kt", "java", "xml", "json", "gradle", "properties", "toml", "yaml", "yml",
            "md", "txt", "py", "js", "ts", "html", "css", "c", "cpp", "h", "sql", "sh"
        )

        fun updateNode(node: FileNode): FileNode {
            return if (node.isDirectory) {
                val newChildren = node.children.map { updateNode(it) }
                val allIncluded = newChildren.isNotEmpty() && newChildren.all { it.isIncluded }
                node.copy(isIncluded = allIncluded, children = newChildren)
            } else {
                val isCode = codeExtensions.contains(node.extension.lowercase())
                node.copy(isIncluded = isCode)
            }
        }

        val updatedTree = _fileTree.value.map { updateNode(it) }
        _fileTree.value = updatedTree
        _currentProject.value?.let { persistProjectState(it, updatedTree) }
        addLog(LogEntry(
            level = LogLevel.INFO,
            message = "Applied filter: Code & config files selected."
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

        preferences.saveCadence(typingCps.value, linePauseMs.value, humanJitter.value, startCountdownSec.value)

        addLog(LogEntry(
            level = LogLevel.INFO,
            message = "Cadence updated: ${typingCps.value} cps, ${linePauseMs.value}ms pause, countdown ${startCountdownSec.value}s."
        ))
    }

    fun updateTheme(mode: ThemeMode, palette: PalettePreset) {
        _themeMode.value = mode
        _palettePreset.value = palette
        preferences.saveTheme(mode.name.lowercase(), palette.name.lowercase())
        addLog(LogEntry(
            level = LogLevel.INFO,
            message = "Theme changed: ${mode.label} (${palette.label})"
        ))
    }

    fun clearCheckpoint() {
        preferences.clearCheckpoint()
        _checkpoint.value = null
        humanTypingSimulator.clearActiveCheckpoint()
        addLog(LogEntry(level = LogLevel.INFO, message = "Saved checkpoint cleared."))
    }

    private fun buildIncludedPayloads(): List<HumanTypingSimulator.FilePayload> {
        val project = _currentProject.value ?: return emptyList()
        val rootDir = File(project.rootPath)
        val payloads = mutableListOf<HumanTypingSimulator.FilePayload>()

        fun collect(nodes: List<FileNode>) {
            for (node in nodes) {
                if (!node.isDirectory && node.isIncluded) {
                    val fileOnDisk = File(rootDir, node.relativePath)
                    val content = if (fileOnDisk.exists()) fileOnDisk.readText() else ""
                    if (content.isNotBlank()) {
                        payloads.add(HumanTypingSimulator.FilePayload(node.relativePath, content))
                    }
                } else if (node.isDirectory) {
                    collect(node.children)
                }
            }
        }
        collect(_fileTree.value)
        return payloads
    }

    fun startBluetoothTypingIntoPc() {
        val payloads = buildIncludedPayloads()
        if (payloads.isEmpty()) {
            _importStatusMessage.value = "No files selected to type. Check included files."
            addLog(LogEntry(level = LogLevel.WARN, message = "Start failed: No files are marked as included."))
            return
        }

        clearCheckpoint()

        // Keep CPU awake and Bluetooth streaming alive even if screen turns off
        KeystrokeForegroundService.start(
            getApplication(),
            payloads.firstOrNull()?.relativePath ?: "project files"
        )

        humanTypingSimulator.startTypingSequence(
            files = payloads,
            startDelayMs = (startCountdownSec.value * 1000).toLong(),
            resumeFileIndex = 0,
            resumeCharOffset = 0,
            resumeCharsTyped = 0,
            onLog = { addLog(it) },
            onCheckpointUpdated = { saveCheckpointState(it) }
        )
    }

    fun resumeFromCheckpoint() {
        val cp = _checkpoint.value
        val payloads = buildIncludedPayloads()

        if (cp == null || payloads.isEmpty()) {
            startBluetoothTypingIntoPc()
            return
        }

        // Find file index matching checkpoint
        var targetFileIndex = cp.fileIndex
        if (targetFileIndex >= payloads.size || payloads[targetFileIndex].relativePath != cp.fileRelativePath) {
            val foundIdx = payloads.indexOfFirst { it.relativePath == cp.fileRelativePath }
            if (foundIdx >= 0) targetFileIndex = foundIdx
        }

        // Keep CPU awake and Bluetooth streaming alive even if screen turns off
        KeystrokeForegroundService.start(
            getApplication(),
            payloads.getOrNull(targetFileIndex)?.relativePath ?: "project files"
        )

        humanTypingSimulator.startTypingSequence(
            files = payloads,
            startDelayMs = (startCountdownSec.value * 1000).toLong(),
            resumeFileIndex = targetFileIndex,
            resumeCharOffset = cp.charOffset,
            resumeCharsTyped = cp.charsTyped,
            onLog = { addLog(it) },
            onCheckpointUpdated = { saveCheckpointState(it) }
        )
    }

    private fun saveCheckpointState(cp: HumanTypingSimulator.ActiveCheckpoint) {
        val projectName = _currentProject.value?.name ?: "Current Project"
        val checkpoint = TypingCheckpoint(
            projectName = projectName,
            fileRelativePath = cp.relativePath,
            fileIndex = cp.fileIndex,
            charOffset = cp.charOffset,
            charsTyped = cp.charsTyped,
            totalChars = cp.totalChars,
            timestamp = System.currentTimeMillis()
        )
        preferences.saveCheckpoint(checkpoint)
        _checkpoint.value = checkpoint
    }

    fun pauseBluetoothTyping() {
        KeystrokeForegroundService.updateStatus(getApplication(), "Paused — Bluetooth HID Keyboard Ready")
        humanTypingSimulator.pause(
            onLog = { addLog(it) },
            onCheckpointSaved = { saveCheckpointState(it) }
        )
    }

    fun resumeBluetoothTyping() {
        val current = humanTypingSimulator.typingState.value
        val fileName = if (current is HumanTypingSimulator.TypingState.Paused) current.currentFile else "code"
        KeystrokeForegroundService.updateStatus(getApplication(), "Typing $fileName...")
        humanTypingSimulator.resume { addLog(it) }
    }

    fun stopBluetoothTyping() {
        KeystrokeForegroundService.updateStatus(getApplication(), "Bluetooth HID Keyboard Active & Ready")
        humanTypingSimulator.stop(
            onLog = { addLog(it) },
            onCheckpointSaved = { saveCheckpointState(it) }
        )
    }

    fun spoofBluetoothName(name: String) {
        val success = bluetoothHidManager.spoofDeviceName(name)
        currentBtName.value = bluetoothHidManager.getDeviceName()
        isBtNameSpoofed.value = bluetoothHidManager.isNameSpoofed()
        addLog(LogEntry(
            level = if (success) LogLevel.SUCCESS else LogLevel.WARN,
            message = if (success) "Bluetooth device name spoofed to '$name'. Your PC will now see this name!" else "Failed to spoof Bluetooth name."
        ))
    }

    fun restoreOriginalBluetoothName() {
        val success = bluetoothHidManager.restoreOriginalName()
        currentBtName.value = bluetoothHidManager.getDeviceName()
        isBtNameSpoofed.value = bluetoothHidManager.isNameSpoofed()
        addLog(LogEntry(
            level = LogLevel.INFO,
            message = "Restored original Bluetooth device name: '${currentBtName.value}'."
        ))
    }

    fun toggleKeepScreenAwake(keep: Boolean) {
        keepScreenAwake.value = keep
        preferences.setKeepScreenOn(keep)
        addLog(LogEntry(
            level = LogLevel.INFO,
            message = if (keep) "Screen wake lock enabled during typing." else "Screen wake lock disabled."
        ))
    }

    fun testSampleKeystroke() {
        processScope.launch {
            val isSim = bluetoothHidManager.hidState.value is BluetoothHidKeyboardManager.HidState.SimulationMode
            if (!isSim && bluetoothHidManager.ensureHidConnected() == null) {
                addLog(LogEntry(
                    level = LogLevel.ERROR,
                    message = "Test keystroke failed: No active Bluetooth HID connection. Tap the Bluetooth bar and tap 'Use PC' on your target device."
                ))
                return@launch
            }
            val testLine = "// CodeFlow Keyboard verified!\n"
            addLog(LogEntry(level = LogLevel.INFO, message = "Sending test keystrokes: ${testLine.trim()}"))
            var deliveredCount = 0
            for (ch in testLine) {
                if (bluetoothHidManager.sendChar(ch)) {
                    deliveredCount++
                }
            }
            if (deliveredCount > 0) {
                addLog(LogEntry(level = LogLevel.SUCCESS, message = "Test keystrokes delivered ($deliveredCount/${testLine.length} chars)."))
            } else {
                addLog(LogEntry(level = LogLevel.ERROR, message = "Failed to send keystrokes over Bluetooth HID."))
            }
        }
    }

    fun importProjectZip(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            _importProgress.value = ImportProgress(
                isActive = true,
                title = "Importing ZIP Archive",
                statusText = "Reading ZIP package..."
            )
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
                    _importProgress.value = ImportProgress(
                        isActive = true,
                        title = "Importing ZIP Archive",
                        statusText = "Safely unpacked $count file(s)...",
                        filesCount = count
                    )
                }

                if (!result.success) {
                    _importProgress.value = ImportProgress(
                        isActive = true,
                        title = "ZIP Import Failed",
                        isError = true,
                        errorMessage = result.errorMessage
                    )
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

                persistProjectState(project, tree)
                clearCheckpoint()

                _importProgress.value = ImportProgress(
                    isActive = true,
                    title = "ZIP Import Complete",
                    statusText = "Imported ${result.totalFiles} files into persistent memory.",
                    filesCount = result.totalFiles,
                    isComplete = true
                )
                addLog(LogEntry(
                    level = LogLevel.SUCCESS,
                    message = "ZIP imported: '$projectName' (${result.totalFiles} files, ${result.totalBytes / 1024} KB saved to memory)."
                ))
            } catch (e: Exception) {
                _importProgress.value = ImportProgress(
                    isActive = true,
                    title = "Import Error",
                    isError = true,
                    errorMessage = e.localizedMessage ?: "Unknown error"
                )
                addLog(LogEntry(level = LogLevel.ERROR, message = "Import exception: ${e.localizedMessage}"))
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun importGitHubRepo(repoUrl: String, branch: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            _importProgress.value = ImportProgress(
                isActive = true,
                title = "Importing GitHub Repository",
                statusText = "Connecting to GitHub..."
            )
            addLog(LogEntry(level = LogLevel.INFO, message = "Connecting to GitHub for repository '$repoUrl' ($branch)..."))
            try {
                val result = gitHubRepoDownloader.downloadRepo(repoUrl, branch) { status ->
                    _importProgress.value = _importProgress.value?.copy(statusText = status)
                        ?: ImportProgress(isActive = true, title = "Importing GitHub Repository", statusText = status)
                }

                if (!result.success || result.project == null) {
                    _importProgress.value = ImportProgress(
                        isActive = true,
                        title = "GitHub Import Failed",
                        isError = true,
                        errorMessage = result.errorMessage
                    )
                    addLog(LogEntry(level = LogLevel.ERROR, message = "GitHub download failed: ${result.errorMessage}"))
                    return@launch
                }

                _currentProject.value = result.project
                _fileTree.value = result.fileTree

                persistProjectState(result.project, result.fileTree)
                clearCheckpoint()

                _importProgress.value = ImportProgress(
                    isActive = true,
                    title = "Repository Imported",
                    statusText = "Loaded ${result.project.name} (${result.project.totalFiles} files ready).",
                    filesCount = result.project.totalFiles,
                    isComplete = true
                )
                addLog(LogEntry(
                    level = LogLevel.SUCCESS,
                    message = "GitHub repository unpacked & saved: '${result.project.name}' (${result.project.totalFiles} files ready)."
                ))
            } catch (e: Exception) {
                _importProgress.value = ImportProgress(
                    isActive = true,
                    title = "GitHub Import Failed",
                    isError = true,
                    errorMessage = e.localizedMessage ?: "Network or extraction error"
                )
                addLog(LogEntry(level = LogLevel.ERROR, message = "GitHub fetch error: ${e.localizedMessage}"))
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun dismissImportProgress() {
        _importProgress.value = null
    }

    fun dismissImportStatus() {
        _importStatusMessage.value = null
    }

    fun setTargetOs(os: com.example.automation.TargetOS) {
        _targetOs.value = os
        targetWorkspaceOrchestrator.targetOs = os
        addLog(LogEntry(
            level = LogLevel.INFO,
            message = "Target OS shell profile set to: ${os.label}"
        ))
    }

    fun toggleVimPlugin(enabled: Boolean) {
        pluginRegistry.isVimPluginEnabled = enabled
        addLog(LogEntry(
            level = LogLevel.INFO,
            message = if (enabled) "Vim Plugin enabled ('i' for insert, 'Esc :w' for save)." else "Vim Plugin disabled."
        ))
    }

    fun startOrchestratedWorkspaceBuild() {
        val payloads = buildIncludedPayloads()
        if (payloads.isEmpty()) {
            addLog(LogEntry(level = LogLevel.WARN, message = "Orchestration failed: No files are marked as included."))
            return
        }

        KeystrokeForegroundService.start(getApplication(), "Orchestrating ${payloads.size} files into VS Code")
        targetWorkspaceOrchestrator.startOrchestratedBuild(
            files = payloads,
            onLog = { addLog(it) }
        )
    }

    fun startTerminalScriptBootstrap() {
        val payloads = buildIncludedPayloads()
        if (payloads.isEmpty()) {
            addLog(LogEntry(level = LogLevel.WARN, message = "Script bootstrap failed: No files included."))
            return
        }

        KeystrokeForegroundService.start(getApplication(), "Terminal Bootstrap Script")
        targetWorkspaceOrchestrator.startTerminalScriptBootstrap(
            files = payloads,
            onLog = { addLog(it) }
        )
    }

    fun resetToSampleProject() {
        targetWorkspaceOrchestrator.stop(onLog = { addLog(it) })
        humanTypingSimulator.stop(onLog = { addLog(it) })
        clearCheckpoint()
        loadDefaultSampleProject()
    }
}
