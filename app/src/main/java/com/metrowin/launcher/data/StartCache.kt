package com.metrowin.launcher.data

import android.content.ComponentName
import android.os.UserHandle
import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What Start needs to draw its first frame without waiting for the list of apps (one call to the system for each app, seconds on a
 * cold start): the apps as the last load found them, and the small live values of the tiles (what the next events are, the missed calls,
 * the unread mail, the open things to do, the next alarm). The pictures (photos, faces) are not kept: they come with the live read.
 * The layout of the tiles is not here, it is in the settings of the launcher and is read at once.
 */
class StartSnapshot(val apps: List<AppItem>, val live: StartLive)

/** The live values kept by [StartCache]; [LiveTileData] takes them as the first content of the tiles. */
data class StartLive(
    val nextEvent: LiveEvent? = null,
    val events: List<LiveEvent> = emptyList(),
    val missedCalls: Int = 0,
    val unreadMail: Int = 0,
    val todo: LiveTodo? = null,
    val nextAlarm: Long = 0L,
)

/**
 * The cache of the first frame of Start, one file in the cache folder of the app (so it is never part of a backup and the system may
 * drop it). A file of another version of the app or of another language, or one that cannot be read, is no cache: Start then
 * waits for the list of apps as it always did. Pure encode and decode, so it is tested.
 */
object StartCache {
    const val SCHEMA = 1
    private const val FILE = "start_cache.json"

    /** [version] is the version code of the app and [locale] the language tag: either one changing makes the cache old. */
    fun encode(snapshot: StartSnapshot, version: Long, locale: String): String {
        val apps = JSONArray()
        snapshot.apps.forEach { app -> encodeApp(app)?.let(apps::put) }
        val live = snapshot.live
        val liveJson = JSONObject()
            .put("next", live.nextEvent?.let(::encodeEvent) ?: JSONObject.NULL)
            .put("events", JSONArray().apply { live.events.forEach { put(encodeEvent(it)) } })
            .put("missed", live.missedCalls)
            .put("mail", live.unreadMail)
            .put("alarm", live.nextAlarm)
        live.todo?.let { liveJson.put("todo", JSONObject().put("open", it.open).put("first", JSONArray(it.first))) }
        return JSONObject().put("schema", SCHEMA).put("version", version).put("locale", locale).put("apps", apps).put("live", liveJson).toString()
    }

    /** Null when [json] is not a cache of this [version] and [locale], or is damaged. [owner] is the profile of the apps. */
    fun decode(json: String?, version: Long, locale: String, owner: UserHandle): StartSnapshot? = runCatching {
        val root = JSONObject(json ?: return null)
        if (root.getInt("schema") != SCHEMA || root.getLong("version") != version || root.getString("locale") != locale) return null
        val array = root.getJSONArray("apps")
        val apps = (0 until array.length()).map { decodeApp(array.getJSONObject(it), owner) ?: return null }
        val live = root.getJSONObject("live")
        val events = live.getJSONArray("events")
        val todo = live.optJSONObject("todo")
        StartSnapshot(
            apps,
            StartLive(
                nextEvent = live.optJSONObject("next")?.let(::decodeEvent),
                events = (0 until events.length()).map { decodeEvent(events.getJSONObject(it)) },
                missedCalls = live.getInt("missed"),
                unreadMail = live.getInt("mail"),
                todo = todo?.let { t ->
                    val first = t.getJSONArray("first")
                    LiveTodo(t.getInt("open"), (0 until first.length()).map(first::getString))
                },
                nextAlarm = live.getLong("alarm"),
            ),
        )
    }.getOrNull()

    /** An app of another profile, a shortcut or any tile that is not a plain app is not kept: those come with the load. */
    private fun encodeApp(app: AppItem): JSONObject? {
        if (app.isWork || app.isPrivate || app.pinOnly || app.note != null || app.data != null || app.photoPath != null) return null
        val o = JSONObject().put("id", app.id).put("label", app.label).put("removable", app.removable).put("installed", app.installedAt)
        when (val launch = app.launch) {
            is Launch.Activity -> o.put("component", launch.component.flattenToString())
            is Launch.Action -> o.put("action", launch.action)
            is Launch.Internal -> o.put("route", launch.route)
            else -> return null
        }
        app.glyph?.let { o.put("glyph", it.name) }
        app.live?.let { o.put("live", it.name) }
        app.packageName?.let { o.put("package", it) }
        app.category?.let { o.put("category", it.name) }
        return o
    }

    private fun decodeApp(o: JSONObject, owner: UserHandle): AppItem? {
        val launch = when {
            o.has("component") -> Launch.Activity(ComponentName.unflattenFromString(o.getString("component")) ?: return null, owner)
            o.has("action") -> Launch.Action(o.getString("action"))
            o.has("route") -> Launch.Internal(o.getString("route"))
            else -> return null
        }
        return AppItem(
            id = o.getString("id"),
            label = o.getString("label"),
            launch = launch,
            glyph = o.optString("glyph").takeIf { it.isNotEmpty() }?.let { Glyph.valueOf(it) },
            live = o.optString("live").takeIf { it.isNotEmpty() }?.let { LiveKind.valueOf(it) },
            packageName = o.optString("package").takeIf { it.isNotEmpty() },
            removable = o.getBoolean("removable"),
            category = o.optString("category").takeIf { it.isNotEmpty() }?.let { AppCategory.valueOf(it) },
            installedAt = o.getLong("installed"),
        )
    }

    private fun encodeEvent(e: LiveEvent) = JSONObject().put("title", e.title).put("start", e.startMillis).put("allDay", e.allDay).put("end", e.endMillis)
        .put("id", e.id).put("location", e.location).put("link", e.link ?: JSONObject.NULL).put("calendar", e.calendarId)

    private fun decodeEvent(o: JSONObject) = LiveEvent(
        title = o.getString("title"), startMillis = o.getLong("start"), allDay = o.getBoolean("allDay"), endMillis = o.getLong("end"),
        id = o.getLong("id"), location = o.getString("location"), link = if (o.isNull("link")) null else o.getString("link"), calendarId = o.getLong("calendar"),
    )

    /** Blocking, a small file: call [read] before the first frame is asked for, [write] off the main thread. */
    fun read(dir: File, version: Long, locale: String, owner: UserHandle): StartSnapshot? =
        runCatching { File(dir, FILE).readText() }.getOrNull().let { decode(it, version, locale, owner) }

    /** The new text goes beside the file and replaces it in one step, so a crash leaves the old cache whole. */
    fun write(dir: File, snapshot: StartSnapshot, version: Long, locale: String) {
        runCatching {
            val temporary = File(dir, "$FILE.tmp")
            temporary.writeText(encode(snapshot, version, locale))
            if (!temporary.renameTo(File(dir, FILE))) temporary.delete()
        }
    }
}
