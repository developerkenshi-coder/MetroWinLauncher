package com.metrowin.launcher.model

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.Immutable

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

/** Something pinned to Start: a [Tile] or a [Folder]. */
sealed interface StartEntry {
    val id: String
    val size: TileSize
}

/** A tile pinned to Start. [id] is the [AppItem.id] it opens. */
@Immutable
data class Tile(override val id: String, override val size: TileSize) : StartEntry

/** A Start folder: tiles dragged onto each other. [children] are [AppItem.id]s. */
@Immutable
data class Folder(
    override val id: String,
    val name: String,
    override val size: TileSize,
    val children: List<String>,
) : StartEntry

sealed interface Launch {
    /** [user] is the profile the app lives in: the owner, or a work or clone profile. */
    data class Activity(val component: ComponentName, val user: UserHandle) : Launch

    /** Opens a system screen by intent action (Battery Saver, Data Sense, Storage Sense). */
    data class Action(val action: String) : Launch

    /** Opens a screen of the launcher itself, such as start+theme. */
    data class Internal(val route: String) : Launch
}

/** Which kind of live content a tile can show on its face. */
enum class LiveKind { Calendar, Photos, People, Phone }

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
)

/** Glyphs drawn by the launcher for the stock apps, see ui/components/Glyphs.kt. */
enum class Glyph {
    Alarm, Battery, Calculator, Calendar, Camera, Cortana, Data, Excel, Files, Finance, Games,
    Browser, Mail, Maps, Messaging, Mic, Music, News, Office, OneDrive, OneNote, People, Phone,
    Photos, Podcasts, PowerPoint, Settings, Skype, Sports, Storage, Store, Video, Wallet,
    Weather, Word, Xbox, Theme,
}
