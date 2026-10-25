package com.example.bluetooth

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
import java.util.Random

class HumanTypingSimulator(
    private val keyboardManager: BluetoothHidKeyboardManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    sealed class TypingState {
        data object Idle : TypingState()
        data class Typing(
            val currentFile: String,
            val charsTyped: Int,
            val totalChars: Int,
            val progress: Float,
            val fileIndex: Int,
            val totalFiles: Int,
            val line: Int,
            val col: Int
        ) : TypingState()
        data class Paused(
            val currentFile: String,
            val charsTyped: Int,
            val totalChars: Int,
            val progress: Float,
            val fileIndex: Int,
            val charOffset: Int
        ) : TypingState()
        data class Completed(val totalChars: Int, val durationMs: Long) : TypingState()
        data class Stopped(val reason: String) : TypingState()
    }

    data class ActiveCheckpoint(
        val fileIndex: Int,
        val charOffset: Int,
        val relativePath: String,
        val charsTyped: Int,
        val totalChars: Int
    )

    private val _typingState = MutableStateFlow<TypingState>(TypingState.Idle)
    val typingState: StateFlow<TypingState> = _typingState.asStateFlow()

    private var typingJob: Job? = null
    private var isPaused = false

    // Current checkpoint tracking
    var currentCheckpoint: ActiveCheckpoint? = null
        private set

    // Configuration
    var charsPerSecond: Int = 32
    var linePauseMs: Long = 180L
    var betweenFilesPauseMs: Long = 1500L
    var humanJitter: Boolean = true
    var randomSeed: Long = 42L

    data class FilePayload(
        val relativePath: String,
        val content: String
    )

    fun startTypingSequence(
        files: List<FilePayload>,
        startDelayMs: Long = 3000L,
        resumeFileIndex: Int = 0,
        resumeCharOffset: Int = 0,
        resumeCharsTyped: Int = 0,
        onLog: (LogEntry) -> Unit = {},
        onCheckpointUpdated: ((ActiveCheckpoint) -> Unit)? = null
    ) {
        stop(onLog)
        if (files.isEmpty()) {
            _typingState.value = TypingState.Stopped("No files selected to type.")
            onLog(LogEntry(level = LogLevel.WARN, message = "Typing aborted: No files included."))
            return
        }

        isPaused = false
        val totalChars = files.sumOf { it.content.length }.coerceAtLeast(1)

        typingJob = scope.launch {
            val random = Random(randomSeed)
            var totalTyped = resumeCharsTyped.coerceAtLeast(0)
            val startTime = System.currentTimeMillis()

            val isResuming = resumeFileIndex > 0 || resumeCharOffset > 0
            if (isResuming) {
                onLog(LogEntry(
                    level = LogLevel.INFO,
                    message = "Resuming PC typing sequence at file [${resumeFileIndex + 1}/${files.size}], char offset $resumeCharOffset ($totalTyped/$totalChars chars typed)..."
                ))
            } else {
                onLog(LogEntry(
                    level = LogLevel.INFO,
                    message = "Initiating PC typing sequence for ${files.size} file(s) ($totalChars total chars)..."
                ))
            }

            // Countdown buffer to allow user to switch focus to PC VS Code
            val countdownSec = (startDelayMs / 1000).toInt().coerceAtLeast(1)
            for (countdown in countdownSec downTo 1) {
                _typingState.value = TypingState.Typing(
                    currentFile = "Countdown: $countdown s (Focus VS Code on PC)...",
                    charsTyped = totalTyped,
                    totalChars = totalChars,
                    progress = (totalTyped.toFloat() / totalChars).coerceIn(0f, 1f),
                    fileIndex = resumeFileIndex + 1,
                    totalFiles = files.size,
                    line = 1,
                    col = 1
                )
                onLog(LogEntry(
                    level = LogLevel.INFO,
                    message = "Switch focus to PC VS Code! Starting in $countdown s..."
                ))
                delay(1000L)
            }

            val startIndex = resumeFileIndex.coerceIn(0, files.size - 1)

            for (fileIdx in startIndex until files.size) {
                if (!isActive) break
                val file = files[fileIdx]
                val content = file.content

                // Determine start character for this file (if resuming in the middle of this file)
                val charStart = if (fileIdx == startIndex) resumeCharOffset.coerceIn(0, content.length) else 0

                // Calculate initial line & column
                var line = 1
                var col = 1
                for (i in 0 until charStart) {
                    if (content[i] == '\n') {
                        line++
                        col = 1
                    } else {
                        col++
                    }
                }

                onLog(LogEntry(
                    level = LogLevel.SUCCESS,
                    message = ">> Typing [${fileIdx + 1}/${files.size}]: ${file.relativePath} (${content.length - charStart} remaining chars) at ~$charsPerSecond cps"
                ))

                for (charIdx in charStart until content.length) {
                    while (isPaused && isActive) {
                        delay(100L)
                    }
                    if (!isActive) break

                    val char = content[charIdx]
                    keyboardManager.sendChar(char)
                    totalTyped++

                    if (char == '\n') {
                        line++
                        col = 1
                    } else {
                        col++
                    }

                    val progress = (totalTyped.toFloat() / totalChars).coerceIn(0f, 1f)
                    _typingState.value = TypingState.Typing(
                        currentFile = file.relativePath,
                        charsTyped = totalTyped,
                        totalChars = totalChars,
                        progress = progress,
                        fileIndex = fileIdx + 1,
                        totalFiles = files.size,
                        line = line,
                        col = col
                    )

                    // Track checkpoint state
                    val checkpoint = ActiveCheckpoint(
                        fileIndex = fileIdx,
                        charOffset = charIdx + 1,
                        relativePath = file.relativePath,
                        charsTyped = totalTyped,
                        totalChars = totalChars
                    )
                    currentCheckpoint = checkpoint

                    // Humanized timing delays
                    val baseDelayMs = (1000.0 / charsPerSecond.coerceAtLeast(5)).coerceAtLeast(10.0)
                    val jitter = if (humanJitter) {
                        (random.nextDouble() * 0.4 - 0.2) * baseDelayMs
                    } else {
                        0.0
                    }

                    var delayTime = (baseDelayMs + jitter).toLong().coerceAtLeast(5L)
                    if (char == '\n') {
                        delayTime += linePauseMs
                    } else if (char in listOf(';', '{', '}', ',', '.')) {
                        delayTime += 40L
                    }

                    // Periodic keystroke logging & checkpoint persistence
                    if (totalTyped % 80 == 0) {
                        onLog(LogEntry(
                            level = LogLevel.KEYSTROKE,
                            message = "${file.relativePath}: ${charIdx + 1}/${content.length} chars typed (Ln $line, Col $col)"
                        ))
                        onCheckpointUpdated?.invoke(checkpoint)
                    }

                    delay(delayTime)
                }

                onLog(LogEntry(
                    level = LogLevel.SUCCESS,
                    message = "Completed ${file.relativePath} (${content.length} chars)."
                ))

                // Pause between files
                if (fileIdx < files.size - 1 && isActive) {
                    onLog(LogEntry(
                        level = LogLevel.INFO,
                        message = "Pausing ${betweenFilesPauseMs}ms before next file..."
                    ))
                    delay(betweenFilesPauseMs)
                }
            }

            if (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val durationSec = (elapsed / 1000).coerceAtLeast(1)
                val avgCps = totalTyped / durationSec
                _typingState.value = TypingState.Completed(totalChars = totalTyped, durationMs = elapsed)
                currentCheckpoint = null
                onLog(LogEntry(
                    level = LogLevel.SUCCESS,
                    message = "SUCCESS: All ${files.size} file(s) typed into PC ($totalTyped chars in ${durationSec}s, avg $avgCps cps)."
                ))
            }
        }
    }

    fun pause(onLog: (LogEntry) -> Unit = {}, onCheckpointSaved: ((ActiveCheckpoint) -> Unit)? = null) {
        isPaused = true
        val current = _typingState.value
        if (current is TypingState.Typing) {
            val cp = currentCheckpoint
            _typingState.value = TypingState.Paused(
                currentFile = current.currentFile,
                charsTyped = current.charsTyped,
                totalChars = current.totalChars,
                progress = current.progress,
                fileIndex = cp?.fileIndex ?: (current.fileIndex - 1).coerceAtLeast(0),
                charOffset = cp?.charOffset ?: 0
            )
            if (cp != null) {
                onCheckpointSaved?.invoke(cp)
            }
            onLog(LogEntry(level = LogLevel.WARN, message = "PC typing paused. Checkpoint stored."))
        }
    }

    fun resume(onLog: (LogEntry) -> Unit = {}) {
        isPaused = false
        onLog(LogEntry(level = LogLevel.INFO, message = "PC typing resumed."))
    }

    fun stop(onLog: (LogEntry) -> Unit = {}, onCheckpointSaved: ((ActiveCheckpoint) -> Unit)? = null) {
        val wasActive = typingJob?.isActive == true
        val cp = currentCheckpoint
        if (wasActive && cp != null) {
            onCheckpointSaved?.invoke(cp)
        }
        typingJob?.cancel()
        typingJob = null
        isPaused = false
        keyboardManager.emergencyReleaseAllKeys()
        _typingState.value = TypingState.Idle
        if (wasActive) {
            onLog(LogEntry(level = LogLevel.WARN, message = "PC typing halted. Checkpoint saved to memory."))
        }
    }

    fun clearActiveCheckpoint() {
        currentCheckpoint = null
    }
}
