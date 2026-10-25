package com.metrowin.launcher.data

import android.app.AlarmManager
import android.content.Context
import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the Metro lock screen tells: the time, the next alarm, how many notifications wait, the battery. */
@Immutable
data class LockInfo(
    val nowMillis: Long,
    val nextAlarmMillis: Long? = null,
    val notifications: Int = 0,
    val battery: BatteryInfo? = null,
    /** The weather kept from the last time it was fetched, or null when there is none. */
    val weather: WeatherData? = null,
    /** The next event of the calendar (the next 24 hours), or null: none, or no permission. */
    val nextEvent: LiveEvent? = null,
)

/** Text of the lock screen, kept pure so it can be tested in any language. */
object LockText {
    /** The time of the alarm; with the weekday in front when it is not today ("ven 07:30"). */
    fun alarm(alarmMillis: Long, nowMillis: Long, zone: ZoneId, is24Hour: Boolean, locale: Locale): String {
        val alarm = Instant.ofEpochMilli(alarmMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val time = ClockFormat.time(alarm.toLocalTime(), is24Hour, locale) + ClockFormat.meridiem(alarm.toLocalTime(), is24Hour, locale).let { if (it.isEmpty()) "" else " $it" }
        return if (alarm.toLocalDate() == today) time else DateTimeFormatter.ofPattern("EEE", locale).format(alarm) + " " + time
    }

    /** "15:30" for an event of today, "ven 15:30" for another day; an event of the whole day only has the weekday (or nothing today). */
    fun event(event: LiveEvent, nowMillis: Long, zone: ZoneId, is24Hour: Boolean, locale: Locale): String {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        if (event.allDay) {
            // An all-day event is dated in UTC: its day is the same whatever the zone.
            val day = Instant.ofEpochMilli(event.startMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
            val title = event.title
            return if (day == today) title else DateTimeFormatter.ofPattern("EEE", locale).format(day) + " " + title
        }
        return event.title + " · " + alarm(event.startMillis, nowMillis, zone, is24Hour, locale)
    }

    /** Milliseconds from [nowMillis] to the start of the next minute, when the clock changes. */
    fun untilNextMinute(nowMillis: Long): Long = 60_000L - nowMillis.mod(60_000L)
}

class LockInfoSource(
    private val context: Context,
    /** Asks the calendar for the next event after the given time; a query. */
    private val findEvent: (Long) -> LiveEvent? = { now -> CalendarSearch(context).next(now, DAY_MILLIS) },
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private val weatherRepository = WeatherRepository(context)

    private var eventAskedAt = Long.MIN_VALUE
    private var event: LiveEvent? = null

    /**
     * The alarm and the battery, read now; the notification count comes from the notification hub. The weather is the one kept
     * (nothing is fetched here). The calendar is only asked with [withEvent]: a query, so not on the main thread.
     */
    fun read(nowMillis: Long = System.currentTimeMillis(), withEvent: Boolean = false): LockInfo = LockInfo(
        nowMillis = nowMillis,
        nextAlarmMillis = runCatching { alarms.nextAlarmClock?.triggerTime }.getOrNull(),
        battery = runCatching { BatteryInfo.read(context) }.getOrNull(),
        weather = runCatching { weatherRepository.cached() }.getOrNull(),
        nextEvent = if (withEvent) nextEvent(nowMillis) else null,
    )

    /**
     * The clock is read every minute, and the calendar, a query through a content provider, is not asked that often: the event
     * is kept for [EVENT_KEPT_MS], and asked again at once when it has started (the next one takes its place).
     */
    private fun nextEvent(nowMillis: Long): LiveEvent? {
        val kept = event
        val fresh = nowMillis - eventAskedAt in 0 until EVENT_KEPT_MS && (kept == null || kept.allDay || nowMillis < kept.startMillis)
        if (!fresh) {
            event = runCatching { findEvent(nowMillis) }.getOrNull()
            eventAskedAt = nowMillis
        }
        return event
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** How long the next event of the calendar is kept on the lock screen before the calendar is asked again. */
        const val EVENT_KEPT_MS = 5L * 60 * 1000
    }
}
