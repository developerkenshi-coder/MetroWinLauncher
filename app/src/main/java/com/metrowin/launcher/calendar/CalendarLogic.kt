package com.metrowin.launcher.calendar

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** The fixed numbers of the calendar, in one place. */
object CalendarDefaults {
    /** A new event lasts one hour and, on a day other than today, starts at nine. */
    const val DURATION_MS = 3_600_000L
    const val START_HOUR = 9
    /** How many days from today the agenda looks ahead. */
    const val AGENDA_DAYS = 120
    /** The reminders offered as buttons, in minutes before the event (-1 is "none"); a custom one is typed in. */
    val REMINDERS = listOf(10, 30, 60, 1440)
    /** The most events read at once: more than this are left out and the screen says so. */
    const val MAX_EVENTS = 3_000
    /** The shortest event the form accepts. */
    const val MIN_EVENT_MS = 60_000L
    /** How many minutes before an event can a custom reminder be (four weeks). */
    const val MAX_REMINDER_MIN = 40_320
}

/** How a new event repeats. */
enum class Repeat(val rule: String?) {
    None(null), Daily("FREQ=DAILY"), Weekly("FREQ=WEEKLY"), Monthly("FREQ=MONTHLY"), Yearly("FREQ=YEARLY"),
}

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
    /** True when the calendar does not accept changes (a subscribed or shared one), or the entry is not an event (a birthday). */
    val readOnly: Boolean = false,
    /** [android.provider.CalendarContract.Events.STATUS]: 2 is cancelled. */
    val status: Int = 1,
    /** [android.provider.CalendarContract.Attendees]'s status of the user: 1 accepted, 2 declined, 4 maybe, 0 or 3 no answer. */
    val selfStatus: Int = 0,
    /** The time zone the event was written in, blank for an all-day one. */
    val timeZone: String = "",
    /** The colour set on this event alone, 0 when it takes that of its calendar. */
    val ownColor: Int = 0,
    val birthday: Boolean = false,
    val rule: String = "",
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

    /** The events of [range] grouped by the days they are on (an event of several days is under each), each day in the order of [on]. One pass over the events. */
    fun byDay(events: List<CalEvent>, range: ClosedRange<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<CalEvent>> {
        val map = HashMap<LocalDate, ArrayList<CalEvent>>()
        for (event in events) {
            val span = days(event, zone)
            var day = maxOf(span.start, range.start)
            val last = minOf(span.endInclusive, range.endInclusive)
            while (!day.isAfter(last)) { map.getOrPut(day) { ArrayList() } += event; day = day.plusDays(1) }
        }
        map.values.forEach { it.sortWith(compareByDescending<CalEvent> { e -> e.allDay }.thenBy { e -> e.start }) }
        return map
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

    // ---- the day on the wall clock ----

    /** The minute of [day] (from midnight, as a clock on the wall says) at which [instant] is: 0 before the day and 1440 after it. A day with a change of summer time has an hour less or more, and the clock follows. */
    fun wallMinutes(instant: Long, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
        val at = Instant.ofEpochMilli(instant).atZone(zone)
        val date = at.toLocalDate()
        return when {
            date.isBefore(day) -> 0
            date.isAfter(day) -> DayLayout.MINUTES_IN_DAY
            else -> at.hour * 60 + at.minute
        }
    }

    /** The moment of [minute] (wall clock) of [day]: in the hour that a change to summer time skips, the moment just after it. */
    fun timeAt(day: LocalDate, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.of(day.atStartOfDay().plusMinutes(minute.toLong()), zone).toInstant().toEpochMilli()

    // ---- reading the events ----

    /** The number of the week that has [day] in it, as the [locale] counts them. */
    fun weekNumber(day: LocalDate, locale: Locale = Locale.getDefault()): Int = day.get(WeekFields.of(locale).weekOfWeekBasedYear())

    /** What the screens show: no cancelled event, none of the [hidden] calendars, and the ones the user declined only when [showDeclined]. */
    fun visible(events: List<CalEvent>, hidden: Set<Long> = emptySet(), showDeclined: Boolean = true): List<CalEvent> =
        events.filter { it.status != 2 && it.calendarId !in hidden && (showDeclined || it.selfStatus != 2) }

    /** How many minutes of [day] are taken by timed events (an all-day one counts as a whole day), for the heat of a month. */
    fun busyMinutes(events: List<CalEvent>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
        var total = 0
        for (e in events) {
            if (!isOn(e, day, zone)) continue
            total += if (e.allDay) DayLayout.MINUTES_IN_DAY else (wallMinutes(e.end, day, zone) - wallMinutes(e.start, day, zone)).coerceAtLeast(0)
        }
        return total.coerceAtMost(DayLayout.MINUTES_IN_DAY)
    }

    /** The free stretches of [day] between the minutes [from] and [to] that last at least [minLength] minutes, as (start, end) minutes. */
    fun freeSlots(events: List<CalEvent>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault(), from: Int = 8 * 60, to: Int = 20 * 60, minLength: Int = 30): List<Pair<Int, Int>> {
        val taken = events.filter { !it.allDay && isOn(it, day, zone) }
            .map { wallMinutes(it.start, day, zone) to wallMinutes(it.end, day, zone) }.sortedBy { it.first }
        val out = ArrayList<Pair<Int, Int>>()
        var cursor = from
        for ((s, e) in taken) {
            if (s - cursor >= minLength && s <= to) out += cursor to minOf(s, to)
            cursor = maxOf(cursor, e)
        }
        if (to - cursor >= minLength) out += cursor to to
        return out
    }

    /** The link to a video call in the text of an event (place or notes), or null. Only https links of the known services. */
    fun conferenceLink(vararg texts: String): String? {
        for (t in texts) CONFERENCE.find(t)?.let { return it.value.trimEnd('.', ',', ')', '>') }
        return null
    }

    private val CONFERENCE = Regex(
        "https://(?:meet\\.google\\.com/[a-z0-9\\-?=&_]+|teams\\.microsoft\\.com/l/meetup-join/[^\\s<>\"]+|teams\\.live\\.com/meet/[^\\s<>\"]+|(?:[a-z0-9\\-]+\\.)?zoom\\.us/(?:j|my)/[^\\s<>\"]+|[a-z0-9\\-]+\\.webex\\.com/[^\\s<>\"]+)",
        RegexOption.IGNORE_CASE,
    )

    /** Whether [event] has the words of [query] (all of them, in any order, in title, place or notes). */
    fun matches(event: CalEvent, query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = (event.title + " " + event.location + " " + event.description).lowercase()
        return words.all { it in hay }
    }
}
