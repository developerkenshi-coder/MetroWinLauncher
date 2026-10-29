package com.metrowin.launcher.model

import android.content.ComponentName
import android.os.UserHandle
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R

/** Tile sizes of the Windows Phone 8 Start screen, in units of the small tile. */
enum class TileSize(val cols: Int, val rows: Int, val code: String) {
    Small(1, 1, "s"),
    Medium(2, 2, "m"),
    Wide(4, 2, "w"),

    /** Four small tiles across and four down: the Large tile of Windows 10 Mobile. */
    Large(4, 4, "l");

    /** The resize arrow of the Start screen edit mode cycles through the sizes; the Large one only when [allowLarge]. */
    fun next(allowLarge: Boolean = true): TileSize {
        val next = entries[(ordinal + 1) % entries.size]
        return if (next == Large && !allowLarge) entries.first() else next
    }

    /** The nearest of the two sizes some tiles have: small becomes medium, large becomes wide. */
    fun mediumOrWide(): TileSize = when (this) {
        Small -> Medium
        Large -> Wide
        else -> this
    }

    companion object {
        fun fromCode(code: String?): TileSize = entries.firstOrNull { it.code == code } ?: Medium
    }
}

/** How a tile shows its content. */
enum class TileStyle(val code: String) {
    /** The app's glyph or icon, flipping to live content where there is some. */
    Standard("std"),

    /** Windows Phone's Iconic template: the icon on one side and a big counter in the middle. */
    Iconic("iconic"),

    /** Windows Phone's Cycle template: photos fading into one another (the Photos tile). */
    Cycle("cycle"),

    /** Many small pictures side by side, like a collage (the Collection template). */
    Collection("collection");

    /**
     * The style the style button of the edit bar goes to next. Cycle is only for tiles that have photos to cycle
     * through ([cycleAvailable]); Collection comes after it when those photos may also make a collage ([collectionAvailable]).
     */
    fun next(cycleAvailable: Boolean, collectionAvailable: Boolean = false): TileStyle = when (this) {
        Standard -> Iconic
        Iconic -> if (cycleAvailable) Cycle else Standard
        Cycle -> if (collectionAvailable) Collection else Standard
        Collection -> Standard
    }

    companion object {
        fun fromCode(code: String?): TileStyle = entries.firstOrNull { it.code == code } ?: Standard
    }
}

/** Something pinned to Start: a [Tile] or a [Folder]. */
sealed interface StartEntry {
    val id: String
    val size: TileSize

    /** The color the user gave the tile (one of the 20 accents, as ARGB), or 0 for the accent of the theme. */
    val color: Int

    /** The title of the group of tiles that starts here (it is written above, and the tile starts a new row), or empty. */
    val group: String
}

/** A tile pinned to Start. [id] is the [AppItem.id] it opens. */
@Immutable
data class Tile(
    override val id: String,
    override val size: TileSize,
    val style: TileStyle = TileStyle.Standard,
    override val color: Int = 0,
    override val group: String = "",
) : StartEntry

/** A Start folder: tiles dragged onto each other. [children] are [AppItem.id]s. */
@Immutable
data class Folder(
    override val id: String,
    val name: String,
    override val size: TileSize,
    val children: List<String>,
    override val color: Int = 0,
    override val group: String = "",
) : StartEntry

sealed interface Launch {
    /** [user] is the profile the app lives in: the owner, or a work or clone profile. */
    data class Activity(val component: ComponentName, val user: UserHandle) : Launch

    /** Opens a system screen by intent action (Battery Saver, Data Sense, Storage Sense). */
    data class Action(val action: String) : Launch

    /** Opens a screen of the launcher itself, such as start+theme. */
    data class Internal(val route: String) : Launch

    /** Opens a link: a contact (`content://...`) or a web page (`https://...`). */
    data class Link(val uri: String) : Launch

    /** An Android widget placed on Start. The widget itself is drawn by the tile. */
    data class Widget(val appWidgetId: Int) : Launch
}

/** Which kind of live content a tile can show on its face. */
enum class LiveKind { Calendar, Photos, People, Phone, Weather, Battery, Messages, Torch, Music, Storage, Alarm, Birthdays, Album, Group, News, Finance }

/**
 * One entry of the app list: either a Windows Phone stock app mapped onto the Android app that
 * plays that role, or any other installed app.
 */
@Immutable
data class AppItem(
    val id: String,
    val label: String,
    val launch: Launch,
    /** Metro glyph used instead of the app's own icon (stock apps). */
    val glyph: Glyph? = null,
    val live: LiveKind? = null,
    val packageName: String? = null,
    val removable: Boolean = false,
    /** The app lives in a work or clone profile, not in the owner's profile. */
    val isWork: Boolean = false,
    /** The app lives in the private space (Android 15). */
    val isPrivate: Boolean = false,
    /** A shortcut that only exists on Start (a contact, a site, a widget): not in the app list. */
    val pinOnly: Boolean = false,
    /** Path of a picture kept for the tile of a contact or a site. */
    val photoPath: String? = null,
    /** What the app says it is (Android's own category), or null when it does not say. */
    val category: AppCategory? = null,
    /** When the app was first installed (epoch milliseconds), or 0 when not known. */
    val installedAt: Long = 0L,
    /** The text of a note tile, which shows it on its face; null for every other tile. */
    val note: String? = null,
    /** The day a countdown tile counts to (epoch day), or 0 for every other tile. */
    val targetDay: Long = 0L,
    /** What a counter, a checklist, a world clock or a moon tile shows; null for every other tile. */
    val data: TileData? = null,
)

