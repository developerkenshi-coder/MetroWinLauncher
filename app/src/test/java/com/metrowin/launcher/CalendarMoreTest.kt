package com.metrowin.launcher

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.calendar.Birthdays
import com.metrowin.launcher.calendar.CalEvent
import com.metrowin.launcher.calendar.CalendarDefaults
import com.metrowin.launcher.calendar.CalendarLogic
import com.metrowin.launcher.calendar.CalendarOptions
import com.metrowin.launcher.calendar.CalendarRepository
import com.metrowin.launcher.calendar.DayLayout
import com.metrowin.launcher.calendar.Failure
import com.metrowin.launcher.calendar.Ics
import com.metrowin.launcher.calendar.IcsEvent
import com.metrowin.launcher.calendar.Option
import com.metrowin.launcher.calendar.focusMinute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** The pure parts of the calendar added in the improvement round: the wall clock, free time, links, search, files. */
class CalendarMoreTest {
    private val rome = ZoneId.of("Europe/Rome")
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0, zone: ZoneId = rome) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    private fun timed(start: Long, end: Long, id: Long = 1, cal: Long = 1) = CalEvent(id, cal, "t$id", start, end, false)

    @Test fun `on the day summer time starts the blocks follow the wall clock`() {
        val day = LocalDate.of(2026, 3, 29) // 02:00 -> 03:00 in Rome
        val e = timed(at(2026, 3, 29, 3, 30), at(2026, 3, 29, 4, 30))
        assertEquals(3 * 60 + 30, CalendarLogic.wallMinutes(e.start, day, rome))
        val block = DayLayout.blocks(listOf(e), day, rome).single()
        assertEquals(3 * 60 + 30, block.startMin)
        assertEquals(4 * 60 + 30, block.endMin)
        // The label of the skipped 02:30 is the moment after the gap, not an earlier one.
        assertEquals(at(2026, 3, 29, 3, 0), CalendarLogic.timeAt(day, 2 * 60 + 30, rome) - 30 * 60_000L)
    }

    @Test fun `on the day summer time ends the repeated hour keeps its blocks in order`() {
        val day = LocalDate.of(2026, 10, 25)
        val e = timed(at(2026, 10, 25, 4), at(2026, 10, 25, 5))
        assertEquals(240, DayLayout.blocks(listOf(e), day, rome).single().startMin)
        assertEquals(0, CalendarLogic.wallMinutes(at(2026, 10, 24, 23), day, rome))
        assertEquals(1440, CalendarLogic.wallMinutes(at(2026, 10, 26, 1), day, rome))
    }

    @Test fun `week numbers follow the locale`() {
        assertEquals(1, CalendarLogic.weekNumber(LocalDate.of(2026, 1, 1), Locale.ITALY))
        assertEquals(53, CalendarLogic.weekNumber(LocalDate.of(2026, 12, 31), Locale.ITALY))
    }

    @Test fun `cancelled and hidden and declined events are left out`() {
        val ok = timed(1, 2, 1, 1)
        val cancelled = timed(1, 2, 2, 1).copy(status = 2)
        val hidden = timed(1, 2, 3, 9)
        val declined = timed(1, 2, 4, 1).copy(selfStatus = 2)
        assertEquals(listOf(1L, 4L), CalendarLogic.visible(listOf(ok, cancelled, hidden, declined), setOf(9L), true).map { it.id })
        assertEquals(listOf(1L), CalendarLogic.visible(listOf(ok, cancelled, hidden, declined), setOf(9L), false).map { it.id })
    }

    @Test fun `free time is what the events leave between eight and twenty`() {
        val day = LocalDate.of(2026, 11, 2)
        val events = listOf(timed(at(2026, 11, 2, 9), at(2026, 11, 2, 10)), timed(at(2026, 11, 2, 9, 30), at(2026, 11, 2, 11), 2), timed(at(2026, 11, 2, 18), at(2026, 11, 2, 19, 45), 3))
        assertEquals(listOf(8 * 60 to 9 * 60, 11 * 60 to 18 * 60), CalendarLogic.freeSlots(events, day, rome))
        assertEquals(listOf(8 * 60 to 20 * 60), CalendarLogic.freeSlots(emptyList(), day, rome))
    }

    @Test fun `busy minutes count an all-day event as a whole day and never more`() {
        val day = LocalDate.of(2026, 11, 2)
        val (s, e) = CalendarLogic.allDayRange(day, day)
        assertEquals(1440, CalendarLogic.busyMinutes(listOf(CalEvent(1, 1, "a", s, e, true), timed(at(2026, 11, 2, 9), at(2026, 11, 2, 10), 2)), day, rome))
        assertEquals(60, CalendarLogic.busyMinutes(listOf(timed(at(2026, 11, 2, 9), at(2026, 11, 2, 10))), day, rome))
    }

    @Test fun `a link to a call is found in the place or the notes`() {
        assertEquals("https://meet.google.com/abc-defg-hij", CalendarLogic.conferenceLink("Room 4", "Join: https://meet.google.com/abc-defg-hij."))
        assertEquals("https://us02web.zoom.us/j/123456?pwd=x", CalendarLogic.conferenceLink("https://us02web.zoom.us/j/123456?pwd=x"))
        assertNull(CalendarLogic.conferenceLink("http://meet.google.com/abc", "https://evil.example/meet.google.com/x", ""))
    }

    @Test fun `a search needs every word`() {
        val e = timed(1, 2).copy(title = "Dentist", location = "Via Roma 5", description = "bring the card")
        assertTrue(CalendarLogic.matches(e, "roma dent"))
        assertFalse(CalendarLogic.matches(e, "roma bank"))
        assertTrue(CalendarLogic.matches(e, "  "))
    }

    @Test fun `events are grouped by day once, an event of two days is under both`() {
        val two = timed(at(2026, 11, 1, 22), at(2026, 11, 2, 2), 1)
        val one = timed(at(2026, 11, 2, 9), at(2026, 11, 2, 10), 2)
        val map = CalendarLogic.byDay(listOf(two, one), LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 30), rome)
        assertEquals(listOf(1L), map.getValue(LocalDate.of(2026, 11, 1)).map { it.id })
        assertEquals(listOf(1L, 2L), map.getValue(LocalDate.of(2026, 11, 2)).map { it.id })
    }

    @Test fun `one column for each calendar`() {
        val day = LocalDate.of(2026, 11, 2)
        val a = timed(at(2026, 11, 2, 9), at(2026, 11, 2, 10), 1, cal = 5)
        val b = timed(at(2026, 11, 2, 9), at(2026, 11, 2, 10), 2, cal = 3)
        val lanes = DayLayout.byCalendar(DayLayout.blocks(listOf(a, b), day, rome))
        assertEquals(2, lanes.first().columns)
        assertEquals(1, lanes.first { it.event.id == 1L }.column)
        assertEquals(0, lanes.first { it.event.id == 2L }.column)
    }

    @Test fun `the day opens on the first event, or on now when nothing is still to come`() {
        val day = LocalDate.of(2026, 11, 2)
        val blocks = DayLayout.blocks(listOf(timed(at(2026, 11, 2, 9), at(2026, 11, 2, 10))), day, rome)
        assertEquals(540, focusMinute(blocks, -1))
        assertEquals(540, focusMinute(blocks, 8 * 60))
        assertEquals(15 * 60, focusMinute(blocks, 15 * 60))
        assertEquals(15 * 60, focusMinute(emptyList(), 15 * 60))
        assertEquals(480, focusMinute(emptyList(), -1))
    }

    @Test fun `a file of events is written and read back`() {
        val a = CalEvent(1, 1, "Lunch, with; friends", at(2026, 11, 2, 12), at(2026, 11, 2, 13), false, "Via \\ Roma", "line1\nline2", reminders = listOf(10, 60))
        val (s, e) = CalendarLogic.allDayRange(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6))
        val b = CalEvent(2, 1, "Trip", s, e, true)
        val back = Ics.parse(Ics.write(listOf(a, b), now = 0), rome)
        assertEquals(2, back.size)
        assertEquals("Lunch, with; friends", back[0].title)
        assertEquals(a.start, back[0].start); assertEquals(a.end, back[0].end)
        assertEquals("Via \\ Roma", back[0].location); assertEquals("line1\nline2", back[0].description)
        assertEquals(listOf(10, 60), back[0].reminders)
        assertTrue(back[1].allDay); assertEquals(s, back[1].start); assertEquals(e, back[1].end)
    }

    @Test fun `a long line is folded and read as one`() {
        val long = "x".repeat(150) // titles are kept to 200 characters
        val text = Ics.write(listOf(CalEvent(1, 1, long, 0, 3_600_000, false)))
        assertTrue(text.lines().all { it.length <= 71 })
        assertEquals(long, Ics.parse(text).single().title)
    }

    @Test fun `times with a zone, floating, and a length instead of an end`() {
        val text = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART;TZID=America/New_York:20261102T090000
            DURATION:PT1H30M
            SUMMARY:ny
            RRULE:FREQ=WEEKLY;COUNT=3
            BEGIN:VALARM
            TRIGGER:-PT15M
            END:VALARM
            BEGIN:VALARM
            TRIGGER;VALUE=DATE-TIME:20261102T080000Z
            END:VALARM
            END:VEVENT
            BEGIN:VEVENT
            DTSTART:20261103T100000
            SUMMARY:floating
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val list = Ics.parse(text, rome)
        val ny = list[0]
        assertEquals(at(2026, 11, 2, 9, zone = ZoneId.of("America/New_York")), ny.start)
        assertEquals(90 * 60_000L, ny.end - ny.start)
        assertEquals("FREQ=WEEKLY;COUNT=3", ny.rule)
        assertEquals(listOf(15), ny.reminders)
        assertEquals(at(2026, 11, 3, 10), list[1].start)
        assertEquals(3_600_000L, list[1].end - list[1].start)
    }

    @Test fun `a hostile file gets no rule, no cancelled events, and a limit`() {
        val bad = "BEGIN:VEVENT\nDTSTART:20261102T090000Z\nRRULE:FREQ=DAILY;X=<script>\nSUMMARY:a\nEND:VEVENT\nBEGIN:VEVENT\nDTSTART:20261102T090000Z\nSTATUS:CANCELLED\nEND:VEVENT\nBEGIN:VEVENT\nSUMMARY:no start\nEND:VEVENT"
        val list = Ics.parse(bad)
        assertEquals(1, list.size)
        assertEquals("", list.single().rule)
        val many = (1..Ics.MAX_EVENTS + 50).joinToString("\n") { "BEGIN:VEVENT\nDTSTART:20261102T090000Z\nEND:VEVENT" }
        assertEquals(Ics.MAX_EVENTS, Ics.parse(many).size)
    }

    @Test fun `lengths and bounded reading`() {
        assertEquals(5_400_000L, Ics.durationMs("PT1H30M"))
        assertEquals(7 * 86_400_000L, Ics.durationMs("P1W"))
        assertEquals(-900_000L, Ics.durationMs("-PT15M"))
        assertNull(Ics.durationMs("soon"))
        assertEquals(Ics.MAX_CHARS, Ics.readBounded(java.io.ByteArrayInputStream(ByteArray(Ics.MAX_CHARS + 1000) { 'a'.code.toByte() })).length)
    }

    @Test fun `birthdays with and without a year, and the 29th of February`() {
        assertEquals(MonthDay.of(3, 9), Birthdays.parse("1984-03-09"))
        assertEquals(MonthDay.of(3, 9), Birthdays.parse("--03-09"))
        assertNull(Birthdays.parse("not a date"))
        val entries = Birthdays.entries(listOf(7L to ("Ann" to MonthDay.of(2, 29))), LocalDate.of(2026, 1, 1)..LocalDate.of(2027, 12, 31), "b")
        assertEquals(2, entries.size)
        assertEquals(LocalDate.of(2026, 2, 28).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), entries[0].start)
        assertTrue(entries.all { it.readOnly && it.birthday && it.allDay && it.id < 0 })
    }
}

