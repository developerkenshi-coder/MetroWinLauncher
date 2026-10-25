package com.metrowin.launcher.data

import org.json.JSONArray

/** The last things searched for, most recent first, kept on the phone apart from the settings (a backup does not carry them). */
object RecentSearches {
    const val MAX = 6
    const val MIN_LENGTH = 2

    /** [query] goes to the front; one already there moves (compared without case or accents) instead of being listed twice. */
    fun record(recent: List<String>, query: String, max: Int = MAX): List<String> {
        val clean = query.trim().replace(Regex("\\s+"), " ")
        if (clean.length < MIN_LENGTH) return recent
        val key = AppSections.fold(clean)
        return (listOf(clean) + recent.filter { AppSections.fold(it) != key }).take(max)
    }

    fun encode(recent: List<String>): String = JSONArray(recent).toString()

    fun decode(json: String?): List<String> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())
}
