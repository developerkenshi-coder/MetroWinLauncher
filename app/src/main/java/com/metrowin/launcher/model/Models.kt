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
    Wide(4, 2, "w");

    /** The resize arrow of the Start screen edit mode cycles through the sizes. */
    fun next(): TileSize = entries[(ordinal + 1) % entries.size]

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
    Cycle("cycle");

    /**
     * The style the style button of the edit bar goes to next. Cycle is only for tiles that have photos to cycle
     * through ([cycleAvailable]).
     */
    fun next(cycleAvailable: Boolean): TileStyle = when (this) {
        Standard -> Iconic
        Iconic -> if (cycleAvailable) Cycle else Standard
        Cycle -> Standard
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
}

/** A tile pinned to Start. [id] is the [AppItem.id] it opens. */
@Immutable
data class Tile(
    override val id: String,
    override val size: TileSize,
    val style: TileStyle = TileStyle.Standard,
    override val color: Int = 0,
) : StartEntry

/** A Start folder: tiles dragged onto each other. [children] are [AppItem.id]s. */
@Immutable
data class Folder(
    override val id: String,
    val name: String,
    override val size: TileSize,
    val children: List<String>,
    override val color: Int = 0,
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
enum class LiveKind { Calendar, Photos, People, Phone, Weather, Battery, Messages, Torch, Music }

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
)

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
}