/** What the tiles that keep something of their own show (see [AppItem.data]). */
sealed interface TileData {
    /** A number that goes up by a touch. */
    @Immutable
    data class Counter(val count: Int) : TileData

    /** A list of things to do, as text (see Checklist). */
    @Immutable
    data class Checklist(val raw: String) : TileData

    /** The time in a city, by the id of its time zone. */
    @Immutable
    data class WorldClock(val zone: String) : TileData

    /** The phase of the moon today. */
    data object Moon : TileData

    /** A place the tile starts the navigation to: its address, as it was typed. */
    @Immutable
    data class Place(val address: String) : TileData

    /** The photos of one album of the phone ([bucket] is the id Android gives the album), cycling on the tile. */
    @Immutable
    data class Album(val bucket: String) : TileData

    /** The people of one group of the contacts ([groupId] is its id in the contacts), their faces cycling on the tile. */
    @Immutable
    data class Group(val groupId: Long) : TileData

    /** A countdown that runs without the launcher: see [TimerState]. */
    @Immutable
    data class Timer(val state: TimerState) : TileData

    /** A stopwatch: see [StopwatchState]. */
    @Immutable
    data class Stopwatch(val state: StopwatchState) : TileData

    /** The headlines of the news source the user chose. */
    data object News : TileData

    /** The currencies and coins the user follows. */
    data object Finance : TileData

    /** The grid of numbers that are called at a touch. */
    data object DialGrid : TileData

    /** The people hub: favourites, recent calls, what is new. */
    data object PeopleHub : TileData

    /** The reminders that wait. */
    data object Reminders : TileData

    /** A switch of the Action Center ([action] is the id of a [com.metrowin.launcher.data.QuickAction]) kept on Start. */
    @Immutable
    data class Toggle(val action: String) : TileData

    /** The sun today: when it rises and sets, and how long the day is. */
    data object Sun : TileData

    /** The full weather: today with as many details as the tile has room for (medium and wide only). */
    data object WeatherPlus : TileData

    /** The steps taken today. */
    data object Steps : TileData

    /** How many apps the phone has, and (wide and large) the five used most this week. */
    data object AppsRecap : TileData

    /** The charge of the battery with a graph of how long it should last. */
    data object BatteryGraph : TileData

    /** The data used today over Wi-Fi and mobile, and (wide and large) the average of a month. */
    data object DataUse : TileData

    /** The memory used and free, and (wide and large) what takes it: apps, photos, videos, music, the rest. */
    data object MemoryUse : TileData

    /** The voice recorder. */
    data object Recorder : TileData

    /** The camera, with the last photo on its face. */
    data object Camera : TileData

    /** The cards of the wallet. */
    data object Wallet : TileData

    /** The place the phone is at. */
    data object WhereAmI : TileData

    /** The place where the car was left. */
    data object Parking : TileData

    /** A clock with hands. */
    data object AnalogClock : TileData

    /** A person from the contacts: the latest message of the person shows on the tile. */
    data object Contact : TileData

    /** A web page at [url]: its newest headline shows on the tile. */
    @Immutable
    data class Site(val url: String) : TileData
}

/** The categories an app can declare (see `ApplicationInfo.category`), as the filter of the app list names them. */
enum class AppCategory(val code: String, @param:StringRes val label: Int, private val android: Int) {
    Game("game", R.string.cat_game, 0),
    Audio("audio", R.string.cat_audio, 1),
    Video("video", R.string.cat_video, 2),
    Image("image", R.string.cat_image, 3),
    Social("social", R.string.cat_social, 4),
    News("news", R.string.cat_news, 5),
    Maps("maps", R.string.cat_maps, 6),
    Productivity("productivity", R.string.cat_productivity, 7),
    Accessibility("accessibility", R.string.cat_accessibility, 8);

    companion object {
        /** The category for the value of `ApplicationInfo.category`; null for undefined (-1) and for those not known yet. */
        fun fromAndroid(value: Int): AppCategory? = entries.firstOrNull { it.android == value }
    }
}

/** Glyphs drawn by the launcher for the stock apps, see ui/components/Glyphs.kt. */
enum class Glyph {
    Alarm, Battery, Calculator, Calendar, Camera, Cortana, Data, Excel, Files, Finance, Games,
    Browser, Mail, Maps, Messaging, Mic, Music, News, Office, OneDrive, OneNote, People, Phone,
    Photos, Podcasts, PowerPoint, Settings, Skype, Sports, Storage, Store, Video, Wallet,
    Weather, Word, Xbox, Theme, Search, Wifi, Bluetooth, Airplane, Moon, Flashlight, Rotation, Hotspot, Vpn,
    Cast, Brightness, Bell, Kids, Globe, Widget, Lock, Star, Home, Gesture, Folder, Recents,
    Hourglass, Stopwatch, Timeout, AutoBrightness,

    /** The Metro tiles: the settings of this launcher. */
    Launcher,

    /** A phone with a gear in it: the settings of Android. */
    DeviceSettings,
}
