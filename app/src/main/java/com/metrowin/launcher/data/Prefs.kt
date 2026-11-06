package com.metrowin.launcher.data

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
    const val DIALER_NUMBERS = "metrowin_dialer_numbers"
    const val TILES = "metrowin_tiles"
    const val UPDATES = "updates"

    fun main(context: Context): SharedPreferences = open(context, MAIN)

    fun open(context: Context, name: String): SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
}
