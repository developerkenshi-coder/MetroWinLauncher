package com.metrowin.launcher.data

import org.json.JSONObject

/**
 * How many times each app was opened from the launcher, for the "most used" group at the top of the app list. The counts
 * stay on the phone, apart from the settings, so a backup or a sync does not carry what you open.
 */
object AppUsage {
    /** An app needs this many opens to count as used often. */
    const val MIN_OPENS = 3
    const val SHOWN = 4

    /** More apps than this are not kept: the least used are forgotten first. */
    private const val KEPT = 150

    fun record(counts: Map<String, Int>, id: String): Map<String, Int> {
        // The app just opened goes first, so among equal counts it is the older ones that are forgotten.
        val updated = linkedMapOf(id to (counts[id] ?: 0) + 1).also { it.putAll(counts - id) }
        if (updated.size <= KEPT) return updated
        return updated.entries.sortedByDescending { it.value }.take(KEPT).associate { it.key to it.value }
    }

    /** The most opened apps among [visible] (an app hidden or uninstalled since is not offered), most opened first. */
    fun top(counts: Map<String, Int>, visible: Set<String>, limit: Int = SHOWN, min: Int = MIN_OPENS): List<String> =
        counts.entries
            .filter { it.key in visible && it.value >= min }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }

    fun encode(counts: Map<String, Int>): String = JSONObject(counts as Map<*, *>).toString()

    fun decode(json: String?): Map<String, Int> = runCatching {
        val o = JSONObject(json ?: return emptyMap())
        o.keys().asSequence().associateWith { o.getInt(it) }.filterValues { it > 0 }
    }.getOrDefault(emptyMap())
}
