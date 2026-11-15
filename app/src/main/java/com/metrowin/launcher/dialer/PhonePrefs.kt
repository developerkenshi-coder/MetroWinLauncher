package com.metrowin.launcher.dialer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray

/**
 * The switches of the Phone app (menu "…" → options), kept in a preferences file of their own. Each is read once and kept in a Compose state, so a
 * screen that reads it redraws when it is changed. What touches money (a text on decline), the battery or a gesture is off until the user asks.
 */
class PhonePrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** A long press on a key of the keypad asks before it calls the number on it. */
    var confirmQuickDial by flag("confirm_quick_dial", true)

    /** The history has a heading for each day. */
    var dayHeaders by flag("day_headers", true)

    /** The history is drawn as a timeline: a line with a dot for each call. */
    var timeline by flag("timeline", false)

    /** A long press on a call of the history starts choosing several, to take them out together. */
    var multiSelect by flag("multi_select", false)

    /** The keys of the keypad play their tone and tick (the tone of the phone settings is not followed: it is one switch here). */
    var keyTones by flag("key_tones", false)

    /** The numbers of the quick dial, with their name, in a row over the keypad. */
    var quickRow by flag("quick_row", true)

    /** Answer and decline are a slider to drag, not two buttons to touch (nothing is answered by a touch in the pocket). */
    var swipeAnswer by flag("swipe_answer", false)

    /** A call coming in can be declined with a text message (the operator may charge it). */
    var declineMessage by flag("decline_message", false)

    /** The call screen with big buttons, and the speaker on when a call is answered. */
    var carMode by flag("car_mode", false)

    /** Bigger letters and digits, and pure black and white on the keypad and the call screen. */
    var bigText by flag("big_text", false)

    /** With two SIMs, a call from the contacts, the history or the quick dial asks which SIM to use. */
    var askSim by flag("ask_sim", true)

    /** The pages of the phone fade and slide as the finger turns them. */
    var pageFade by flag("page_fade", true)

    /** Rings spread around the picture of a caller while the phone rings. */
    var ringAnimation by flag("ring_animation", true)

    /** The button of the sound shows a list of the places the sound can go (ear, speaker, headset, Bluetooth) when there are more than two. */
    var audioPicker by flag("audio_picker", true)

    /** The texts offered when a call is declined with a message, or null while the user has not written their own (the ones of the app are offered). */
    var declineTexts: List<String>?
        get() = runCatching { JSONArray(prefs.getString(TEXTS, null) ?: return null).let { json -> List(json.length()) { json.getString(it) } } }.getOrNull()
        set(value) = prefs.edit { if (value == null) remove(TEXTS) else putString(TEXTS, JSONArray(value.take(TEXT_COUNT)).toString()) }

    /** The permissions of the phone were explained once. */
    var explained: Boolean
        get() = prefs.getBoolean("explained", false)
        set(value) = prefs.edit { putBoolean("explained", value) }

    private fun flag(key: String, default: Boolean) = Flag(prefs, key, default)

    companion object {
        const val FILE = "metrowin_phone_prefs"
        private const val TEXTS = "decline_texts"
        const val TEXT_COUNT = 3
    }
}

/** One boolean of [PhonePrefs]: a Compose state that writes itself to the preferences when it is set. */
class Flag(private val prefs: SharedPreferences, private val key: String, default: Boolean) {
    private var state by mutableStateOf(prefs.getBoolean(key, default))

    operator fun getValue(owner: Any?, property: Any?): Boolean = state

    operator fun setValue(owner: Any?, property: Any?, value: Boolean) {
        state = value
        prefs.edit { putBoolean(key, value) }
    }
}
