package com.metrowin.launcher.todo

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

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
)

/** The things to do and what is done with them. Pure, so it is tested with words and dates. */
object Todos {
    const val MAX_ITEMS = 500
    const val MAX_TEXT = 200
    const val MAX_LIST = 40

    /** Where an open item goes in the page: what is late, what is for today, what is for later, and what has no date. */
    enum class Section { Late, Today, Later, NoDate }

    fun clean(text: String): String? = text.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.take(MAX_TEXT)

    fun cleanList(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_LIST)

    fun nextId(items: List<TodoItem>): Long = (items.maxOfOrNull { it.id } ?: 0L) + 1L

    /** [items] with a new one, or as they are when the text is empty or the list is full. */
    fun add(items: List<TodoItem>, text: String, due: Long = 0L, list: String = "", starred: Boolean = false, now: Long = 0L): List<TodoItem> {
        val clean = clean(text) ?: return items
        if (items.size >= MAX_ITEMS) return items
        return items + TodoItem(nextId(items), clean, due = due, list = cleanList(list), starred = starred, created = now)
    }

    fun replace(items: List<TodoItem>, item: TodoItem): List<TodoItem> = items.map { if (it.id == item.id) item else it }

    fun remove(items: List<TodoItem>, id: Long): List<TodoItem> = items.filter { it.id != id }

    fun clearDone(items: List<TodoItem>): List<TodoItem> = items.filter { !it.done }

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

    /** The open items of [list] (all of them when it is null) as the page shows them: by section, then the ones the user placed, then starred first, then by date. */
    fun open(items: List<TodoItem>, list: String?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Map<Section, List<TodoItem>> =
        items.filter { !it.done && (list == null || it.list == list) }
            .sortedWith(compareBy<TodoItem> { it.order == 0L }.thenBy { it.order }.thenByDescending { it.starred }.thenBy { if (it.due == 0L) Long.MAX_VALUE else it.due }.thenBy { it.created })
            .groupBy { section(it, now, zone) }
            .toSortedMap()

    /** The done items of [list], the last one done first (the highest id stands for it). */
    fun finished(items: List<TodoItem>, list: String?): List<TodoItem> =
        items.filter { it.done && (list == null || it.list == list) }.sortedByDescending { it.id }

    /** [items] with the ones in [ids] placed one after the other in that order, as the page shows them after a drag. */
    fun reorder(items: List<TodoItem>, ids: List<Long>): List<TodoItem> {
        val place = ids.withIndex().associate { (i, id) -> id to i + 1L }
        return items.map { item -> place[item.id]?.let { item.copy(order = it) } ?: item }
    }

    fun lists(items: List<TodoItem>): List<String> = items.map { it.list }.filter { it.isNotEmpty() }.distinct().sorted()

    fun openCount(items: List<TodoItem>): Int = items.count { !it.done }

    fun encode(items: List<TodoItem>): String = JSONArray().also { array ->
        items.forEach {
            array.put(
                JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("due", it.due).put("star", it.starred)
                    .put("list", it.list).put("rid", it.reminderId).put("created", it.created).put("ord", it.order),
            )
        }
    }.toString()

    fun decode(json: String?): List<TodoItem> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until minOf(array.length(), MAX_ITEMS)).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val text = clean(o.getString("text")) ?: return@runCatching null
                TodoItem(
                    o.getLong("id"), text, o.optBoolean("done"), o.optLong("due"), o.optBoolean("star"),
                    cleanList(o.optString("list")), o.optLong("rid"), o.optLong("created"), o.optLong("ord"),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
}
