package com.metrowin.launcher

import com.metrowin.launcher.data.MiniLink
import com.metrowin.launcher.data.MiniSearch
import com.metrowin.launcher.notes.Note
import com.metrowin.launcher.todo.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniSearchTest {
    @Test fun notesSkipBinAndLockedOnes() {
        val all = listOf(
            Note("a", title = "Trip", text = "Trip\nbuy tickets for Rome"),
            Note("b", title = "Trip bin", deleted = 5L),
            Note("c", title = "Trip secret", locked = true),
        )
        val found = MiniSearch.notes(all, "trip")
        assertEquals(listOf("a"), found.map { it.id })
        assertEquals("buy tickets for Rome", found[0].snippet)
    }

    @Test fun todosSkipDoneOnes() {
        val all = listOf(TodoItem(1, "Buy milk", list = "Home"), TodoItem(2, "Buy bread", done = true))
        assertEquals(listOf(1L), MiniSearch.todos(all, "buy").map { it.id })
    }

    @Test fun recentNoteIsTheLastChangedOpenOne() {
        val day = 86_400_000L
        val all = listOf(
            Note("old", title = "Old", updated = 100L),
            Note("new", title = "New", updated = 9 * day),
            Note("lock", title = "Secret", updated = 10 * day, locked = true),
            Note("empty", updated = 10 * day),
        )
        assertEquals("new", MiniSearch.recentNote(all, 10 * day)?.id)
        assertNull(MiniSearch.recentNote(all, 30 * day))
    }

    @Test fun linksRoundTrip() {
        assertEquals("note" to "x-1", MiniLink.parse(MiniLink.note("x-1")))
        assertEquals("todo" to "42", MiniLink.parse(MiniLink.todo(42)))
        assertNull(MiniLink.parse("https://example.com"))
        assertNull(MiniLink.parse("metrowin://note/"))
    }
}
