package com.metrowin.launcher.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The playlists of the Music hub: names and lists of song ids in a file of the launcher (MediaStore's own playlists are closed to apps from
 * Android 11). A song that is gone from the phone is simply not shown. Nothing leaves the phone.
 */
class MusicPlaylists(private val read: () -> String?, private val write: (String) -> Unit) {
    constructor(context: Context) : this(
        { context.getSharedPreferences("metrowin_music", Context.MODE_PRIVATE).getString("playlists", null) },
        { context.getSharedPreferences("metrowin_music", Context.MODE_PRIVATE).edit().putString("playlists", it).apply() },
    )

    private fun load(): LinkedHashMap<String, List<Long>> = decode(read())

    fun names(): List<String> = load().keys.toList()

    fun idsOf(name: String): List<Long> = load()[name].orEmpty()

    /** False when [name] is empty or already used (no case): a playlist is told apart by its name. */
    fun create(name: String): Boolean {
        val clean = clean(name)
        val all = load()
        if (clean.isEmpty() || all.keys.any { it.equals(clean, ignoreCase = true) }) return false
        all[clean] = emptyList()
        write(encode(all))
        return true
    }

    fun delete(name: String) {
        val all = load()
        if (all.remove(name) != null) write(encode(all))
    }

    /** Adds [id] at the end of [name], once; false when there is no such playlist. */
    fun add(name: String, id: Long): Boolean {
        val all = load()
        val list = all[name] ?: return false
        if (id !in list) all[name] = list + id
        write(encode(all))
        return true
    }

    fun remove(name: String, id: Long) {
        val all = load()
        val list = all[name] ?: return
        all[name] = list - id
        write(encode(all))
    }

    companion object {
        const val MAX_NAME = 60
        const val MAX_LISTS = 100

        fun clean(name: String) = name.replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim().take(MAX_NAME)

        fun encode(lists: Map<String, List<Long>>): String = JSONArray().apply {
            lists.entries.take(MAX_LISTS).forEach { (name, ids) -> put(JSONObject().put("name", name).put("ids", JSONArray(ids))) }
        }.toString()

        /** A damaged or empty text is no playlists, never a crash. */
        fun decode(text: String?): LinkedHashMap<String, List<Long>> {
            val out = LinkedHashMap<String, List<Long>>()
            runCatching {
                val array = JSONArray(text ?: return out)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val name = clean(item.getString("name"))
                    val ids = item.getJSONArray("ids").let { a -> (0 until a.length()).map { a.getLong(it) } }
                    if (name.isNotEmpty()) out[name] = ids.distinct()
                }
            }
            return out
        }
    }
}
