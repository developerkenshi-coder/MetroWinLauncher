package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.ClickSound
import com.metrowin.launcher.ui.anim.LocalTapFeedback
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The click and the vibration of a tap come from one place: every button, tile and switch asks for them when it is tapped, and
 * only then. The sound itself is made by the launcher, so it plays whatever the touch sound switch of the phone says.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class TapFeedbackTest : ScreenshotBase() {
    @Test
    fun aTapOnAnythingThatIsPressedAsksForTheFeedbackOnce() {
        var feedback = 0
        var clicks = 0
        show {
            CompositionLocalProvider(LocalTapFeedback provides { feedback++ }) {
                Box(Modifier.size(80.dp).testTag("button").metroPress(onClick = { clicks++ }))
            }
        }
        advance(100)
        compose.onNodeWithTag("button").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
        assertEquals(1, feedback)
    }

    @Test
    fun theFeedbackComesBeforeWhatTheTapDoes() {
        val order = ArrayList<String>()
        show {
            CompositionLocalProvider(LocalTapFeedback provides { order += "feedback" }) {
                Box(Modifier.size(80.dp).testTag("button").metroPress(onClick = { order += "click" }))
            }
        }
        advance(100)
        compose.onNodeWithTag("button").performClick()
        compose.waitForIdle()
        assertEquals(listOf("feedback", "click"), order)
    }

    @Test
    fun aDisabledButtonMakesNoFeedback() {
        var feedback = 0
        show {
            CompositionLocalProvider(LocalTapFeedback provides { feedback++ }) {
                Box(Modifier.size(80.dp).testTag("button").metroPress(onClick = { }, enabled = false))
            }
        }
        advance(100)
        compose.onNodeWithTag("button").performClick()
        compose.waitForIdle()
        assertEquals(0, feedback)
    }

    @Test
    fun aSwitchGivesTheFeedbackToo() {
        var feedback = 0
        var checked = false
        show {
            CompositionLocalProvider(LocalTapFeedback provides { feedback++ }) {
                MetroToggle("label", checked, { checked = it }, Modifier.testTag("switch"))
            }
        }
        advance(100)
        compose.onNodeWithTag("switch").performClick()
        compose.waitForIdle()
        assertTrue(checked)
        assertEquals(1, feedback)
    }

    @Test
    fun withNothingProvidedATapStillWorks() {
        var clicks = 0
        show { Box(Modifier.size(80.dp).testTag("button").metroPress(onClick = { clicks++ })) }
        advance(100)
        compose.onNodeWithTag("button").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
    }

    // --- the click itself

    @Test
    fun theClickIsAShortTickThatDiesAway() {
        val pcm = ClickSound.pcm()
        assertEquals(ClickSound.SAMPLE_RATE * ClickSound.MILLIS / 1000, pcm.size)
        // Heard (not silent), never at full scale (it must not startle), and nothing is left at the end (no pop).
        val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("peak $peak", peak in 2_000..Short.MAX_VALUE * 6 / 10)
        assertEquals(0, pcm.last().toInt())
        // It dies away: the last quarter is far quieter than the first.
        val head = pcm.take(pcm.size / 4).maxOf { kotlin.math.abs(it.toInt()) }
        val tail = pcm.takeLast(pcm.size / 4).maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("head $head tail $tail", tail * 8 < head)
    }

    @Test
    fun theClickIsAValidWaveFile() {
        val pcm = ClickSound.pcm()
        val wav = ClickSound.wav(pcm)
        assertEquals(44 + pcm.size * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("fmt ", String(wav, 12, 4))
        assertEquals("data", String(wav, 36, 4))
        fun int(at: Int) = (wav[at].toInt() and 0xFF) or (wav[at + 1].toInt() and 0xFF shl 8) or (wav[at + 2].toInt() and 0xFF shl 16) or (wav[at + 3].toInt() and 0xFF shl 24)
        assertEquals(wav.size - 8, int(4))
        assertEquals(ClickSound.SAMPLE_RATE, int(24))
        assertEquals(pcm.size * 2, int(40))
        // The first sample after the header is the first of the sound.
        assertEquals(pcm[1].toInt() and 0xFF, wav[46].toInt() and 0xFF)
    }
}
