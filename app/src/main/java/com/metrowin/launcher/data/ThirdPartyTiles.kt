package com.metrowin.launcher.data

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.metrowin.launcher.notifications.TilePeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What an app published for its own tile: a title, a line of text and a number. */
@Immutable
data class ThirdPartyTile(val packageName: String, val title: String, val text: String, val count: Int, val updatedAt: Long)

/**
 * The rules of the tiles that apps publish through [TilePublishProvider]. Pure, so they can be tested: what is kept of
 * what an app sends (it is untrusted text), how many tiles are kept, and how they are saved.
 */
object ThirdPartyTiles {
    const val MAX_TILES = 64
    const val MAX_TITLE = 60
    const val MAX_TEXT = 160
    const val MAX_COUNT = 99_999

    const val METHOD_PUBLISH = "publish"
    const val METHOD_CLEAR = "clear"
    const val KEY_TITLE = "title"
    const val KEY_TEXT = "text"
    const val KEY_COUNT = "count"

    /** In the answer of a call. */
    const val KEY_ACCEPTED = "accepted"
    const val KEY_REASON = "reason"

    /** Text for a tile: one line, without control characters, no longer than [max]. */
    fun clean(value: String?, max: Int): String =
        value.orEmpty().map { if (it.isISOControl() || it == ' ' || it == ' ') ' ' else it }
            .joinToString("").replace(Regex(" {2,}"), " ").trim().take(max).trim()

    fun publish(current: Map<String, ThirdPartyTile>, packageName: String, title: String?, text: String?, count: Int, now: Long): Map<String, ThirdPartyTile> {
        val tile = ThirdPartyTile(packageName, clean(title, MAX_TITLE), clean(text, MAX_TEXT), count.coerceIn(0, MAX_COUNT), now)
        val updated = LinkedHashMap(current)
        updated.remove(packageName)
        updated[packageName] = tile
        // A phone full of apps that publish keeps the most recent ones.
        while (updated.size > MAX_TILES) updated.remove(updated.entries.minByOrNull { it.value.updatedAt }!!.key)
        return updated
    }

    fun clear(current: Map<String, ThirdPartyTile>, packageName: String): Map<String, ThirdPartyTile> = current - packageName

    /** What the tiles flip to. A tile with neither title nor text only has its number. */
    fun peeks(tiles: Map<String, ThirdPartyTile>): Map<String, TilePeek> =
        tiles.values.filter { it.title.isNotEmpty() || it.text.isNotEmpty() }.associate { it.packageName to TilePeek(it.title, it.text) }

    fun counts(tiles: Map<String, ThirdPartyTile>): Map<String, Int> =
        tiles.values.filter { it.count > 0 }.associate { it.packageName to it.count }

    fun encode(tiles: Map<String, ThirdPartyTile>): String = JSONArray().apply {
        tiles.values.forEach {
            put(JSONObject().put("pkg", it.packageName).put(KEY_TITLE, it.title).put(KEY_TEXT, it.text).put(KEY_COUNT, it.count).put("at", it.updatedAt))
        }
    }.toString()

    fun decode(json: String?): Map<String, ThirdPartyTile> = runCatching {
        val array = JSONArray(json ?: return emptyMap())
        val tiles = LinkedHashMap<String, ThirdPartyTile>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val pkg = o.getString("pkg").takeIf { it.isNotBlank() } ?: continue
            tiles[pkg] = ThirdPartyTile(
                pkg, clean(o.optString(KEY_TITLE), MAX_TITLE), clean(o.optString(KEY_TEXT), MAX_TEXT),
                o.optInt(KEY_COUNT).coerceIn(0, MAX_COUNT), o.optLong("at"),
            )
        }
        tiles
    }.getOrDefault(emptyMap())
}

/** The tiles apps have published, shared with the rest of the launcher and kept across restarts. */
object TileFeed {
    private val _tiles = MutableStateFlow<Map<String, ThirdPartyTile>>(emptyMap())
    val tiles: StateFlow<Map<String, ThirdPartyTile>> = _tiles.asStateFlow()

    @Volatile
    private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("metrowin_tiles", Context.MODE_PRIVATE)

    /** Reads the saved tiles, once per process. The provider and the launcher both call it first. */
    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _tiles.value = ThirdPartyTiles.decode(prefs(context).getString("tiles", null))
            loaded = true
        }
    }

    private fun update(context: Context, change: (Map<String, ThirdPartyTile>) -> Map<String, ThirdPartyTile>) {
        load(context)
        synchronized(this) {
            val updated = change(_tiles.value)
            _tiles.value = updated
            prefs(context).edit { putString("tiles", ThirdPartyTiles.encode(updated)) }
        }
    }

    fun clear(context: Context, packageName: String) = update(context) { ThirdPartyTiles.clear(it, packageName) }

    fun clearAll(context: Context) = update(context) { emptyMap() }

    /**
     * Answers one call of the provider. [caller] is the package the system says is calling: a tile is only ever
     * published for the caller's own package, whatever the call says.
     */
    fun handle(context: Context, enabled: Boolean, caller: String?, method: String, extras: Bundle?, now: Long = System.currentTimeMillis()): Bundle {
        fun answer(accepted: Boolean, reason: String? = null) = Bundle().apply {
            putBoolean(ThirdPartyTiles.KEY_ACCEPTED, accepted)
            if (reason != null) putString(ThirdPartyTiles.KEY_REASON, reason)
        }
        if (caller.isNullOrBlank()) return answer(false, "unknown caller")
        if (!enabled) return answer(false, "turned off by the user")
        return when (method) {
            ThirdPartyTiles.METHOD_PUBLISH -> {
                val title = extras?.getString(ThirdPartyTiles.KEY_TITLE)
                val text = extras?.getString(ThirdPartyTiles.KEY_TEXT)
                val count = extras?.getInt(ThirdPartyTiles.KEY_COUNT, 0) ?: 0
                update(context) { ThirdPartyTiles.publish(it, caller, title, text, count, now) }
                answer(true)
            }
            ThirdPartyTiles.METHOD_CLEAR -> {
                clear(context, caller)
                answer(true)
            }
            else -> answer(false, "unknown method")
        }
    }
}
