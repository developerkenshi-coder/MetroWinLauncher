package com.metrowin.launcher.todo

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The things to do of the launcher: lists, a date for each (it rings as a reminder), stars, and what is done. */
class TodoActivity : MiniActivity() {
    @Composable
    override fun Content() = TodoScreen()
}
