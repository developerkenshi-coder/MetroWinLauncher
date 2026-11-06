package com.metrowin.launcher

import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.components.Hole
import com.metrowin.launcher.ui.components.TopCutouts
import com.metrowin.launcher.ui.components.cornerInset
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

    @Test fun theCurveOfARoundedCornerPushesTheTextInOnlyNearTheTop() {
        // Radius 100 px: at the very top the whole radius is in the way, at the height of the centre of the arc nothing is.
        assertEquals(100f, cornerInset(100, 0f), 0.01f)
        assertEquals(0f, cornerInset(100, 100f), 0.01f)
        assertEquals(0f, cornerInset(0, 10f), 0.01f)
        // Halfway down the corner the curve is at 100 - sqrt(100^2 - 50^2) = 13.4 px.
        assertEquals(13.4f, cornerInset(100, 50f), 0.1f)
        // Above the screen (a negative depth) counts as the top.
        assertEquals(100f, cornerInset(100, -5f), 0.01f)
    }
}
