package com.metrowin.launcher

import android.os.OutcomeReceiver
import android.os.ParcelUuid
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import com.metrowin.launcher.calls.CallAudioHost
import com.metrowin.launcher.calls.CallManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallSpeakerToggleTest {
    private class Fake(override var audioState: CallAudioState? = null, var refuse: Boolean = false) : CallAudioHost {
        val routes = mutableListOf<Int>()
        val requested = mutableListOf<Int>()
        var muted: Boolean? = null

        override fun setMuted(on: Boolean) { muted = on }

        override fun setAudioRoute(route: Int) { routes += route }

        override fun requestCallEndpointChange(endpoint: CallEndpoint, receiver: OutcomeReceiver<Void, CallEndpointException>) {
            requested += endpoint.endpointType
            if (refuse) receiver.onError(CallEndpointException("no", CallEndpointException.ERROR_UNSPECIFIED)) else receiver.onResult(null)
        }
    }

    private fun endpoint(type: Int) = CallEndpoint("e$type", type, ParcelUuid(UUID.randomUUID()))

    @After
    fun unbind() {
        CallManager.bindAudio(null)
        CallManager.endpointsChanged(emptyList())
    }

    @Test
    fun withoutEndpointsTheOldRouteIsSet() {
        val host = Fake(CallAudioState(false, CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER))
        CallManager.bindAudio(host)
        CallManager.toggleSpeaker()
        assertEquals(listOf(CallAudioState.ROUTE_SPEAKER), host.routes)
    }

    @Test
    fun endpointsMoveTheSoundToTheSpeakerAndBackToBluetooth() {
        val host = Fake()
        CallManager.bindAudio(host)
        val ear = endpoint(CallEndpoint.TYPE_EARPIECE)
        val speaker = endpoint(CallEndpoint.TYPE_SPEAKER)
        val bluetooth = endpoint(CallEndpoint.TYPE_BLUETOOTH)
        CallManager.endpointsChanged(listOf(ear, speaker, bluetooth))
        CallManager.endpointChanged(ear)
        CallManager.toggleSpeaker()
        CallManager.endpointChanged(speaker)
        CallManager.toggleSpeaker()
        assertEquals(listOf(CallEndpoint.TYPE_SPEAKER, CallEndpoint.TYPE_BLUETOOTH), host.requested)
        assertEquals(emptyList<Int>(), host.routes)
    }

    @Test
    fun anEndpointThatRefusesFallsBackToTheOldRoute() {
        val host = Fake(refuse = true)
        CallManager.bindAudio(host)
        val ear = endpoint(CallEndpoint.TYPE_EARPIECE)
        CallManager.endpointsChanged(listOf(ear, endpoint(CallEndpoint.TYPE_SPEAKER)))
        CallManager.endpointChanged(ear)
        CallManager.toggleSpeaker()
        assertEquals(listOf(CallAudioState.ROUTE_SPEAKER), host.routes)
    }

    @Test
    fun muteGoesToTheHost() {
        val host = Fake()
        CallManager.bindAudio(host)
        CallManager.mute(true)
        assertEquals(true, host.muted)
    }
}
