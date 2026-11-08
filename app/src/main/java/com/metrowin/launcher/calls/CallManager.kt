package com.metrowin.launcher.calls

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.data.LivePermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One call as the screen of a call shows it. */
@Immutable
data class CallLine(
    val id: Int,
    val number: String,
    val name: String?,
    val state: Int,
    /** When the call was answered, in wall clock time; 0 before. */
    val connectedAt: Long,
    val canHold: Boolean,
    val canMerge: Boolean,
    val inConference: Boolean,
) {
    val ringing get() = state == Call.STATE_RINGING
    val held get() = state == Call.STATE_HOLDING
    val dialing get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_PULLING_CALL

    /** Hung up, but Android has not removed it yet: nothing in the notification is about it any more. */
    val ended get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
    val title get() = name ?: number.ifEmpty { "" }
}

@Immutable
data class CallAudio(val muted: Boolean = false, val speaker: Boolean = false, val bluetooth: Boolean = false)

/** What the routing of the sound asks of the service of the call: a thin seam, so [CallManager.toggleSpeaker] can be tried with a fake. */
internal interface CallAudioHost {
    @Suppress("DEPRECATION")
    val audioState: CallAudioState?

    fun setMuted(on: Boolean)

    fun setAudioRoute(route: Int)

    @androidx.annotation.RequiresApi(34)
    fun requestCallEndpointChange(endpoint: CallEndpoint, receiver: android.os.OutcomeReceiver<Void, CallEndpointException>)
}

private class ServiceAudioHost(private val service: InCallService, private val handler: Handler) : CallAudioHost {
    @Suppress("DEPRECATION")
    override val audioState: CallAudioState? get() = service.callAudioState

    override fun setMuted(on: Boolean) = service.setMuted(on)

    override fun setAudioRoute(route: Int) = service.setAudioRoute(route)

    @androidx.annotation.RequiresApi(34)
    override fun requestCallEndpointChange(endpoint: CallEndpoint, receiver: android.os.OutcomeReceiver<Void, CallEndpointException>) =
        service.requestCallEndpointChange(endpoint, { handler.post(it) }, receiver)
}

/**
 * The calls Android hands to the launcher while it is the phone app, and what the screen of a call can do with them. The calls are
 * kept here, and not in the service, so that the screen and the notification read the same thing. Everything runs on the main thread,
 * where the service delivers.
 */
object CallManager {
    private val calls = ArrayList<Call>()
    private val ids = HashMap<Call, Int>()
    private var nextId = 1
    private var service: InCallService? = null
    private var audioHost: CallAudioHost? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _lines = MutableStateFlow<List<CallLine>>(emptyList())
    val lines: StateFlow<List<CallLine>> = _lines.asStateFlow()

    private val _audio = MutableStateFlow(CallAudio())
    val audio: StateFlow<CallAudio> = _audio.asStateFlow()

