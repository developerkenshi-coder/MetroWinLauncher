package com.metrowin.launcher.data

import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileData
import org.json.JSONArray
import org.json.JSONObject

enum class ShortcutKind(val code: String) {
    /** A person from the contacts. */
    Contact("contact"),

    /** A web page. */
    Site("site"),

    /** An Android widget placed on Start. */
    Widget("widget"),

    /** A number that is called at a touch (speed dial). */
    Call("call"),

    /** A note written on the tile itself. */
    Note("note"),

    /** The days left to a date, or gone since it. */
    Countdown("countdown"),

    /** A screen of the Android settings (Wi-Fi, battery…). */
    Setting("setting"),

    /** A shortcut an app offers ("new message") put on Start; [ShortcutDef.uri] is `appshortcut:package:user:id`. */
    AppShortcut("appshortcut"),

    /** A number that goes up by a touch. */
    Counter("counter"),

    /** A list of things to do. */
    Checklist("checklist"),

    /** The time in another city; [ShortcutDef.uri] is the time zone. */
    WorldClock("worldclock"),

    /** The phase of the moon. */
    Moon("moon"),

    /** The next birthdays of the contacts. */
    Birthdays("birthdays");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code }
    }
}

/**
 * Something pinned to Start that is not an app: a contact, a web page or a widget. It has a tile and no
 * entry in the app list. [uri] is the contact's lookup address or the page's address; a widget has none and
 * is known by [widgetId] (given by Android on this phone only) and the [widgetProvider] it comes from.
 */
data class ShortcutDef(
    val id: String,
    val kind: ShortcutKind,
    val label: String,
    val uri: String = "",
    /** File name (inside the app's storage) of the picture shown on a contact's tile. */
    val photoFile: String? = null,
    val widgetProvider: String = "",
    val widgetId: Int = -1,
    /** The text of a note. */
    val text: String = "",
    /** The day a countdown counts to, as an epoch day. */
    val epochDay: Long = 0L,
    /** The count of a counter. */
    val number: Long = 0L,
) {
    /** The tile's entry. Shortcuts only exist on Start ([AppItem.pinOnly]). */
    fun toItem(photoPath: String?): AppItem = AppItem(
        id = id,
        label = label,
        launch = when (kind) {
            ShortcutKind.Widget -> Launch.Widget(widgetId)
            ShortcutKind.Note -> Launch.Internal(AppRepository.ROUTE_NOTE)
            ShortcutKind.Countdown -> Launch.Internal(AppRepository.ROUTE_COUNTDOWN)
            ShortcutKind.Setting -> Launch.Action(uri)
            ShortcutKind.Counter -> Launch.Internal(AppRepository.ROUTE_COUNTER)
            ShortcutKind.Checklist -> Launch.Internal(AppRepository.ROUTE_CHECKLIST)
            ShortcutKind.WorldClock, ShortcutKind.Moon -> Launch.Internal(AppRepository.ROUTE_NONE)
            ShortcutKind.Birthdays -> Launch.Link(CONTACTS_ADDRESS)
            else -> Launch.Link(uri)
        },
        glyph = when (kind) {
            ShortcutKind.Contact -> Glyph.People
            ShortcutKind.Site -> Glyph.Globe
            ShortcutKind.Widget -> Glyph.Widget
            ShortcutKind.Call -> Glyph.Phone
            ShortcutKind.Note -> Glyph.OneNote
            ShortcutKind.Countdown -> Glyph.Calendar
            ShortcutKind.Setting -> GlobalSearch.systemSettings.firstOrNull { it.action == uri }?.glyph ?: Glyph.Settings
            ShortcutKind.AppShortcut -> null
            ShortcutKind.Counter -> Glyph.Calculator
            ShortcutKind.Checklist -> Glyph.Files
            ShortcutKind.WorldClock -> Glyph.Globe
            ShortcutKind.Moon -> Glyph.Moon
            ShortcutKind.Birthdays -> Glyph.Star
        },
        pinOnly = true,
        photoPath = photoPath,
        // The icon of an app shortcut is the icon of its app.
        packageName = if (kind == ShortcutKind.AppShortcut) appShortcutParts(uri)?.first else null,
        live = if (kind == ShortcutKind.Birthdays) LiveKind.Birthdays else null,
        note = text.takeIf { kind == ShortcutKind.Note },
        targetDay = if (kind == ShortcutKind.Countdown) epochDay else 0L,
        data = when (kind) {
            ShortcutKind.Counter -> TileData.Counter(number.toInt())
            ShortcutKind.Checklist -> TileData.Checklist(text)
            ShortcutKind.WorldClock -> TileData.WorldClock(uri)
            ShortcutKind.Moon -> TileData.Moon
            else -> null
        },
    )

    companion object {
        /** Where the Birthdays tile opens: the contacts. */
        const val CONTACTS_ADDRESS = "content://com.android.contacts/contacts"

        /** `appshortcut:package:user:id` as (package, user serial number, shortcut id), or null when [uri] is not one. */
        fun appShortcutParts(uri: String): Triple<String, Long, String>? {
            if (!uri.startsWith("appshortcut:")) return null
            val parts = uri.removePrefix("appshortcut:").split(':', limit = 3)
            if (parts.size != 3) return null
            val serial = parts[1].toLongOrNull() ?: return null
            return Triple(parts[0], serial, parts[2])
        }

        fun appShortcutUri(packageName: String, userSerial: Long, id: String) = "appshortcut:$packageName:$userSerial:$id"

        fun newId(kind: ShortcutKind, seed: String): String = "shortcut:${kind.code}:${seed.hashCode().toUInt().toString(16)}:${System.nanoTime().toString(36)}"
    }
}

