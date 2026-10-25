package com.example

import com.example.bluetooth.HidKeycodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HidKeycodesTest {

    @Test
    fun testLowercaseLetters() {
        val strokeA = HidKeycodes.charToKeystroke('a')
        assertNotNull(strokeA)
        assertEquals(HidKeycodes.MOD_NONE, strokeA!!.modifier)
        assertEquals(HidKeycodes.KEY_A, strokeA.keycode)

        val strokeZ = HidKeycodes.charToKeystroke('z')
        assertNotNull(strokeZ)
        assertEquals(HidKeycodes.MOD_NONE, strokeZ!!.modifier)
        assertEquals((HidKeycodes.KEY_A + 25).toByte(), strokeZ.keycode)
    }

    @Test
    fun testUppercaseLettersRequireShift() {
        val strokeA = HidKeycodes.charToKeystroke('A')
        assertNotNull(strokeA)
        assertEquals(HidKeycodes.MOD_LEFT_SHIFT, strokeA!!.modifier)
        assertEquals(HidKeycodes.KEY_A, strokeA.keycode)
    }

    @Test
    fun testSpecialKeys() {
        val enter = HidKeycodes.charToKeystroke('\n')
        assertNotNull(enter)
        assertEquals(HidKeycodes.KEY_ENTER, enter!!.keycode)

        val tab = HidKeycodes.charToKeystroke('\t')
        assertNotNull(tab)
        assertEquals(HidKeycodes.KEY_TAB, tab!!.keycode)

        val space = HidKeycodes.charToKeystroke(' ')
        assertNotNull(space)
        assertEquals(HidKeycodes.KEY_SPACE, space!!.keycode)
    }

    @Test
    fun testPunctuationAndShiftedSymbols() {
        val exclamation = HidKeycodes.charToKeystroke('!')
        assertNotNull(exclamation)
        assertEquals(HidKeycodes.MOD_LEFT_SHIFT, exclamation!!.modifier)
        assertEquals(HidKeycodes.KEY_1, exclamation.keycode)

        val brace = HidKeycodes.charToKeystroke('{')
        assertNotNull(brace)
        assertEquals(HidKeycodes.MOD_LEFT_SHIFT, brace!!.modifier)
        assertEquals(HidKeycodes.KEY_LEFT_BRACKET, brace.keycode)
    }

    @Test
    fun testReportDescriptorFormat() {
        val report = HidKeycodes.makeReport(HidKeycodes.MOD_LEFT_SHIFT, HidKeycodes.KEY_A)
        assertEquals(8, report.size)
        assertEquals(HidKeycodes.MOD_LEFT_SHIFT, report[0])
        assertEquals(0x00.toByte(), report[1])
        assertEquals(HidKeycodes.KEY_A, report[2])

        val empty = HidKeycodes.emptyReport()
        assertEquals(8, empty.size)
        assertEquals(0x00.toByte(), empty[0])
        assertEquals(0x00.toByte(), empty[2])
    }
}
