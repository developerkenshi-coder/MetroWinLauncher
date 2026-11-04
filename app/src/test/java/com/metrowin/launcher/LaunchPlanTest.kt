package com.metrowin.launcher

import com.metrowin.launcher.data.LaunchAnimation
import com.metrowin.launcher.data.LaunchPlans
import com.metrowin.launcher.ui.anim.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPlanTest {
    @Test
    fun theTurnstileTurnsTheTilesAwayAndWaitsForThem() {
        val fromStart = LaunchPlans.decide(LaunchAnimation.Turnstile, reduceMotion = false, fromStart = true)
        assertTrue(fromStart.tilesTurnAway)
        assertFalse(fromStart.expand)
        assertEquals(LaunchPlans.START_TURN_MS, fromStart.waitMs)
        val fromList = LaunchPlans.decide(LaunchAnimation.Turnstile, reduceMotion = false, fromStart = false)
        assertEquals(LaunchPlans.LIST_TURN_MS, fromList.waitMs)
    }

    @Test
    fun theExpandKeepsTheScreenStillAndOpensAtOnce() {
        for (fromStart in listOf(true, false)) {
            val plan = LaunchPlans.decide(LaunchAnimation.Expand, reduceMotion = false, fromStart = fromStart)
            assertFalse(plan.tilesTurnAway)
            assertTrue(plan.expand)
            assertEquals(0L, plan.waitMs)
        }
    }

    @Test
    fun theOriginalTurnstileTurnsTheTilesAwayAndIsNotSlowerThanAThird() {
        val fromStart = LaunchPlans.decide(LaunchAnimation.Original, reduceMotion = false, fromStart = true)
        assertTrue(fromStart.tilesTurnAway && fromStart.original)
        assertFalse(fromStart.expand)
        assertEquals(LaunchPlans.ORIGINAL_START_MS, fromStart.waitMs)
        assertTrue(fromStart.waitMs <= 350L)
        assertEquals(LaunchPlans.LIST_TURN_MS, LaunchPlans.decide(LaunchAnimation.Original, false, fromStart = false).waitMs)
        assertFalse(LaunchPlans.decide(LaunchAnimation.Turnstile, false, true).original)
    }

    @Test
    fun theTappedTileTurnsLastAndTheRowsGoFromTheTop() {
        val others = (0..40).map { LaunchPlans.outDelay(it, tapped = false) }
        assertEquals(0L, others.first())
        assertEquals(others.sorted(), others)
        assertTrue(LaunchPlans.outDelay(0, tapped = true) > others.max())
        // The last tile has turned away not long after the app starts, so the launch is not held back.
        assertTrue(LaunchPlans.outDelay(0, tapped = true) + LaunchPlans.ORIGINAL_OUT_MS < LaunchPlans.ORIGINAL_START_MS + 100)
        val back = (0..40).map { LaunchPlans.inDelay(it) }
        assertEquals(0L, back.first())
        assertEquals(back.sorted(), back)
        assertTrue(back.max() <= 250L)
        assertEquals(0L, LaunchPlans.inDelay(-3))
    }

    @Test
    fun theExponentialEasesStartAndEndWhereTheyShould() {
        assertEquals(0f, Motion.ExpoIn.transform(0f), 1e-4f)
        assertEquals(1f, Motion.ExpoIn.transform(1f), 1e-4f)
        assertEquals(0f, Motion.ExpoOut.transform(0f), 1e-4f)
        assertEquals(1f, Motion.ExpoOut.transform(1f), 1e-4f)
        // In: slow at first. Out: most of the way early.
        assertTrue(Motion.ExpoIn.transform(0.5f) < 0.1f)
        assertTrue(Motion.ExpoOut.transform(0.5f) > 0.9f)
    }

    @Test
    fun reducedMotionOpensAtOnceWithNeitherAnimation() {
        for (animation in LaunchAnimation.entries) for (fromStart in listOf(true, false)) {
            val plan = LaunchPlans.decide(animation, reduceMotion = true, fromStart = fromStart)
            assertFalse("$animation", plan.tilesTurnAway)
            assertFalse("$animation", plan.expand)
            assertEquals(0L, plan.waitMs)
        }
    }

    @Test
    fun codesRoundTrip() {
        LaunchAnimation.entries.forEach { assertEquals(it, LaunchAnimation.fromCode(it.code)) }
        assertEquals(LaunchAnimation.Original, LaunchAnimation.fromCode("spin"))
    }
}
