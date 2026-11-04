package com.metrowin.launcher.news

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The news of the launcher: one page for each source (Google News, its topics, feeds of your own), and the choice of the source of the tile of Start. */
class NewsActivity : MiniActivity() {
    @Composable
    override fun Content() = NewsScreen()
}
