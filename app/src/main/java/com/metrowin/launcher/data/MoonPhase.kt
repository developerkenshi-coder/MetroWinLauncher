package com.metrowin.launcher.data

import androidx.annotation.StringRes
import com.metrowin.launcher.R
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos

/** The phase of the moon on a day, worked out from the length of the lunar month: no data, no network. Pure. */
object MoonPhase {
    enum class Name(@param:StringRes val label: Int) {
        New(R.string.moon_new), WaxingCrescent(R.string.moon_waxing_crescent), FirstQuarter(R.string.moon_first_quarter),
        WaxingGibbous(R.string.moon_waxing_gibbous), Full(R.string.moon_full), WaningGibbous(R.string.moon_waning_gibbous),
        LastQuarter(R.string.moon_last_quarter), WaningCrescent(R.string.moon_waning_crescent),
    }

    /** The age of the moon in days (0 at the new moon, up to the length of the month). */
    fun age(date: LocalDate): Double {
        // A new moon at noon (UT) on 6 January 2000; the mean month is 29.530588853 days.
        val days = date.toEpochDay() - REFERENCE_NEW_MOON_EPOCH_DAY
        return ((days % SYNODIC + SYNODIC) % SYNODIC)
    }

    /** How much of the disc is lit, 0 to 1. */
    fun illumination(date: LocalDate): Double = (1 - cos(2 * PI * age(date) / SYNODIC)) / 2

    /** The moon is growing (from new to full) rather than shrinking. */
    fun waxing(date: LocalDate): Boolean = age(date) < SYNODIC / 2

    fun name(date: LocalDate): Name {
        // Eight equal parts of the month, the quarters and the full and new moons in the middle of theirs.
        val part = ((age(date) / SYNODIC * 8) + 0.5).toInt() % 8
        return Name.entries[part]
    }

    private const val SYNODIC = 29.530588853
    private const val REFERENCE_NEW_MOON_EPOCH_DAY = 10_962L // 2000-01-06
}
