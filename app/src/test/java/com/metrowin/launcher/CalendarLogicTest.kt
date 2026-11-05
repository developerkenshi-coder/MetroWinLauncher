package com.metrowin.launcher

import com.metrowin.launcher.calendar.CalEvent
import com.metrowin.launcher.calendar.CalendarLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarLogicTest {
    private val rome = ZoneId.of("Europe/Rome")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(rome).toInstant().toEpochMilli()
    private fun timed(start: Long, end: Long) = CalEvent(1, 1, "t", start, end, false)

    @Test
    fun `an event is on the days it covers and not on the day it ends at midnight`() {
        val e = timed(at(2026, 11, 1, 22), at(2026, 11, 2, 2))
        assertEquals(LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 2), CalendarLogic.days(e, rome))
        val ends = timed(at(2026, 11, 1, 22), at(2026, 11, 2, 0))
        assertEquals(LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 1), CalendarLogic.days(ends, rome))
        assertFalse(CalendarLogic.isOn(ends, LocalDate.of(2026, 11, 2), rome))
    }

    @Test
    fun `an all-day event keeps its dates in every zone`() {
        val (s, e) = CalendarLogic.allDayRange(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6))
        val event = CalEvent(2, 1, "x", s, e, true)
        for (zone in listOf("Pacific/Auckland", "America/Los_Angeles", "Europe/Rome")) {
            assertEquals(LocalDate.of(2026, 11, 5)..LocalDate.of(2026, 11, 6), CalendarLogic.days(event, ZoneId.of(zone)))
        }
        assertEquals(LocalDate.of(2026, 11, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), s)
    }

    @Test
    fun `a day lists the all-day events first and then by hour`() {
        val a = timed(at(2026, 11, 1, 15), at(2026, 11, 1, 16)).copy(id = 1)
        val b = timed(at(2026, 11, 1, 9), at(2026, 11, 1, 10)).copy(id = 2)
        val (s, e) = CalendarLogic.allDayRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1))
        val c = CalEvent(3, 1, "all", s, e, true)
        assertEquals(listOf(3L, 2L, 1L), CalendarLogic.on(listOf(a, b, c), LocalDate.of(2026, 11, 1), rome).map { it.id })
    }

    @Test
    fun `busy days are those with an event inside the range`() {
        val e = timed(at(2026, 10, 30, 22), at(2026, 11, 2, 1))
        val busy = CalendarLogic.busyDays(listOf(e), LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 30), rome)
        assertEquals(setOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)), busy)
    }

    @Test
    fun `a month is rows of seven days from the first day of the week`() {
        val weeks = CalendarLogic.weeks(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(LocalDate.of(2026, 10, 26), weeks.first().first())
        assertEquals(LocalDate.of(2026, 11, 30), weeks.last()[0])
        assertEquals(6, weeks.size)
        val sunday = CalendarLogic.weeks(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sunday.first().first())
        // February 2027 starts on a Monday and ends on a Sunday: four rows with a Monday start.
        assertEquals(4, CalendarLogic.weeks(YearMonth.of(2027, 2), DayOfWeek.MONDAY).size)
        assertEquals(6, CalendarLogic.weeks(YearMonth.of(2026, 8), DayOfWeek.MONDAY).size)
    }

    @Test
    fun `a week and the next full hour`() {
        assertEquals(LocalDate.of(2026, 11, 2), CalendarLogic.week(LocalDate.of(2026, 11, 5), DayOfWeek.MONDAY).first())
        assertEquals(at(2026, 11, 1, 13), CalendarLogic.nextHour(at(2026, 11, 1, 12, 30), rome))
        assertEquals(at(2026, 11, 1, 13), CalendarLogic.nextHour(at(2026, 11, 1, 12, 0), rome))
    }
}

class CalendarSeriesTest {
    @org.junit.Test fun remindersToggleAndClear() {
        org.junit.Assert.assertEquals(listOf(10, 60), CalendarLogic.toggleReminder(listOf(60), 10))
        org.junit.Assert.assertEquals(listOf(60), CalendarLogic.toggleReminder(listOf(10, 60), 10))
        org.junit.Assert.assertEquals(emptyList<Int>(), CalendarLogic.toggleReminder(listOf(10, 60), -1))
    }

    @org.junit.Test fun repeatingEventHasALengthNotAnEnd() {
        org.junit.Assert.assertEquals("P3600S", CalendarLogic.duration(false, 0L, 3_600_000L))
        org.junit.Assert.assertEquals("P2D", CalendarLogic.duration(true, 0L, 2 * 86_400_000L))
        org.junit.Assert.assertEquals("P1D", CalendarLogic.duration(true, 0L, 0L))
    }

    @org.junit.Test fun seriesMovesByAsMuchAsTheOpenedTime() {
        org.junit.Assert.assertEquals(1_000L + 500L, CalendarLogic.movedSeriesStart(1_000L, 9_000L, 9_500L))
        org.junit.Assert.assertEquals(1_000L, CalendarLogic.movedSeriesStart(1_000L, 9_000L, 9_000L))
    }
}
