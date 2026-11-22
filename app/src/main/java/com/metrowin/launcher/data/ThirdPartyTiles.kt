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

    /** Answers `version=` [PROTOCOL] (the contract an app is written against, see docs/progetti/tile-sdk.md); the three fields of 1 never change meaning. */
    const val METHOD_VERSION = "version"
    const val KEY_VERSION = "version"
    const val PROTOCOL = 1

    /** An app that changes its tile more often than this is told "too fast": a tile is not a stream (the same values again are no change and always accepted). */
    const val MIN_INTERVAL_MS = 250L
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
        // The same words again change nothing, and an app that says them every few seconds must not make the launcher write them
        // to the disk and redraw its tile every time.
        current[packageName]?.let { if (it.title == tile.title && it.text == tile.text && it.count == tile.count) return current }
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

    /** When each app last changed its tile (kept only in memory, trimmed): the rate limit of [handle]. */
    private val lastChange = HashMap<String, Long>()

    private fun prefs(context: Context) = Prefs.open(context.applicationContext, Prefs.TILES)

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
            if (updated == _tiles.value) return
            _tiles.value = updated
            prefs(context).edit { putString("tiles", ThirdPartyTiles.encode(updated)) }
        }
    }

    fun clear(context: Context, packageName: String) {
        synchronized(lastChange) { lastChange.remove(packageName) }
        update(context) { ThirdPartyTiles.clear(it, packageName) }
    }

    fun clearAll(context: Context) {
        synchronized(lastChange) { lastChange.clear() }
        update(context) { emptyMap() }
    }

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
        // The version is told even when the feature is off: a client uses it to choose its fields, and "turned off" comes with its first publish.
        if (method == ThirdPartyTiles.METHOD_VERSION) return answer(true).apply { putInt(ThirdPartyTiles.KEY_VERSION, ThirdPartyTiles.PROTOCOL) }
        if (!enabled) return answer(false, "turned off by the user")
        return when (method) {
            ThirdPartyTiles.METHOD_PUBLISH -> {
                val title = extras?.getString(ThirdPartyTiles.KEY_TITLE)
                val text = extras?.getString(ThirdPartyTiles.KEY_TEXT)
                val count = extras?.getInt(ThirdPartyTiles.KEY_COUNT, 0) ?: 0
                load(context)
                val before = _tiles.value
                // The same values again are no change: accepted, nothing written. A change that comes too soon after the last one is refused.
                if (ThirdPartyTiles.publish(before, caller, title, text, count, now) != before) {
                    synchronized(lastChange) {
                        val last = lastChange[caller]
                        if (last != null && now - last in 0 until ThirdPartyTiles.MIN_INTERVAL_MS) return answer(false, "too fast")
                        lastChange[caller] = now
                        if (lastChange.size > ThirdPartyTiles.MAX_TILES * 4) lastChange.entries.removeAll { now - it.value > 60_000 }
                    }
                    update(context) { ThirdPartyTiles.publish(it, caller, title, text, count, now) }
                }
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
