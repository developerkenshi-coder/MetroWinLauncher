package com.metrowin.launcher

import com.metrowin.launcher.todo.Repeat
import com.metrowin.launcher.todo.Sub
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

    @Test
    fun `finished items come last done first and an old one without a time falls back to its id`() {
        val items = listOf(
            TodoItem(1, "old a", done = true), TodoItem(2, "old b", done = true),
            TodoItem(3, "new", done = true, completedAt = 500), TodoItem(4, "newer", done = true, completedAt = 900),
        )
        assertEquals(listOf("newer", "new", "old b", "old a"), Todos.finished(items, null).map { it.text })
        val ticked = Todos.toggled(TodoItem(5, "x"), 77)
        assertEquals(77L, ticked.completedAt)
        assertEquals(0L, Todos.toggled(ticked, 99).completedAt)
    }

    @Test
    fun `an id is the one given and a full list refuses more`() {
        val a = Todos.add(emptyList(), "a", id = 40)
        assertEquals(40L, a.single().id)
        val full = (1L..Todos.MAX_ITEMS).map { TodoItem(it, "t$it") }
        assertTrue(Todos.isFull(full))
        assertEquals(full, Todos.add(full, "more"))
    }

    @Test
    fun `a thing that repeats leaves a new one after the next time and skips the days missed`() {
        val due = at(1, 9)
        val done = TodoItem(1, "water", done = true, due = due, repeat = Repeat.Daily, completedAt = now, subs = listOf(Sub("a", true)))
        val next = Todos.spawn(done, 9, now, zone)!!
        assertEquals(at(2, 9), next.due)
        assertEquals(9L, next.id)
        assertEquals(false, next.done)
        assertEquals(false, next.subs.single().done)
        // Done three days late: the next one is the first after now, not the day after the old one.
        assertEquals(at(2, 9), Todos.nextDue(at(1, 9) - 3 * 86_400_000L, Repeat.Daily, now, zone))
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0).atZone(zone).toInstant().toEpochMilli(), Todos.nextDue(due, Repeat.Monthly, now, zone))
        assertEquals(at(8, 9), Todos.nextDue(due, Repeat.Weekly, now, zone))
        assertEquals(null, Todos.spawn(done.copy(repeat = Repeat.None), 9, now))
        assertEquals(null, Todos.spawn(done.copy(due = 0), 9, now))
    }

    @Test
    fun `groups by day and by list, with a filter and a search`() {
        val items = listOf(
            TodoItem(1, "a", due = at(5, 9)), TodoItem(2, "b", due = at(5, 18), list = "work", starred = true),
            TodoItem(3, "c", list = "work", note = "call Anna"), TodoItem(4, "d", due = at(3, 9)),
        )
        val byDay = Todos.groups(items, null, Todos.GroupBy.Day, now, zone = zone)
        assertEquals(listOf(listOf("d"), listOf("b", "a"), listOf("c")), byDay.map { g -> g.items.map { it.text } })
        val byList = Todos.groups(items, null, Todos.GroupBy.List, now, zone = zone)
        assertEquals(listOf("", "work"), byList.map { it.list })
        assertEquals(listOf("b"), Todos.groups(items, null, Todos.GroupBy.Section, now, Todos.Filter.Starred, zone = zone).flatMap { it.items }.map { it.text })
        assertEquals(listOf("c"), Todos.groups(items, null, Todos.GroupBy.Section, now, query = "anna", zone = zone).flatMap { it.items }.map { it.text })
    }

    @Test
    fun `items move by a place for a screen reader and lists are renamed and deleted without losing items`() {
        assertEquals(listOf(2L, 1L, 3L), Todos.moved(listOf(1, 2, 3), 2, -1))
        assertEquals(listOf(1L, 2L, 3L), Todos.moved(listOf(1, 2, 3), 1, -1))
        val items = listOf(TodoItem(1, "a", list = "x"), TodoItem(2, "b", list = "y"))
        assertEquals(listOf("z", "y"), Todos.renameList(items, "x", " z ").map { it.list })
        assertEquals(items, Todos.renameList(items, "x", "  "))
        assertEquals(listOf("", "y"), Todos.deleteList(items, "x").map { it.list })
    }

    @Test
    fun `what was deleted comes back once and the list keeps its limit`() {
        val kept = listOf(TodoItem(1, "a"))
        val back = Todos.restore(kept, listOf(TodoItem(1, "a"), TodoItem(2, "b")))
        assertEquals(listOf(1L, 2L), back.map { it.id })
        val almost = (1L..Todos.MAX_ITEMS - 1).map { TodoItem(it, "t") }
        assertEquals(Todos.MAX_ITEMS, Todos.restore(almost, listOf(TodoItem(900, "x"), TodoItem(901, "y"))).size)
    }

    @Test
    fun `stats count the last seven days and the day shows what is left`() {
        val items = listOf(
            TodoItem(1, "a", done = true, completedAt = now), TodoItem(2, "b", done = true, completedAt = now - 86_400_000L),
            TodoItem(3, "c", done = true, completedAt = now - 20 * 86_400_000L), TodoItem(4, "late", due = at(1, 8)), TodoItem(5, "today", due = at(1, 18)),
        )
        val s = Todos.stats(items, now, zone = zone)
        assertEquals(2, s.doneWeek)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1), s.perDay)
        assertEquals(2, s.open)
        assertEquals(1, s.late)
        val d = Todos.today(items, now, zone)
        assertEquals(1, d.open); assertEquals(1, d.late); assertEquals(1, d.done)
    }

    @Test
    fun `the new fields come back from their JSON and a damaged text is not intact`() {
        val items = listOf(TodoItem(1, "a", completedAt = 5, note = "n", repeat = Repeat.Weekly, subs = listOf(Sub("s1", true), Sub("s2"))))
        assertEquals(items, Todos.decode(Todos.encode(items)))
        assertTrue(Todos.intact(Todos.encode(items)))
        assertTrue(Todos.intact(null))
        assertEquals(false, Todos.intact("nope"))
        assertEquals(false, Todos.intact("""[{"id":1,"text":"ok"},{"id":2}]"""))
    }
}
