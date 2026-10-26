package com.example.automation

import com.example.bluetooth.HumanTypingSimulator.FilePayload

object WorkspaceSynthesizer {

    data class DirectoryStructure(
        val directories: List<String>,
        val files: List<FilePayload>
    )

    /**
     * Extracts all unique parent directory paths from the list of file payloads.
     */
    fun extractDirectories(files: List<FilePayload>): List<String> {
        val dirs = mutableSetOf<String>()
        for (file in files) {
            val parts = file.relativePath.split('/', '\\')
            if (parts.size > 1) {
                var currentPath = ""
                for (i in 0 until parts.size - 1) {
                    val part = parts[i]
                    if (part.isNotBlank()) {
                        currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                        dirs.add(currentPath)
                    }
                }
            }
        }
        return dirs.sortedBy { it.length }
    }

    /**
     * Generates a single, executable terminal script for the target OS that bootstraps the entire project tree.
     */
    fun generateBootstrapScript(os: TargetOS, files: List<FilePayload>): String {
        if (files.isEmpty()) return ""

        val dirs = extractDirectories(files)
        val sb = StringBuilder()

        when (os) {
            TargetOS.WINDOWS_POWERSHELL -> {
                sb.append("# CodeFlow Studio Workspace Bootstrap Script\n")
                if (dirs.isNotEmpty()) {
                    val dirList = dirs.joinToString(", ") { "'$it'" }
                    sb.append("New-Item -ItemType Directory -Force -Path @($dirList) | Out-Null\n")
                }
                for (file in files) {
                    val safePath = file.relativePath.replace('/', '\\')
                    sb.append("Set-Content -Path '$safePath' -Value @'\n")
                    sb.append(file.content)
                    if (!file.content.endsWith("\n")) sb.append("\n")
                    sb.append("'@ -Encoding UTF8\n")
                }
                sb.append("Write-Host '[CodeFlow Studio] Workspace structure successfully bootstrapped!'\n")
            }

            TargetOS.WINDOWS_CMD -> {
                sb.append("@echo off\n")
                sb.append("echo [CodeFlow] Creating folder hierarchy...\n")
                for (dir in dirs) {
                    val safeDir = dir.replace('/', '\\')
                    sb.append("if not exist \"$safeDir\" mkdir \"$safeDir\"\n")
                }
                for (file in files) {
                    val safePath = file.relativePath.replace('/', '\\')
                    val lines = file.content.lines()
                    if (lines.isNotEmpty()) {
                        val firstLine = lines.first().replace(">", "^>").replace("<", "^<").replace("&", "^&").replace("|", "^|")
                        sb.append("echo $firstLine > \"$safePath\"\n")
                        for (i in 1 until lines.size) {
                            val line = lines[i].replace(">", "^>").replace("<", "^<").replace("&", "^&").replace("|", "^|")
                            sb.append("echo $line >> \"$safePath\"\n")
                        }
                    } else {
                        sb.append("type nul > \"$safePath\"\n")
                    }
                }
                sb.append("echo [CodeFlow] Workspace creation complete!\n")
            }

            TargetOS.LINUX_BASH, TargetOS.MACOS_ZSH -> {
                sb.append("#!/usr/bin/env bash\n")
                sb.append("# CodeFlow Studio Workspace Bootstrap Script\n")
                if (dirs.isNotEmpty()) {
                    sb.append("mkdir -p ")
                    sb.append(dirs.joinToString(" ") { "'$it'" })
                    sb.append("\n")
                }
                for (file in files) {
                    sb.append("cat << 'EOF' > '${file.relativePath}'\n")
                    sb.append(file.content)
                    if (!file.content.endsWith("\n")) sb.append("\n")
                    sb.append("EOF\n")
                }
                sb.append("echo '[CodeFlow Studio] Workspace structure successfully bootstrapped!'\n")
            }
        }

        return sb.toString()
    }

    /**
     * Generates a 1-line command sequence to paste into target terminal for instant execution.
     */
    fun generateCompactBootstrapCommand(os: TargetOS, files: List<FilePayload>): String {
        val dirs = extractDirectories(files)
        return when (os) {
            TargetOS.WINDOWS_POWERSHELL -> {
                val dirCmd = if (dirs.isNotEmpty()) "New-Item -ItemType Directory -Force -Path " + dirs.joinToString(",") { "'$it'" } + " | Out-Null; " else ""
                val touchCmd = "New-Item -ItemType File -Force -Path " + files.joinToString(",") { "'${it.relativePath.replace('/', '\\')}'" } + " | Out-Null"
                "$dirCmd$touchCmd"
            }
            TargetOS.WINDOWS_CMD -> {
                val dirCmd = if (dirs.isNotEmpty()) dirs.joinToString(" && ") { "mkdir \"${it.replace('/', '\\')}\" 2>nul" } + " && " else ""
                val touchCmd = files.joinToString(" && ") { "type nul > \"${it.relativePath.replace('/', '\\')}\"" }
                "$dirCmd$touchCmd"
            }
            TargetOS.LINUX_BASH, TargetOS.MACOS_ZSH -> {
                val dirCmd = if (dirs.isNotEmpty()) "mkdir -p " + dirs.joinToString(" ") { "'$it'" } + " && " else ""
                val touchCmd = "touch " + files.joinToString(" ") { "'${it.relativePath}'" }
                "$dirCmd$touchCmd"
            }
        }
    }
}
