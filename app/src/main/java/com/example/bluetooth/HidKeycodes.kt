package com.example.bluetooth

object HidKeycodes {

    // Modifiers (Byte 0 of Keyboard Report)
    const val MOD_NONE: Byte = 0x00
    const val MOD_LEFT_CTRL: Byte = 0x01
    const val MOD_LEFT_SHIFT: Byte = 0x02
    const val MOD_LEFT_ALT: Byte = 0x04
    const val MOD_LEFT_GUI: Byte = 0x08 // Windows key / Mac Command
    const val MOD_RIGHT_CTRL: Byte = 0x10
    const val MOD_RIGHT_SHIFT: Byte = 0x20
    const val MOD_RIGHT_ALT: Byte = 0x40
    const val MOD_RIGHT_GUI: Byte = 0x80.toByte()

    // Key codes (Usage Page 0x07)
    const val KEY_A: Byte = 0x04
    const val KEY_ENTER: Byte = 0x28
    const val KEY_ESCAPE: Byte = 0x29
    const val KEY_BACKSPACE: Byte = 0x2A
    const val KEY_TAB: Byte = 0x2B
    const val KEY_SPACE: Byte = 0x2C
    const val KEY_MINUS: Byte = 0x2D
    const val KEY_EQUAL: Byte = 0x2E
    const val KEY_LEFT_BRACKET: Byte = 0x2F
    const val KEY_RIGHT_BRACKET: Byte = 0x30
    const val KEY_BACKSLASH: Byte = 0x31
    const val KEY_SEMICOLON: Byte = 0x33
    const val KEY_QUOTE: Byte = 0x34
    const val KEY_GRAVE: Byte = 0x35
    const val KEY_COMMA: Byte = 0x36
    const val KEY_PERIOD: Byte = 0x37
    const val KEY_SLASH: Byte = 0x38

    // Numbers 1-9, 0
    const val KEY_1: Byte = 0x1E
    const val KEY_0: Byte = 0x27

