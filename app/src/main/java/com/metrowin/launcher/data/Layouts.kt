package com.metrowin.launcher.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * A Start screen kept under a name (Home, Work, Travel): its pages and the shortcuts they hold, as the files of a backup write them.
 * [hour] is the hour of the day (0 to 23) from which the layout takes Start over by itself when the schedule is on, or -1 for none.
 */
@Immutable
data class SavedLayout(val id: String, val name: String, val hour: Int, val pages: String, val shortcuts: String)

/** The layouts as a JSON text, and the choice of which one the clock asks for. Pure, so it is tested. */
object Layouts {
    const val MAX = 8
    const val MAX_NAME = 24

    fun encode(layouts: List<SavedLayout>): String = JSONArray().apply {
        layouts.forEach { l ->
            put(JSONObject().put("id", l.id).put("name", l.name).put("hour", l.hour).put("pages", l.pages).put("shortcuts", l.shortcuts))
        }
    }.toString()

    fun decode(json: String?): List<SavedLayout> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val id = o.optString("id").ifEmpty { return@mapNotNull null }
            SavedLayout(id, o.optString("name").take(MAX_NAME), o.optInt("hour", -1).takeIf { it in 0..23 } ?: -1, o.optString("pages"), o.optString("shortcuts"))
        }.take(MAX)
    }.getOrDefault(emptyList())

    /**
     * The layout the schedule wants at [hour]: the one whose hour came last (the last of yesterday before the first of today), or null when
     * no layout has an hour. Two layouts with the same hour: the one saved first.
     */
    fun due(layouts: List<SavedLayout>, hour: Int): SavedLayout? {
        val scheduled = layouts.filter { it.hour in 0..23 }
        if (scheduled.isEmpty()) return null
        return scheduled.filter { it.hour <= hour }.maxByOrNull { it.hour } ?: scheduled.maxByOrNull { it.hour }
    }
}

class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    fun load(): List<SavedLayout> = Layouts.decode(prefs.getString(KEY, null))

    fun save(layouts: List<SavedLayout>) = prefs.edit { putString(KEY, Layouts.encode(layouts)) }

    /** The layout the schedule asked for last, so a layout chosen by hand is not taken back at the next return to Start. */
    var lastScheduled: String
        get() = prefs.getString(KEY_LAST, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_LAST, value) }

    private companion object {
        const val KEY = "layouts"
        const val KEY_LAST = "layouts_last_scheduled"
    }
}