/** JSON form of the shortcuts, apart from Android storage so it can be tested. */
object ShortcutCodec {
    fun encode(shortcuts: List<ShortcutDef>): String = JSONArray().apply {
        shortcuts.forEach { s ->
            put(
                JSONObject().apply {
                    put("id", s.id)
                    put("kind", s.kind.code)
                    put("label", s.label)
                    if (s.uri.isNotEmpty()) put("uri", s.uri)
                    s.photoFile?.let { put("photo", it) }
                    if (s.kind == ShortcutKind.Widget) {
                        put("provider", s.widgetProvider)
                        put("widgetId", s.widgetId)
                    }
                    if (s.text.isNotEmpty()) put("text", s.text)
                    if (s.epochDay != 0L) put("day", s.epochDay)
                    if (s.number != 0L) put("n", s.number)
                },
            )
        }
    }.toString()

    /** Null when [json] is not what [encode] writes. A shortcut of an unknown kind is skipped, not an error. */
    fun decode(json: String): List<ShortcutDef>? = runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val kind = ShortcutKind.fromCode(o.optString("kind")) ?: continue
                add(
                    ShortcutDef(
                        id = o.getString("id"),
                        kind = kind,
                        label = o.optString("label"),
                        uri = o.optString("uri"),
                        photoFile = o.optString("photo").ifEmpty { null },
                        widgetProvider = o.optString("provider"),
                        widgetId = o.optInt("widgetId", -1),
                        text = o.optString("text"),
                        epochDay = o.optLong("day", 0L),
                        number = o.optLong("n", 0L),
                    ),
                )
            }
        }
    }.getOrNull()

    /** What goes into a backup: the shortcuts that mean the same on another phone. A widget's id belongs to this one. */
    fun portable(shortcuts: List<ShortcutDef>): List<ShortcutDef> =
        shortcuts.filter { it.kind != ShortcutKind.Widget }.map { it.copy(photoFile = null) }
}

class ShortcutStore(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)
    private val photoDir = java.io.File(context.filesDir, "shortcut_photos").apply { mkdirs() }

    fun load(): List<ShortcutDef> = prefs.getString(KEY, null)?.let(ShortcutCodec::decode).orEmpty()

    fun save(shortcuts: List<ShortcutDef>) = prefs.edit { putString(KEY, ShortcutCodec.encode(shortcuts)) }

    /** Full path of a stored picture, or null when [file] is null or gone. */
    fun photoPath(file: String?): String? = file?.let { java.io.File(photoDir, it) }?.takeIf { it.exists() }?.path

    /** Keeps [bytes] as the picture of shortcut [id] and returns its file name. */
    fun savePhoto(id: String, bytes: ByteArray): String {
        val name = id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg"
        java.io.File(photoDir, name).writeBytes(bytes)
        return name
    }

    fun deletePhoto(file: String?) {
        if (file != null) java.io.File(photoDir, file).delete()
    }

    private companion object {
        const val KEY = "shortcuts"
    }
}