    /**
     * Standard USB HID Keyboard Report Descriptor (8-byte boot keyboard report)
     */
    val KEYBOARD_REPORT_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x05.toByte(), 0x07.toByte(), //   Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(), //   Usage Minimum (224)
        0x29.toByte(), 0xE7.toByte(), //   Usage Maximum (231)
        0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), //   Report Size (1)
        0x95.toByte(), 0x08.toByte(), //   Report Count (8)
        0x81.toByte(), 0x02.toByte(), //   Input (Data, Variable, Absolute) - Modifier byte
        0x95.toByte(), 0x01.toByte(), //   Report Count (1)
        0x75.toByte(), 0x08.toByte(), //   Report Size (8)
        0x81.toByte(), 0x01.toByte(), //   Input (Constant) - Reserved byte
        0x95.toByte(), 0x05.toByte(), //   Report Count (5)
        0x75.toByte(), 0x01.toByte(), //   Report Size (1)
        0x05.toByte(), 0x08.toByte(), //   Usage Page (LEDs)
        0x19.toByte(), 0x01.toByte(), //   Usage Minimum (1)
        0x29.toByte(), 0x05.toByte(), //   Usage Maximum (5)
        0x91.toByte(), 0x02.toByte(), //   Output (Data, Variable, Absolute) - LEDs
        0x95.toByte(), 0x01.toByte(), //   Report Count (1)
        0x75.toByte(), 0x03.toByte(), //   Report Size (3)
        0x91.toByte(), 0x01.toByte(), //   Output (Constant) - LED padding
        0x95.toByte(), 0x06.toByte(), //   Report Count (6)
        0x75.toByte(), 0x08.toByte(), //   Report Size (8)
        0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(), //   Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(), //   Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(), //   Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(), //   Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(), //   Input (Data, Array) - Key arrays (6 bytes)
        0xC0.toByte()                 // End Collection
    )

    data class Keystroke(
        val modifier: Byte,
        val keycode: Byte
    )

    /**
     * Converts an ASCII character into a standard HID keystroke (modifier + keycode).
     */
    fun charToKeystroke(char: Char): Keystroke? {
        return when (char) {
            in 'a'..'z' -> Keystroke(MOD_NONE, (KEY_A + (char - 'a')).toByte())
            in 'A'..'Z' -> Keystroke(MOD_LEFT_SHIFT, (KEY_A + (char - 'A')).toByte())
            in '1'..'9' -> Keystroke(MOD_NONE, (KEY_1 + (char - '1')).toByte())
            '0' -> Keystroke(MOD_NONE, KEY_0)

            ' ' -> Keystroke(MOD_NONE, KEY_SPACE)
            '\n' -> Keystroke(MOD_NONE, KEY_ENTER)
            '\t' -> Keystroke(MOD_NONE, KEY_TAB)

            '!' -> Keystroke(MOD_LEFT_SHIFT, KEY_1)
            '@' -> Keystroke(MOD_LEFT_SHIFT, (KEY_1 + 1).toByte()) // 2
            '#' -> Keystroke(MOD_LEFT_SHIFT, (KEY_1 + 2).toByte()) // 3
            '$' -> Keystroke(MOD_LEFT_SHIFT, (KEY_1 + 3).toByte()) // 4
            '%' -> Keystroke(MOD_LEFT_SHIFT, (KEY_1 + 4).toByte()) // 5
            '^' -> Keystroke(MOD_LEFT_SHIFT, (KEY_1 + 5).toByte()) // 6
            '&' -> Keystroke(MOD_LEFT_SHIFT, (KEY_1 + 6).toByte()) // 7
            '*' -> Keystroke(MOD_LEFT_SHIFT, (KEY_1 + 7).toByte()) // 8
            '(' -> Keystroke(MOD_LEFT_SHIFT, (KEY_1 + 8).toByte()) // 9
            ')' -> Keystroke(MOD_LEFT_SHIFT, KEY_0)

            '-' -> Keystroke(MOD_NONE, KEY_MINUS)
            '_' -> Keystroke(MOD_LEFT_SHIFT, KEY_MINUS)
            '=' -> Keystroke(MOD_NONE, KEY_EQUAL)
            '+' -> Keystroke(MOD_LEFT_SHIFT, KEY_EQUAL)
            '[' -> Keystroke(MOD_NONE, KEY_LEFT_BRACKET)
            '{' -> Keystroke(MOD_LEFT_SHIFT, KEY_LEFT_BRACKET)
            ']' -> Keystroke(MOD_NONE, KEY_RIGHT_BRACKET)
            '}' -> Keystroke(MOD_LEFT_SHIFT, KEY_RIGHT_BRACKET)
            '\\' -> Keystroke(MOD_NONE, KEY_BACKSLASH)
            '|' -> Keystroke(MOD_LEFT_SHIFT, KEY_BACKSLASH)
            ';' -> Keystroke(MOD_NONE, KEY_SEMICOLON)
            ':' -> Keystroke(MOD_LEFT_SHIFT, KEY_SEMICOLON)
            '\'' -> Keystroke(MOD_NONE, KEY_QUOTE)
            '"' -> Keystroke(MOD_LEFT_SHIFT, KEY_QUOTE)
            '`' -> Keystroke(MOD_NONE, KEY_GRAVE)
            '~' -> Keystroke(MOD_LEFT_SHIFT, KEY_GRAVE)
            ',' -> Keystroke(MOD_NONE, KEY_COMMA)
            '<' -> Keystroke(MOD_LEFT_SHIFT, KEY_COMMA)
            '.' -> Keystroke(MOD_NONE, KEY_PERIOD)
            '>' -> Keystroke(MOD_LEFT_SHIFT, KEY_PERIOD)
            '/' -> Keystroke(MOD_NONE, KEY_SLASH)
            '?' -> Keystroke(MOD_LEFT_SHIFT, KEY_SLASH)

            else -> null
        }
    }

    fun emptyReport(): ByteArray {
        return ByteArray(8)
    }

    fun makeReport(modifier: Byte, keycode: Byte): ByteArray {
        val report = ByteArray(8)
        report[0] = modifier
        report[1] = 0x00 // Reserved
        report[2] = keycode
        return report
    }
}
