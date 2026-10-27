package com.metrowin.launcher.data

import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import org.json.JSONArray
import org.json.JSONObject

/** JSON form of what is pinned to Start. Kept apart from Android storage so it can be unit tested. */
object TileCodec {
    fun encode(entries: List<StartEntry>): String = JSONArray().apply {
        entries.forEach { entry ->
            put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("size", entry.size.code)
                    if (entry is Tile && entry.style != TileStyle.Standard) put("style", entry.style.code)
                    if (entry.color != 0) put("color", entry.color)
                    if (entry.group.isNotEmpty()) put("group", entry.group)
                    if (entry is Folder) {
                        put("type", "folder")
                        put("name", entry.name)
                        put("children", JSONArray(entry.children))
                    }
                },
            )
        }
    }.toString()

    /** Null when [json] is not something [encode] wrote. Entries without a type are tiles, as in version 1. */
    fun decode(json: String): List<StartEntry>? = runCatching {
        val array = JSONArray(json)
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            val size = TileSize.fromCode(obj.optString("size"))
            if (obj.optString("type") == "folder") {
                val children = obj.getJSONArray("children")
                Folder(obj.getString("id"), obj.optString("name"), size, List(children.length()) { children.getString(it) }, colorOf(obj), obj.optString("group"))
            } else {
                Tile(obj.getString("id"), size, TileStyle.fromCode(obj.optString("style")), colorOf(obj), obj.optString("group"))
            }
        }
    }.getOrNull()

    /** The color kept for an entry, made opaque: a color saved with any transparency would show the page through the tile. */
    private fun colorOf(obj: JSONObject): Int = obj.optInt("color", 0).let { if (it == 0) 0 else it or (0xFF shl 24) }

    /** Several Start screens: an array with one array of entries per screen. */
    fun encodePages(pages: List<List<StartEntry>>): String = JSONArray().apply {
        pages.forEach { put(JSONArray(encode(it))) }
    }.toString()

    /** Reads [encodePages], or a single list of entries written by an older version as the first screen. */
    fun decodePages(json: String): List<List<StartEntry>>? = runCatching {
        val array = JSONArray(json)
        if (array.length() > 0 && array.get(0) is JSONObject) {
            listOf(decode(json) ?: return@runCatching null)
        } else {
            List(array.length()) { decode(array.getJSONArray(it).toString()) ?: return@runCatching null }
        }
    }.getOrNull()
}

/** The Start screen of a fresh install: modelled on the default Windows Phone 8.1 Start. */
object DefaultStart {
    private const val MIN_TILES = 6

    private val layout = listOf(
        "phone" to TileSize.Medium,
        "people" to TileSize.Medium,
        "messaging" to TileSize.Medium,
        "mail" to TileSize.Medium,
        "internetexplorer" to TileSize.Wide,
        "photos" to TileSize.Wide,
        "calendar" to TileSize.Medium,
        "camera" to TileSize.Medium,
        "store" to TileSize.Medium,
        "music" to TileSize.Medium,
        "maps" to TileSize.Medium,
        "settings" to TileSize.Medium,
        "alarms" to TileSize.Small,
        "calculator" to TileSize.Small,
        "files" to TileSize.Small,
        "wallet" to TileSize.Small,
    )

    fun build(items: List<AppItem>): List<Tile> {
        val available = items.mapTo(HashSet()) { it.id }
        val tiles = layout
            .map { (key, size) -> Tile("stock:$key", size) }
            .filter { it.id in available }
            .toMutableList()
        // A device with few recognisable stock apps still gets a usable Start screen.
        val pinned = tiles.mapTo(HashSet()) { it.id }
        items.filter { it.id !in pinned }.take((MIN_TILES - tiles.size).coerceAtLeast(0)).forEach {
            tiles += Tile(it.id, TileSize.Medium)
        }
        return tiles
    }
}

class TileStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    /** The Start screens, or null on a fresh install. Reads what version 1 saved (one list) as the first screen. */
    fun loadPages(): List<List<StartEntry>>? =
        prefs.getString(KEY_PAGES, null)?.let(TileCodec::decodePages)
            ?: prefs.getString(KEY_TILES, null)?.let(TileCodec::decode)?.let { listOf(it) }

    fun savePages(pages: List<List<StartEntry>>) {
        prefs.edit { putString(KEY_PAGES, TileCodec.encodePages(pages)) }
    }

    /** The apps opened last, most recent first, for the star of the jump list. */
    var recentApps: List<String>
        get() = RecentApps.decode(prefs.getString(KEY_RECENT, null))
        set(value) = prefs.edit { putString(KEY_RECENT, RecentApps.encode(value)) }

    /** How many times each app was opened from the launcher (see [AppUsage]); kept apart from the settings. */
    var appUsage: Map<String, Int>
        get() = AppUsage.decode(prefs.getString(KEY_USAGE, null))
        set(value) = prefs.edit { putString(KEY_USAGE, AppUsage.encode(value)) }

    /** What was searched for last (see [RecentSearches]), most recent first. */
    var recentSearches: List<String>
        get() = RecentSearches.decode(prefs.getString(KEY_SEARCHES, null))
        set(value) = prefs.edit { putString(KEY_SEARCHES, RecentSearches.encode(value)) }

    var tutorialDone: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_TUTORIAL_DONE, value) }

    private companion object {
        const val KEY_TILES = "tiles"
        const val KEY_PAGES = "pages"
        const val KEY_RECENT = "recent_apps"
        const val KEY_USAGE = "app_usage"
        const val KEY_SEARCHES = "recent_searches"
        const val KEY_TUTORIAL_DONE = "permissions_handled"
    }
}
