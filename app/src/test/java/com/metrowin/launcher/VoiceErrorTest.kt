package com.metrowin.launcher

import android.speech.SpeechRecognizer
import com.metrowin.launcher.data.VoiceError
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceErrorTest {
    @Test
    fun silenceAndNoMatchMeanNothingWasCaught() {
        assertEquals(VoiceError.NoMatch, VoiceError.fromCode(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(VoiceError.NoMatch, VoiceError.fromCode(SpeechRecognizer.ERROR_NO_MATCH))
    }

    @Test
    fun aMissingPermissionIsToldApart() {
        assertEquals(VoiceError.Permission, VoiceError.fromCode(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
    }

    @Test
    fun connectionAndLanguageTroublesAskForANetwork() {
        listOf(
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
            10, 11, 12, 13,
        ).forEach { assertEquals("code $it", VoiceError.Network, VoiceError.fromCode(it)) }
    }

    @Test
    fun theRestIsTreatedAsBusyAndWorthAnotherTry() {
        assertEquals(VoiceError.Busy, VoiceError.fromCode(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals(VoiceError.Busy, VoiceError.fromCode(SpeechRecognizer.ERROR_AUDIO))
        assertEquals(VoiceError.Busy, VoiceError.fromCode(SpeechRecognizer.ERROR_CLIENT))
        assertEquals(VoiceError.Busy, VoiceError.fromCode(999))
    }
}
