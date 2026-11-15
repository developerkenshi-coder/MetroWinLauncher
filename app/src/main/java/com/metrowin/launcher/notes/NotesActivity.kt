package com.metrowin.launcher.notes

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The notes of the launcher: text with titles, lists and boxes to tick, sketches for diagrams, a backup to keep and to restore. */
class NotesActivity : MiniActivity() {
    @Composable
    override fun Content() = NotesScreen(openId = intent.getStringExtra(EXTRA_NOTE))

    companion object {
        /** The id of the note to open at once, put by a result of the search. */
        const val EXTRA_NOTE = "note"
    }
}
