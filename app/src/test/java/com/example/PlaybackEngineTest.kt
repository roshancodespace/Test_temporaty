package com.example

import com.example.data.model.PlaybackAction
import com.example.data.model.PlaybackSession
import com.example.data.model.PlaybackSpeed
import com.example.data.model.PlaybackState
import com.example.engine.PlaybackEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testStartAndDeterministicTyping() = testScope.runTest {
        val engine = PlaybackEngine(this)
        val text = "val message = \"Hello, CodeFlow!\""
        val session = PlaybackSession(
            id = "test_1",
            name = "Test Session",
            actions = listOf(
                PlaybackAction.OpenFile("Test.kt"),
                PlaybackAction.InsertText(text)
            ),
            defaultCps = 100, // fast
            lineEndPauseMs = 0L,
            randomJitter = false
        )

        engine.start(session)
        advanceUntilIdle()

        val documents = engine.documents.value
        assertEquals(text, documents["Test.kt"])
        assertTrue(engine.state.value is PlaybackState.Completed)
    }

    @Test
    fun testPauseAndResume_noDuplicateOrLostChars() = testScope.runTest {
        val engine = PlaybackEngine(this)
        val text = "abcdefghijklmnopqrstuvwxyz"
        val session = PlaybackSession(
            id = "test_pause",
            name = "Pause Test",
            actions = listOf(
                PlaybackAction.OpenFile("Alphabet.txt"),
                PlaybackAction.InsertText(text)
            ),
            defaultCps = 10, // 100ms per char
            lineEndPauseMs = 0L,
            randomJitter = false
        )

        engine.start(session)
        // Advance time partially (e.g. 500ms = 5 chars)
        advanceTimeBy(550)
        engine.pause()

        val pausedDoc = engine.documents.value["Alphabet.txt"] ?: ""
        assertTrue("Some chars typed", pausedDoc.isNotEmpty() && pausedDoc.length < text.length)
        val pausedLength = pausedDoc.length

        // Resume and finish
        engine.resume()
        advanceUntilIdle()

        val finalDoc = engine.documents.value["Alphabet.txt"]
        assertEquals("Final text must match exactly without duplicate characters", text, finalDoc)
    }

    @Test
    fun testSeek_setsCorrectDocumentBuffer() = testScope.runTest {
        val engine = PlaybackEngine(this)
        val text = "0123456789"
        val session = PlaybackSession(
            id = "test_seek",
            name = "Seek Test",
            actions = listOf(
                PlaybackAction.OpenFile("Digits.txt"),
                PlaybackAction.InsertText(text)
            ),
            defaultCps = 20,
            lineEndPauseMs = 0L,
            randomJitter = false
        )

        engine.start(session)
        engine.seekToProgress(0.5f)

        val doc = engine.documents.value["Digits.txt"] ?: ""
        assertEquals(5, doc.length)
        assertEquals("01234", doc)
    }

    @Test
    fun testPlaybackSpeedMultiplier() {
        val engine = PlaybackEngine(testScope)
        assertEquals(PlaybackSpeed.NORMAL, engine.speed.value)
        engine.setSpeed(PlaybackSpeed.DOUBLE)
        assertEquals(PlaybackSpeed.DOUBLE, engine.speed.value)
        assertEquals(2.0f, engine.speed.value.multiplier, 0.001f)
    }
}
