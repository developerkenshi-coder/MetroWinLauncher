package com.metrowin.launcher.todo

import com.metrowin.launcher.data.Prefs
import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.ReminderStore
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.notifications.Alerts

/** The things to do kept on the phone. */
class TodoStore(context: Context) {
    private val prefs = Prefs.open(context, Prefs.TODO)

    fun load(): List<TodoItem> = Todos.decode(prefs.getString(KEY, null))

    fun save(items: List<TodoItem>) = prefs.edit { putString(KEY, Todos.encode(items)) }

    private companion object {
        const val KEY = "items"
    }
}

/**
 * The link between a thing to do with a date and the reminders of the launcher: the reminder is the alarm that rings at that time
 * (with its "done" and "later" buttons, set again after a reboot). A thing to do that is done, or has no date, has no reminder.
 */
object TodoReminders {
    /** [item] with its reminder made to match: [before] is the item as it was, so a reminder that no longer fits is taken away. */
    fun sync(context: Context, item: TodoItem, before: TodoItem?, now: Long = System.currentTimeMillis()): TodoItem {
        val store = ReminderStore(context)
        var list = store.load()
        var result = item
        val stale = result.reminderId != 0L && (result.done || result.due != before?.due || result.text != before?.text || result.due <= now)
        if (stale) {
            Alerts.cancelReminder(context, result.reminderId)
            list = Reminders.remove(list, result.reminderId)
            result = result.copy(reminderId = 0L)
        }
        if (!result.done && result.due > now && result.reminderId == 0L) {
            val id = Reminders.nextId(list)
            list = Reminders.add(list, result.text, result.due)
            list.firstOrNull { it.id == id }?.let { reminder: Reminder ->
                result = result.copy(reminderId = id)
                Alerts.scheduleReminder(context, reminder)
            }
        }
        store.save(list)
        return result
    }

    /** The reminder of a thing to do that is deleted goes with it. */
    fun release(context: Context, item: TodoItem) {
        if (item.reminderId == 0L) return
        Alerts.cancelReminder(context, item.reminderId)
        val store = ReminderStore(context)
        store.save(Reminders.remove(store.load(), item.reminderId))
    }

    /** What was done from the notification of a reminder is done here too, and a reminder taken away from the reminders is let go. */
    fun pull(context: Context, items: List<TodoItem>): List<TodoItem> {
        val reminders = ReminderStore(context).load().associateBy { it.id }
        return items.map { item ->
            if (item.reminderId == 0L) return@map item
            val reminder = reminders[item.reminderId]
            when {
                reminder == null -> item.copy(reminderId = 0L)
                reminder.done -> item.copy(done = true, reminderId = 0L)
                else -> item
            }
        }
    }
}
