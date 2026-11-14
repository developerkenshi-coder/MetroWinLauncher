package com.metrowin.launcher.datasense

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs
import java.time.LocalDate
import java.time.ZoneId

/**
 * The switches of the "options" of the app. What only looks different is on; what reads more of the phone, keeps something or uses the
 * battery is off until the user turns it on. Kept in the preferences of the app, under "opt_<key>".
 */
enum class Opt(val key: String, val default: Boolean, val label: Int) {
    Chart("chart", true, R.string.dsx_opt_chart),
    Icons("icons", true, R.string.dsx_opt_icons),
    Accent("accent", true, R.string.dsx_opt_accent),
    Compact("compact", false, R.string.dsx_opt_compact),
    Wide("wide", true, R.string.dsx_opt_wide),
    Trends("trends", true, R.string.dsx_opt_trends),
    Tools("tools", true, R.string.dsx_opt_tools),
    Cleanup("cleanup", true, R.string.dsx_opt_cleanup),
    HideLauncher("hide_launcher", true, R.string.dsx_opt_hide),
    Live("live", false, R.string.dsx_opt_live),
    History("history", false, R.string.dsx_opt_history),
    BigFiles("big_files", false, R.string.dsx_opt_big),
    Cost("plan_cost", false, R.string.dsx_opt_cost),
    Extra("extra_charts", false, R.string.dsx_opt_extra),
    Overview("overview", false, R.string.dsx_opt_overview),
}

class DsOptions(private val prefs: SharedPreferences) {
    private val state = mutableStateMapOf<Opt, Boolean>().apply { Opt.entries.forEach { put(it, read(prefs, it)) } }

    operator fun get(option: Opt): Boolean = state.getValue(option)

    fun set(option: Opt, on: Boolean) {
        state[option] = on
        prefs.edit { putBoolean("opt_${option.key}", on) }
    }

    companion object {
        fun read(prefs: SharedPreferences, option: Opt) = prefs.getBoolean("opt_${option.key}", option.default)
    }
}

val LocalDsOptions = staticCompositionLocalOf<DsOptions> { error("no options") }

/** What the app keeps of its own when the user turned "history" on: the use of each day, and the free space. Plain lines in the preferences. */
object DataHistory {
    private const val DAYS = "hist_days"
    private const val FREE = "hist_free"

    private fun prefs(context: Context) = Prefs.open(context, Prefs.DATA_SENSE)

    fun enabled(context: Context) = DsOptions.read(prefs(context), Opt.History)

    @Synchronized
    fun recordDays(context: Context, fresh: Map<Long, Pair<Long, Long>>) {
        if (!enabled(context) || fresh.isEmpty()) return
        val p = prefs(context)
        p.edit { putString(DAYS, DataMath.mergeDaily(p.getString(DAYS, "").orEmpty(), fresh)) }
    }

    fun days(context: Context): List<Triple<Long, Long, Long>> = DataMath.parseDaily(prefs(context).getString(DAYS, "").orEmpty())

    @Synchronized
    fun recordFree(context: Context, free: Long, today: Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()) {
        if (!enabled(context)) return
        val p = prefs(context)
        p.edit { putString(FREE, DataMath.mergeFree(p.getString(FREE, "").orEmpty(), today, free)) }
    }

    fun free(context: Context): List<Pair<Long, Long>> = DataMath.parseFree(prefs(context).getString(FREE, "").orEmpty())

    fun clear(context: Context) = prefs(context).edit { remove(DAYS); remove(FREE) }
}
