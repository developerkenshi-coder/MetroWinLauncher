package com.metrowin.launcher

import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.VoiceError
import com.metrowin.launcher.ui.voice.VoiceSession
import com.metrowin.launcher.ui.voice.VoiceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceSessionTest {
    private val session = VoiceSession(ApplicationProvider.getApplicationContext())

    private fun problem() = session.state as VoiceUiState.Problem

    @Test
    fun theTryAgainLoopEndsAfterThreeFailuresInARow() {
        session.fail(VoiceError.NoMatch)
        assertFalse(problem().final)
        assertEquals(VoiceError.NoMatch.message, problem().message)
        session.fail(VoiceError.NoMatch)
        assertFalse(problem().final)
        session.fail(VoiceError.NoMatch)
        assertTrue(problem().final)
        assertEquals(com.metrowin.launcher.R.string.voice_gave_up, problem().message)
    }

    @Test
    fun closingStartsTheCountAgain() {
        repeat(2) { session.fail(VoiceError.Busy) }
        session.close()
        session.fail(VoiceError.Busy)
        assertFalse(problem().final)
    }

    @Test
    fun noRecognizerAtAllIsFinalAtOnce() {
        session.fail(VoiceError.Unavailable)
        assertTrue(problem().final)
        assertEquals(VoiceError.Unavailable.message, problem().message)
    }
}
