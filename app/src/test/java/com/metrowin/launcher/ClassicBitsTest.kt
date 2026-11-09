package com.metrowin.launcher

import com.metrowin.launcher.ui.components.DotsMotion
import com.metrowin.launcher.ui.components.scrollThumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicBitsTest {
    @Test
    fun dotsWaitThenCrossLeftToRightOneAfterTheOther() {
        assertNull(DotsMotion.position(0f, 1))
        assertEquals(0f, DotsMotion.position(0f, 0)!!, 0.001f)
        val ms = 900f
        val places = (0 until DotsMotion.COUNT).mapNotNull { DotsMotion.position(ms, it) }
        assertTrue(places.size > 1)
        // The first dot is always ahead of the next one, and every place is on the bar.
        assertEquals(places.sortedDescending(), places)
        assertTrue(places.all { it in 0f..1f })
        // All five are gone before the cycle ends, so each cycle starts clean.
        assertTrue((0 until DotsMotion.COUNT).all { DotsMotion.position(DotsMotion.CYCLE_MS - 1f, it) == null })
    }

    @Test
    fun scrollBarShowsOnlyWhenTheListIsLongerThanTheScreen() {
        assertNull(scrollThumb(1000f, 1000f, 0f, 24f))
        assertNull(scrollThumb(0f, 500f, 0f, 24f))
        val (top, length) = scrollThumb(1000f, 4000f, 0f, 24f)!!
        assertEquals(0f, top, 0.001f)
        assertEquals(250f, length, 0.001f)
        // At the end the bar rests on the bottom edge.
        val (endTop, endLength) = scrollThumb(1000f, 4000f, 3000f, 24f)!!
        assertEquals(1000f, endTop + endLength, 0.001f)
        // A very long list does not make the bar a speck.
        assertEquals(24f, scrollThumb(1000f, 1_000_000f, 10f, 24f)!!.second, 0.001f)
        assertNotNull(scrollThumb(1000f, 1001f, 0f, 24f))
    }
}
