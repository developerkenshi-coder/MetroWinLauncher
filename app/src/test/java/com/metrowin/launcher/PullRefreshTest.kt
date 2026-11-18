package com.metrowin.launcher

import com.metrowin.launcher.ui.components.PullRefresh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRefreshTest {
    @Test
    fun theLineGrowsUpToOneAndAHalfTimesTheThreshold() {
        assertEquals(40f, PullRefresh.pull(0f, 40f, 100f), 0f)
        assertEquals(150f, PullRefresh.pull(120f, 90f, 100f), 0f)
    }

    @Test
    fun pullingBackNeverGoesBelowZero() = assertEquals(0f, PullRefresh.pull(20f, -90f, 100f), 0f)

    @Test
    fun itRefreshesOnlyFromTheThreshold() {
        assertFalse(PullRefresh.reached(99f, 100f))
        assertTrue(PullRefresh.reached(100f, 100f))
    }
}
