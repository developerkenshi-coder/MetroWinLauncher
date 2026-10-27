package com.metrowin.launcher.notifications

import androidx.annotation.StringRes
import com.metrowin.launcher.R
import java.time.Instant
import java.time.ZoneId

/**
 * How long an app can be silenced from the Action Center: its notifications and banners stay out of sight until the time is up, and
 * then come back (the copies are kept meanwhile). Pure, so the times are tested.
 */
object Mute {
    enum class Option(@param:StringRes val label: Int) {
        Hour(R.string.mute_hour),
        EightHours(R.string.mute_8h),
        Morning(R.string.mute_morning),
    }

    /** The moment (epoch milliseconds) the silence of [option] ends, when it is [now] in [zone]. */
    fun until(option: Option, now: Instant, zone: ZoneId): Long = now.toEpochMilli() + when (option) {
        Option.Hour -> 60 * MINUTE
        Option.EightHours -> 8 * 60 * MINUTE
        Option.Morning -> Snooze.millis(Snooze.Option.Morning, now, zone)
    }

    private const val MINUTE = 60_000L
}
