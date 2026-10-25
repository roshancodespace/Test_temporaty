package com.example.data.model

sealed class PlaybackAction {
    data class OpenFile(val path: String) : PlaybackAction()
    data class InsertText(val text: String, val startOffset: Int = -1) : PlaybackAction()
    data class ReplaceRange(val start: Int, val end: Int, val text: String) : PlaybackAction()
    data class RemoveRange(val start: Int, val end: Int) : PlaybackAction()
    data class MoveCaret(val offset: Int) : PlaybackAction()
    data class SelectRange(val start: Int, val end: Int) : PlaybackAction()
    data class ScrollToLine(val line: Int) : PlaybackAction()
    data class Pause(val durationMs: Long) : PlaybackAction()
    data class SwitchTab(val path: String) : PlaybackAction()
    data class TerminalCommand(val command: String) : PlaybackAction()
    data class TerminalOutput(val output: String) : PlaybackAction()
}

enum class PlaybackSpeed(val multiplier: Float, val label: String) {
    HALF(0.5f, "0.5x"),
    NORMAL(1.0f, "1.0x"),
    DOUBLE(2.0f, "2.0x"),
    QUAD(4.0f, "4.0x")
}

sealed class PlaybackState {
    data object Idle : PlaybackState()

    data class Playing(
        val progress: Float,
        val currentActionIndex: Int,
        val totalActions: Int,
        val currentFile: String,
        val charsTyped: Int,
        val totalChars: Int,
        val line: Int,
        val col: Int,
        val elapsedMs: Long,
        val estimatedRemainingMs: Long
    ) : PlaybackState()

    data class Paused(
        val progress: Float,
        val currentActionIndex: Int,
        val totalActions: Int,
        val currentFile: String,
        val charsTyped: Int,
        val totalChars: Int,
        val line: Int,
        val col: Int,
        val elapsedMs: Long,
        val estimatedRemainingMs: Long
    ) : PlaybackState()

    data class Completed(
        val totalChars: Int,
        val durationMs: Long
    ) : PlaybackState()

    data class Error(
        val message: String
    ) : PlaybackState()
}

data class PlaybackSession(
    val id: String,
    val name: String,
    val actions: List<PlaybackAction>,
    val defaultCps: Int = 30, // Characters per second
    val lineEndPauseMs: Long = 180L,
    val randomJitter: Boolean = true,
    val randomSeed: Long = 42L
)
