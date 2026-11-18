package com.metrowin.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Process
import android.os.SystemClock

/**
 * Safe mode. A launcher that crashes at start cannot be left to crash again and again: after [LIMIT] crashes in a row, each soon after the
 * process began, the next start uses the base theme and the base Start screen and tells the user what happened. What is saved is not touched
 * (nothing is written while it is on), so leaving it brings everything back.
 */
object SafeStart {
    const val LIMIT = 2

    /** A crash this soon after the process began is a crash "at start". */
    const val QUICK_MS = 15_000L
    private const val FILE = "metrowin_safe_start"
    private const val KEY = "crashes"

    /** True for this whole run of the process; set once by [init]. */
    @Volatile
    var on = false
        private set

    /** The count after a crash that came [aliveMs] after the start: one more when it is quick, none when the launcher had run well before it. */
    fun nextCount(count: Int, aliveMs: Long): Int = if (aliveMs <= QUICK_MS) count + 1 else 0

    fun shouldBeOn(count: Int): Boolean = count >= LIMIT

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun init(context: Context) {
        on = shouldBeOn(prefs(context).getInt(KEY, 0))
    }

    /** Called while the process is dying: written at once (commit), never throws. */
    @SuppressLint("ApplySharedPref")
    fun recordCrash(context: Context) {
        runCatching {
            val alive = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
            val store = prefs(context)
            store.edit().putInt(KEY, nextCount(store.getInt(KEY, 0), alive)).commit()
        }
    }

    /** The launcher ran past the first seconds: the crashes that came before do not count. Safe mode stays until the user leaves it. */
    fun markStable(context: Context) {
        if (!on) prefs(context).edit().putInt(KEY, 0).apply()
    }

    /** The user asked for the normal start; takes effect at the next start of the process. */
    @SuppressLint("ApplySharedPref")
    fun leave(context: Context) {
        prefs(context).edit().putInt(KEY, 0).commit()
    }
}
