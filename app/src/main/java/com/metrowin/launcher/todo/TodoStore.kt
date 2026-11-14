package com.metrowin.launcher.todo

import com.metrowin.launcher.data.Prefs
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.ReminderStore
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.notifications.Alerts
import java.util.concurrent.Executors

/** The things to do kept on the phone. */
class TodoStore(context: Context) {
    private val prefs = Prefs.open(context, Prefs.TODO)

    /**
     * The items. When the stored text is damaged the copy kept before the last save is read instead (and the damaged one is kept
     * apart, in case it is worth reading by hand), so one bad write does not empty the page.
     */
    fun load(): List<TodoItem> {
        val current = prefs.getString(KEY, null)
        if (Todos.intact(current)) return Todos.decode(current)
        val previous = prefs.getString(PREVIOUS, null)
        if (previous != null && Todos.intact(previous)) {
            prefs.edit { putString(DAMAGED, current) }
            return Todos.decode(previous)
        }
        return Todos.decode(current)
    }

    /** Saves now, on this thread; use [saveLater] from the screen. */
    fun save(items: List<TodoItem>) {
        val json = Todos.encode(items)
        val old = prefs.getString(KEY, null)
        prefs.edit {
            // The copy before this one is kept only when it was whole, so a damaged text never replaces the good copy.
            if (old != null && old != json && Todos.intact(old)) putString(PREVIOUS, old)
            putString(KEY, json)
        }
    }

    /** Saves on a thread of its own, one after the other in the order asked, so a late save never undoes a newer one. */
    fun saveLater(items: List<TodoItem>) {
        writer.execute { runCatching { save(items) } }
    }

    /** The first of [count] ids that were never used (one is not given twice, even after its item is deleted). */
    @Synchronized
    fun reserve(items: List<TodoItem>, count: Int = 1): Long {
        val first = maxOf(prefs.getLong(NEXT, 1L), Todos.nextId(items))
        prefs.edit { putLong(NEXT, first + count) }
        return first
    }

    private companion object {
        const val KEY = "items"
        const val PREVIOUS = "items_previous"
        const val DAMAGED = "items_damaged"
        const val NEXT = "next_id"
        val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "todo-save").apply { isDaemon = true } }
    }
}

/** What the app of things to do shows and does, set from its "options". */
data class TodoOptions(
    val group: Todos.GroupBy = Todos.GroupBy.Section,
    val view: View = View.Rows,
    val filter: Todos.Filter = Todos.Filter.All,
    /** The number of open items in the page names and the chips of the lists. */
    val counts: Boolean = true,
    /** A row swiped one way is done, the other way deleted (off: the pages are the ones that swipe). */
    val swipe: Boolean = false,
    /** A few small squares fly from the box when something is done. */
    val burst: Boolean = true,
    /** An item opens on a page of its own; off, in a dialog. */
    val editPage: Boolean = true,
    /** A card at the top with what the day holds. */
    val dashboard: Boolean = false,
    /** A button that writes what is said (Android's own speech input), and reads the day from it. */
    val dictation: Boolean = false,
    val large: Boolean = false,
    val contrast: Boolean = false,
    val listColors: Boolean = true,
    /** The first line of the note under the text of an item. */
    val preview: Boolean = true,
) {
    enum class View { Rows, Compact, Kanban }
}

/** The options of the app, in the same file as its items (keys that start with "opt_"). Off by default what touches privacy or the network. */
class TodoOptionStore(context: Context) {
    private val prefs = Prefs.open(context, Prefs.TODO)

    fun load() = TodoOptions(
        group = enumOf(KEY_GROUP, Todos.GroupBy.Section),
        view = enumOf(KEY_VIEW, TodoOptions.View.Rows),
        filter = enumOf(KEY_FILTER, Todos.Filter.All),
        counts = prefs.getBoolean(KEY_COUNTS, true),
        swipe = prefs.getBoolean(KEY_SWIPE, false),
        burst = prefs.getBoolean(KEY_BURST, true),
        editPage = prefs.getBoolean(KEY_EDIT_PAGE, true),
        dashboard = prefs.getBoolean(KEY_DASHBOARD, false),
        dictation = prefs.getBoolean(KEY_DICTATION, false),
        large = prefs.getBoolean(KEY_LARGE, false),
        contrast = prefs.getBoolean(KEY_CONTRAST, false),
        listColors = prefs.getBoolean(KEY_LIST_COLORS, true),
        preview = prefs.getBoolean(KEY_PREVIEW, true),
    )

    fun save(o: TodoOptions) = prefs.edit {
        putString(KEY_GROUP, o.group.name).putString(KEY_VIEW, o.view.name).putString(KEY_FILTER, o.filter.name)
            .putBoolean(KEY_COUNTS, o.counts).putBoolean(KEY_SWIPE, o.swipe).putBoolean(KEY_BURST, o.burst).putBoolean(KEY_EDIT_PAGE, o.editPage)
            .putBoolean(KEY_DASHBOARD, o.dashboard).putBoolean(KEY_DICTATION, o.dictation).putBoolean(KEY_LARGE, o.large)
            .putBoolean(KEY_CONTRAST, o.contrast).putBoolean(KEY_LIST_COLORS, o.listColors).putBoolean(KEY_PREVIEW, o.preview)
    }

