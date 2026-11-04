package com.metrowin.launcher

import com.metrowin.launcher.data.LockIconRow
import com.metrowin.launcher.data.LockIconRow.GAP_DP
import com.metrowin.launcher.data.LockIconRow.MAX_ICON_DP
import com.metrowin.launcher.data.LockIconRow.MIN_ICON_DP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockIconRowTest {
    private fun used(fit: LockIconRow.Fit, digits: List<Int>): Float =
        digits.take(fit.shown).sumOf { LockIconRow.slotDp(fit.iconDp, it).toDouble() }.toFloat() + GAP_DP * (fit.shown - 1)

    @Test
    fun fewAppsKeepTheBiggestIconsAndMoreShrinkThemToFit() {
        val few = LockIconRow.fit(listOf(1, 0), 312f)
        assertEquals(LockIconRow.Fit(MAX_ICON_DP, 2, 0), few)
        val five = listOf(1, 2, 3, 0, 1)
        val fit = LockIconRow.fit(five, 312f)
        assertEquals(0, fit.hidden)
        assertEquals(5, fit.shown)
        assertTrue(fit.iconDp in MIN_ICON_DP..MAX_ICON_DP)
        assertTrue(used(fit, five) <= 312f)
    }

    @Test
    fun whenEvenTheSmallestDoNotFitTheLastOnesBecomeAPlusN() {
        for (width in listOf(200f, 240f, 312f, 400f)) for (count in 1..30) {
            val digits = List(count) { it % 4 }
            val fit = LockIconRow.fit(digits, width)
            assertEquals("$width $count", count, fit.shown + fit.hidden)
            assertTrue("$width $count: at least one app", fit.shown >= 1)
            assertTrue("$width $count: icon ${fit.iconDp}", fit.iconDp in MIN_ICON_DP..MAX_ICON_DP)
            // Never wider than the room, with the "+N" counted when there is one (the row of one app alone may be wider only on a tiny screen).
            if (fit.shown > 1 || fit.hidden == 0) assertTrue("$width $count: ${used(fit, digits)}", used(fit, digits) <= width)
        }
        assertTrue(LockIconRow.fit(List(12) { 2 }, 312f).hidden > 0)
    }

    @Test
    fun nothingToShowIsNothing() {
        assertEquals(0, LockIconRow.fit(emptyList(), 312f).shown)
    }
}
