package com.metrowin.launcher

import com.metrowin.launcher.todo.Repeat
import com.metrowin.launcher.todo.SpokenDate
import com.metrowin.launcher.todo.Sub
import com.metrowin.launcher.todo.TodoFormats
import com.metrowin.launcher.todo.TodoFormats.Format
import com.metrowin.launcher.todo.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TodoFormatsTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    private val now = at(1, 12)

    private val items = listOf(
        TodoItem(1, "buy milk", due = at(5, 18), starred = true, repeat = Repeat.Weekly, note = "two bottles", subs = listOf(Sub("skimmed", true), Sub("whole"))),
        TodoItem(2, "call Anna", list = "family"), TodoItem(3, "pay rent", done = true, list = "home office", completedAt = now),
    )

    @Test
    fun `markdown goes out and comes back with date, star, repeat, note, steps and list`() {
        val back = TodoFormats.parse(TodoFormats.export(items, Format.Markdown, zone), now, zone)
        assertEquals(3, back.size)
        with(back[0]) {
            assertEquals("buy milk", text); assertEquals(at(5, 18), due); assertTrue(starred)
            assertEquals(Repeat.Weekly, repeat); assertEquals("two bottles", note); assertEquals(listOf(Sub("skimmed", true), Sub("whole")), subs)
        }
        assertEquals("family", back[1].list)
        assertTrue(back[2].done); assertEquals("home office", back[2].list)
    }

    @Test
    fun `todo txt goes out and comes back`() {
        val text = TodoFormats.export(items, Format.TodoTxt, zone)
        assertTrue(text.lines().first().startsWith("(A) "))
        val back = TodoFormats.parse(text, now, zone)
        assertEquals(listOf("buy milk", "call Anna", "pay rent"), back.map { it.text })
        assertEquals(at(5, 18), back[0].due); assertTrue(back[0].starred)
        assertEquals("home office", back[2].list); assertTrue(back[2].done)
        assertEquals("walk dog", TodoFormats.parse("2026-10-30 walk dog", now, zone).single().text)
    }

    @Test
    fun `csv quotes what has a comma, a quote or a line break`() {
        val csv = TodoFormats.export(listOf(TodoItem(1, "a, \"b\"", note = "x\ny")), Format.Csv, zone)
        assertEquals("text,done,due,starred,list,note\n\"a, \"\"b\"\"\",false,,false,,\"x\ny\"\n", csv)
    }

    @Test
    fun `an import skips what is there and keeps the limit and gives new ids`() {
        val have = listOf(TodoItem(7, "buy milk"))
        val merged = TodoFormats.merge(have, listOf(TodoItem(0, "Buy milk"), TodoItem(0, "eggs")), 3)
        assertEquals(listOf(7L, 8L), merged.map { it.id })
        assertEquals("eggs", merged.last().text)
    }

    @Test
    fun `a spoken date takes the day and the hour out of the words`() {
        val a = SpokenDate.parse("buy milk tomorrow at 6 pm", now, zone)
        assertEquals("buy milk", a.text); assertEquals(at(2, 18), a.due)
        val b = SpokenDate.parse("chiamare Anna domani alle 9:30", now, zone)
        assertEquals("chiamare Anna", b.text); assertEquals(at(2, 9, 30), b.due)
        val c = SpokenDate.parse("Müll rausbringen übermorgen", now, zone)
        assertEquals("Müll rausbringen", c.text); assertEquals(at(3, 9), c.due)
        // An hour that has passed today is meant for tomorrow.
        assertEquals(at(2, 8), SpokenDate.parse("run at 8", now, zone).due)
        // A number alone is not an hour.
        val d = SpokenDate.parse("buy 2 apples", now, zone)
        assertEquals("buy 2 apples", d.text); assertEquals(0L, d.due)
    }
}
