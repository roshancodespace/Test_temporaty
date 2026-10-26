package com.example.automation

import com.example.bluetooth.HidKeycodes

enum class TargetOS(
    val label: String,
    val defaultShell: String,
    val pathSeparator: String,
    val defaultTerminalModifier: Byte,
    val defaultTerminalKeycode: Byte
) {
    WINDOWS_POWERSHELL(
        label = "Windows (PowerShell)",
        defaultShell = "PowerShell 7 / Windows PowerShell",
        pathSeparator = "\\",
        defaultTerminalModifier = HidKeycodes.MOD_LEFT_CTRL,
        defaultTerminalKeycode = HidKeycodes.KEY_GRAVE
    ),
    WINDOWS_CMD(
        label = "Windows (Command Prompt)",
        defaultShell = "cmd.exe",
        pathSeparator = "\\",
        defaultTerminalModifier = HidKeycodes.MOD_LEFT_CTRL,
        defaultTerminalKeycode = HidKeycodes.KEY_GRAVE
    ),
    LINUX_BASH(
        label = "Linux (Bash / Zsh)",
        defaultShell = "bash / zsh",
        pathSeparator = "/",
        defaultTerminalModifier = HidKeycodes.MOD_LEFT_CTRL,
        defaultTerminalKeycode = HidKeycodes.KEY_GRAVE
    ),
    MACOS_ZSH(
        label = "macOS (Zsh)",
        defaultShell = "zsh",
        pathSeparator = "/",
        defaultTerminalModifier = HidKeycodes.MOD_LEFT_CTRL,
        defaultTerminalKeycode = HidKeycodes.KEY_GRAVE
    );

    fun getMkdirCommand(directories: List<String>): String {
        if (directories.isEmpty()) return ""
        return when (this) {
            WINDOWS_POWERSHELL -> {
                val dirs = directories.joinToString(",") { "'$it'" }
                "New-Item -ItemType Directory -Force -Path $dirs | Out-Null"
            }
            WINDOWS_CMD -> {
                directories.joinToString(" && ") { "mkdir \"$it\" 2>nul" }
            }
            LINUX_BASH, MACOS_ZSH -> {
                "mkdir -p " + directories.joinToString(" ") { "\"$it\"" }
            }
        }
    }

    fun getTouchFilesCommand(filePaths: List<String>): String {
        if (filePaths.isEmpty()) return ""
        return when (this) {
            WINDOWS_POWERSHELL -> {
                val files = filePaths.joinToString(",") { "'$it'" }
                "New-Item -ItemType File -Force -Path $files | Out-Null"
            }
            WINDOWS_CMD -> {
                filePaths.joinToString(" && ") { "type nul > \"$it\"" }
            }
            LINUX_BASH, MACOS_ZSH -> {
                "touch " + filePaths.joinToString(" ") { "\"$it\"" }
            }
        }
    }

    fun getOpenFileInVsCodeCommand(relativePath: String): String {
        val safePath = relativePath.replace('/', if (pathSeparator == "\\") '\\' else '/')
        return "code -r \"$safePath\""
    }

    fun getReconnaissanceCommand(): String {
        return when (this) {
            WINDOWS_POWERSHELL -> "Write-Host 'OS:PowerShell' (Get-Location).Path"
            WINDOWS_CMD -> "echo OS:CMD %CD%"
            LINUX_BASH, MACOS_ZSH -> "echo \"OS:UNIX $(pwd)\""
        }
    }
}
