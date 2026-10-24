package com.metrowin.launcher

import com.metrowin.launcher.ui.gesture.PullRules
import com.metrowin.launcher.ui.gesture.pageDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRulesTest {
    private val travel = 760f // dp: a phone-sized panel

    private fun open(pulledDp: Float, velocity: Float = 0f, startedOpen: Boolean = false) =
        PullRules.shouldOpen(pulledDp / travel, travel, velocity, startedOpen)

    @Test
    fun aShortPullFallsBackAndALongOneOpens() {
        assertFalse(open(pulledDp = 60f))
        assertTrue(open(pulledDp = 200f))
    }

    @Test
    fun aFlickOpensOrClosesWhateverTheDistance() {
        assertTrue("a quick flick down from a small pull", open(pulledDp = 20f, velocity = 900f))
        assertFalse("a quick flick up from a long pull", open(pulledDp = 400f, velocity = -900f))
    }

    @Test
    fun aGentleSpeedDoesNotOverrideTheDistance() {
        assertFalse(open(pulledDp = 60f, velocity = 100f))
        assertTrue(open(pulledDp = 300f, velocity = -100f))
    }

    @Test
    fun anOpenPanelStaysOpenUnlessItWasPushedUpFarEnough() {
        // Grabbed when fully open (progress 1) and pushed up 40 dp: still open.
        assertTrue(PullRules.shouldOpen(1f - 40f / travel, travel, 0f, startedOpen = true))
        // Pushed up 300 dp: closes.
        assertFalse(PullRules.shouldOpen(1f - 300f / travel, travel, 0f, startedOpen = true))
    }

    @Test
    fun theSameDistanceMeansDifferentThingsDependingOnWhereTheFingerStarted() {
        val half = 0.5f
        assertTrue("pulling down from closed: past the commit distance", PullRules.shouldOpen(half, travel, 0f, startedOpen = false))
        assertFalse("pushing up from open: past the commit distance", PullRules.shouldOpen(half, travel, 0f, startedOpen = true))
    }

    @Test
    fun theDistanceToAPageIsAFractionOfThePageWidth() {
        assertEquals(0f, pageDistance(page = 0, currentPage = 0, currentPageOffsetFraction = 0f), 0f)
        assertEquals(1f, pageDistance(page = 1, currentPage = 0, currentPageOffsetFraction = 0f), 0f)
        // Start is the current page, dragged a quarter of the way towards the app list.
        assertEquals(0.25f, pageDistance(page = 0, currentPage = 0, currentPageOffsetFraction = 0.25f), 1e-6f)
        assertEquals(0.75f, pageDistance(page = 1, currentPage = 0, currentPageOffsetFraction = 0.25f), 1e-6f)
        // The app list is the current page, dragged back a third of the way towards Start.
        assertEquals(0.4f, pageDistance(page = 1, currentPage = 1, currentPageOffsetFraction = -0.4f), 1e-6f)
        assertEquals(0.6f, pageDistance(page = 0, currentPage = 1, currentPageOffsetFraction = -0.4f), 1e-6f)
    }
}
