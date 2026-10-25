package com.metrowin.launcher

import com.metrowin.launcher.data.LaunchAnimation
import com.metrowin.launcher.data.LaunchPlans
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
        assertEquals(LaunchAnimation.Turnstile, LaunchAnimation.fromCode("spin"))
    }
}
