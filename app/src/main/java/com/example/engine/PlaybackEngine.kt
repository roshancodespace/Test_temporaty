package com.example.engine

import com.example.data.model.PlaybackAction
import com.example.data.model.PlaybackSession
import com.example.data.model.PlaybackSpeed
import com.example.data.model.PlaybackState
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

class PlaybackEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // File path to current presentation text buffer
    private val _documents = MutableStateFlow<Map<String, String>>(emptyMap())
    val documents: StateFlow<Map<String, String>> = _documents.asStateFlow()

    private val _activeFile = MutableStateFlow<String>("")
    val activeFile: StateFlow<String> = _activeFile.asStateFlow()

    private val _caretPosition = MutableStateFlow<Int>(0)
    val caretPosition: StateFlow<Int> = _caretPosition.asStateFlow()

    private val _speed = MutableStateFlow(PlaybackSpeed.NORMAL)
    val speed: StateFlow<PlaybackSpeed> = _speed.asStateFlow()

    private var playbackJob: Job? = null
    private var currentSession: PlaybackSession? = null

    // Internal state tracking for deterministic resumption
    private var actionIndex = 0
    private var charOffsetInAction = 0
    private var totalCharsInSession = 0
    private var charsTypedCount = 0
    private var totalSessionActions = 0
    private var startTimeMs = 0L
    private var accumulatedElapsedMs = 0L

    // In-memory buffers for presentation
    private val documentBuffers = mutableMapOf<String, StringBuilder>()

    fun setSpeed(newSpeed: PlaybackSpeed) {
        _speed.value = newSpeed
    }

    fun start(session: PlaybackSession) {
        stop()
        currentSession = session
        actionIndex = 0
        charOffsetInAction = 0
        charsTypedCount = 0
        accumulatedElapsedMs = 0L
        documentBuffers.clear()
        _documents.value = emptyMap()

        // Count total characters to be inserted in session for progress
        totalCharsInSession = session.actions.sumOf { action ->
            when (action) {
                is PlaybackAction.InsertText -> action.text.length
                else -> 0
            }
        }.coerceAtLeast(1)

        totalSessionActions = session.actions.size
        launchPlaybackLoop()
    }

    fun pause() {
        val current = _state.value
        if (current is PlaybackState.Playing) {
            playbackJob?.cancel()
            playbackJob = null
            accumulatedElapsedMs += (System.currentTimeMillis() - startTimeMs)

            _state.value = PlaybackState.Paused(
                progress = current.progress,
                currentActionIndex = current.currentActionIndex,
                totalActions = current.totalActions,
                currentFile = current.currentFile,
                charsTyped = current.charsTyped,
                totalChars = current.totalChars,
                line = current.line,
                col = current.col,
                elapsedMs = accumulatedElapsedMs,
                estimatedRemainingMs = current.estimatedRemainingMs
            )
        }
    }

    fun resume() {
        if (_state.value is PlaybackState.Paused && currentSession != null) {
            launchPlaybackLoop()
        }
    }

    fun restart() {
        val session = currentSession ?: return
        start(session)
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _state.value = PlaybackState.Idle
    }

    fun seekToProgress(targetProgress: Float) {
        val session = currentSession ?: return
        val wasPlaying = _state.value is PlaybackState.Playing
        playbackJob?.cancel()
        playbackJob = null

        val targetChars = (targetProgress.coerceIn(0f, 1f) * totalCharsInSession).toInt()

        // Deterministically recompute state from action 0
        documentBuffers.clear()
        charsTypedCount = 0
        actionIndex = 0
        charOffsetInAction = 0
        var currentFile = ""

        for (i in session.actions.indices) {
            val action = session.actions[i]
            when (action) {
                is PlaybackAction.OpenFile -> {
                    currentFile = action.path
                    _activeFile.value = currentFile
                    documentBuffers.getOrPut(currentFile) { StringBuilder() }
                }
                is PlaybackAction.SwitchTab -> {
                    currentFile = action.path
                    _activeFile.value = currentFile
                    documentBuffers.getOrPut(currentFile) { StringBuilder() }
                }
                is PlaybackAction.InsertText -> {
                    val remaining = targetChars - charsTypedCount
                    if (remaining >= action.text.length) {
                        val buf = documentBuffers.getOrPut(currentFile) { StringBuilder() }
                        buf.append(action.text)
                        charsTypedCount += action.text.length
                    } else if (remaining > 0) {
                        val buf = documentBuffers.getOrPut(currentFile) { StringBuilder() }
                        buf.append(action.text.substring(0, remaining))
                        charsTypedCount += remaining
                        actionIndex = i
                        charOffsetInAction = remaining
                        break
                    } else {
                        actionIndex = i
                        charOffsetInAction = 0
                        break
                    }
                }
                else -> {}
            }
            if (charsTypedCount >= targetChars) {
                actionIndex = i + 1
                charOffsetInAction = 0
                break
            }
        }

        updateDocumentsState()
        val (line, col) = computeLineCol(currentFile)
        val progress = (charsTypedCount.toFloat() / totalCharsInSession).coerceIn(0f, 1f)

        if (wasPlaying) {
            _state.value = PlaybackState.Playing(
                progress = progress,
                currentActionIndex = actionIndex,
                totalActions = totalSessionActions,
                currentFile = currentFile,
                charsTyped = charsTypedCount,
                totalChars = totalCharsInSession,
                line = line,
                col = col,
                elapsedMs = accumulatedElapsedMs,
                estimatedRemainingMs = computeRemainingMs(totalCharsInSession - charsTypedCount, session.defaultCps)
            )
            launchPlaybackLoop()
        } else {
            _state.value = PlaybackState.Paused(
                progress = progress,
                currentActionIndex = actionIndex,
                totalActions = totalSessionActions,
                currentFile = currentFile,
                charsTyped = charsTypedCount,
                totalChars = totalCharsInSession,
                line = line,
                col = col,
                elapsedMs = accumulatedElapsedMs,
                estimatedRemainingMs = computeRemainingMs(totalCharsInSession - charsTypedCount, session.defaultCps)
            )
        }
    }

    private fun launchPlaybackLoop() {
        val session = currentSession ?: return
        playbackJob?.cancel()

        startTimeMs = System.currentTimeMillis()

        playbackJob = scope.launch {
            val random = Random(session.randomSeed + actionIndex + charsTypedCount)

            while (isActive && actionIndex < session.actions.size) {
                val action = session.actions[actionIndex]
                val speedMultiplier = _speed.value.multiplier

                when (action) {
                    is PlaybackAction.OpenFile -> {
                        _activeFile.value = action.path
                        documentBuffers.getOrPut(action.path) { StringBuilder() }
                        updateDocumentsState()
                        actionIndex++
                        charOffsetInAction = 0
                        delay((80L / speedMultiplier).toLong())
                    }

                    is PlaybackAction.SwitchTab -> {
                        _activeFile.value = action.path
                        documentBuffers.getOrPut(action.path) { StringBuilder() }
                        updateDocumentsState()
                        actionIndex++
                        charOffsetInAction = 0
                        delay((100L / speedMultiplier).toLong())
                    }

                    is PlaybackAction.Pause -> {
                        val pauseMs = (action.durationMs / speedMultiplier).toLong()
                        delay(pauseMs)
                        actionIndex++
                        charOffsetInAction = 0
                    }

                    is PlaybackAction.ScrollToLine -> {
                        // Caret and view positioning
                        actionIndex++
                        charOffsetInAction = 0
                        delay((50L / speedMultiplier).toLong())
                    }

                    is PlaybackAction.MoveCaret -> {
                        _caretPosition.value = action.offset
                        actionIndex++
                        charOffsetInAction = 0
                    }

                    is PlaybackAction.InsertText -> {
                        val activePath = _activeFile.value
                        val buffer = documentBuffers.getOrPut(activePath) { StringBuilder() }

                        while (isActive && charOffsetInAction < action.text.length) {
                            val char = action.text[charOffsetInAction]
                            buffer.append(char)
                            charOffsetInAction++
                            charsTypedCount++

                            _caretPosition.value = buffer.length
                            updateDocumentsState()

                            val progress = (charsTypedCount.toFloat() / totalCharsInSession).coerceIn(0f, 1f)
                            val (line, col) = computeLineCol(activePath)
                            val remainingChars = totalCharsInSession - charsTypedCount
                            val remainingMs = computeRemainingMs(remainingChars, session.defaultCps)

                            _state.value = PlaybackState.Playing(
                                progress = progress,
                                currentActionIndex = actionIndex,
                                totalActions = totalSessionActions,
                                currentFile = activePath,
                                charsTyped = charsTypedCount,
                                totalChars = totalCharsInSession,
                                line = line,
                                col = col,
                                elapsedMs = accumulatedElapsedMs + (System.currentTimeMillis() - startTimeMs),
                                estimatedRemainingMs = remainingMs
                            )

                            // Compute delay with deterministic typing jitter
                            val baseMs = (1000.0 / session.defaultCps).coerceAtLeast(8.0)
                            val jitter = if (session.randomJitter) {
                                (random.nextDouble() * 0.4 - 0.2) * baseMs // +/- 20%
                            } else {
                                0.0
                            }
                            var charDelay = ((baseMs + jitter) / speedMultiplier).toLong().coerceAtLeast(4L)

                            // Longer pause after line endings and punctuation
                            if (char == '\n') {
                                charDelay += (session.lineEndPauseMs / speedMultiplier).toLong()
                            } else if (char == '.' || char == ';' || char == '{' || char == '}') {
                                charDelay += (50L / speedMultiplier).toLong()
                            }

                            delay(charDelay)
                        }

                        if (charOffsetInAction >= action.text.length) {
                            actionIndex++
                            charOffsetInAction = 0
                        }
                    }

                    is PlaybackAction.ReplaceRange -> {
                        val activePath = _activeFile.value
                        val buffer = documentBuffers.getOrPut(activePath) { StringBuilder() }
                        if (action.start in 0..buffer.length && action.end in action.start..buffer.length) {
                            buffer.replace(action.start, action.end, action.text)
                            updateDocumentsState()
                        }
                        actionIndex++
                        charOffsetInAction = 0
                        delay((80L / speedMultiplier).toLong())
                    }

                    is PlaybackAction.RemoveRange -> {
                        val activePath = _activeFile.value
                        val buffer = documentBuffers.getOrPut(activePath) { StringBuilder() }
                        if (action.start in 0..buffer.length && action.end in action.start..buffer.length) {
                            buffer.delete(action.start, action.end)
                            updateDocumentsState()
                        }
                        actionIndex++
                        charOffsetInAction = 0
                        delay((60L / speedMultiplier).toLong())
                    }

                    else -> {
                        actionIndex++
                        charOffsetInAction = 0
                    }
                }
            }

            if (isActive && actionIndex >= session.actions.size) {
                accumulatedElapsedMs += (System.currentTimeMillis() - startTimeMs)
                _state.value = PlaybackState.Completed(
                    totalChars = charsTypedCount,
                    durationMs = accumulatedElapsedMs
                )
            }
        }
    }

    private fun updateDocumentsState() {
        _documents.value = documentBuffers.mapValues { it.value.toString() }
    }

    private fun computeLineCol(path: String): Pair<Int, Int> {
        val text = documentBuffers[path]?.toString() ?: return Pair(1, 1)
        val pos = _caretPosition.value.coerceIn(0, text.length)
        var line = 1
        var col = 1
        for (i in 0 until pos) {
            if (text[i] == '\n') {
                line++
                col = 1
            } else {
                col++
            }
        }
        return Pair(line, col)
    }

    private fun computeRemainingMs(remainingChars: Int, cps: Int): Long {
        if (cps <= 0) return 0L
        val multiplier = _speed.value.multiplier
        return ((remainingChars.toDouble() / cps) * 1000.0 / multiplier).toLong()
    }
}
