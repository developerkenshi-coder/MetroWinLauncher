package com.metrowin.launcher

import androidx.compose.ui.geometry.Offset
import com.metrowin.launcher.photos.fitSize
import com.metrowin.launcher.photos.panLimit
import com.metrowin.launcher.photos.zoomAbout
import com.metrowin.launcher.calendar.CalEvent
import com.metrowin.launcher.calendar.DayLayout
import com.metrowin.launcher.music.playbackPosition
import com.metrowin.launcher.people.PeopleBook
import com.metrowin.launcher.people.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

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

    private fun at(h: Int, m: Int = 0) = LocalDate.of(2026, 11, 9).atTime(h, m).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun event(id: Long, from: Long, to: Long, allDay: Boolean = false) = CalEvent(id, 1, "e$id", from, to, allDay)

    @Test
    fun overlappingEventsShareColumnsAndLoneOnesTakeTheWholeWidth() {
        val day = LocalDate.of(2026, 11, 9)
        val blocks = DayLayout.blocks(
            listOf(event(1, at(9), at(11)), event(2, at(10), at(12)), event(3, at(11), at(12, 30)), event(4, at(15), at(16)), event(5, at(0), at(0), allDay = true)),
            day, ZoneOffset.UTC,
        ).associateBy { it.event.id }
        assertEquals(null, blocks[5])
        // 1 and 2 overlap, 3 starts when 1 ends and takes its column: two columns for the three of them.
        assertEquals(2, blocks.getValue(1).columns)
        assertEquals(0, blocks.getValue(1).column)
        assertEquals(1, blocks.getValue(2).column)
        assertEquals(0, blocks.getValue(3).column)
        assertEquals(2, blocks.getValue(3).columns)
        assertEquals(1, blocks.getValue(4).columns)
        assertEquals(15 * 60, blocks.getValue(4).startMin)
    }
}