/** The switches keep their word, and start as they were meant to (what touches contacts or writes by a gesture is off). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarOptionsTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun defaultsAndPersistence() {
        val o = CalendarOptions(context)
        assertTrue(o[Option.WeekGrid]); assertFalse(o[Option.Birthdays]); assertFalse(o[Option.DragEvents])
        o.set(Option.WeekGrid, false); o.toggleHidden(4); o.setColor(4, 123); o.chooseSecondZone("Asia/Tokyo")
        val again = CalendarOptions(context)
        assertFalse(again[Option.WeekGrid])
        assertEquals(setOf(4L), again.hidden)
        assertEquals(123, again.colors[4L])
        assertEquals("Asia/Tokyo", again.secondZone)
        again.toggleHidden(4); again.setColor(4, null)
        assertTrue(CalendarOptions(context).hidden.isEmpty())
    }
}

/** A calendar provider that only records what is asked of it, to check what the repository writes (never the real calendars of a phone). */
class FakeCalendarProvider : ContentProvider() {
    companion object {
        val log = ArrayList<Pair<String, ContentValues?>>()
        var writable = true
        var throwOnInsert = false
        var seriesStart = 1_000L
        private var next = 100L
    }

    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val cols = projection ?: arrayOf("_id")
        val cursor = MatrixCursor(cols)
        val row = cols.map { c ->
            when (c) {
                CalendarContract.Calendars._ID -> 1L
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME -> "Test"
                CalendarContract.Calendars.ACCOUNT_NAME -> "local"
                CalendarContract.Calendars.CALENDAR_COLOR -> 0xFF0000
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL -> if (writable) CalendarContract.Calendars.CAL_ACCESS_OWNER else CalendarContract.Calendars.CAL_ACCESS_READ
                CalendarContract.Events.DTSTART -> seriesStart
                else -> null
            }
        }
        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (throwOnInsert) throw SecurityException("no")
        log += "insert ${uri.path}" to values
        return Uri.parse("content://com.android.calendar/${uri.pathSegments.first()}/${next++}")
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int { log += "delete ${uri.path} ${selectionArgs?.toList()}" to null; return 1 }
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int { log += "update ${uri.path}" to values; return 1 }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarRepositoryTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun repo(): CalendarRepository {
        Robolectric.buildContentProvider(FakeCalendarProvider::class.java).create(CalendarContract.AUTHORITY)
        FakeCalendarProvider.log.clear(); FakeCalendarProvider.writable = true; FakeCalendarProvider.throwOnInsert = false
        return CalendarRepository(context)
    }
    private val event = CalEvent(7, 1, "x", 10_000, 3_610_000, false, reminders = listOf(10, 60))

