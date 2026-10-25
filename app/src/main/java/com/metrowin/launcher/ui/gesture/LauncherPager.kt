package com.metrowin.launcher.ui.gesture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.metrowin.launcher.ui.anim.Motion
import kotlin.math.abs

const val PAGE_START = 0

/** How far [page] is from the middle of the pager: 0 when it fills the screen, 1 when a whole page away. */
fun pageDistance(page: Int, currentPage: Int, currentPageOffsetFraction: Float): Float =
    abs(page - (currentPage + currentPageOffsetFraction)).coerceIn(0f, 1f)

fun PagerState.distanceTo(page: Int): Float = pageDistance(page, currentPage, currentPageOffsetFraction)

/** A page leaving or arriving swings a little around its left edge and fades, so the swipe has the Metro turnstile in it. */
private const val PAGE_SWING = 0.3f

/**
 * The page follows the finger one to one. On top of that it swings and fades with the distance from the
 * middle of the pager. The distance is only read while drawing, so a swipe never recomposes the pages.
 */
fun Modifier.pageTurnstile(state: PagerState, page: Int): Modifier = graphicsLayer {
    val away = state.distanceTo(page)
    if (away > 0f) {
        transformOrigin = TransformOrigin(0f, 0.5f)
        rotationY = Motion.TURNSTILE_DEGREES * PAGE_SWING * away
        cameraDistance = Motion.CAMERA * density
        alpha = 1f - away * away
        // Pages do not overlap their own content, so the alpha can be applied to each draw call
        // instead of drawing the whole page into an off-screen layer first.
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/**
 * The Start screens and, after the last of them, the app list, side by side. The neighbours of the page in
 * view are kept ready (composed one page ahead), so the first swipe does not have to build the list while the
 * finger is already moving, and a lighter drag is enough to turn the page. [start] gets the index of the screen.
 */
@Composable
fun LauncherPager(
    state: PagerState,
    startPages: Int,
    userScrollEnabled: Boolean,
    start: @Composable (page: Int) -> Unit,
    apps: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = state,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = userScrollEnabled,
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(state, snapPositionalThreshold = 0.3f),
        key = { it },
    ) { page ->
        Box(Modifier.fillMaxSize().pageTurnstile(state, page)) {
            if (page < startPages) start(page) else apps()
        }
    }
}
