package com.metrowin.launcher

import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.components.Hole
import com.metrowin.launcher.ui.components.TopCutouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopCutoutsTest {
    private val width = 1080
    private val band = 150

    @Test fun aPunchHoleInTheMiddleLeavesTheMiddleClear() {
        val cutouts = TopCutouts.of(listOf(Hole(500, 30, 580, 110)), width, band, density = 2f)
        assertTrue(cutouts.hasCenter)
        assertEquals(245.dp, cutouts.centerStart!!)
        assertEquals(295.dp, cutouts.centerEnd!!)
        assertEquals(0.dp, cutouts.left)
        assertEquals(0.dp, cutouts.right)
    }

    @Test fun aHoleInTheRightCornerPushesWhatIsOnTheRightToItsLeft() {
        val cutouts = TopCutouts.of(listOf(Hole(900, 30, 1000, 130)), width, band, density = 2f)
        assertFalse(cutouts.hasCenter)
        assertEquals(95.dp, cutouts.right) // 1080 - 900 = 180 px = 90 dp, plus the margin kept clear of the hole
        assertEquals(0.dp, cutouts.left)
    }

    @Test fun holesAtBothSidesAndInTheMiddleAreAllKnown() {
        val cutouts = TopCutouts.of(listOf(Hole(60, 20, 140, 100), Hole(500, 20, 580, 100), Hole(940, 20, 1020, 100)), width, band, density = 2f)
        assertTrue(cutouts.hasCenter)
        assertTrue(cutouts.left > 0.dp)
        assertTrue(cutouts.right > 0.dp)
    }

    @Test fun aHoleOnTheSideOfATurnedPhoneIsNotAtTheTop() {
        val cutouts = TopCutouts.of(listOf(Hole(0, 900, 90, 1000)), 2400, band, density = 2f)
        assertNull(cutouts.centerStart)
        assertEquals(0.dp, cutouts.left)
        assertEquals(0.dp, cutouts.bottom)
    }

    @Test fun noHoleMeansNothingToKeepClearOf() {
        assertEquals(TopCutouts(), TopCutouts.of(emptyList(), width, band, density = 2f))
    }
}
