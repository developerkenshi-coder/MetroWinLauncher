package com.metrowin.launcher

import androidx.compose.ui.geometry.Offset
import com.metrowin.launcher.photos.fitSize
import com.metrowin.launcher.photos.panLimit
import com.metrowin.launcher.photos.zoomAbout
import com.metrowin.launcher.music.playbackPosition
import com.metrowin.launcher.people.PeopleBook
import com.metrowin.launcher.people.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The small pure parts of the Classic visuals that are not worth a screen test. */
class HeavyVisualsTest {
    @Test
    fun photoFitsWholeAndPansOnlyWhenZoomed() {
        val (w, h) = fitSize(4000f, 3000f, 1000f, 2000f)
        assertEquals(1000f, w, 0.01f)
        assertEquals(750f, h, 0.01f)
        assertEquals(0f, panLimit(1000f, w, 1f), 0f)
        assertEquals(1000f, panLimit(1000f, w, 3f), 0.01f)
        // At 3x the picture is 750 high x3 = 2250, the box 2000: it may move 125 up or down.
        assertEquals(125f, panLimit(2000f, h, 3f), 0.01f)
    }

    @Test
    fun theFingersStayOnTheSamePointWhileZooming() {
        val centroid = Offset(100f, 50f)
        val next = zoomAbout(Offset.Zero, centroid, 1f, 2f)
        assertEquals(Offset(-100f, -50f), next)
        assertEquals(Offset.Zero, zoomAbout(next, centroid, 2f, 1f))
    }

    @Test
    fun peopleAreListedUnderTheirLetterWithoutAccents() {
        val people = listOf("Émile", "anna", "Zoe", "3 Pizza", "Bob").map { Person(it, it, false, null) }
        assertEquals(listOf("A", "B", "E", "Z", "#"), PeopleBook.grouped(people).map { it.first })
    }

    @Test
    fun aNumberIsTheSameLineHoweverItIsWritten() {
        assertTrue(PeopleBook.sameNumber("+39 333 1234567", "3331234567"))
        assertFalse(PeopleBook.sameNumber("3331234567", "3331234568"))
        assertFalse(PeopleBook.sameNumber("", "3331234567"))
    }

    @Test
    fun theSongMovesOnWhilePlayingAndStaysInsideItself() {
        assertEquals(3000L, playbackPosition(1000L, 0L, 2000L, 1f, playing = true, duration = 10_000L))
        assertEquals(1000L, playbackPosition(1000L, 0L, 2000L, 1f, playing = false, duration = 10_000L))
        assertEquals(10_000L, playbackPosition(9000L, 0L, 5000L, 1f, playing = true, duration = 10_000L))
    }
}
