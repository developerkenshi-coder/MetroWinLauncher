package com.metrowin.launcher.photos

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf

/** The switches of the Photo hub (menu "⋯" → options), kept in a file of their own. Every one has a default; the ones that touch what leaves the phone are off. */
enum class PhotoOption(val key: String, val default: Boolean) {
    /** Select several pictures (long press, or drag over them), then share, delete, mark or compare them. */
    Select("select", true),
    /** The heart in the viewer writes the favourite mark of the picture (the system asks first). */
    FavoriteEdit("favorite_edit", true),
    /** The search box over the pages: by album, month, year or day. */
    Search("search", true),
    /** Pictures keep their own proportions in the grids instead of being cut square. */
    KeepRatio("keep_ratio", true),
    /** Headings stay at the top while the list scrolls, and a handle at the side jumps through the dates. */
    DateScrubber("date_scrubber", true),
    /** An album shows four of its pictures, not one. */
    Mosaic("mosaic", true),
    /** The camera folder comes first among the albums. */
    CameraFirst("camera_first", true),
    /** The page "memories": the pictures of this day in earlier years. */
    Memories("memories", true),
    /** The viewer: bar that fades, thumbnail that melts into the picture, page counter, bounce of the zoom. */
    ViewerFx("viewer_fx", true),
    /** The system bars go away with the bar of the viewer. */
    Immersive("immersive", true),
    /** A swipe down on a picture closes the viewer. */
    SwipeClose("swipe_close", true),
    /** The strip of small pictures over the bar of the viewer. */
    Filmstrip("filmstrip", false),
    /** The viewer takes the dark tone of the picture instead of plain black. */
    AdaptiveBackground("adaptive_background", false),
    /** Slideshow and "set as wallpaper" in the viewer. */
    Extras("extras", true),
    /** A shared picture goes without its metadata (place, camera, date): a copy is made in the cache of the app. */
    StripMetadata("strip_metadata", false),
    /** Pictures of the same size and moment are listed together, to delete the extra ones. */
    Duplicates("duplicates", true),
    /** The first-time hint on the gestures of the viewer. */
    Hints("hints", true),
    /** A small heart on the thumbnail of a favourite. */
    FavoriteMark("favorite_mark", true),
    /** The placeholder of a picture still being read is grey, not the colour of the accent. */
    NeutralPlaceholder("neutral_placeholder", true),
}

/** The values of [PhotoOption] and the other small choices, readable in composition: they change on the spot. */
class PhotoPrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val options = mutableStateMapOf<PhotoOption, Boolean>().apply { PhotoOption.entries.forEach { put(it, prefs.getBoolean(it.key, it.default)) } }

    operator fun get(option: PhotoOption): Boolean = options[option] ?: option.default

    fun set(option: PhotoOption, on: Boolean) {
        options[option] = on
        prefs.edit().putBoolean(option.key, on).apply()
    }

    /** The wanted height of a row of the grids, dp; the pinch changes it between [MIN_CELL] and [MAX_CELL]. */
    var cell: Int
        get() = cellState
        set(v) { cellState = v.coerceIn(MIN_CELL, MAX_CELL); prefs.edit().putInt("cell", cellState).apply() }
    private var cellState by androidx.compose.runtime.mutableIntStateOf(prefs.getInt("cell", 96))

    /** The space between pictures, dp. */
    var gap: Int
        get() = gapState
        set(v) { gapState = v; prefs.edit().putInt("gap", v).apply() }
    private var gapState by androidx.compose.runtime.mutableIntStateOf(prefs.getInt("gap", 2))

    /** What a heading of the date page covers. */
    var granularity: Granularity
        get() = granularityState
        set(v) { granularityState = v; prefs.edit().putString("granularity", v.name).apply() }
    private var granularityState by androidx.compose.runtime.mutableStateOf(runCatching { Granularity.valueOf(prefs.getString("granularity", null).orEmpty()) }.getOrDefault(Granularity.Day))

    /** True once the hint on the gestures was shown. */
    var hintSeen: Boolean
        get() = prefs.getBoolean("hint_seen", false)
        set(v) { prefs.edit().putBoolean("hint_seen", v).apply() }

    companion object {
        const val FILE = "photos_prefs"
        const val MIN_CELL = 64
        const val MAX_CELL = 200
        val GAPS = listOf(0, 2, 4, 8)
    }
}
