package com.metrowin.launcher.data

import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.model.AppItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * A list of the app list, shown as a chip under the search box. A [category] fills it with the apps that declare it (the lists
 * that are there at the first start); [items] are the apps put in by hand. [name] empty means the name of the category.
 */
data class AppList(val id: String, val name: String = "", val category: AppCategory? = null, val items: Set<String> = emptySet())

/** The lists of the app list: the ones there from the start, which can be deleted like any other, and the ones the user makes. Pure, so it is tested. */
object AppLists {
    const val MAX_NAME = 24
    const val MAX_LISTS = 40

    /** One list for each category: what the phone has until the user changes them. */
    val DEFAULTS: List<AppList> = AppCategory.entries.map { AppList(it.code, category = it) }

    /** The apps of [list]: those of its category and those put in by hand. */
    fun members(list: AppList, items: List<AppItem>): List<AppItem> =
        items.filter { (list.category != null && it.category == list.category) || it.id in list.items }

    /** The lists that show as chips: the made ones always, the ones of a category only while some app belongs to them. */
    fun visible(lists: List<AppList>, items: List<AppItem>): List<AppList> =
        lists.filter { it.category == null || members(it, items).isNotEmpty() }

    fun clean(name: String): String? = name.trim().take(MAX_NAME).takeIf { it.isNotEmpty() }

    /** [lists] with a new list called [name] at the end; the same lists when the name is empty or there are too many. */
    fun create(lists: List<AppList>, name: String, id: String): List<AppList> {
        val clean = clean(name) ?: return lists
        return if (lists.size >= MAX_LISTS) lists else lists + AppList(id, clean)
    }

    /** An empty name gives a list of a category back its own name. */
    fun rename(lists: List<AppList>, id: String, name: String): List<AppList> =
        lists.map { if (it.id == id && (clean(name) != null || it.category != null)) it.copy(name = clean(name).orEmpty()) else it }

    fun delete(lists: List<AppList>, id: String): List<AppList> = lists.filter { it.id != id }

    /** Puts app [appId] in the list, or takes it out when it was put in by hand. */
    fun toggle(lists: List<AppList>, id: String, appId: String): List<AppList> =
        lists.map { if (it.id == id) it.copy(items = if (appId in it.items) it.items - appId else it.items + appId) else it }

    fun encode(lists: List<AppList>): JSONArray = JSONArray().apply {
        lists.forEach { list ->
            put(
                JSONObject().apply {
                    put("id", list.id)
                    if (list.name.isNotEmpty()) put("name", list.name)
                    list.category?.let { put("category", it.code) }
                    if (list.items.isNotEmpty()) put("items", JSONArray(list.items.sorted()))
                },
            )
        }
    }

    /** The saved lists; a broken line is skipped, and the ids stay unique. */
    fun decode(array: JSONArray): List<AppList> = buildList {
        for (i in 0 until array.length().coerceAtMost(MAX_LISTS)) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotEmpty() && none { list -> list.id == it } } ?: continue
            val items = o.optJSONArray("items")
            add(
                AppList(
                    id = id.take(64),
                    name = o.optString("name").take(MAX_NAME),
                    category = AppCategory.entries.firstOrNull { it.code == o.optString("category") },
                    items = items?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.take(2000).toSet() } ?: emptySet(),
                ),
            )
        }
    }
}
