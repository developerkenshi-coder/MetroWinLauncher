package com.metrowin.launcher.recents

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The recent apps as cards, the way Windows Phone showed them on a long press of Back. */
class RecentsActivity : MiniActivity() {
    @Composable
    override fun Content() = RecentsScreen(::finish)
}
