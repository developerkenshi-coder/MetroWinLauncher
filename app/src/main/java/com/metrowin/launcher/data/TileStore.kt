package com.metrowin.launcher.data

import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
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
                Folder(obj.getString("id"), obj.optString("name"), size, List(children.length()) { children.getString(it) })
            } else {
                Tile(obj.getString("id"), size)
            }
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

    fun loadTiles(): List<StartEntry>? = prefs.getString(KEY_TILES, null)?.let(TileCodec::decode)

    fun saveTiles(tiles: List<StartEntry>) {
        prefs.edit { putString(KEY_TILES, TileCodec.encode(tiles)) }
    }

    var permissionsHandled: Boolean
        get() = prefs.getBoolean(KEY_PERMISSIONS_HANDLED, false)
        set(value) = prefs.edit { putBoolean(KEY_PERMISSIONS_HANDLED, value) }

    private companion object {
        const val KEY_TILES = "tiles"
        const val KEY_PERMISSIONS_HANDLED = "permissions_handled"
    }
}
