package com.metrowin.launcher.data

import android.content.Context
import androidx.core.content.edit
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.StopwatchState
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.model.TimerState
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
    Birthdays("birthdays"),

    /** A place (home, work, an address): a touch starts the navigation to it; [ShortcutDef.uri] is the address. */
    Place("place"),

    /** An album of the photos of the phone: [ShortcutDef.uri] is the id of the album, [ShortcutDef.label] its name. */
    Album("album"),

    /** A group of the contacts: [ShortcutDef.uri] is the id of the group, [ShortcutDef.label] its name. */
    Group("group"),

    /** A countdown; [ShortcutDef.text] is its state (see [com.metrowin.launcher.model.TimerState]). */
    Timer("timer"),

    /** A stopwatch; [ShortcutDef.text] is its state (see [com.metrowin.launcher.model.StopwatchState]). */
    Stopwatch("stopwatch"),

    /** The headlines of the news source the user chose. */
    News("news"),

    /** The exchange rates and the coins the user follows. */
    Finance("finance"),

    /** A grid of numbers that are called at a touch. */
    DialGrid("dialgrid"),

    /** The people hub: favourites, recent calls and what is new. */
    PeopleHub("peoplehub"),

    /** The reminders that wait for their time. */
    Reminders("reminders"),

    /** A switch of the Action Center on Start; [ShortcutDef.uri] is the id of the quick action. */
    Toggle("toggle"),

    /** When the sun rises and sets today. */
    Sun("sun"),

    /** The steps of today. */
    Steps("steps"),

    /** The voice recorder. */
    Recorder("recorder"),

    /** The camera, with the last photo on the tile. */
    Camera("camera"),

    /** The cards of the wallet (loyalty cards, tickets) as barcodes. */
    Wallet("wallet"),

    /** The street and the numbers of the place the phone is at. */
    WhereAmI("whereami"),

    /** The place where the car was left. */
    Parking("parking"),

    /** A clock with hands. */
    AnalogClock("analogclock");

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
            ShortcutKind.WorldClock, ShortcutKind.Moon, ShortcutKind.AnalogClock -> Launch.Internal(AppRepository.ROUTE_NONE)
            ShortcutKind.Birthdays -> Launch.Link(CONTACTS_ADDRESS)
            ShortcutKind.Place -> Launch.Link(Places.navigationUri(uri))
            ShortcutKind.Album -> Launch.Internal(AppRepository.ROUTE_ALBUM)
            ShortcutKind.Group -> Launch.Internal(AppRepository.ROUTE_GROUP)
            ShortcutKind.Timer -> Launch.Internal(AppRepository.ROUTE_TIMER)
            ShortcutKind.Stopwatch -> Launch.Internal(AppRepository.ROUTE_STOPWATCH)
            ShortcutKind.News -> Launch.Internal(AppRepository.ROUTE_NEWS)
            ShortcutKind.Finance -> Launch.Internal(AppRepository.ROUTE_FINANCE)
            ShortcutKind.DialGrid -> Launch.Internal(AppRepository.ROUTE_DIALGRID)
            ShortcutKind.PeopleHub -> Launch.Internal(AppRepository.ROUTE_PEOPLEHUB)
            ShortcutKind.Reminders -> Launch.Internal(AppRepository.ROUTE_REMINDERS)
            ShortcutKind.Toggle -> Launch.Internal(AppRepository.ROUTE_TOGGLE)
            ShortcutKind.Sun -> Launch.Internal(AppRepository.ROUTE_WEATHER)
            ShortcutKind.Steps -> Launch.Internal(AppRepository.ROUTE_STEPS)
            ShortcutKind.Recorder -> Launch.Internal(AppRepository.ROUTE_RECORDER)
            ShortcutKind.Camera -> Launch.Action(CAMERA_ACTION)
            ShortcutKind.Wallet -> Launch.Internal(AppRepository.ROUTE_WALLET)
            ShortcutKind.WhereAmI -> Launch.Internal(AppRepository.ROUTE_WHEREAMI)
            ShortcutKind.Parking -> Launch.Internal(AppRepository.ROUTE_PARKING)
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
            ShortcutKind.Place -> Glyph.Maps
            ShortcutKind.Album -> Glyph.Photos
            ShortcutKind.Group -> Glyph.People
            ShortcutKind.Timer -> Glyph.Hourglass
            ShortcutKind.Stopwatch -> Glyph.Stopwatch
            ShortcutKind.News -> Glyph.News
            ShortcutKind.Finance -> Glyph.Finance
            ShortcutKind.DialGrid -> Glyph.Phone
            ShortcutKind.PeopleHub -> Glyph.People
            ShortcutKind.Reminders -> Glyph.Bell
            ShortcutKind.Toggle -> QuickAction.fromId(uri)?.glyph ?: Glyph.Settings
            ShortcutKind.Sun -> Glyph.Weather
            ShortcutKind.Steps -> Glyph.Sports
            ShortcutKind.Recorder -> Glyph.Mic
            ShortcutKind.Camera -> Glyph.Camera
            ShortcutKind.Wallet -> Glyph.Wallet
            ShortcutKind.WhereAmI -> Glyph.Maps
            ShortcutKind.Parking -> Glyph.Maps
            ShortcutKind.AnalogClock -> Glyph.Alarm
        },
        pinOnly = true,
        photoPath = photoPath,
        // The icon of an app shortcut is the icon of its app.
        packageName = if (kind == ShortcutKind.AppShortcut) appShortcutParts(uri)?.first else null,
        live = when (kind) {
            ShortcutKind.Birthdays -> LiveKind.Birthdays
            ShortcutKind.Album -> LiveKind.Album
            ShortcutKind.Group -> LiveKind.Group
            ShortcutKind.News -> LiveKind.News
            ShortcutKind.Finance -> LiveKind.Finance
            // The camera tile shows the latest photo: the photos are read for it.
            ShortcutKind.Camera -> LiveKind.Photos
            else -> null
        },
        note = text.takeIf { kind == ShortcutKind.Note },
        targetDay = if (kind == ShortcutKind.Countdown) epochDay else 0L,
        data = when (kind) {
            ShortcutKind.Counter -> TileData.Counter(number.toInt())
            ShortcutKind.Checklist -> TileData.Checklist(text)
            ShortcutKind.WorldClock -> TileData.WorldClock(uri)
            ShortcutKind.Moon -> TileData.Moon
            ShortcutKind.Place -> TileData.Place(uri)
            ShortcutKind.Album -> TileData.Album(uri)
            ShortcutKind.Group -> TileData.Group(uri.toLongOrNull() ?: -1L)
            ShortcutKind.Timer -> TileData.Timer(TimerState.decode(text))
            ShortcutKind.Stopwatch -> TileData.Stopwatch(StopwatchState.decode(text))
            ShortcutKind.News -> TileData.News
            ShortcutKind.Finance -> TileData.Finance
            ShortcutKind.DialGrid -> TileData.DialGrid
            ShortcutKind.PeopleHub -> TileData.PeopleHub
            ShortcutKind.Reminders -> TileData.Reminders
            ShortcutKind.Toggle -> TileData.Toggle(uri)
            ShortcutKind.Sun -> TileData.Sun
            ShortcutKind.Steps -> TileData.Steps
            ShortcutKind.Recorder -> TileData.Recorder
            ShortcutKind.Camera -> TileData.Camera
            ShortcutKind.Wallet -> TileData.Wallet
            ShortcutKind.WhereAmI -> TileData.WhereAmI
            ShortcutKind.Parking -> TileData.Parking
            ShortcutKind.AnalogClock -> TileData.AnalogClock
            ShortcutKind.Contact -> TileData.Contact
            ShortcutKind.Site -> TileData.Site(uri)
            else -> null
        },
    )

    companion object {
        /** Where the Birthdays tile opens: the contacts. */
        const val CONTACTS_ADDRESS = "content://com.android.contacts/contacts"

        /** What opens the camera app of the phone, whichever it is. */
        const val CAMERA_ACTION = "android.media.action.STILL_IMAGE_CAMERA"

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
