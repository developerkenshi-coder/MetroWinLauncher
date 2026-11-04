package com.metrowin.launcher.files

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The file manager of the launcher: two panels, copy and move between them, a trash, archives, bookmarks and a text editor. */
class FilesActivity : MiniActivity() {
    @Composable
    override fun Content() = FilesScreen()
}
