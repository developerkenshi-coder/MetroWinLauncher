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
    fun withoutBluetoothTheButtonSwitchesBetweenEarAndSpeaker() {
        val supported = ear or speaker
        assertEquals(speaker, CallManager.nextRoute(ear, supported))
        assertEquals(CallAudioState.ROUTE_WIRED_OR_EARPIECE, CallManager.nextRoute(speaker, supported))
    }

    @Test
    fun withBluetoothTheSoundGoesRoundEarBluetoothSpeaker() {
        val supported = ear or bt or speaker
        assertEquals(bt, CallManager.nextRoute(ear, supported))
        assertEquals(speaker, CallManager.nextRoute(bt, supported))
        assertEquals(CallAudioState.ROUTE_WIRED_OR_EARPIECE, CallManager.nextRoute(speaker, supported))
    }

    @Test
    fun aWiredHeadsetCountsAsTheEar() {
        val supported = CallAudioState.ROUTE_WIRED_HEADSET or speaker
        assertEquals(speaker, CallManager.nextRoute(CallAudioState.ROUTE_WIRED_HEADSET, supported))
    }

    @Test
    fun nothingOfferedLeavesTheRoute() {
        assertEquals(speaker, CallManager.nextRoute(speaker, 0))
    }
}
