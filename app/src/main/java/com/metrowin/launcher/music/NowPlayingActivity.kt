package com.metrowin.launcher.music

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** Now Playing: the song that plays in any app, across the whole page, with its controls and queue. */
class NowPlayingActivity : MiniActivity() {
    @Composable
    override fun Content() = NowPlayingScreen()
}
