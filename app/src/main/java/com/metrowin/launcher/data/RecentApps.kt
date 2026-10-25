package com.metrowin.launcher.data

import org.json.JSONArray

/** The apps opened last, most recent first. Pure functions, so the rules can be unit tested. */
object RecentApps {
    const val MAX = 10

    /** [id] goes to the front; if it was already there it moves, it is not listed twice. */
    fun record(recent: List<String>, id: String, max: Int = MAX): List<String> =
        (listOf(id) + recent.filter { it != id }).take(max)

    /** The most recent app that [visible] still shows (it may have been uninstalled or hidden since). */
    fun lastVisible(recent: List<String>, visible: Set<String>): String? = recent.firstOrNull { it in visible }

    fun encode(recent: List<String>): String = JSONArray(recent).toString()

    fun decode(json: String?): List<String> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())
}
