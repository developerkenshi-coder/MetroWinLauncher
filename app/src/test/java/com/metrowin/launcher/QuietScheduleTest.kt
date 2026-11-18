package com.metrowin.launcher

import com.metrowin.launcher.notifications.QuietSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class QuietScheduleTest {
    @Test fun nightWindowPassesMidnight() {
        assertTrue(QuietSchedule.inside(23, 22, 7))
        assertTrue(QuietSchedule.inside(3, 22, 7))
        assertFalse(QuietSchedule.inside(7, 22, 7))
        assertFalse(QuietSchedule.inside(12, 22, 7))
    }

    @Test fun dayWindow() {
        assertTrue(QuietSchedule.inside(13, 13, 15))
        assertFalse(QuietSchedule.inside(15, 13, 15))
    }

    @Test fun sameHourIsNoWindow() = assertFalse(QuietSchedule.inside(5, 5, 5))

    @Test fun nextChangeIsTheNextBoundary() {
        assertEquals(LocalDateTime.of(2026, 11, 18, 22, 0), QuietSchedule.nextChange(LocalDateTime.of(2026, 11, 18, 15, 40), 22, 7))
        assertEquals(LocalDateTime.of(2026, 11, 19, 7, 0), QuietSchedule.nextChange(LocalDateTime.of(2026, 11, 18, 23, 10), 22, 7))
        assertEquals(LocalDateTime.of(2026, 11, 19, 7, 0), QuietSchedule.nextChange(LocalDateTime.of(2026, 11, 19, 2, 0), 22, 7))
    }
}
