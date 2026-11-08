package com.metrowin.launcher.data

import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry

/**
 * Several Start screens, each a list of entries. An app is pinned on one screen at most, so most operations find
 * the screen that holds the entry and change only that one, with the same functions as for a single screen
 * ([StartEntries]).
 */
object StartPages {
    /**
     * The screen that has the clock and date (and the voice icon): the home one, always, whatever the number of screens and wherever the
     * secondary ones are. Before the screens are read there is one, and the clock is on it.
     */
    fun clockPage(home: Int, count: Int): Int = home.coerceIn(0, (count - 1).coerceAtLeast(0))

    /**
     * [settings] with the home and the favourite Start screen naming screens that exist: with fewer screens than before, a home that was
     * the last one would point at nothing, and with more again at an empty screen that is not the one the user has Start on.
     */
    fun settled(settings: LauncherSettings): LauncherSettings {
        val last = settings.startPages - 1
        val home = settings.homePage.coerceIn(0, last)
        val favorite = if (settings.favoritePage > last) -1 else settings.favoritePage
        return if (home == settings.homePage && favorite == settings.favoritePage) settings else settings.copy(homePage = home, favoritePage = favorite)
    }

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

    /**
     * The screen the user has Start on: the home, unless the home is empty and another screen is not (a phone that had the secondary Start
     * set to the left before the main screen followed the home): then the fullest one.
     */
    fun mainPage(pages: List<List<StartEntry>>, home: Int): Int {
        val h = home.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        if (pages.getOrNull(h)?.isNotEmpty() != false) return h
        return pages.indices.maxByOrNull { pages[it].size }?.takeIf { pages[it].isNotEmpty() } ?: h
    }

    /** Every entry of [ids] to the end of screen [target], in the order they have now, so a group keeps its tiles one after the other. */
    fun moveManyToPage(pages: List<List<StartEntry>>, ids: Set<String>, target: Int): List<List<StartEntry>> =
        pages.flatten().map { it.id }.filter { it in ids }.fold(pages) { acc, id -> moveToPage(acc, id, target) }

    /** Screen [from] put at place [to], the others keeping their order: the Start the user knows follows the home when it moves. */
    fun movePage(pages: List<List<StartEntry>>, from: Int, to: Int): List<List<StartEntry>> {
        if (from !in pages.indices || to !in pages.indices || from == to) return pages
        return pages.toMutableList().also { it.add(to, it.removeAt(from)) }
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
        // On another screen its cell may be taken: it goes where the flow puts it.
        val moved = StartEntries.unplaced(pages[from].first { it.id == entryId })
        return pages.mapIndexed { index, entries ->
            when (index) {
                from -> entries.filterNot { it.id == entryId }
                to -> entries + moved
                else -> entries
            }
        }
    }

    /**
     * Puts the app [appId] in the folder [folderId], from wherever it is: pinned as a tile, inside another folder, or
     * not on Start at all. Nothing changes when there is no such folder or the app is in it already.
     */
    fun addToFolder(pages: List<List<StartEntry>>, appId: String, folderId: String): List<List<StartEntry>> {
        val target = pages.flatten().firstOrNull { it.id == folderId } as? Folder ?: return pages
        if (appId in target.children) return pages
        return pages.map { page ->
            StartEntries.unpin(page, appId).map { entry ->
                if (entry is Folder && entry.id == folderId) entry.copy(children = entry.children + appId) else entry
            }
        }
    }

    fun prune(pages: List<List<StartEntry>>, known: Set<String>): List<List<StartEntry>> =
        pages.map { StartEntries.prune(it, known) }

    /** Every app id that is pinned on any screen, including those inside folders. */
    fun pinnedIds(pages: List<List<StartEntry>>): Set<String> =
        pages.flatten().flatMapTo(HashSet()) { entry -> if (entry is Folder) entry.children + entry.id else listOf(entry.id) }
}
