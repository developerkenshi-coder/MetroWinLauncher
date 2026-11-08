package com.metrowin.launcher

import com.metrowin.launcher.data.RecentTasks
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTasksTest {
    @Test
    fun newestFirstOncePerAppWithoutTheSkippedAndTheClosed() {
        val events = listOf("a" to 10L, "b" to 20L, "a" to 30L, "me" to 40L, "c" to 5L)
        // a was closed at 25 but used again at 30: it is back. c was closed at 6, after its last use: it stays out.
        assertEquals(listOf("a", "b"), RecentTasks.order(events, mapOf("a" to 25L, "c" to 6L), setOf("me"), 10))
        // b was closed after its use: out.
        assertEquals(listOf("a"), RecentTasks.order(events, mapOf("b" to 21L), setOf("me", "c"), 10))
        assertEquals(listOf("a"), RecentTasks.order(events, emptyMap(), setOf("me", "b", "c"), 10))
        assertEquals(listOf("a"), RecentTasks.order(events, emptyMap(), setOf("me"), 1))
    }

    @Test
    fun theAppInFrontIsTheLastOneResumedAndNotPausedSince() {
        assertEquals("b", RecentTasks.top(listOf("a" to true, "a" to false, "b" to true)))
        // The game was left for a dialog of another app that went away again: the game is still the one.
        assertEquals("game", RecentTasks.top(listOf("game" to true, "x" to true, "x" to false, "game" to true)))
        assertEquals(null, RecentTasks.top(listOf("a" to true, "a" to false)))
        assertEquals(null, RecentTasks.top(emptyList()))
    }
}
