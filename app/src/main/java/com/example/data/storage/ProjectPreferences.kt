package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences

data class TypingCheckpoint(
    val projectName: String,
    val fileRelativePath: String,
    val fileIndex: Int,
    val charOffset: Int,
    val charsTyped: Int,
    val totalChars: Int,
    val timestamp: Long
)

data class SavedProjectState(
    val id: String,
    val name: String,
    val rootPath: String,
    val isBundledSample: Boolean,
    val includedFilePaths: Set<String>
)

data class CadenceSettings(
    val cps: Int,
    val linePauseMs: Long,
    val jitter: Boolean,
    val countdownSec: Int
)

class ProjectPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("codeflow_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PROJECT_ID = "project_id"
        private const val KEY_PROJECT_NAME = "project_name"
        private const val KEY_PROJECT_ROOT_PATH = "project_root_path"
        private const val KEY_PROJECT_IS_SAMPLE = "project_is_sample"
        private const val KEY_INCLUDED_FILES = "project_included_files"

        // Checkpoint
        private const val KEY_CP_EXISTS = "cp_exists"
        private const val KEY_CP_PROJECT = "cp_project"
        private const val KEY_CP_FILE = "cp_file"
        private const val KEY_CP_FILE_INDEX = "cp_file_index"
        private const val KEY_CP_CHAR_OFFSET = "cp_char_offset"
        private const val KEY_CP_CHARS_TYPED = "cp_chars_typed"
        private const val KEY_CP_TOTAL_CHARS = "cp_total_chars"
        private const val KEY_CP_TIMESTAMP = "cp_timestamp"

        // Cadence
        private const val KEY_CPS = "cadence_cps"
        private const val KEY_LINE_PAUSE = "cadence_line_pause"
        private const val KEY_JITTER = "cadence_jitter"
        private const val KEY_COUNTDOWN = "cadence_countdown"

        // Theme
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_PALETTE = "theme_palette"
    }

    fun saveProject(
        id: String,
        name: String,
        rootPath: String,
        isBundledSample: Boolean,
        includedFiles: Set<String>
    ) {
        prefs.edit()
            .putString(KEY_PROJECT_ID, id)
            .putString(KEY_PROJECT_NAME, name)
            .putString(KEY_PROJECT_ROOT_PATH, rootPath)
            .putBoolean(KEY_PROJECT_IS_SAMPLE, isBundledSample)
            .putStringSet(KEY_INCLUDED_FILES, includedFiles)
            .apply()
    }

    fun loadSavedProject(): SavedProjectState? {
        val id = prefs.getString(KEY_PROJECT_ID, null) ?: return null
        val name = prefs.getString(KEY_PROJECT_NAME, "Project") ?: "Project"
        val rootPath = prefs.getString(KEY_PROJECT_ROOT_PATH, "") ?: ""
        val isSample = prefs.getBoolean(KEY_PROJECT_IS_SAMPLE, true)
        val included = prefs.getStringSet(KEY_INCLUDED_FILES, emptySet()) ?: emptySet()

        if (rootPath.isBlank()) return null
        return SavedProjectState(id, name, rootPath, isSample, included)
    }

    fun saveIncludedFiles(includedFiles: Set<String>) {
        prefs.edit().putStringSet(KEY_INCLUDED_FILES, includedFiles).apply()
    }

    // Checkpoint
    fun saveCheckpoint(checkpoint: TypingCheckpoint) {
        prefs.edit()
            .putBoolean(KEY_CP_EXISTS, true)
            .putString(KEY_CP_PROJECT, checkpoint.projectName)
            .putString(KEY_CP_FILE, checkpoint.fileRelativePath)
            .putInt(KEY_CP_FILE_INDEX, checkpoint.fileIndex)
            .putInt(KEY_CP_CHAR_OFFSET, checkpoint.charOffset)
            .putInt(KEY_CP_CHARS_TYPED, checkpoint.charsTyped)
            .putInt(KEY_CP_TOTAL_CHARS, checkpoint.totalChars)
            .putLong(KEY_CP_TIMESTAMP, checkpoint.timestamp)
            .apply()
    }

    fun getCheckpoint(): TypingCheckpoint? {
        if (!prefs.getBoolean(KEY_CP_EXISTS, false)) return null
        val project = prefs.getString(KEY_CP_PROJECT, "") ?: ""
        val file = prefs.getString(KEY_CP_FILE, "") ?: ""
        val fileIndex = prefs.getInt(KEY_CP_FILE_INDEX, 0)
        val charOffset = prefs.getInt(KEY_CP_CHAR_OFFSET, 0)
        val charsTyped = prefs.getInt(KEY_CP_CHARS_TYPED, 0)
        val totalChars = prefs.getInt(KEY_CP_TOTAL_CHARS, 0)
        val timestamp = prefs.getLong(KEY_CP_TIMESTAMP, 0L)
        return TypingCheckpoint(project, file, fileIndex, charOffset, charsTyped, totalChars, timestamp)
    }

    fun clearCheckpoint() {
        prefs.edit()
            .putBoolean(KEY_CP_EXISTS, false)
            .remove(KEY_CP_PROJECT)
            .remove(KEY_CP_FILE)
            .remove(KEY_CP_FILE_INDEX)
            .remove(KEY_CP_CHAR_OFFSET)
            .remove(KEY_CP_CHARS_TYPED)
            .remove(KEY_CP_TOTAL_CHARS)
            .remove(KEY_CP_TIMESTAMP)
            .apply()
    }

    // Cadence
    fun saveCadence(cps: Int, linePauseMs: Long, jitter: Boolean, countdownSec: Int) {
        prefs.edit()
            .putInt(KEY_CPS, cps)
            .putLong(KEY_LINE_PAUSE, linePauseMs)
            .putBoolean(KEY_JITTER, jitter)
            .putInt(KEY_COUNTDOWN, countdownSec)
            .apply()
    }

    fun getCadence(): CadenceSettings {
        val cps = prefs.getInt(KEY_CPS, 32)
        val linePause = prefs.getLong(KEY_LINE_PAUSE, 180L)
        val jitter = prefs.getBoolean(KEY_JITTER, true)
        val countdown = prefs.getInt(KEY_COUNTDOWN, 3)
        return CadenceSettings(cps, linePause, jitter, countdown)
    }

    // Theme
    fun saveTheme(mode: String, palette: String) {
        prefs.edit()
            .putString(KEY_THEME_MODE, mode)
            .putString(KEY_THEME_PALETTE, palette)
            .apply()
    }

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
    fun getThemePalette(): String = prefs.getString(KEY_THEME_PALETTE, "vscode") ?: "vscode"
}
