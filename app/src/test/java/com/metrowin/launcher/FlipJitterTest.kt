package com.metrowin.launcher

import com.metrowin.launcher.ui.anim.nextJitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FlipJitterTest {
    @Test
    fun neverTheSameTwiceAndWithinSevenSteps() {
        val random = Random(1)
        var last = -1L
        val seen = HashSet<Long>()
        repeat(500) {
            val next = nextJitter(last, 200L, random)
            assertTrue(next != last && next % 200L == 0L && next in 0L..1_400L)
            seen += next
            last = next
        }
        assertEquals(8, seen.size)
    }
}
