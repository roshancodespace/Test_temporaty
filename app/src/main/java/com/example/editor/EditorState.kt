package com.example.editor

data class EditorTab(
    val filePath: String,
    val fileName: String,
    val isModified: Boolean = false,
    val isPinned: Boolean = false
)

data class EditorUiState(
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabPath: String = "",
    val fontSizeSp: Float = 13f,
    val isDistractionFree: Boolean = false,
    val isExplorerExpanded: Boolean = true,
    val showTerminal: Boolean = false,
    val terminalOutput: String = ""
)