    /** The colour chosen for a list: 1 to [COLORS], or 0 while it has none (the screen picks one from its name). */
    fun listColor(name: String): Int = prefs.getInt(COLOR_PREFIX + name, 0)

    fun setListColor(name: String, color: Int) = prefs.edit { if (color == 0) remove(COLOR_PREFIX + name) else putInt(COLOR_PREFIX + name, color) }

    /** The colour of a list follows its name when it is renamed. */
    fun renameListColor(from: String, to: String) {
        val c = listColor(from)
        prefs.edit { remove(COLOR_PREFIX + from) }
        if (c != 0) setListColor(to, c)
    }

    /** Whether the tip about dragging was shown (it is, once, and goes away for good when a row is dragged). */
    var dragTipSeen: Boolean
        get() = prefs.getBoolean(KEY_TIP, false)
        set(value) = prefs.edit { putBoolean(KEY_TIP, value) }

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == prefs.getString(key, null) } ?: default

    companion object {
        const val COLORS = 8
        private const val KEY_GROUP = "opt_group"
        private const val KEY_VIEW = "opt_view"
        private const val KEY_FILTER = "opt_filter"
        private const val KEY_COUNTS = "opt_counts"
        private const val KEY_SWIPE = "opt_swipe"
        private const val KEY_BURST = "opt_burst"
        private const val KEY_EDIT_PAGE = "opt_edit_page"
        private const val KEY_DASHBOARD = "opt_dashboard"
        private const val KEY_DICTATION = "opt_dictation"
        private const val KEY_LARGE = "opt_large"
        private const val KEY_CONTRAST = "opt_contrast"
        private const val KEY_LIST_COLORS = "opt_list_colors"
        private const val KEY_PREVIEW = "opt_preview"
        private const val KEY_TIP = "opt_tip_drag_seen"
        private const val COLOR_PREFIX = "opt_color:"
    }
}

/**
 * The link between a thing to do with a date and the reminders of the launcher: the reminder is the alarm that rings at that time
 * (with its "done" and "later" buttons, set again after a reboot). A thing to do that is done, or has no date, has no reminder.
 * The reminders are read and written by one caller at a time, so two quick changes never write over each other.
 */
object TodoReminders {
    private val lock = Any()

    /** [item] with its reminder made to match: [before] is the item as it was, so a reminder that no longer fits is taken away. */
    fun sync(context: Context, item: TodoItem, before: TodoItem?, now: Long = System.currentTimeMillis()): TodoItem = synchronized(lock) {
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
        result
    }

    /** The reminder of a thing to do that is deleted goes with it. */
    fun release(context: Context, item: TodoItem) = releaseAll(context, listOf(item))

    /** The reminders of all of [items] go, in one write. */
    fun releaseAll(context: Context, items: List<TodoItem>) = synchronized(lock) {
        val ids = items.map { it.reminderId }.filter { it != 0L }
        if (ids.isEmpty()) return@synchronized
        ids.forEach { Alerts.cancelReminder(context, it) }
        val store = ReminderStore(context)
        store.save(store.load().filter { it.id !in ids })
    }

    /** The reminders by id, as they are now. */
    fun reminders(context: Context): Map<Long, Reminder> = synchronized(lock) { ReminderStore(context).load().associateBy { it.id } }

    /** What was done from the notification of a reminder is done here too, and a reminder taken away from the reminders is let go. */
    fun pull(context: Context, items: List<TodoItem>, now: Long = System.currentTimeMillis()): List<TodoItem> = applyReminders(items, reminders(context), now)

    /** [pull] on what is already in memory. */
    fun applyReminders(items: List<TodoItem>, reminders: Map<Long, Reminder>, now: Long = 0L): List<TodoItem> = items.map { item ->
        if (item.reminderId == 0L) return@map item
        val reminder = reminders[item.reminderId]
        when {
            reminder == null -> item.copy(reminderId = 0L)
            reminder.done && !item.done -> item.copy(done = true, reminderId = 0L, completedAt = now)
            reminder.done -> item.copy(reminderId = 0L)
            else -> item
        }
    }

    /** The alarms of the open items that have one, asked again: asking twice sets the same alarm, and one lost (a cleared cache, a restore) comes back. */
    fun verify(context: Context, items: List<TodoItem>, now: Long = System.currentTimeMillis()) {
        val byId = reminders(context)
        items.filter { !it.done && it.reminderId != 0L && it.due > now }.forEach { item ->
            byId[item.reminderId]?.takeIf { !it.done && it.at > now }?.let { Alerts.scheduleReminder(context, it) }
        }
    }

    class Health(val exactAlarms: Boolean, val notifications: Boolean) {
        val ok get() = exactAlarms && notifications
    }

    /** Whether a reminder can ring on time and be seen: both are the choice of the user in the settings of Android. */
    fun health(context: Context): Health = Health(Alerts.canScheduleExact(context), NotificationManagerCompat.from(context).areNotificationsEnabled())
}
