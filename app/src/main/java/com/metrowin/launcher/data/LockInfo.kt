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

    /** Milliseconds from [nowMillis] to the start of the next minute, when the clock changes. */
    fun untilNextMinute(nowMillis: Long): Long = 60_000L - nowMillis.mod(60_000L)
}

class LockInfoSource(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** The alarm and the battery, read now; the notification count comes from the notification hub. */
    fun read(nowMillis: Long = System.currentTimeMillis()): LockInfo = LockInfo(
        nowMillis = nowMillis,
        nextAlarmMillis = runCatching { alarms.nextAlarmClock?.triggerTime }.getOrNull(),
        battery = runCatching { BatteryInfo.read(context) }.getOrNull(),
    )
}