    @Test fun `a new event and its reminders go in one batch, the reminders on the event that was written`() {
        val r = repo()
        val id = r.insert(event.copy(id = 0))
        assertTrue(id > 0)
        val log = FakeCalendarProvider.log
        assertEquals("insert /events", log[0].first)
        assertEquals(3_610_000L, log[0].second!!.getAsLong(CalendarContract.Events.DTEND))
        val reminders = log.filter { it.first == "insert /reminders" }
        assertEquals(listOf(10, 60), reminders.map { it.second!!.getAsInteger(CalendarContract.Reminders.MINUTES) })
        assertTrue(reminders.all { it.second!!.getAsLong(CalendarContract.Reminders.EVENT_ID) == id })
    }

    @Test fun `a repeating new event has a rule and a length and no end`() {
        val r = repo()
        r.insert(event.copy(id = 0), "FREQ=WEEKLY")
        val v = FakeCalendarProvider.log.first().second!!
        assertEquals("FREQ=WEEKLY", v.getAsString(CalendarContract.Events.RRULE))
        assertEquals("P3600S", v.getAsString(CalendarContract.Events.DURATION))
        assertFalse(v.containsKey(CalendarContract.Events.DTEND) && v.get(CalendarContract.Events.DTEND) != null)
    }

    @Test fun `an event is never written into a calendar that is read only`() {
        val r = repo()
        FakeCalendarProvider.writable = false
        assertEquals(0L, r.insert(event.copy(id = 0)))
        assertEquals(Failure.ReadOnly, r.failure)
        assertTrue(FakeCalendarProvider.log.none { it.first.startsWith("insert") })
        assertFalse(r.update(event.copy(readOnly = true), event.copy(readOnly = true)))
        assertFalse(r.delete(event.copy(readOnly = true)))
        assertTrue(FakeCalendarProvider.log.isEmpty())
    }

