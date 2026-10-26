package com.example.automation

import com.example.bluetooth.BluetoothHidKeyboardManager
import com.example.bluetooth.HumanTypingSimulator
import com.example.bluetooth.HumanTypingSimulator.FilePayload
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TargetWorkspaceOrchestrator(
    private val keyboardManager: BluetoothHidKeyboardManager,
    private val typingSimulator: HumanTypingSimulator,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    sealed class OrchestratorState {
        data object Idle : OrchestratorState()
        data class Running(
            val stepName: String,
            val currentFile: String,
            val fileIndex: Int,
            val totalFiles: Int,
            val progress: Float
        ) : OrchestratorState()
        data class Paused(val stepName: String) : OrchestratorState()
        data class Completed(val totalFiles: Int, val durationMs: Long) : OrchestratorState()
        data class Error(val message: String) : OrchestratorState()
    }

    private val _orchestratorState = MutableStateFlow<OrchestratorState>(OrchestratorState.Idle)
    val orchestratorState: StateFlow<OrchestratorState> = _orchestratorState.asStateFlow()

    private var orchestratorJob: Job? = null
    var targetOs: TargetOS = TargetOS.WINDOWS_POWERSHELL
    val pluginRegistry = AutomationPluginRegistry()

    fun startOrchestratedBuild(
        files: List<FilePayload>,
        onLog: (LogEntry) -> Unit
    ) {
        stop(onLog)
        if (files.isEmpty()) {
            _orchestratorState.value = OrchestratorState.Error("No files selected to build.")
            onLog(LogEntry(level = LogLevel.WARN, message = "Orchestration aborted: No files selected."))
            return
        }

        orchestratorJob = scope.launch {
            val startTime = System.currentTimeMillis()
            onLog(LogEntry(
                level = LogLevel.INFO,
                message = "Initiating Context-Aware VS Code Automation for target OS: [${targetOs.label}] (${files.size} files)..."
            ))

            // Ensure Bluetooth HID connection
            val isSim = keyboardManager.hidState.value is BluetoothHidKeyboardManager.HidState.SimulationMode
            if (!isSim && keyboardManager.ensureHidConnected() == null) {
                _orchestratorState.value = OrchestratorState.Error("Bluetooth HID connection not active. Connect to PC first.")
                onLog(LogEntry(
                    level = LogLevel.ERROR,
                    message = "Orchestration failed: Bluetooth HID channel is not connected to target PC."
                ))
                return@launch
            }

            // Step 1: Open Terminal in VS Code
            _orchestratorState.value = OrchestratorState.Running(
                stepName = "Opening VS Code Terminal...",
                currentFile = "",
                fileIndex = 0,
                totalFiles = files.size,
                progress = 0.05f
            )
            onLog(LogEntry(level = LogLevel.INFO, message = ">> Step 1/4: Sending VS Code Toggle Terminal shortcut..."))
            keyboardManager.sendShortcut(
                pluginRegistry.toggleTerminalBinding.modifier,
                pluginRegistry.toggleTerminalBinding.keycode
            )
            delay(1200L)

            // Step 2: Create directory hierarchy via terminal
            val dirs = WorkspaceSynthesizer.extractDirectories(files)
            if (dirs.isNotEmpty()) {
                _orchestratorState.value = OrchestratorState.Running(
                    stepName = "Creating directory tree via terminal...",
                    currentFile = "",
                    fileIndex = 0,
                    totalFiles = files.size,
                    progress = 0.15f
                )
                val mkdirCmd = targetOs.getMkdirCommand(dirs) + "\n"
                onLog(LogEntry(level = LogLevel.INFO, message = ">> Step 2/4: Creating ${dirs.size} directories in terminal..."))
                for (ch in mkdirCmd) {
                    if (!isActive) break
                    keyboardManager.sendChar(ch)
                    delay(12L)
                }
                delay(1000L)
            }

            // Step 3: Touch / Initialize all blank file nodes in terminal
            _orchestratorState.value = OrchestratorState.Running(
                stepName = "Initializing blank file entries in terminal...",
                currentFile = "",
                fileIndex = 0,
                totalFiles = files.size,
                progress = 0.25f
            )
            val touchCmd = targetOs.getTouchFilesCommand(files.map { it.relativePath }) + "\n"
            onLog(LogEntry(level = LogLevel.INFO, message = ">> Step 3/4: Creating ${files.size} blank file nodes in workspace..."))
            for (ch in touchCmd) {
                if (!isActive) break
                keyboardManager.sendChar(ch)
                delay(10L)
            }
            delay(1200L)

            // Step 4: Open each file explicitly in VS Code editor, stream content, and save!
            for (i in files.indices) {
                if (!isActive) break
                val file = files[i]
                val currentProgress = 0.25f + (0.75f * ((i + 1).toFloat() / files.size))

                _orchestratorState.value = OrchestratorState.Running(
                    stepName = "Opening '${file.relativePath}' in VS Code...",
                    currentFile = file.relativePath,
                    fileIndex = i + 1,
                    totalFiles = files.size,
                    progress = currentProgress
                )

                onLog(LogEntry(
                    level = LogLevel.SUCCESS,
                    message = ">> Step 4/4 [${i + 1}/${files.size}]: Opening '${file.relativePath}' in VS Code editor buffer..."
                ))

                // Open file in VS Code via terminal command `code -r "file"`
                val openCmd = targetOs.getOpenFileInVsCodeCommand(file.relativePath) + "\n"
                for (ch in openCmd) {
                    if (!isActive) break
                    keyboardManager.sendChar(ch)
                    delay(12L)
                }

                // Wait for VS Code to switch tab focus into the active editor buffer
                delay(pluginRegistry.bufferFocusDelayMs)

                // If Vim plugin enabled, send 'i' to enter insert mode
                if (pluginRegistry.isVimPluginEnabled) {
                    keyboardManager.sendChar('i')
                    delay(100L)
                }

                // Select all & clear if file already had content, ensuring pristine buffer
                keyboardManager.sendShortcut(pluginRegistry.saveFileBinding.modifier, com.example.bluetooth.HidKeycodes.KEY_A) // Ctrl + A
                delay(50L)
                keyboardManager.sendShortcut(0, com.example.bluetooth.HidKeycodes.KEY_BACKSPACE) // Backspace
                delay(50L)

                // Stream code content line-by-line into active editor buffer
                onLog(LogEntry(level = LogLevel.INFO, message = "Streaming ${file.content.length} chars into buffer '${file.relativePath}'..."))
                for (ch in file.content) {
                    if (!isActive) break
                    keyboardManager.sendChar(ch)
                    delay((1000.0 / typingSimulator.charsPerSecond).toLong().coerceAtLeast(8L))
                }

                // If Vim plugin enabled, send Esc -> :w -> Enter
                if (pluginRegistry.isVimPluginEnabled) {
                    keyboardManager.sendShortcut(0, com.example.bluetooth.HidKeycodes.KEY_ESCAPE)
                    delay(100L)
                    val vimSave = ":w\n"
                    for (ch in vimSave) {
                        keyboardManager.sendChar(ch)
                        delay(20L)
                    }
                } else if (pluginRegistry.isAutoSaveAfterFileEnabled) {
                    // Send Ctrl + S to save buffer
                    keyboardManager.sendShortcut(pluginRegistry.saveFileBinding.modifier, pluginRegistry.saveFileBinding.keycode)
                    delay(300L)
                }

                onLog(LogEntry(level = LogLevel.SUCCESS, message = "Saved '${file.relativePath}' (${file.content.length} chars)."))
                delay(600L)
            }

            if (isActive) {
                val duration = System.currentTimeMillis() - startTime
                _orchestratorState.value = OrchestratorState.Completed(totalFiles = files.size, durationMs = duration)
                onLog(LogEntry(
                    level = LogLevel.SUCCESS,
                    message = "AUTOMATION SUCCESS: Created full workspace tree and typed all ${files.size} files into VS Code in ${duration / 1000}s!"
                ))
            }
        }
    }

    /**
     * Instantly pastes a single Terminal Bootstrap Script into target shell
     */
    fun startTerminalScriptBootstrap(
        files: List<FilePayload>,
        onLog: (LogEntry) -> Unit
    ) {
        stop(onLog)
        if (files.isEmpty()) return

        orchestratorJob = scope.launch {
            onLog(LogEntry(level = LogLevel.INFO, message = "Generating single-shot terminal bootstrap script for ${targetOs.label}..."))
            val compactCmd = WorkspaceSynthesizer.generateCompactBootstrapCommand(targetOs, files) + "\n"

            val isSim = keyboardManager.hidState.value is BluetoothHidKeyboardManager.HidState.SimulationMode
            if (!isSim && keyboardManager.ensureHidConnected() == null) {
                _orchestratorState.value = OrchestratorState.Error("Bluetooth HID connection not active.")
                return@launch
            }

            // Open terminal
            keyboardManager.sendShortcut(pluginRegistry.toggleTerminalBinding.modifier, pluginRegistry.toggleTerminalBinding.keycode)
            delay(1000L)

            onLog(LogEntry(level = LogLevel.INFO, message = "Pasting terminal bootstrap command sequence..."))
            for (ch in compactCmd) {
                if (!isActive) break
                keyboardManager.sendChar(ch)
                delay(12L)
            }

            onLog(LogEntry(level = LogLevel.SUCCESS, message = "Terminal bootstrap command sequence delivered! Directory tree and files initialized."))
            _orchestratorState.value = OrchestratorState.Completed(files.size, 2000L)
        }
    }

    fun stop(onLog: (LogEntry) -> Unit = {}) {
        val wasActive = orchestratorJob?.isActive == true
        orchestratorJob?.cancel()
        orchestratorJob = null
        keyboardManager.emergencyReleaseAllKeys()
        _orchestratorState.value = OrchestratorState.Idle
        if (wasActive) {
            onLog(LogEntry(level = LogLevel.WARN, message = "VS Code Orchestration halted."))
        }
    }
}
