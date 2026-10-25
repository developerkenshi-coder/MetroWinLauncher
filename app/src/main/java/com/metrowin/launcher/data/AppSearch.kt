package com.metrowin.launcher.data

import com.metrowin.launcher.model.AppItem

/** Typing in the app list: the apps whose name matches, the ones that start with it first. */
object AppSearch {
    /**
     * Matches ignore case and accents, so "fotocamera" finds "Fotocàmera". An app matches when its name,
     * or any word of its name, starts with the query, or when the query is somewhere inside the name.
     * Better matches come first, and within a rank the order of [items] (alphabetical) is kept.
     */
    fun filter(items: List<AppItem>, query: String): List<AppItem> {
        val needle = AppSections.fold(query)
        if (needle.isEmpty()) return items
        return items
            .mapNotNull { item ->
                val name = AppSections.fold(item.label)
                val rank = when {
                    name.startsWith(needle) -> 0
                    name.split(' ', '-', '+', '.', '_').any { it.startsWith(needle) } -> 1
                    needle in name -> 2
                    else -> return@mapNotNull null
                }
                rank to item
            }
            .sortedBy { it.first } // stable: alphabetical within a rank
            .map { it.second }
    }
}
