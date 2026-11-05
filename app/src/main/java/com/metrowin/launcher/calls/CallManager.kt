package com.metrowin.launcher.calls

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
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

    /** The route after [current] among those the phone offers now (ear or wired headset, Bluetooth, speaker), going round; [current] unknown gives the first. */
    internal fun nextRoute(current: Int, supported: Int): Int {
        val order = listOf(CallAudioState.ROUTE_WIRED_OR_EARPIECE, CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_SPEAKER).filter { supported and it != 0 }
        if (order.isEmpty()) return current
        val at = order.indexOfFirst { current and it != 0 }
        return order[(at + 1) % order.size]
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

    fun mute(on: Boolean) = service?.setMuted(on)

    /** Moves the sound to the next place: ear or headset, Bluetooth when there is one, speaker. With only ear and speaker it is the old switch. */
    @Suppress("DEPRECATION")
    fun cycleRoute() = service?.setAudioRoute(nextRoute(route, routes))

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
