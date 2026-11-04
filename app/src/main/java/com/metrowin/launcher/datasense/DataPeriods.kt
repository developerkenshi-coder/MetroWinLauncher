package com.metrowin.launcher.datasense

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The periods the data page can look at, and how each is cut in the bars of its chart. Pure, so it is tested. */
enum class DataPeriod { Today, Week, Month30, ThisMonth }

/** One bar of the chart: its label and the span of time it covers (epoch milliseconds, [to] exclusive). */
data class DataBucket(val label: String, val from: Long, val to: Long)

object DataPeriods {
    /** The moment the period starts at, counted back from [now]. */
    fun start(period: DataPeriod, now: ZonedDateTime): ZonedDateTime = when (period) {
        DataPeriod.Today -> now.toLocalDate().atStartOfDay(now.zone)
        DataPeriod.Week -> now.toLocalDate().minusDays(6).atStartOfDay(now.zone)
        DataPeriod.Month30 -> now.toLocalDate().minusDays(29).atStartOfDay(now.zone)
        DataPeriod.ThisMonth -> now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.zone)
    }

    /** The bars: one for each hour of today up to now, and one for each day of the other periods (today included). */
    fun buckets(period: DataPeriod, now: ZonedDateTime): List<DataBucket> {
        val start = start(period, now)
        if (period == DataPeriod.Today) {
            return (0..now.hour).map { hour ->
                val from = start.plusHours(hour.toLong())
                DataBucket(hour.toString(), from.toInstant().toEpochMilli(), minOf(from.plusHours(1).toInstant().toEpochMilli(), now.toInstant().toEpochMilli()))
            }
        }
        val today = now.toLocalDate()
        val days = generateSequence(start.toLocalDate()) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        return days.map { day -> dayBucket(day, now.zone, now) }
    }

    private fun dayBucket(day: LocalDate, zone: ZoneId, now: ZonedDateTime): DataBucket {
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now.toInstant().toEpochMilli())
        return DataBucket(day.dayOfMonth.toString(), from, to)
    }

    /** The share of [part] in [whole] as a fraction from 0 to 1 (0 when there is nothing to share). */
    fun share(part: Long, whole: Long): Float = if (whole <= 0L) 0f else (part.toDouble() / whole).toFloat().coerceIn(0f, 1f)
}
