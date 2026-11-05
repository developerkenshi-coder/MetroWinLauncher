package com.metrowin.launcher

import com.metrowin.launcher.datasense.DataPeriod
import com.metrowin.launcher.datasense.DataPeriods
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DataLimitTest {
    private fun at(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 10, 0, 0, 0, ZoneId.of("UTC"))

    @Test fun cycleStartsOnTheBillingDayOfThisOrThePreviousMonth() {
        assertEquals(LocalDate.of(2026, 11, 15), DataPeriods.cycleStart(at(2026, 11, 20), 15).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 15), DataPeriods.cycleStart(at(2026, 11, 15), 15).toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 15), DataPeriods.cycleStart(at(2026, 11, 14), 15).toLocalDate())
        assertEquals(LocalDate.of(2025, 12, 28), DataPeriods.cycleStart(at(2026, 1, 5), 28).toLocalDate())
    }

    @Test fun billingDayIsKeptInsideTheShortestMonth() {
        assertEquals(LocalDate.of(2026, 2, 28), DataPeriods.cycleStart(at(2026, 3, 2), 31).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 1), DataPeriods.start(DataPeriod.ThisMonth, at(2026, 11, 20)).toLocalDate())
    }

    @Test fun alertsOnlyWhenALevelIsNewlyCrossed() {
        val limit = 1000L
        assertEquals(0, DataPeriods.alertLevel(799, limit, 0))
        assertEquals(80, DataPeriods.alertLevel(800, limit, 0))
        assertEquals(0, DataPeriods.alertLevel(900, limit, 80))
        assertEquals(100, DataPeriods.alertLevel(1000, limit, 80))
        assertEquals(100, DataPeriods.alertLevel(5000, limit, 0))
        assertEquals(0, DataPeriods.alertLevel(5000, limit, 100))
        assertEquals(0, DataPeriods.alertLevel(5000, 0, 0))
    }
}
