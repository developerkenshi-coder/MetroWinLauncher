package com.metrowin.launcher

import com.metrowin.launcher.data.Diagnostics
import com.metrowin.launcher.data.logged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun describeKeepsPlaceKindAndTheStartOfTheMessage() {
        assertEquals("a.b: IllegalStateException: boom", Diagnostics.describe("a.b", IllegalStateException("boom")))
        assertEquals("a.b: SecurityException", Diagnostics.describe("a.b", SecurityException()))
        assertTrue(Diagnostics.describe("x", RuntimeException("y".repeat(500))).length < 260)
    }

    @Test
    fun loggedPassesTheResultThrough() {
        assertEquals(3, runCatching { 3 }.logged("t").getOrNull())
        assertEquals(null, runCatching { error("x") }.logged("t").getOrNull())
    }
}
