package com.metrowin.launcher.calculator

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs

/** The switches of the "options" of the calculator. What touches the clipboard is off until it is turned on; the looks and the tools are on. */
enum class CalcOption(val key: String, val default: Boolean, @StringRes val title: Int, @StringRes val hint: Int) {
    Shortcuts("opt_shortcuts", true, R.string.calc_opt_shortcuts, R.string.calc_opt_shortcuts_hint),
    Clipboard("opt_clipboard", false, R.string.calc_opt_clipboard, R.string.calc_opt_clipboard_hint),
    Look("opt_look", true, R.string.calc_opt_look, R.string.calc_opt_look_hint),
    KeysLook("opt_keys", true, R.string.calc_opt_keys, R.string.calc_opt_keys_hint),
    Tools("opt_tools", true, R.string.calc_opt_tools, R.string.calc_opt_tools_hint),
    OneHand("opt_onehand", false, R.string.calc_opt_onehand, R.string.calc_opt_onehand_hint),
    Tape("opt_tape", false, R.string.calc_opt_tape, R.string.calc_opt_tape_hint),
    LeftHand("opt_lefthand", false, R.string.calc_opt_lefthand, R.string.calc_opt_lefthand_hint),
}

/** The options and the small things the calculator remembers (the scientific keys, the favourite units, the hint seen), in its own preference file. */
class CalcOptions(context: Context) {
    private val prefs = Prefs.open(context, Prefs.CALC)
    private val values = mutableStateMapOf<CalcOption, Boolean>().apply { CalcOption.entries.forEach { put(it, prefs.getBoolean(it.key, it.default)) } }

    operator fun get(option: CalcOption): Boolean = values.getValue(option)

    operator fun set(option: CalcOption, on: Boolean) {
        values[option] = on
        prefs.edit { putBoolean(option.key, on) }
    }

    var scientific by mutableStateOf(prefs.getBoolean(KEY_SCIENTIFIC, false))
        private set

    fun keepScientific(on: Boolean) {
        scientific = on
        prefs.edit { putBoolean(KEY_SCIENTIFIC, on) }
    }

    var hintSeen by mutableStateOf(prefs.getBoolean(KEY_HINT, false))
        private set

    fun seeHint() {
        if (hintSeen) return
        hintSeen = true
        prefs.edit { putBoolean(KEY_HINT, true) }
    }

    /** The units marked as favourites, each as "kind:symbol" (money as "Currency:USD"). */
    var favorites by mutableStateOf(prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet())
        private set

    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit { putStringSet(KEY_FAVORITES, favorites) }
    }

    private companion object {
        const val KEY_SCIENTIFIC = "scientific"
        const val KEY_HINT = "hint_seen"
        const val KEY_FAVORITES = "favorite_units"
    }
}
