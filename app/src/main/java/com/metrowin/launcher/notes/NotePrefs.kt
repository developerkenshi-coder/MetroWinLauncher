package com.metrowin.launcher.notes

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/**
 * The switches of the notes app (the "⋯" menu of the list), in the preferences of the app itself. What touches privacy, the files of the
 * phone or the way the notes are deleted starts off; what only looks better or types better starts on. Read as state: a screen that
 * reads one is drawn again when it changes.
 */
class NotePrefs(private val sp: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE))

    private val state = mutableStateMapOf<String, Int>()

    operator fun get(key: String): Int = state[key] ?: sp.getInt(key, DEFAULTS[key] ?: 0).also { state[key] = it }

    fun on(key: String) = get(key) != 0

    operator fun set(key: String, value: Int) {
        state[key] = value
        sp.edit().putInt(key, value).apply()
    }

    fun toggle(key: String, on: Boolean) = set(key, if (on) 1 else 0)

    companion object {
        /** Deleted notes wait in a bin for [NoteStore.BIN_DAYS] days and can come back. */
        const val BIN = "bin"
        /** A short row for each note, instead of the row with its first line. */
        const val COMPACT = "compact"
        /** "5 minutes ago" instead of the day and the hour. */
        const val RELATIVE = "relative_dates"
        /** Searching puts the notes with the words in the title first. */
        const val RANKED = "rank_search"
        /** The title, the bold and the done boxes are shown as such while the note is typed. */
        const val LIVE = "live_style"
        /** Enter at the end of a list item starts the next one. */
        const val AUTO_LIST = "auto_list"
        /** Undo and redo for the text of a note. */
        const val UNDO = "undo_text"
        /** The photos of a note go in its PDF. */
        const val PDF_PHOTOS = "pdf_photos"
        /** Paper with squares, lines or dots for a sketch. */
        const val PAPER = "sketch_paper"
        /** A tool in the sketch that moves what was drawn. */
        const val MOVE = "sketch_move"
        /** Once a pen of a stylus touches the sketch, the fingers (the palm) draw nothing. */
        const val STYLUS_ONLY = "stylus_only"
        /** A note can ask for the lock of the phone before it opens. */
        const val LOCK = "lock"
        /** A backup can be kept under a password. */
        const val SEAL = "backup_password"
        /** A heading for each folder in the list. */
        const val SECTIONS = "folder_sections"
        /** The first photo of a note on its tile. */
        const val TILE_PHOTO = "tile_photo"
        /** Long press a note to pick several. */
        const val PICK = "multi_select"
        /** Which order the list is in: see [NoteStore.sorted]. */
        const val SORT = "sort"

        private val DEFAULTS = mapOf(
            BIN to 0, COMPACT to 0, RELATIVE to 1, RANKED to 1, LIVE to 1, AUTO_LIST to 1, UNDO to 1, PDF_PHOTOS to 1, PAPER to 1, MOVE to 1,
            STYLUS_ONLY to 0, LOCK to 0, SEAL to 0, SECTIONS to 0, TILE_PHOTO to 1, PICK to 1, SORT to 0,
        )
    }
}
