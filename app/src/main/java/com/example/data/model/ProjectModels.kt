package com.example.data.model

data class Project(
    val id: String,
    val name: String,
    val rootPath: String,
    val isBundledSample: Boolean = false,
    val totalFiles: Int = 0,
    val includedFilesCount: Int = 0
)

data class FileNode(
    val id: String,
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList(),
    val isIncluded: Boolean = true,
    val extension: String = "",
    val sizeBytes: Long = 0L
)

data class ProjectFilterRules(
    val excludedExtensions: Set<String> = setOf(
        "class", "dex", "apk", "aab", "jar", "lock", "png", "webp", "jpg", "jpeg", "ico"
    ),
    val excludedDirs: Set<String> = setOf(
        ".git", ".gradle", "build", "node_modules", ".idea", "dist", "out", ".venv", "__pycache__"
    )
)
