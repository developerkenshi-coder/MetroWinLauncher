package com.metrowin.launcher

import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.MotionTable
import com.metrowin.launcher.ui.anim.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTableTest {
    @Test
    fun classicKeepsTheTimesTheConstantsHave() {
        val page = MotionTable.timing(Surface.Page, ThemeStyle.Classic, reduced = false)
        assertEquals(Motion.TURNSTILE_IN_MS, page.inMs)
        assertEquals(Motion.TURNSTILE_OUT_MS, page.outMs)
    }

    @Test
    fun glassIsSlowerAndDroidBriskerForEverySurface() = Surface.entries.forEach { s ->
        val classic = MotionTable.timing(s, ThemeStyle.Classic, false)
        assertTrue(MotionTable.timing(s, ThemeStyle.Glass, false).inMs > classic.inMs)
        assertTrue(MotionTable.timing(s, ThemeStyle.Droid, false).inMs < classic.inMs)
    }

    @Test
    fun reducedMotionOnlyFadesAPageAndStillsTheRest() = ThemeStyle.entries.forEach { style ->
        assertEquals(120, MotionTable.timing(Surface.Page, style, true).inMs)
        assertEquals(0, MotionTable.timing(Surface.Dialog, style, true).inMs)
        assertEquals(0, MotionTable.timing(Surface.Menu, style, true).outMs)
    }
}
