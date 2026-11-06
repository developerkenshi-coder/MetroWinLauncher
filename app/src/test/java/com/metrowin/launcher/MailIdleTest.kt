package com.metrowin.launcher

import com.metrowin.launcher.mail.MailIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailIdleTest {
    @Test fun runsOnlyWhenOnAndThereIsAnAccount() {
        assertTrue(MailIdle.shouldRun(true, 1))
        assertFalse(MailIdle.shouldRun(true, 0))
        assertFalse(MailIdle.shouldRun(false, 3))
    }

    @Test fun waitDoublesFromFiveSecondsAndStopsAtFifteenMinutes() {
        assertEquals(listOf(5_000L, 10_000L, 20_000L), (0..2).map(MailIdle::backoffMs))
        assertEquals(900_000L, MailIdle.backoffMs(8))
        assertEquals(900_000L, MailIdle.backoffMs(1_000))
        assertEquals(5_000L, MailIdle.backoffMs(-3))
    }

    @Test fun accountsAreStartedAndStoppedOneByOne() {
        assertEquals(setOf("a") to setOf("c"), MailIdle.plan(setOf("a", "b"), setOf("b", "c")))
        assertEquals(emptySet<String>() to emptySet<String>(), MailIdle.plan(setOf("a"), setOf("a")))
    }

    @Test fun renewalIsBeforeTheServersThirtyMinutes() {
        assertTrue(MailIdle.RENEW_MS < 30 * 60_000L)
    }
}
