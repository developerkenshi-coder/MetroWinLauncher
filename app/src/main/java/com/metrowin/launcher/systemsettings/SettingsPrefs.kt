package com.metrowin.launcher.systemsettings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.metrowin.launcher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The switches of the options of the app (menu ⋯). What touches the network, privacy or something Android keeps to itself starts off. */
internal enum class Option(val key: String, val default: Boolean, @StringRes val label: Int, @StringRes val hint: Int) {
    Looks("looks", true, R.string.ssx_opt_looks, R.string.ssx_opt_looks_hint),
    HiddenWifi("hidden_wifi", false, R.string.ssx_opt_hidden, R.string.ssx_opt_hidden_hint),
    ManualTime("manual_time", false, R.string.ssx_opt_time, R.string.ssx_opt_time_hint),
    SoundExtras("sound_extras", false, R.string.ssx_opt_sound, R.string.ssx_opt_sound_hint),
    MorePages("more_pages", false, R.string.ssx_opt_more, R.string.ssx_opt_more_hint),
    Reduced("reduced", false, R.string.ssx_opt_reduced, R.string.ssx_opt_reduced_hint),
    History("history", false, R.string.ssx_opt_history, R.string.ssx_opt_history_hint),
}

/** Own preferences of this app: nothing here is shared with the settings of the launcher. */
internal object SettingsPrefs {
    private const val FILE = "system_settings"
    private const val LOG = "log"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun on(context: Context, option: Option) = prefs(context).getBoolean(option.key, option.default)

    fun set(context: Context, option: Option, value: Boolean) = prefs(context).edit().putBoolean(option.key, value).apply()

    fun history(context: Context): List<String> = prefs(context).getString(LOG, "").orEmpty().lines().filter { it.isNotBlank() }

    fun clearHistory(context: Context) = prefs(context).edit().remove(LOG).apply()

    /** Adds what a command did to the log, when the log is on. */
    fun log(context: Context, args: Array<String>) {
        if (!on(context, Option.History)) return
        val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        val lines = SettingsLogic.appendHistory(history(context), "$stamp  ${SettingsLogic.describe(args)}")
        prefs(context).edit().putString(LOG, lines.joinToString("\n")).apply()
    }
}

/** The options as the screen sees them: changing one is shown at once and kept. */
internal class Opts(private val context: Context) {
    private val state = mutableStateMapOf<Option, Boolean>().apply { Option.entries.forEach { put(it, SettingsPrefs.on(context, it)) } }

    operator fun get(option: Option): Boolean = state.getValue(option)

    fun set(option: Option, value: Boolean) {
        state[option] = value
        SettingsPrefs.set(context, option, value)
    }
}

internal val LocalOpts = staticCompositionLocalOf<Opts> { error("the options are given by SystemSettingsScreen") }
