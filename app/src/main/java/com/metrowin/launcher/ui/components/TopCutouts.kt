package com.metrowin.launcher.ui.components

import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** One camera hole (or notch) as the phone says it, in pixels of the screen. */
data class Hole(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Where the camera is at the top of the screen, read from the phone itself (so a punch hole in the middle, one in a corner, a notch, or
 * two of them are all known without a list of devices): how much room to leave at the left and at the right, the stretch of the middle
 * to keep clear, and how far down the hole goes. All in dp from the edges of the screen.
 */
@Immutable
data class TopCutouts(
    val left: Dp = 0.dp,
    val right: Dp = 0.dp,
    val centerStart: Dp? = null,
    val centerEnd: Dp? = null,
    val bottom: Dp = 0.dp,
) {
    val hasCenter get() = centerStart != null && centerEnd != null

    companion object {
        private val MARGIN = 5.dp

        /**
         * The holes that begin within [bandPx] of the top edge, sorted into those of the left corner, the right corner and the middle. A
         * hole on the side of the screen (the phone turned) is not at the top, and the insets of the sides already keep clear of it. Pure, so
         * it is tested.
         */
        fun of(holes: List<Hole>, widthPx: Int, bandPx: Int, density: Float): TopCutouts {
            if (widthPx <= 0) return TopCutouts()
            var left = 0
            var right = 0
            var centerStart = Int.MAX_VALUE
            var centerEnd = 0
            var bottom = 0
            for (hole in holes) {
                if (hole.top >= bandPx || hole.bottom <= 0 || hole.right - hole.left >= widthPx / 2) continue
                val middle = (hole.left + hole.right) / 2f
                when {
                    middle < widthPx * 0.33f -> left = maxOf(left, hole.right)
                    middle > widthPx * 0.67f -> right = maxOf(right, widthPx - hole.left)
                    else -> { centerStart = minOf(centerStart, hole.left); centerEnd = maxOf(centerEnd, hole.right) }
                }
                bottom = maxOf(bottom, hole.bottom)
            }
            fun dp(px: Int) = (px / density).dp
            val centre = centerEnd > centerStart
            return TopCutouts(
                left = if (left > 0) dp(left) + MARGIN else 0.dp,
                right = if (right > 0) dp(right) + MARGIN else 0.dp,
                centerStart = if (centre) dp(centerStart) - MARGIN else null,
                centerEnd = if (centre) dp(centerEnd) + MARGIN else null,
                bottom = if (bottom > 0) dp(bottom) + MARGIN else 0.dp,
            )
        }
    }
}

private fun readTop(context: Context, view: View, density: Float, bandPx: Int): TopCutouts {
    if (Build.VERSION.SDK_INT < 28) return TopCutouts()
    val (cutout, width) = if (Build.VERSION.SDK_INT >= 30) {
        val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
        metrics.windowInsets.displayCutout to metrics.bounds.width()
    } else {
        view.rootWindowInsets?.displayCutout to context.resources.displayMetrics.widthPixels
    }
    val holes = cutout?.boundingRects.orEmpty().map { Hole(it.left, it.top, it.right, it.bottom) }
    return TopCutouts.of(holes, width, bandPx, density)
}

/** The camera holes at the top, read again when the phone turns; empty where the phone has none. [band] is how far from the top edge a hole counts. */
@Composable
fun rememberTopCutouts(band: Dp = 56.dp): TopCutouts {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val cutouts by produceState(TopCutouts(), configuration, band, density) {
        while (true) {
            value = runCatching { readTop(context, view, density, (band.value * density).toInt()) }.getOrDefault(TopCutouts())
            // The first reading can come before the window knows its insets: asked again a little later, then now and then.
            delay(2_000)
        }
    }
    return cutouts
}

/**
 * Keeps content below the status bar and the camera hole at the top. In the window of a service the insets of the status bar are empty,
 * so the depth of the hole read from the phone is used when it is more.
 */
@Composable
fun Modifier.lockTopPadding(holes: TopCutouts): Modifier {
    val density = LocalDensity.current
    val inset = with(density) { WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(density).toDp() }
    return padding(top = maxOf(inset, holes.bottom))
}
