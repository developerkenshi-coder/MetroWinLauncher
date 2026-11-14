package com.metrowin.launcher.todo

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How a thing to do comes back after it is done: its date moves on by a day, a week or a month. */
enum class Repeat { None, Daily, Weekly, Monthly }

/** A step of a thing to do. */
data class Sub(val text: String, val done: Boolean = false)

/**
 * A thing to do. [due] is when (epoch milliseconds, 0 for no date); [reminderId] is the reminder of the launcher that rings at that
 * time (0 for none, see [TodoReminders]); [list] is the name of the list it is in ("" is the main one).
 */
data class TodoItem(
    val id: Long,
    val text: String,
    val done: Boolean = false,
    val due: Long = 0L,
    val starred: Boolean = false,
    val list: String = "",
    val reminderId: Long = 0L,
    val created: Long = 0L,
    /** Where the user put it by dragging (1, 2, 3… among the items of its section); 0 while it was never moved, which puts it after the moved ones. */
    val order: Long = 0L,
    /** When it was ticked (0 for an item ticked before this was kept, or not done). */
    val completedAt: Long = 0L,
    val note: String = "",
    val repeat: Repeat = Repeat.None,
    val subs: List<Sub> = emptyList(),
)

/** The things to do and what is done with them. Pure, so it is tested with words and dates. */
object Todos {
    const val MAX_ITEMS = 500
    const val MAX_TEXT = 200
    const val MAX_LIST = 40
    const val MAX_NOTE = 500
    const val MAX_SUBS = 30
    const val MAX_SUB_TEXT = 100

    /** The date of an item made for "in one hour" when none was set: the dialog starts there. */
    const val DEFAULT_DUE_OFFSET_MS = 3_600_000L

    /** Where an open item goes in the page: what is late, what is for today, what is for later, and what has no date. */
    enum class Section { Late, Today, Later, NoDate }

    /** How the open page groups what is open: by when (the sections above), by day, or by list. */
    enum class GroupBy { Section, Day, List }

    /** What the open page leaves out. */
    enum class Filter { All, Starred, Late, Dated }

    /** A run of rows under one heading; [key] is stable, so the list keeps its place when a group changes. */
    data class Group(val key: String, val section: Section?, val day: LocalDate?, val list: String?, val items: List<TodoItem>)

    fun clean(text: String): String? = text.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.take(MAX_TEXT)

    fun cleanList(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_LIST)

    fun cleanNote(note: String): String = note.trim().take(MAX_NOTE)

    fun nextId(items: List<TodoItem>): Long = (items.maxOfOrNull { it.id } ?: 0L) + 1L

    fun isFull(items: List<TodoItem>): Boolean = items.size >= MAX_ITEMS

    /** [items] with a new one, or as they are when the text is empty or the list is full. [id] is the one to give it (the store never reuses one). */
    fun add(
        items: List<TodoItem>, text: String, due: Long = 0L, list: String = "", starred: Boolean = false, now: Long = 0L,
        id: Long = nextId(items),
    ): List<TodoItem> {
        val clean = clean(text) ?: return items
        if (isFull(items)) return items
        return items + TodoItem(id, clean, due = due, list = cleanList(list), starred = starred, created = now)
    }

    fun replace(items: List<TodoItem>, item: TodoItem): List<TodoItem> = items.map { if (it.id == item.id) item else it }

    fun remove(items: List<TodoItem>, id: Long): List<TodoItem> = items.filter { it.id != id }

    fun clearDone(items: List<TodoItem>): List<TodoItem> = items.filter { !it.done }

    /** What was taken away, put back (what the list can still hold; one whose id is back already is not doubled). */
    fun restore(items: List<TodoItem>, removed: List<TodoItem>): List<TodoItem> {
        val ids = items.map { it.id }.toSet()
        return items + removed.filter { it.id !in ids }.take((MAX_ITEMS - items.size).coerceAtLeast(0))
    }

    /** [item] ticked or unticked at [now]. */
    fun toggled(item: TodoItem, now: Long): TodoItem =
        if (item.done) item.copy(done = false, completedAt = 0L) else item.copy(done = true, completedAt = now)

    /** The next time of a thing that repeats, the first after [now] (a day or two missed do not bring back the days between). */
    fun nextDue(due: Long, repeat: Repeat, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (repeat == Repeat.None || due == 0L) return 0L
        var at = Instant.ofEpochMilli(due).atZone(zone).toLocalDateTime()
        val limit = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        var guard = 0
        do {
            at = when (repeat) {
                Repeat.Daily -> at.plusDays(1)
                Repeat.Weekly -> at.plusWeeks(1)
                else -> at.plusMonths(1)
            }
        } while (!at.isAfter(limit) && ++guard < MAX_CATCH_UP)
        return at.atZone(zone).toInstant().toEpochMilli()
    }

