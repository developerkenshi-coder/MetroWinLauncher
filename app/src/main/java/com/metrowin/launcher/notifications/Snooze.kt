package com.metrowin.launcher.notifications

import androidx.annotation.StringRes
import com.metrowin.launcher.R
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** How long a notification can be put away for: it comes back by itself. Pure, so the times are tested. */
object Snooze {
    enum class Option(@param:StringRes val label: Int) {
        QuarterHour(R.string.snooze_15),
        Hour(R.string.snooze_hour),
        ThreeHours(R.string.snooze_3h),
        Morning(R.string.snooze_morning),
    }

    /** The hour of the morning the last choice waits for. */
    val MORNING: LocalTime = LocalTime.of(8, 0)

    /** For how many milliseconds to snooze with [option], when it is [now] in [zone]. */
    fun millis(option: Option, now: Instant, zone: ZoneId): Long = when (option) {
        Option.QuarterHour -> 15 * MINUTE
        Option.Hour -> 60 * MINUTE
        Option.ThreeHours -> 180 * MINUTE
        Option.Morning -> {
            val local = now.atZone(zone)
            // The next 8:00: today when it has not come yet, tomorrow otherwise.
            val today = local.toLocalDate().atTime(MORNING).atZone(zone)
            val next = if (today.toInstant().isAfter(now)) today else today.plusDays(1)
            next.toInstant().toEpochMilli() - now.toEpochMilli()
        }
    }

    private const val MINUTE = 60_000L
}