    private val watcher = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = publish()
        override fun onParentChanged(call: Call, parent: Call?) = publish()
    }

    internal fun bind(host: InCallService?) {
        service = host
        audioHost = host?.let { ServiceAudioHost(it, handler) }
        if (host == null) {
            calls.forEach { it.unregisterCallback(watcher) }
            calls.clear()
            ids.clear()
            publish()
        }
    }

    internal fun added(call: Call) {
        if (call in calls) return
        calls += call
        ids[call] = nextId++
        call.registerCallback(watcher)
        publish()
    }

    internal fun removed(call: Call) {
        call.unregisterCallback(watcher)
        calls -= call
        ids.remove(call)
        publish()
    }

    @Suppress("DEPRECATION") // the new endpoints come only with Android 14; this works on every version the launcher runs on
    internal fun audioChanged(state: CallAudioState?) {
        route = state?.route ?: 0
        routes = state?.supportedRouteMask ?: 0
        _audio.value = CallAudio(muted = state?.isMuted == true, speaker = route == CallAudioState.ROUTE_SPEAKER, bluetooth = route == CallAudioState.ROUTE_BLUETOOTH)
    }

    private var route = 0
    private var routes = 0

    /**
     * Where the speaker button sends the sound: to the speaker when it is not there, and back to where it was before when it is (the Bluetooth
     * device when one is connected, else the headset or the ear). Two states, not a tour of every device until the right one comes.
     */
    internal fun speakerTarget(current: Int, supported: Int): Int = when {
        current and CallAudioState.ROUTE_SPEAKER == 0 -> CallAudioState.ROUTE_SPEAKER
        supported and CallAudioState.ROUTE_BLUETOOTH != 0 -> CallAudioState.ROUTE_BLUETOOTH
        else -> CallAudioState.ROUTE_WIRED_OR_EARPIECE
    }

    private fun publish() {
        val context = service
        _lines.value = calls.map { call ->
            val details = call.details
            val number = details.handle?.schemeSpecificPart.orEmpty()
            CallLine(
                id = ids.getValue(call), number = number, name = nameOf(context, number, details),
                state = call.state, connectedAt = details.connectTimeMillis,
                canHold = details.can(Call.Details.CAPABILITY_HOLD),
                canMerge = details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
                inConference = call.children.isNotEmpty() || details.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            )
        }
    }

    private val names = HashMap<String, String?>()

    private fun nameOf(context: Context?, number: String, details: Call.Details): String? {
        if (android.os.Build.VERSION.SDK_INT >= 30) details.contactDisplayName?.takeIf { it.isNotBlank() }?.let { return it }
        if (context == null || number.isEmpty() || !LivePermissions.isGranted(context, LivePermissions.contacts)) return null
        return names.getOrPut(number) {
            runCatching {
                context.contentResolver.query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
                )?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull()
        }
    }

    private fun call(id: Int) = ids.entries.firstOrNull { it.value == id }?.key

    fun answer(id: Int) = call(id)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun decline(id: Int) = call(id)?.reject(false, null)
    fun hangUp(id: Int) = call(id)?.disconnect()

    fun hold(id: Int, on: Boolean) {
        val call = call(id) ?: return
        if (on) call.hold() else call.unhold()
    }

    fun mute(on: Boolean) = audioHost?.setMuted(on)

    /** Puts a stand-in for the service in place of the real one, or takes it away with null. */
    internal fun bindAudio(host: CallAudioHost?) {
        audioHost = host
    }

    /**
     * The speaker button: speaker on, or off again (see [speakerTarget]). Android 14 and later talk about "endpoints" and may no longer send the
     * old audio state, so the places are asked of the service itself when this class has not heard of them: a button that has nothing to go on
     * must still switch the speaker, not do nothing.
     */
    fun toggleSpeaker() {
        val host = audioHost ?: return
        if (android.os.Build.VERSION.SDK_INT >= 34 && endpoints.isNotEmpty() && toggleEndpoint(host)) return
        toggleLegacy(host)
    }

    @Suppress("DEPRECATION")
    private fun toggleLegacy(host: CallAudioHost) {
        val state = host.audioState
        val supported = (state?.supportedRouteMask ?: routes).takeIf { it != 0 } ?: (CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
        host.setAudioRoute(speakerTarget(state?.route ?: route, supported))
    }

    private var endpoints: List<CallEndpoint> = emptyList()
    private var endpoint: CallEndpoint? = null

    @androidx.annotation.RequiresApi(34)
    internal fun endpointsChanged(list: List<CallEndpoint>) {
        endpoints = list
    }

    @androidx.annotation.RequiresApi(34)
    internal fun endpointChanged(current: CallEndpoint) {
        endpoint = current
        _audio.value = _audio.value.copy(
            speaker = current.endpointType == CallEndpoint.TYPE_SPEAKER, bluetooth = current.endpointType == CallEndpoint.TYPE_BLUETOOTH,
        )
    }

    internal fun muteChanged(muted: Boolean) {
        _audio.value = _audio.value.copy(muted = muted)
    }

    @androidx.annotation.RequiresApi(34)
    private fun toggleEndpoint(host: CallAudioHost): Boolean {
        val speaker = endpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_SPEAKER } ?: return false
        val back = listOf(CallEndpoint.TYPE_BLUETOOTH, CallEndpoint.TYPE_WIRED_HEADSET, CallEndpoint.TYPE_EARPIECE).firstNotNullOfOrNull { type -> endpoints.firstOrNull { it.endpointType == type } }
        val next = if (endpoint?.endpointType == CallEndpoint.TYPE_SPEAKER) back ?: return false else speaker
        host.requestCallEndpointChange(
            next,
            object : android.os.OutcomeReceiver<Void, CallEndpointException> {
                override fun onResult(result: Void?) = Unit

                // The endpoints said no (or are out of date): the old way still moves the sound.
                override fun onError(error: CallEndpointException) = toggleLegacy(host)
            },
        )
        return true
    }

    /** Plays the tone of a key of the keypad into the call, for as long as a short press lasts. */
    fun tone(id: Int, key: Char) {
        val call = call(id) ?: return
        call.playDtmfTone(key)
        handler.postDelayed({ call.stopDtmfTone() }, TONE_MS)
    }

    /** Joins two calls in a conference, or the calls of a conference the operator keeps apart. False when the operator cannot. */
    fun merge(id: Int): Boolean {
        val call = call(id) ?: return false
        val other = calls.firstOrNull { it !== call && it.parent == null }
        return when {
            call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) -> call.mergeConference().let { true }
            other != null && call.details.can(Call.Details.CAPABILITY_HOLD) -> call.conference(other).let { true }
            else -> false
        }
    }

    private const val TONE_MS = 150L
}
