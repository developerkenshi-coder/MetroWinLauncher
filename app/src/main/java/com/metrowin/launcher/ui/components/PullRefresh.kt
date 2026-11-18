package com.metrowin.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.theme.LocalAccent

/** How far a list at its top is pulled down, 0..1 (1 is far enough to refresh), and when the finger lets go. Pure, so it is tested. */
internal object PullRefresh {
    const val THRESHOLD_DP = 96f

    /** The pull after the finger moved by [delta] (down is positive); it cannot go below 0 or past 1.5 times the threshold. */
    fun pull(current: Float, delta: Float, thresholdPx: Float): Float = (current + delta).coerceIn(0f, thresholdPx * 1.5f)

    fun reached(pull: Float, thresholdPx: Float): Boolean = pull >= thresholdPx
}

/**
 * Pull a list down from its top to read it again: a line in the accent colour grows across the top while the finger pulls, a tick tells it is far
 * enough, and letting go then calls [onRefresh]. Put on the box around the list; a list that is not at its top scrolls as it did.
 */
@Composable
fun Modifier.pullToRefresh(onRefresh: () -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { PullRefresh.THRESHOLD_DP.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val accent = LocalAccent.current
    val current by rememberUpdatedState(onRefresh)
    var pulled by remember { mutableFloatStateOf(0f) }
    val connection = remember(threshold) {
        object : NestedScrollConnection {
            // A pull that is already on is taken back before the list scrolls up.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pulled <= 0f || available.y >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                val before = pulled
                pulled = PullRefresh.pull(pulled, available.y, threshold)
                return Offset(0f, pulled - before)
            }

            // What the list did not use of a downward drag is the pull.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                val was = PullRefresh.reached(pulled, threshold)
                pulled = PullRefresh.pull(pulled, available.y * 0.6f, threshold)
                if (!was && PullRefresh.reached(pulled, threshold)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled <= 0f) return Velocity.Zero
                if (PullRefresh.reached(pulled, threshold)) current()
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
    return this
        .nestedScroll(connection)
        .then(pullLine(accent) { (pulled / threshold).coerceIn(0f, 1f) })
}

/** The line over the top of the box; its width is read while drawing, so pulling does not compose again. */
private fun pullLine(accent: Color, progress: () -> Float): Modifier = Modifier.drawWithContent {
    drawContent()
    val p = progress()
    if (p > 0f) drawRect(accent, topLeft = Offset((size.width * (1f - p)) / 2f, 0f), size = Size(size.width * p, 3.dp.toPx()))
}
