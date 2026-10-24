package com.metrowin.launcher.ui.gesture

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Brings the launcher back to the top of Start.
 *
 * With [instant] it jumps: used when the launcher comes back from another app, where nothing of it is on
 * screen yet, so the tiles can swing in on the right page. Otherwise the page and the scroll glide at the
 * same time (not one after the other), and any touch on the screen takes over from the glide.
 */
suspend fun returnToStart(pager: PagerState, start: ScrollState, instant: Boolean) {
    if (instant) {
        pager.scrollToPage(PAGE_START)
        start.scrollTo(0)
        return
    }
    coroutineScope {
        launch { if (pager.currentPage != PAGE_START || pager.currentPageOffsetFraction != 0f) pager.animateScrollToPage(PAGE_START) }
        launch { if (start.value != 0) start.animateScrollTo(0) }
    }
}
