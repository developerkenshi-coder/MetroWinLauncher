package com.metrowin.launcher

import com.metrowin.launcher.data.LockText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class LockTextTest {
    private val utc = ZoneId.of("UTC")
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 3, day, hour, minute, 0, 0, utc).toInstant().toEpochMilli()

    @Test
    fun anAlarmTodayIsJustItsTime() {
        assertEquals("07:30", LockText.alarm(at(5, 7, 30), at(5, 1), utc, true, Locale.ITALY))
        assertEquals("7:30 AM", LockText.alarm(at(5, 7, 30), at(5, 1), utc, false, Locale.US))
        assertEquals("7:30 PM", LockText.alarm(at(5, 19, 30), at(5, 1), utc, false, Locale.US))
    }

    @Test
    fun anAlarmOnAnotherDayHasTheWeekdayInFront() {
        // 6 March 2026 is a Friday.
        assertEquals("Fri 07:30", LockText.alarm(at(6, 7, 30), at(5, 22), utc, true, Locale.US))
        assertEquals("ven 07:30", LockText.alarm(at(6, 7, 30), at(5, 22), utc, true, Locale.ITALY))
    }

    @Test
    fun theDayIsJudgedInTheZoneOfThePhone() {
        val rome = ZoneId.of("Europe/Rome")
        // 23:30 in UTC is already the next day in Rome (UTC+1 in March, before the change of clocks).
        val alarm = at(5, 23, 30)
        val now = at(5, 12)
        assertEquals("00:30 is tomorrow in Rome", "Fri 00:30", LockText.alarm(alarm, now, rome, true, Locale.US))
        assertEquals("23:30", LockText.alarm(alarm, now, utc, true, Locale.US))
    }

    @Test
    fun theClockChangesAtTheNextMinute() {
        assertEquals(60_000L, LockText.untilNextMinute(0L))
        assertEquals(1L, LockText.untilNextMinute(59_999L))
        assertEquals(45_000L, LockText.untilNextMinute(at(5, 14, 5) + 15_000L))
    }
}
