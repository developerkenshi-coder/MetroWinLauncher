package com.metrowin.launcher.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import com.metrowin.launcher.gestures.NavBarOverlay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Keeps content out of a camera cutout on the left or right side, which is where it is in landscape. The system bars
 * on the sides are already left out by `navigationBarsPadding`; the top is left to `statusBarsPadding`.
 */
@Composable
fun Modifier.cutoutSidesPadding(): Modifier = windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))

/**
 * Keeps content under the status bar and the camera cut-out at the top. With the status bar turned off the bar leaves no room, but
 * the camera hole is still there: this is `statusBarsPadding` that remembers it.
 */
@Composable
fun Modifier.topBarsPadding(): Modifier {
    // With the bar of Windows Phone drawn, the room it takes is left instead (it is as high as the bar of Android and the camera hole).
    val own = LocalMetroStatusBar.current
    return if (own > 0.dp) padding(top = own) else windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
}

/** The room at the bottom for the navigation bar is already left by whoever holds the page together with its bar below it (Mail's list and its app bar). */
val LocalBottomBarsPadded = compositionLocalOf { false }

/** The sides of the navigation bar (in landscape, a bar at the side of the screen). */
@Composable
fun Modifier.navigationSidesPadding(): Modifier = windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))

/**
 * The bottom of the navigation bar, and also the three buttons of MetroWin drawn over it: on a phone whose bar of Android is hidden
 * (gestures) the system reports less room than the buttons take, and the bottom bar of the app went under them.
 */
@Composable
fun Modifier.navigationBottomPaddingWithButtons(): Modifier {
    val density = LocalDensity.current
    val inset = WindowInsets.navigationBars.getBottom(density)
    val extra = with(density) { (NavBarOverlay.coverPx - inset).coerceAtLeast(0).toDp() }
    return windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)).padding(bottom = extra)
}

/** [navigationSidesPadding] and [navigationBottomPaddingWithButtons]: `navigationBarsPadding` that also keeps clear of the buttons of MetroWin. */
@Composable
fun Modifier.navigationBarsPaddingWithButtons(): Modifier = navigationSidesPadding().navigationBottomPaddingWithButtons()
