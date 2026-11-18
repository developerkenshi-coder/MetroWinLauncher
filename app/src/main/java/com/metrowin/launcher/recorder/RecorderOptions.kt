package com.metrowin.launcher.recorder

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The switches of the recorder, in the menu "…" of the app. What only looks different or helps is on; what touches the microphone, the
 * stored text, the disk or another folder is off until the user turns it on. Every value is a small number: a switch is 0 or 1.
 */
enum class Opt(val key: String, val default: Int, val max: Int = 1) {
    PAUSE("pause", 1),
    MARKS("marks", 1),
    FOCUS("focus", 1),
    SAFE("safe_record", 0),
    QUALITY("quality", 0, 3),
    MAX_MIN("max_minutes", 0, 4),
    LEVEL("level_meter", 1),
    SPEED("speed", 1),
    SEEK("seek_buttons", 1),
    NUDGE("fine_trim", 1),
    CUT_ANY("cut_any", 1),
    TOOLS("audio_tools", 1),
    SPECTRO("spectrogram", 1),
    KEEP_TEXT("keep_transcripts", 0),
    SUMMARY("summary", 1),
    LABELS("labels", 1),
    BACKUP("backup", 0),
    ROUND("round_button", 1),
    RICH_ROWS("rich_rows", 1),
    PRETTY("pretty_names", 1),
    GROUP_DAYS("group_days", 1),
    TRANSITIONS("transitions", 1),
    MINI("mini_player", 1),
    WAVE_THEME("wave_theme", 1),
    TWO_PANE("two_pane", 1),
    CONTRAST("high_contrast", 0),
    SORT("sort", 0, 3),

    /** The recorder starts recording as soon as it opens. */
    START_OPEN("start_on_open", 0),

    /** While recording, a volume key puts a marker instead of changing the volume. */
    VOLUME_MARK("volume_mark", 0),
}

/** The numbers that were scattered in the code: the sound the recorder makes, and what it will not take. */
object RecorderConfig {
    /** A recording stops by itself when the storage has less than this left. */
    const val MIN_FREE_BYTES = 8L * 1024 * 1024

    /** A recording is not started with less than this left. */
    const val START_FREE_BYTES = 24L * 1024 * 1024

    const val MAX_IMPORT = 1L shl 30
    const val PEAK_BUCKETS = 800
    const val UNDO_MS = 8_000L

    /** What each [Opt.QUALITY] step is: bit rate, sampling rate, channels. The first is what the recorder always did. */
    data class Quality(val bitRate: Int, val rate: Int, val channels: Int)

    val QUALITIES = listOf(Quality(96_000, 44_100, 1), Quality(32_000, 22_050, 1), Quality(128_000, 48_000, 1), Quality(192_000, 44_100, 2))

    /** The limits (in minutes) [Opt.MAX_MIN] steps through; 0 is none. */
    val MAX_MINUTES = listOf(0, 30, 60, 120, 240)
}

/** The preference file of the recorder (kept apart from the file the launcher shares), read where there is no screen: the recorder itself. */
object RecorderPrefs {
    private const val NAME = "metrowin_recorder"

    fun file(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun int(context: Context, opt: Opt): Int = file(context).getInt(opt.key, opt.default).coerceIn(0, opt.max)

    fun on(context: Context, opt: Opt): Boolean = int(context, opt) != 0
}

/** The values of [Opt] as the screen sees them: a change writes the file and redraws what reads it. */
class RecorderOptions(context: Context) {
    private val prefs = RecorderPrefs.file(context)
    private val values = mutableStateMapOf<Opt, Int>().apply { Opt.entries.forEach { put(it, RecorderPrefs.int(context, it)) } }

    operator fun get(opt: Opt): Int = values[opt] ?: opt.default

    fun on(opt: Opt): Boolean = this[opt] != 0

    operator fun set(opt: Opt, value: Int) {
        val v = value.coerceIn(0, opt.max)
        values[opt] = v
        prefs.edit().putInt(opt.key, v).apply()
    }

    fun toggle(opt: Opt) { this[opt] = if (on(opt)) 0 else 1 }

    /** The next step of an option with several: after the last it goes back to the first. */
    fun cycle(opt: Opt) { this[opt] = if (this[opt] >= opt.max) 0 else this[opt] + 1 }

    /** The folder the backup goes to (the address of a tree the user picked), or null. */
    var backupTree: String?
        get() = prefs.getString("backup_tree", null)
        set(value) { prefs.edit().putString("backup_tree", value).apply() }

    /** Whether the hint on how to cut was shown (once). */
    var hintShown: Boolean
        get() = hint
        set(value) { hint = value; prefs.edit().putBoolean("hint_cut", value).apply() }
    private var hint by mutableStateOf(prefs.getBoolean("hint_cut", false))
}
