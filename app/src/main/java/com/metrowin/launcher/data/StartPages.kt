package com.metrowin.launcher.data

import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry

/**
 * Several Start screens, each a list of entries. An app is pinned on one screen at most, so most operations find
 * the screen that holds the entry and change only that one, with the same functions as for a single screen
 * ([StartEntries]).
 */
object StartPages {
    /** Index of the screen holding [id] (as a tile, a folder or an app inside a folder), or -1. */
    fun pageOf(pages: List<List<StartEntry>>, id: String): Int =
        pages.indexOfFirst { page -> StartEntries.isPinned(page, id) }

    fun isPinned(pages: List<List<StartEntry>>, id: String) = pageOf(pages, id) >= 0

    fun all(pages: List<List<StartEntry>>): List<StartEntry> = pages.flatten()

    /** Exactly [count] screens: the entries of screens that go away join the last one that stays. */
    fun resize(pages: List<List<StartEntry>>, count: Int): List<List<StartEntry>> {
        val kept = pages.take(count).toMutableList()
        while (kept.size < count) kept.add(emptyList())
        val dropped = pages.drop(count).flatten()
        if (dropped.isNotEmpty()) kept[count - 1] = kept[count - 1] + dropped
        return kept
    }

    /** Pins [id] at the end of screen [page], unless it is pinned somewhere already. */
    fun pin(pages: List<List<StartEntry>>, id: String, page: Int): List<List<StartEntry>> {
        if (isPinned(pages, id)) return pages
        val target = page.coerceIn(0, pages.lastIndex)
        return pages.mapIndexed { index, entries -> if (index == target) StartEntries.pin(entries, id) else entries }
    }

    fun unpin(pages: List<List<StartEntry>>, id: String): List<List<StartEntry>> =
        pages.map { StartEntries.unpin(it, id) }

    /** Applies [change] to the screen that holds [id]. Nothing changes when no screen does. */
    fun update(
        pages: List<List<StartEntry>>,
        id: String,
        change: (List<StartEntry>) -> List<StartEntry>,
    ): List<List<StartEntry>> {
        val index = pageOf(pages, id)
        if (index < 0) return pages
        return pages.mapIndexed { i, entries -> if (i == index) change(entries) else entries }
    }

    /** Moves a tile or folder of the top level to the end of screen [target]. Apps inside folders stay where they are. */
    fun moveToPage(pages: List<List<StartEntry>>, entryId: String, target: Int): List<List<StartEntry>> {
        val to = target.coerceIn(0, pages.lastIndex)
        val from = pages.indexOfFirst { page -> page.any { it.id == entryId } }
        if (from < 0 || from == to) return pages
        val moved = pages[from].first { it.id == entryId }
        return pages.mapIndexed { index, entries ->
            when (index) {
                from -> entries.filterNot { it.id == entryId }
                to -> entries + moved
                else -> entries
            }
        }
    }

    fun prune(pages: List<List<StartEntry>>, known: Set<String>): List<List<StartEntry>> =
        pages.map { StartEntries.prune(it, known) }

    /** Every app id that is pinned on any screen, including those inside folders. */
    fun pinnedIds(pages: List<List<StartEntry>>): Set<String> =
        pages.flatten().flatMapTo(HashSet()) { entry -> if (entry is Folder) entry.children + entry.id else listOf(entry.id) }
}
