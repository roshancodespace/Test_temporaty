package com.example.automation

import com.example.bluetooth.HidKeycodes

data class KeyBinding(
    val id: String,
    val name: String,
    val description: String,
    val modifier: Byte,
    val keycode: Byte
)

class AutomationPluginRegistry {

    // Keybindings configuration
    var toggleTerminalBinding = KeyBinding(
        id = "toggle_terminal",
        name = "Toggle Terminal",
        description = "Opens/closes target terminal in VS Code",
        modifier = HidKeycodes.MOD_LEFT_CTRL,
        keycode = HidKeycodes.KEY_GRAVE
    )

    var quickOpenFileBinding = KeyBinding(
        id = "quick_open",
        name = "Quick Open File",
        description = "Opens VS Code Quick Open Palette",
        modifier = HidKeycodes.MOD_LEFT_CTRL,
        keycode = (HidKeycodes.KEY_A + 15).toByte() // KEY_P is 0x13
    )

    var saveFileBinding = KeyBinding(
        id = "save_file",
        name = "Save File",
        description = "Saves active file buffer in editor",
        modifier = HidKeycodes.MOD_LEFT_CTRL,
        keycode = (HidKeycodes.KEY_A + 18).toByte() // KEY_S is 0x16
    )

    var newFileBinding = KeyBinding(
        id = "new_file",
        name = "New File",
        description = "Creates new file buffer",
        modifier = HidKeycodes.MOD_LEFT_CTRL,
        keycode = (HidKeycodes.KEY_A + 13).toByte() // KEY_N is 0x11
    )

    // Plugin Toggles
    var isVimPluginEnabled: Boolean = false
    var isAutoSaveAfterFileEnabled: Boolean = true
    var isTerminalReconEnabled: Boolean = true
    var isBufferFocusDelayEnabled: Boolean = true
    var bufferFocusDelayMs: Long = 800L

    fun getVimEnterInsertSequence(): String = if (isVimPluginEnabled) "i" else ""
    fun getVimExitInsertSequence(): String = if (isVimPluginEnabled) "\u001B" else "" // Escape key
    fun getVimSaveSequence(): String = if (isVimPluginEnabled) "\u001B:w\n" else ""
}
