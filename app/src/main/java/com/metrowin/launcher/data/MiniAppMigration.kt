package com.metrowin.launcher.data

import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile

/**
 * Start screens saved before the mini apps existed pin the phone, contacts, messages and mail apps of the device
 * as plain apps; once, they become the stock tiles, which open the mini apps of the launcher.
 *
 * shortcut: only the apps named in [MetroCatalog] are recognised, not every app a category resolves to.
 */
object MiniAppMigration {
    private val stockIds = setOf("phone", "people", "messaging", "mail")

    private val stockOfPackage: Map<String, String> by lazy {
        MetroCatalog.apps.filter { it.id in stockIds }
            .flatMap { stock -> stock.targets.filterIsInstance<Target.Pkg>().map { it.name to MetroCatalog.idOf(stock) } }
            .toMap()
    }

    /** The stock id replacing the pinned app [id], or null when it is not one of those apps (or is of another profile). */
    fun stockIdFor(id: String): String? {
        if (!id.startsWith("app:") || '@' in id) return null
        return stockOfPackage[id.removePrefix("app:").substringBefore('/')]
    }

    fun apply(pages: List<List<StartEntry>>): List<List<StartEntry>> {
        val seen = HashSet<String>()
        pages.flatten().forEach { entry ->
            if (entry.id in stockOfPackage.values) seen += entry.id
            if (entry is Folder) seen += entry.children.filter { it in stockOfPackage.values }
        }
        // Keeps the first of the entries that end up with the same id.
        fun swap(id: String): String? {
            val stock = stockIdFor(id) ?: return id
            return if (seen.add(stock)) stock else null
        }
        return pages.map { page ->
            page.mapNotNull { entry ->
                when (entry) {
                    is Tile -> swap(entry.id)?.let { entry.copy(id = it) }
                    is Folder -> entry.copy(children = entry.children.mapNotNull(::swap))
                }
            }
        }
    }
}
