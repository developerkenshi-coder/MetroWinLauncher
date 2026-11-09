package com.metrowin.launcher.calendar

import java.time.LocalDate
import java.time.ZoneId

/** An event of one day placed on the hour grid: minutes from midnight, and the [column] of the [columns] it shares the time with. */
data class DayBlock(val event: CalEvent, val startMin: Int, val endMin: Int, val column: Int, val columns: Int)

/** How the events of a day sit side by side on the hour grid of the day view. Pure, so it is tested. */
object DayLayout {
    /** A block is never drawn shorter than this, nor does it count as shorter when it is placed beside another. */
    const val MIN_MINUTES = 30

    /**
     * The timed events of [day] (the all-day ones are not on the grid) as blocks. Events that overlap share a row of columns: each takes the
     * first column that is free when it starts, and every event of the group is as wide as the group has columns.
     */
    fun blocks(events: List<CalEvent>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DayBlock> {
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val timed = events.filter { !it.allDay && it.start < to && it.end > from }
            .map { e ->
                val start = ((maxOf(e.start, from) - from) / 60_000L).toInt()
                val end = ((minOf(e.end, to) - from) / 60_000L).toInt()
                Triple(e, start, maxOf(end, start + MIN_MINUTES).coerceAtMost(MINUTES_IN_DAY).let { if (it <= start) start + 1 else it })
            }
            .sortedWith(compareBy({ it.second }, { -(it.third - it.second) }))
        val out = ArrayList<DayBlock>()
        var group = ArrayList<Triple<CalEvent, Int, Int>>()
        var groupEnd = -1
        val ends = ArrayList<Int>() // the end of the last event of each column of the group
        val columnOf = ArrayList<Int>()
        fun close() {
            group.forEachIndexed { i, (e, s, en) -> out += DayBlock(e, s, en, columnOf[i], ends.size) }
            group = ArrayList(); ends.clear(); columnOf.clear(); groupEnd = -1
        }
        for (item in timed) {
            if (item.second >= groupEnd && group.isNotEmpty()) close()
            val free = ends.indexOfFirst { it <= item.second }.let { if (it < 0) ends.size else it }
            if (free == ends.size) ends += item.third else ends[free] = item.third
            group += item
            columnOf += free
            groupEnd = maxOf(groupEnd, item.third)
        }
        close()
        return out
    }

    /** The events that last the whole day (or days), for the strip above the grid. */
    fun allDay(events: List<CalEvent>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CalEvent> =
        CalendarLogic.on(events, day, zone).filter { it.allDay }

    const val MINUTES_IN_DAY = 24 * 60
}
