package com.metrowin.launcher

import com.metrowin.launcher.data.ClockFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class ClockFormatTest {
    private val afternoon = LocalTime.of(14, 5)
    private val morning = LocalTime.of(9, 7)
    private val thursday = LocalDate.of(2026, 3, 5)

    @Test
    fun a24HourClockWritesTwoDigitHoursAndNoMeridiem() {
        assertEquals("14:05", ClockFormat.time(afternoon, true, Locale.ITALY))
        assertEquals("09:07", ClockFormat.time(morning, true, Locale.ITALY))
        assertEquals("", ClockFormat.meridiem(afternoon, true, Locale.ITALY))
    }

    @Test
    fun a12HourClockDropsTheLeadingZeroAndGivesTheMeridiem() {
        assertEquals("2:05", ClockFormat.time(afternoon, false, Locale.US))
        assertEquals("9:07", ClockFormat.time(morning, false, Locale.US))
        assertEquals("PM", ClockFormat.meridiem(afternoon, false, Locale.US))
        assertEquals("AM", ClockFormat.meridiem(morning, false, Locale.US))
    }

    @Test
    fun theDateHasTheWeekdayThenTheDayAndMonthInTheOrderOfTheLanguage() {
        assertEquals("giovedì 5 marzo", ClockFormat.date(thursday, Locale.ITALY))
        assertEquals("Thursday March 5", ClockFormat.date(thursday, Locale.US))
    }

    @Test
    fun theYearIsNeverShown() {
        for (locale in listOf(Locale.ITALY, Locale.US, Locale.UK, Locale.GERMANY, Locale.FRANCE, Locale.JAPAN)) {
            assertFalse("no year for $locale", ClockFormat.date(thursday, locale).contains("2026"))
        }
    }
}
