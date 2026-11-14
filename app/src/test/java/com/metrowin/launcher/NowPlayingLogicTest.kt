package com.metrowin.launcher

import android.media.session.PlaybackState
import com.metrowin.launcher.music.Abilities
import com.metrowin.launcher.music.Listened
import com.metrowin.launcher.music.QueueEntry
import com.metrowin.launcher.music.addListened
import com.metrowin.launcher.music.following
import com.metrowin.launcher.music.mmss
import com.metrowin.launcher.music.queueRows
import com.metrowin.launcher.music.remaining
import com.metrowin.launcher.music.seekTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingLogicTest {
    @Test
    fun seekGoesWhereTheTouchIsAndStaysInsideTheSong() {
        assertEquals(50_000L, seekTarget(100_000, 50f, 100f))
        assertEquals(0L, seekTarget(100_000, -20f, 100f))
        assertEquals(100_000L, seekTarget(100_000, 300f, 100f))
        assertEquals(0L, seekTarget(0, 10f, 100f))
        assertEquals(0L, seekTarget(100_000, 10f, 0f))
    }

    @Test
    fun timesAreWrittenAsMinutesAndSeconds() {
        assertEquals("3:05", mmss(185_000))
        assertEquals("-1:30", remaining(30_000, 120_000))
        assertEquals("-0:00", remaining(200_000, 120_000))
    }

    @Test
    fun aPlayerThatSaysNothingIsNotLockedOutButOneThatListsItsActionsIsBelieved() {
        val silent = Abilities(0L)
        assertTrue(silent.next && silent.previous && silent.seek)
        assertFalse(silent.rate || silent.speed)
        val listed = Abilities(PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SET_RATING)
        assertTrue(listed.next && listed.rate)
        assertFalse(listed.previous || listed.seek || listed.speed)
    }

    @Test
    fun theQueueKeepsSongsWithoutTitleAndFiltersAndLimits() {
        val rows = listOf(QueueEntry(1, "Alpha", "Band"), QueueEntry(2, "—", "Beta"), QueueEntry(3, "Gamma", ""))
        assertEquals(3, queueRows(rows, 10).size)
        assertEquals(2, queueRows(rows, 2).size)
        assertEquals(listOf(2L), queueRows(rows, 10, "beta").map { it.id })
        assertEquals(listOf(1L, 3L), queueRows(rows, 10, "a ").map { it.id }.filter { it != 2L })
    }

    @Test
    fun theSameSongTwiceInARowIsHeardOnceAndTheHistoryIsCapped() {
        val a = Listened(1, "A", "x")
        val once = addListened(addListened(emptyList(), a), Listened(2, "A", "x"))
        assertEquals(1, once.size)
        val many = (1..60).fold(emptyList<Listened>()) { list, i -> addListened(list, Listened(i.toLong(), "T$i", "x")) }
        assertEquals(50, many.size)
        assertEquals("T60", many.first().title)
    }

    @Test
    fun choicesGoRound() {
        assertEquals(30, following(listOf(10, 30, 100), 10))
        assertEquals(10, following(listOf(10, 30, 100), 100))
        assertEquals(10, following(listOf(10, 30, 100), 7))
    }
}
