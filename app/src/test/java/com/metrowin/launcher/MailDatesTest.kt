package com.metrowin.launcher

import com.metrowin.launcher.mail.MailDay
import com.metrowin.launcher.mail.MailDates
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MailDatesTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun todayYesterdayWeekdayAndDate() {
        val now = at(13, 18)
        assertEquals(MailDay.Today, MailDates.day(at(13, 1), now, zone))
        assertEquals(MailDay.Yesterday, MailDates.day(at(12, 23), now, zone))
        assertEquals(MailDay.Weekday, MailDates.day(at(9, 12), now, zone))
        assertEquals(MailDay.Date, MailDates.day(at(2, 12), now, zone))
    }
}
