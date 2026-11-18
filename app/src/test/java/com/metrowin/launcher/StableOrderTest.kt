package com.metrowin.launcher

import com.metrowin.launcher.data.StableOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StableOrderTest {
    @Test
    fun typingOnKeepsTheOrderAndAddsNewOnesAtTheEnd() {
        // "ca" showed Camera before Calendar; a better ranking for "cal" does not move them, and Calculator comes after.
        assertEquals(listOf("Camera", "Calendar", "Calculator"), StableOrder.keep("ca", listOf("Camera", "Calendar"), "cal", listOf("Calendar", "Calculator", "Camera")))
    }

    @Test
    fun whatWasShownKeepsItsOrderWhenStillFound() {
        assertEquals(listOf("b", "a", "c"), StableOrder.keep("x", listOf("b", "a"), "xy", listOf("a", "c", "b")))
    }

    @Test
    fun aQueryThatDoesNotExtendTheLastOneStartsOver() {
        assertEquals(listOf("a", "b"), StableOrder.keep("xy", listOf("b", "a"), "x", listOf("a", "b")))
        assertEquals(listOf("a", "b"), StableOrder.keep("ab", listOf("b", "a"), "ab", listOf("a", "b")))
        assertEquals(listOf("a", "b"), StableOrder.keep("", listOf("b", "a"), "a", listOf("a", "b")))
    }

    @Test
    fun extendsNeedsMoreTextAfterTheSameStart() {
        assertTrue(StableOrder.extends("ca", "Càl"))
        assertFalse(StableOrder.extends("ca", "ca"))
        assertFalse(StableOrder.extends("ca", "c"))
        assertFalse(StableOrder.extends("ca", "xca"))
        assertFalse(StableOrder.extends("", "a"))
    }
}
