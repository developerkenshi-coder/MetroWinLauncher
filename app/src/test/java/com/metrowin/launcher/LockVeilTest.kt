package com.metrowin.launcher

import com.metrowin.launcher.ui.lock.LockVeil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockVeilTest {
    @Test fun darkPhotoGetsTheLightestShade() = assertEquals(LockVeil.MIN_ALPHA, LockVeil.alpha(0.1f), 1e-6f)

    @Test fun brightPhotoGetsTheStrongestShade() = assertEquals(LockVeil.MAX_ALPHA, LockVeil.alpha(0.95f), 1e-6f)

    @Test fun shadeGrowsWithTheLuma() {
        assertTrue(LockVeil.alpha(0.4f) < LockVeil.alpha(0.5f))
        assertTrue(LockVeil.alpha(0.5f) < LockVeil.alpha(0.6f))
    }

    @Test fun lumaReadsOnlyTheRowsFromTheFirstOne() {
        // 2 wide, 2 rows: the top row is white, the bottom row black.
        val pixels = intArrayOf(-1, -1, 0xFF000000.toInt(), 0xFF000000.toInt())
        assertEquals(0.5f, LockVeil.luma(pixels, 2, 0), 1e-3f)
        assertEquals(0f, LockVeil.luma(pixels, 2, 1), 1e-6f)
    }

    @Test fun nothingToReadIsMiddleGrey() = assertEquals(0.5f, LockVeil.luma(IntArray(4), 2, 5), 1e-6f)
}