    @Test fun `a missing permission is told apart from another failure`() {
        val r = repo()
        FakeCalendarProvider.throwOnInsert = true
        assertEquals(0L, r.insert(event.copy(id = 0)))
        assertEquals(Failure.Permission, r.failure)
    }

    @Test fun `changing one time of a series writes an exception, not the series`() {
        val r = repo()
        val series = event.copy(recurring = true)
        assertTrue(r.update(series.copy(title = "y"), series, all = false))
        val first = FakeCalendarProvider.log.first()
        assertEquals("insert /exception/7", first.first)
        assertEquals(10_000L, first.second!!.getAsLong(CalendarContract.Events.ORIGINAL_INSTANCE_TIME))
        assertTrue(FakeCalendarProvider.log.none { it.first.startsWith("update") })
    }

    @Test fun `changing a whole series moves its first time by as much and keeps a length`() {
        val r = repo()
        FakeCalendarProvider.seriesStart = 1_000L
        val series = event.copy(recurring = true)
        assertTrue(r.update(series.copy(start = 10_500, end = 3_610_500), series, all = true))
        val update = FakeCalendarProvider.log.first { it.first.startsWith("update") }
        assertEquals(1_500L, update.second!!.getAsLong(CalendarContract.Events.DTSTART))
        assertEquals("P3600S", update.second!!.getAsString(CalendarContract.Events.DURATION))
        // The reminders are replaced after the update, in the same batch.
        val paths = FakeCalendarProvider.log.map { it.first }
        assertTrue(paths.indexOf("update /events/7") in 0 until paths.indexOfFirst { it.startsWith("delete /reminders") })
    }

