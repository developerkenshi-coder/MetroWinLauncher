package com.metrowin.launcher

import com.metrowin.launcher.todo.TodoItem
import com.metrowin.launcher.todo.Todos
import com.metrowin.launcher.todo.Todos.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TodoModelTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val now = LocalDateTime.of(2026, 11, 1, 12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `an item is added with a clean text and a new id`() {
        val a = Todos.add(emptyList(), "  buy   milk \n", now = 5)
        assertEquals("buy milk", a.single().text)
        val b = Todos.add(a, "eggs", due = 9, list = "  home  ")
        assertEquals(listOf(1L, 2L), b.map { it.id })
        assertEquals("home", b[1].list)
        assertEquals(a, Todos.add(a, "   "))
    }

    @Test
    fun `the page puts what is late first and a star before a date`() {
        val items = listOf(
            TodoItem(1, "none"), TodoItem(2, "later", due = at(5, 9)), TodoItem(3, "today", due = at(1, 18)),
            TodoItem(4, "late today", due = at(1, 8)), TodoItem(5, "yesterday", due = at(0 + 1, 1) - 86_400_000L * 2),
            TodoItem(6, "star later", due = at(6, 9), starred = true), TodoItem(7, "done", done = true),
        )
        val open = Todos.open(items, null, now, zone)
        assertEquals(listOf(Section.Late, Section.Today, Section.Later, Section.NoDate), open.keys.toList())
        assertEquals(listOf("yesterday", "late today"), open.getValue(Section.Late).map { it.text })
        assertEquals(listOf("star later", "later"), open.getValue(Section.Later).map { it.text })
        assertEquals(listOf("done"), Todos.finished(items, null).map { it.text })
    }

    @Test
    fun `a list shows only its own`() {
        val items = listOf(TodoItem(1, "a", list = "work"), TodoItem(2, "b"), TodoItem(3, "c", list = "work", done = true))
        assertEquals(listOf("a"), Todos.open(items, "work", now, zone).values.flatten().map { it.text })
        assertEquals(listOf("c"), Todos.finished(items, "work").map { it.text })
        assertEquals(listOf("work"), Todos.lists(items))
        assertEquals(2, Todos.openCount(items))
    }

    @Test
    fun `done items can be cleared and one removed`() {
        val items = listOf(TodoItem(1, "a", done = true), TodoItem(2, "b"))
        assertEquals(listOf(2L), Todos.clearDone(items).map { it.id })
        assertEquals(listOf(1L), Todos.remove(items, 2).map { it.id })
    }

    @Test
    fun `items come back from their JSON and a damaged one is skipped`() {
        val items = listOf(TodoItem(1, "a", true, 5, true, "x", 7, 9), TodoItem(2, "b"))
        assertEquals(items, Todos.decode(Todos.encode(items)))
        assertTrue(Todos.decode("nope").isEmpty())
        assertEquals(listOf(2L), Todos.decode("""[{"x":1},{"id":2,"text":"ok"}]""").map { it.id })
    }

    @Test
    fun `dragging an item puts it where it was dropped and the order is kept`() {
        val items = listOf(TodoItem(1, "a", created = 1), TodoItem(2, "b", created = 2), TodoItem(3, "c", created = 3))
        val moved = Todos.reorder(items, listOf(3, 1, 2))
        assertEquals(listOf("c", "a", "b"), Todos.open(moved, null, now, zone).getValue(Section.NoDate).map { it.text })
        // A new item comes after the ones that were placed.
        val more = Todos.add(moved, "d", now = 4)
        assertEquals(listOf("c", "a", "b", "d"), Todos.open(more, null, now, zone).getValue(Section.NoDate).map { it.text })
        assertEquals(moved, Todos.decode(Todos.encode(moved)))
    }
}