    private const val MAX_CATCH_UP = 4_000

    /** The new open copy a ticked item that repeats leaves behind (null when it does not repeat, or has no date). */
    fun spawn(done: TodoItem, id: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): TodoItem? {
        if (!done.done || done.repeat == Repeat.None || done.due == 0L) return null
        return done.copy(
            id = id, done = false, due = nextDue(done.due, done.repeat, now, zone), reminderId = 0L, created = now, order = 0L,
            completedAt = 0L, subs = done.subs.map { it.copy(done = false) },
        )
    }

    fun section(item: TodoItem, now: Long, zone: ZoneId = ZoneId.systemDefault()): Section {
        if (item.due == 0L) return Section.NoDate
        val day = Instant.ofEpochMilli(item.due).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            day.isBefore(today) -> Section.Late
            day == today -> if (item.due < now) Section.Late else Section.Today
            else -> Section.Later
        }
    }

    private fun placing(): Comparator<TodoItem> =
        compareBy<TodoItem> { it.order == 0L }.thenBy { it.order }.thenByDescending { it.starred }.thenBy { if (it.due == 0L) Long.MAX_VALUE else it.due }.thenBy { it.created }

    /** The open items of [list] (all of them when it is null) as the page shows them: by section, then the ones the user placed, then starred first, then by date. */
    fun open(items: List<TodoItem>, list: String?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Map<Section, List<TodoItem>> =
        items.filter { !it.done && (list == null || it.list == list) }
            .sortedWith(placing())
            .groupBy { section(it, now, zone) }
            .toSortedMap()

    /** The open items as groups under headings: [by] says which; [filter] and [query] leave some out. */
    fun groups(
        items: List<TodoItem>, list: String?, by: GroupBy, now: Long, filter: Filter = Filter.All, query: String = "",
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Group> {
        val shown = search(applyFilter(items.filter { !it.done && (list == null || it.list == list) }, filter, now, zone), query).sortedWith(placing())
        return when (by) {
            GroupBy.Section -> shown.groupBy { section(it, now, zone) }.toSortedMap().map { (s, rows) -> Group("s:$s", s, null, null, rows) }
            GroupBy.Day -> shown.groupBy { if (it.due == 0L) null else Instant.ofEpochMilli(it.due).atZone(zone).toLocalDate() }
                .toList().sortedWith(compareBy(nullsLast()) { it.first }).map { (d, rows) -> Group("d:${d ?: "none"}", null, d, null, rows) }
            GroupBy.List -> shown.groupBy { it.list }.toList().sortedWith(compareBy<Pair<String, List<TodoItem>>> { it.first.isNotEmpty() }.thenBy { it.first.lowercase() })
                .map { (l, rows) -> Group("l:$l", null, null, l, rows) }
        }
    }

    fun applyFilter(items: List<TodoItem>, filter: Filter, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<TodoItem> = when (filter) {
        Filter.All -> items
        Filter.Starred -> items.filter { it.starred }
        Filter.Late -> items.filter { !it.done && section(it, now, zone) == Section.Late }
        Filter.Dated -> items.filter { it.due != 0L }
    }

    /** The items that have [query] (any case) in the text, the note, a step or the list. */
    fun search(items: List<TodoItem>, query: String): List<TodoItem> {
        val q = query.trim()
        if (q.isEmpty()) return items
        return items.filter { it.text.contains(q, true) || it.note.contains(q, true) || it.list.contains(q, true) || it.subs.any { s -> s.text.contains(q, true) } }
    }

    /** The done items of [list], the last one done first (a missing time of completion falls back to the id). */
    fun finished(items: List<TodoItem>, list: String?, query: String = ""): List<TodoItem> =
        search(items.filter { it.done && (list == null || it.list == list) }, query).sortedWith(compareByDescending<TodoItem> { it.completedAt }.thenByDescending { it.id })

    /** [items] with the ones in [ids] placed one after the other in that order, as the page shows them after a drag. */
    fun reorder(items: List<TodoItem>, ids: List<Long>): List<TodoItem> {
        val place = ids.withIndex().associate { (i, id) -> id to i + 1L }
        return items.map { item -> place[item.id]?.let { item.copy(order = it) } ?: item }
    }

    /** [ids] with [id] moved by [delta] places (the step of the actions for a screen reader): as they are when it is at the end already. */
    fun moved(ids: List<Long>, id: Long, delta: Int): List<Long> {
        val from = ids.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return ids
        return ids.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun lists(items: List<TodoItem>): List<String> = items.map { it.list }.filter { it.isNotEmpty() }.distinct().sorted()

    fun openCount(items: List<TodoItem>): Int = items.count { !it.done }

    fun openCount(items: List<TodoItem>, list: String?): Int = items.count { !it.done && (list == null || it.list == list) }

    /** The list called [from] renamed (merged into [to] when that is a list already); a blank name leaves things as they are. */
    fun renameList(items: List<TodoItem>, from: String, to: String): List<TodoItem> {
        val name = cleanList(to)
        if (name.isEmpty()) return items
        return items.map { if (it.list == from) it.copy(list = name) else it }
    }

    /** The list called [name] taken away: its items go to the main list, none is lost. */
    fun deleteList(items: List<TodoItem>, name: String): List<TodoItem> = items.map { if (it.list == name) it.copy(list = "") else it }

    // --- how it went

    class Stats(val open: Int, val done: Int, val late: Int, val doneWeek: Int, val perDay: List<Int>)

    /** How many were done on each of the last [days] days (the last number is today), and the totals. */
    fun stats(items: List<TodoItem>, now: Long, days: Int = 7, zone: ZoneId = ZoneId.systemDefault()): Stats {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val per = IntArray(days)
        items.filter { it.done && it.completedAt != 0L }.forEach {
            val ago = java.time.temporal.ChronoUnit.DAYS.between(Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate(), today).toInt()
            if (ago in 0 until days) per[days - 1 - ago]++
        }
        return Stats(items.count { !it.done }, items.count { it.done }, items.count { !it.done && section(it, now, zone) == Section.Late }, per.sum(), per.toList())
    }

    /** What the day holds: [open] is what is for today and not done, [late] what is overdue, [done] what was ticked today. */
    class Day(val open: Int, val late: Int, val done: Int) {
        val total get() = open + late + done
        val progress get() = if (total == 0) 0f else done.toFloat() / total
    }

    fun today(items: List<TodoItem>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Day {
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val open = items.filter { !it.done }
        return Day(
            open.count { section(it, now, zone) == Section.Today },
            open.count { section(it, now, zone) == Section.Late },
            items.count { it.done && it.completedAt != 0L && Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() == day },
        )
    }

    // --- JSON

    fun encode(items: List<TodoItem>): String = JSONArray().also { array -> items.forEach { array.put(encodeItem(it)) } }.toString()

    private fun encodeItem(it: TodoItem): JSONObject = JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("due", it.due).put("star", it.starred)
        .put("list", it.list).put("rid", it.reminderId).put("created", it.created).put("ord", it.order).also { o ->
            if (it.completedAt != 0L) o.put("doneAt", it.completedAt)
            if (it.note.isNotEmpty()) o.put("note", it.note)
            if (it.repeat != Repeat.None) o.put("rep", it.repeat.name)
            if (it.subs.isNotEmpty()) o.put("subs", JSONArray().also { a -> it.subs.forEach { s -> a.put(JSONObject().put("t", s.text).put("d", s.done)) } })
        }

    private fun decodeItem(o: JSONObject): TodoItem? {
        val text = clean(o.getString("text")) ?: return null
        val subs = o.optJSONArray("subs")?.let { a ->
            (0 until minOf(a.length(), MAX_SUBS)).mapNotNull { i ->
                a.optJSONObject(i)?.let { s -> s.optString("t").trim().take(MAX_SUB_TEXT).takeIf { t -> t.isNotEmpty() }?.let { t -> Sub(t, s.optBoolean("d")) } }
            }
        }.orEmpty()
        return TodoItem(
            o.getLong("id"), text, o.optBoolean("done"), o.optLong("due"), o.optBoolean("star"),
            cleanList(o.optString("list")), o.optLong("rid"), o.optLong("created"), o.optLong("ord"),
            o.optLong("doneAt"), cleanNote(o.optString("note")), Repeat.entries.firstOrNull { r -> r.name == o.optString("rep") } ?: Repeat.None, subs,
        )
    }

    fun decode(json: String?): List<TodoItem> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until minOf(array.length(), MAX_ITEMS)).mapNotNull { i -> runCatching { decodeItem(array.getJSONObject(i)) }.getOrNull() }
    }.getOrDefault(emptyList())

    /** Whether [json] is a list that [decode] reads whole: nothing damaged, nothing past the limit. A blank or missing text is a valid empty list. */
    fun intact(json: String?): Boolean {
        if (json.isNullOrBlank()) return true
        return runCatching {
            val array = JSONArray(json)
            array.length() <= MAX_ITEMS && (0 until array.length()).all { decodeItem(array.getJSONObject(it)) != null }
        }.getOrDefault(false)
    }
}
