package com.metrowin.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle

/** True inside a [com.metrowin.launcher.ui.start.DialogFrame]: Classic buttons there are lowercase, as in the Windows Phone MessageBox. */
val LocalInDialog = staticCompositionLocalOf { false }

/** How the five dots of the progress bar move: each one crosses the bar a little after the one before it, slow at both ends and quick in the middle. */
internal object DotsMotion {
    const val COUNT = 5
    const val CYCLE_MS = 3200
    private const val TRAVEL_MS = 2200
    private const val STAGGER_MS = 200

    /** Where dot [index] is across the bar (0 left, 1 right) [ms] into the cycle; null while it waits to start and after it has gone. */
    fun position(ms: Float, index: Int): Float? {
        val u = (ms - index * STAGGER_MS) / TRAVEL_MS
        if (u < 0f || u >= 1f) return null
        return u * u * (3f - 2f * u)
    }
}

/**
 * The indeterminate progress bar of Windows Phone: five dots that run across the width one after the other. With reduced motion the dots
 * stand still, evenly spaced.
 */
@Composable
fun MetroProgressBar(modifier: Modifier = Modifier, color: Color = LocalAccent.current) {
    val reduced = LocalReduceMotion.current
    val clock = if (reduced) null else rememberInfiniteTransition(label = "progressDots").animateFloat(
        0f, DotsMotion.CYCLE_MS.toFloat(), infiniteRepeatable(tween(DotsMotion.CYCLE_MS, easing = LinearEasing), RepeatMode.Restart), label = "dotsClock",
    )
    Canvas(
        modifier.fillMaxWidth().height(4.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val dot = 4.dp.toPx()
        repeat(DotsMotion.COUNT) { i ->
            val at = if (clock == null) i / (DotsMotion.COUNT - 1f) else DotsMotion.position(clock.value, i)
            if (at != null) drawRect(color, Offset((size.width - dot) * at, 0f), Size(dot, dot))
        }
    }
}

/** The bar of [scrollThumb]: where it starts and how long it is, in the height of the list. Null when everything fits. */
internal fun scrollThumb(viewport: Float, content: Float, scrolled: Float, minLength: Float): Pair<Float, Float>? {
    if (viewport <= 0f || content <= viewport) return null
    val length = (viewport * viewport / content).coerceIn(minLength.coerceAtMost(viewport), viewport)
    val top = (scrolled / (content - viewport)).coerceIn(0f, 1f) * (viewport - length)
    return top to length
}

/**
 * The thin scroll bar of Windows Phone: 2 dp at the right edge, there while the list is scrolled and gone a moment after. Only in Classic.
 * The length of the list is guessed from the rows on screen, so a list of rows of very different heights makes the bar jump a little.
 */
@Composable
fun Modifier.scrollIndicator(state: LazyListState): Modifier {
    val classic = LocalStyle.current.isClassic
    val reduced = LocalReduceMotion.current
    val color = LocalMetroColors.current.foreground
    val scrolling = state.isScrollInProgress
    val alpha by animateFloatAsState(
        if (scrolling) 0.6f else 0f,
        if (reduced) snap() else if (scrolling) tween(80) else tween(450, delayMillis = 700),
        label = "scrollIndicator",
    )
    if (!classic) return this
    return drawWithContent {
        drawContent()
        if (alpha <= 0f) return@drawWithContent
        val info = state.layoutInfo
        val rows = info.visibleItemsInfo
        if (rows.isEmpty()) return@drawWithContent
        val average = rows.sumOf { it.size }.toFloat() / rows.size
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val content = average * info.totalItemsCount
        val scrolled = state.firstVisibleItemIndex * average + state.firstVisibleItemScrollOffset
        val (top, length) = scrollThumb(viewport, content, if (info.reverseLayout) content - viewport - scrolled else scrolled, 24.dp.toPx()) ?: return@drawWithContent
        val width = 2.dp.toPx()
        drawRect(color.copy(alpha = alpha), Offset(size.width - width, top), Size(width, length))
    }
}

/** The first rows of a list come in with the feather of Windows Phone (Classic only); the rows after them are just there. */
@Composable
fun Modifier.featherRow(index: Int): Modifier = if (LocalStyle.current.isClassic && index < FEATHER_ROWS) featherIn(index) else this

private const val FEATHER_ROWS = 10

/** A picture that comes into view fades in (Classic only), unless it was there from the start ([instant]: it came out of a cache). */
@Composable
fun Modifier.arrival(instant: Boolean = false): Modifier {
    val fade = LocalStyle.current.isClassic && !LocalReduceMotion.current && !instant
    val progress = remember { Animatable(if (fade) 0f else 1f) }
    LaunchedEffect(Unit) { if (fade) progress.animateTo(1f, metroTween(220)) }
    return graphicsLayer { alpha = progress.value }
}
