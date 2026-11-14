package com.metrowin.launcher

import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.ReminderStore
import com.metrowin.launcher.todo.TodoItem
import com.metrowin.launcher.todo.TodoReminders
import com.metrowin.launcher.todo.TodoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TodoStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val now = 1_800_000_000_000L

    @Test
    fun `saves asked one after the other end with the last one`() {
        val store = TodoStore(context)
        repeat(60) { n -> store.saveLater(List(n + 1) { TodoItem(it + 1L, "t$it") }) }
        val deadline = System.currentTimeMillis() + 5_000
        while (store.load().size != 60 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(60, store.load().size)
    }

    @Test
    fun `an id is never given twice, not even after its item is deleted`() {
        val store = TodoStore(context)
        val a = store.reserve(listOf(TodoItem(1, "a"), TodoItem(2, "b")))
        assertEquals(3L, a)
        val b = store.reserve(emptyList())
        assertEquals(4L, b)
        assertEquals(5L, store.reserve(emptyList(), 3))
        assertEquals(8L, store.reserve(emptyList()))
    }

    @Test
    fun `a damaged text gives back the copy kept before it`() {
        val store = TodoStore(context)
        store.save(listOf(TodoItem(1, "first")))
        store.save(listOf(TodoItem(1, "first"), TodoItem(2, "second")))
        Prefs.open(context, Prefs.TODO).edit().putString("items", "[{\"id\":1,\"text\":\"x\"},{broken").commit()
        assertEquals(listOf("first"), store.load().map { it.text })
        // Nothing to go back to: what can be read is read.
        Prefs.open(context, Prefs.TODO).edit().remove("items_previous").putString("items", "[{\"id\":4,\"text\":\"kept\"},{\"id\":5}]").commit()
        assertEquals(listOf("kept"), store.load().map { it.text })
    }

    @Test
    fun `a reminder is made when a date is set, moved when it changes and taken away when done`() {
        val item = TodoItem(1, "call", due = now + 3_600_000L)
        val made = TodoReminders.sync(context, item, null, now)
        assertNotEquals(0L, made.reminderId)
        val stored = ReminderStore(context).load()
        assertEquals(listOf("call"), stored.map { it.text })
        assertEquals(item.due, stored.single().at)

        val moved = TodoReminders.sync(context, made.copy(due = now + 7_200_000L), made, now)
        assertNotEquals(0L, moved.reminderId)
        assertEquals(listOf(now + 7_200_000L), ReminderStore(context).load().map { it.at })

        val done = TodoReminders.sync(context, moved.copy(done = true), moved, now)
        assertEquals(0L, done.reminderId)
        assertTrue(ReminderStore(context).load().isEmpty())
    }

    @Test
    fun `a date in the past or none gives no reminder, and release takes it away`() {
        assertEquals(0L, TodoReminders.sync(context, TodoItem(1, "past", due = now - 1), null, now).reminderId)
        assertEquals(0L, TodoReminders.sync(context, TodoItem(2, "none"), null, now).reminderId)
        val a = TodoReminders.sync(context, TodoItem(3, "a", due = now + 1000), null, now)
        val b = TodoReminders.sync(context, TodoItem(4, "b", due = now + 2000), null, now)
        TodoReminders.releaseAll(context, listOf(a, b))
        assertTrue(ReminderStore(context).load().isEmpty())
    }

    @Test
    fun `what was done from a notification is done here with its time, and a reminder taken away is let go`() {
        val a = TodoReminders.sync(context, TodoItem(1, "a", due = now + 1000), null, now)
        val b = TodoReminders.sync(context, TodoItem(2, "b", due = now + 2000), null, now)
        val c = TodoReminders.sync(context, TodoItem(3, "c", due = now + 3000), null, now)
        val store = ReminderStore(context)
        store.save(store.load().filter { it.id != b.reminderId }.map { if (it.id == a.reminderId) it.copy(done = true) else it })
        val pulled = TodoReminders.pull(context, listOf(a, b, c), now + 500)
        assertTrue(pulled[0].done); assertEquals(now + 500, pulled[0].completedAt); assertEquals(0L, pulled[0].reminderId)
        assertEquals(false, pulled[1].done); assertEquals(0L, pulled[1].reminderId)
        assertEquals(c, pulled[2])
    }
}
