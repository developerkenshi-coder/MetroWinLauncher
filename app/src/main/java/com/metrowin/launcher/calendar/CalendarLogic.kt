package com.metrowin.launcher.calendar

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/** An event of the calendar of the phone. [end] is exclusive. An all-day event is kept by Android as whole days in UTC. */
data class CalEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String = "",
    val description: String = "",
    val calendarName: String = "",
    val color: Int = 0,
    val recurring: Boolean = false,
    /** The minutes before the event at which a reminder rings; none when empty. */
    val reminders: List<Int> = emptyList(),
)

/** What the calendar does with days and events. Pure, so it is tested. */
object CalendarLogic {
    /** The reminders after a tap on [minutes]: -1 clears them all, another value is added or, when there already, taken away. */
    fun toggleReminder(current: List<Int>, minutes: Int): List<Int> =
        if (minutes < 0) emptyList() else if (minutes in current) current - minutes else (current + minutes).sorted()

    /** The length of a repeating event as Android keeps it (such an event has a duration, not an end): whole days for an all-day one. */
    fun duration(allDay: Boolean, start: Long, end: Long): String =
        if (allDay) "P${maxOf(1L, (end - start) / DAY_MS)}D" else "P${maxOf(60L, (end - start) / 1000)}S"

    /** Where the first time of a series starts after one time of it, that began at [was], was moved to [now]: the whole series moves by as much. */
    fun movedSeriesStart(seriesStart: Long, was: Long, now: Long): Long = seriesStart + (now - was)

    private const val DAY_MS = 86_400_000L

    /** The first and the last day on which [event] is, in [zone] (an all-day event is on the same dates in every zone). */
    fun days(event: CalEvent, zone: ZoneId = ZoneId.systemDefault()): ClosedRange<LocalDate> {
        val where = if (event.allDay) ZoneOffset.UTC else zone
        val first = Instant.ofEpochMilli(event.start).atZone(where).toLocalDate()
        // The end is the first moment that is not part of the event: midnight itself is the next day's.
        val last = Instant.ofEpochMilli(maxOf(event.end - 1, event.start)).atZone(where).toLocalDate()
        return first..last
    }

    fun isOn(event: CalEvent, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean = day in days(event, zone)

    /** The events on [day], the all-day ones first and then by hour. */
    fun on(events: List<CalEvent>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CalEvent> =
        events.filter { isOn(it, day, zone) }.sortedWith(compareByDescending<CalEvent> { it.allDay }.thenBy { it.start })

    /** The days that have at least one event, within [range]. */
    fun busyDays(events: List<CalEvent>, range: ClosedRange<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> {
        val busy = HashSet<LocalDate>()
        for (event in events) {
            val span = days(event, zone)
            var day = maxOf(span.start, range.start)
            val last = minOf(span.endInclusive, range.endInclusive)
            while (!day.isAfter(last)) { busy += day; day = day.plusDays(1) }
        }
        return busy
    }

    /** The weeks of [month] as rows of seven days, from the [first] day of the week, padded with the days of the months around it. */
    fun weeks(month: YearMonth, first: DayOfWeek): List<List<LocalDate>> {
        var day = month.atDay(1).with(TemporalAdjusters.previousOrSame(first))
        val rows = ArrayList<List<LocalDate>>()
        do {
            rows += List(7) { day.plusDays(it.toLong()) }
            day = day.plusDays(7)
        } while (!day.isAfter(month.atEndOfMonth()))
        return rows
    }

    /** The seven days of the week that has [day] in it, from the [first] day. */
    fun week(day: LocalDate, first: DayOfWeek): List<LocalDate> {
        val start = day.with(TemporalAdjusters.previousOrSame(first))
        return List(7) { start.plusDays(it.toLong()) }
    }

    /** The moment of the start of [day] in [zone], or in UTC for the all-day events. */
    fun startOf(day: LocalDate, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): Long =
        day.atStartOfDay(if (allDay) ZoneOffset.UTC else zone).toInstant().toEpochMilli()

    /** [start] and [end] of an event that lasts the days from [first] to [last], as Android keeps an all-day event. */
    fun allDayRange(first: LocalDate, last: LocalDate): Pair<Long, Long> =
        startOf(first, true) to startOf(maxOf(first, last).plusDays(1), true)

    /** The first moment of the hour that comes next after [now]. */
    fun nextHour(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now).atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
}
