package com.metrowin.launcher

import android.telecom.CallAudioState
import com.metrowin.launcher.calls.CallManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallRouteTest {
    private val ear = CallAudioState.ROUTE_EARPIECE
    private val bt = CallAudioState.ROUTE_BLUETOOTH
    private val speaker = CallAudioState.ROUTE_SPEAKER

    @Test
    fun theSpeakerButtonSwitchesTheSpeakerOnFromAnywhere() {
        // From the ear, and from Bluetooth with the ear and the speaker on offer too: always straight to the speaker.
        assertEquals(speaker, CallManager.speakerTarget(ear, ear or speaker))
        assertEquals(speaker, CallManager.speakerTarget(bt, ear or bt or speaker))
        assertEquals(speaker, CallManager.speakerTarget(CallAudioState.ROUTE_WIRED_HEADSET, CallAudioState.ROUTE_WIRED_HEADSET or speaker))
    }

    @Test
    fun andOffAgainGoesBackToBluetoothIfThereIsOneElseTheEar() {
        assertEquals(CallAudioState.ROUTE_WIRED_OR_EARPIECE, CallManager.speakerTarget(speaker, ear or speaker))
        assertEquals(bt, CallManager.speakerTarget(speaker, ear or bt or speaker))
    }

    @Test
    fun nothingOfferedStillSwitchesTheSpeakerOn() {
        assertEquals(speaker, CallManager.speakerTarget(0, 0))
    }
}
