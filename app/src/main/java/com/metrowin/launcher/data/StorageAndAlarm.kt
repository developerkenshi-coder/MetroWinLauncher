package com.metrowin.launcher.data

import android.app.AlarmManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.runtime.Immutable
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** The space left on the phone, for the tile of Storage Sense. */
@Immutable
data class StorageInfo(val freeBytes: Long, val totalBytes: Long) {
    /** How much of the phone is used, 0 to 100. */
    val usedPercent: Int
        get() = if (totalBytes <= 0) 0 else ((totalBytes - freeBytes) * 100 / totalBytes).toInt().coerceIn(0, 100)

    companion object {
        /** The reading of the main storage (the one apps are stored on); null when the system will not say. */
        fun read(): StorageInfo? = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            StorageInfo(stat.availableBlocksLong * stat.blockSizeLong, stat.blockCountLong * stat.blockSizeLong)
        }.getOrNull()

        /** Sizes the way Android's own settings write them (thousands, not 1024s): "512 MB", "7.4 GB", "46 GB", "1.2 TB". */
        fun format(bytes: Long, locale: Locale): String {
            val value = bytes.coerceAtLeast(0).toDouble()
            val (number, unit) = when {
                value >= TB -> value / TB to "TB"
                value >= GB -> value / GB to "GB"
                value >= MB -> value / MB to "MB"
                else -> value / KB to "kB"
            }
            val text = if (number >= 10 || unit == "kB") number.roundToInt().toString() else "%.1f".format(Locale.ROOT, number)
            return text.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator) + " " + unit
        }

        private const val KB = 1_000.0
        private const val MB = 1_000_000.0
        private const val GB = 1_000_000_000.0
        private const val TB = 1_000_000_000_000.0
    }
}

/** When the next alarm of the clock rings, said as a day and a time of day. Pure, so it is tested with dates. */
@Immutable
data class NextAlarm(val day: Day, val time: LocalTime) {
    sealed interface Day {
        data object Today : Day
        data object Tomorrow : Day
        data class Weekday(val dayOfWeek: DayOfWeek) : Day
    }

    companion object {
        /** The next alarm (epoch milliseconds) the clock has, or 0 when there is none. */
        fun read(context: Context): Long =
            runCatching { context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime ?: 0L }.getOrDefault(0L)

        /** [triggerMillis] as it is said from [nowMillis]: today, tomorrow or a day of the week, and the time. Null when there is no alarm. */
        fun describe(triggerMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): NextAlarm? {
            if (triggerMillis <= 0L) return null
            val at = Instant.ofEpochMilli(triggerMillis).atZone(zone)
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            val days = at.toLocalDate().toEpochDay() - today.toEpochDay()
            val day = when {
                days <= 0L -> Day.Today
                days == 1L -> Day.Tomorrow
                else -> Day.Weekday(at.dayOfWeek)
            }
            return NextAlarm(day, at.toLocalTime().withSecond(0).withNano(0))
        }

        fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone)
    }
}
