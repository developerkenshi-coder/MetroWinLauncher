package com.metrowin.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

/**
 * The one place that opens the preference files of the launcher. The names are the ones the files already have on the phones:
 * changing one would lose what the user saved in it.
 */
internal object Prefs {
    /** The file most of the launcher shares: settings, Start layout, shortcuts, reminders, saved layouts, rates, weather... */
    const val MAIN = "metrowin"
    const val SYSTEM_WALLPAPER = "metrowin_syswall"
    const val DATA_SENSE = "metrowin_datasense"
    const val SMS_DRAFTS = "metrowin_sms_drafts"
    const val CALC = "metrowin_calc"
    const val MAIL_SECURE = "metrowin_mail_secure"
    const val MAIL_SYNC = "metrowin_mail_sync"
    const val TODO = "metrowin_todo"
    const val NEWS = "metrowin_news"
    const val FILES = "metrowin_files"
    const val SHIZUKU = "metrowin_shizuku"
    const val DIALER_NUMBERS = "metrowin_dialer_numbers"
    const val TILES = "metrowin_tiles"
    const val UPDATES = "updates"

    fun main(context: Context): SharedPreferences = open(context, MAIN)

    fun open(context: Context, name: String): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
}

/**
 * The one place the preference files are brought to the schema the code expects. [steps]`[n]` moves them from schema n to n + 1; the
 * number reached is kept in the main file. A step must be safe to run twice (it can be cut short by the process dying), and the names
 * in [Prefs] never change. No step is needed yet: schema 0 is the files as they are.
 */
internal object PrefsMigrations {
    const val VERSION_KEY = "schema_version"

    val steps: List<(Context) -> Unit> = emptyList()

    // commit, not apply: the number must be on disk before the next step runs, or a killed process repeats a step that is not safe twice.
    @SuppressLint("ApplySharedPref")
    fun run(context: Context, steps: List<(Context) -> Unit> = this.steps) {
        val main = Prefs.main(context)
        var version = main.getInt(VERSION_KEY, 0)
        if (version >= steps.size) return
        while (version < steps.size) {
            steps[version](context)
            version++
            main.edit().putInt(VERSION_KEY, version).commit()
        }
    }
}
