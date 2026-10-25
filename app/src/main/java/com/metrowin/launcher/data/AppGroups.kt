package com.metrowin.launcher.data

import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.model.AppItem

/** The apps installed in the last days, for the "new" group at the top of the app list. */
object NewApps {
    const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    const val SHOWN = 3

    /**
     * More than this many installed in the window means the phone was just set up or restored, not that the user
     * installed that many apps: none is called new then.
     */
    private const val SETUP_BURST = 10

    /** The newest first, at most [limit]. Hidden or Start-only entries are not in [items] (see AppFilter). */
    fun recent(items: List<AppItem>, now: Long, windowMs: Long = WINDOW_MS, limit: Int = SHOWN): List<AppItem> {
        val fresh = items.filter { it.installedAt > 0L && now - it.installedAt in 0..windowMs }
        if (fresh.size > SETUP_BURST) return emptyList()
        return fresh.sortedByDescending { it.installedAt }.take(limit)
    }
}

/** Filtering the app list by what the apps say they are. */
object AppCategories {
    /** The categories that at least one of [items] belongs to, in the order of the enum. */
    fun present(items: List<AppItem>): List<AppCategory> {
        val used = items.mapNotNullTo(HashSet()) { it.category }
        return AppCategory.entries.filter { it in used }
    }

    fun filter(items: List<AppItem>, category: AppCategory): List<AppItem> = items.filter { it.category == category }
}