    @Test fun `an update can leave the reminders alone`() {
        val r = repo()
        assertTrue(r.update(event, event, touchReminders = false))
        assertTrue(FakeCalendarProvider.log.none { it.first.contains("reminders") })
    }

    @Test fun `a deleted time of a series is a cancelled exception`() {
        val r = repo()
        assertTrue(r.delete(event.copy(recurring = true), all = false))
        val v = FakeCalendarProvider.log.single().second!!
        assertEquals(CalendarContract.Events.STATUS_CANCELED, v.getAsInteger(CalendarContract.Events.STATUS))
        assertEquals(10_000L, v.getAsLong(CalendarContract.Events.ORIGINAL_INSTANCE_TIME))
    }

    @Test fun `moving writes only the start and the end, and never a series`() {
        val r = repo()
        assertTrue(r.move(event, 20_000, 3_620_000))
        val v = FakeCalendarProvider.log.single().second!!
        assertEquals(setOf(CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND), v.keySet())
        FakeCalendarProvider.log.clear()
        assertFalse(r.move(event.copy(recurring = true), 1, 2))
        assertTrue(FakeCalendarProvider.log.isEmpty())
    }

    @Test fun `the constants are in one place`() {
        assertEquals(3_000, CalendarDefaults.MAX_EVENTS)
        assertEquals(listOf(10, 30, 60, 1440), CalendarDefaults.REMINDERS)
    }
}
