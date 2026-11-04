package com.metrowin.launcher.ui.apps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the position indicator of the app list works out from the rows. Pure, so it can be tested. */
object AlphaIndex {
    /** The letter the row [index] is under: the last header at or before it, or the first one while the list is still above them all. Null without headers. */
    fun letterAt(headers: List<Pair<Int, String>>, index: Int): String? =
        (headers.lastOrNull { it.first <= index } ?: headers.firstOrNull())?.second

    /** How far down the list the first visible row is, 0..1; 1 once the list cannot go further, so the indicator reaches the bottom. */
    fun fraction(firstVisible: Int, count: Int, atEnd: Boolean): Float =
        if (atEnd) 1f else if (count <= 1) 0f else (firstVisible.toFloat() / (count - 1)).coerceIn(0f, 1f)

    /** The row a finger at [fraction] of the track points at. */
    fun indexAt(fraction: Float, count: Int): Int = (fraction.coerceIn(0f, 1f) * (count - 1).coerceAtLeast(0)).roundToInt()
}

/**
 * A square at the right edge of the app list that shows the letter of the apps in view and where the list is. It shows while the list moves
 * and for a moment after, and can be dragged up and down to move through the list by letter, quickly; the list scrolls as before.
 * [headers] are the rows of the letters (row index and letter), [count] all the rows.
 */
@Composable
fun AlphaScroller(
    listState: LazyListState,
    headers: List<Pair<Int, String>>,
    count: Int,
    top: Dp,
    bottom: Dp,
    modifier: Modifier = Modifier,
) {
    if (headers.isEmpty() || count < 2) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf<Float?>(null) }
    var awake by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { moving ->
            if (moving) {
                awake = true
            } else {
                delay(IDLE_MS)
                awake = false
            }
        }
    }
    val shown by animateFloatAsState(if (awake || dragging != null) 1f else 0f, label = "alphaScroller")
    val scrolled by remember(count) {
        derivedStateOf { AlphaIndex.fraction(listState.firstVisibleItemIndex, count, !listState.canScrollForward) }
    }
    val fraction = dragging ?: scrolled
    val letter = AlphaIndex.letterAt(headers, if (dragging != null) AlphaIndex.indexAt(fraction, count) else listState.firstVisibleItemIndex) ?: return
    // Out of the tree while it is away: nothing of it takes a touch or counts as a part of the screen.
    if (shown <= 0f && dragging == null) return
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().padding(top = top, bottom = bottom)) {
        val track = with(density) { (maxHeight - THUMB).toPx() }.coerceAtLeast(1f)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (fraction * track).roundToInt()) }
                .graphicsLayer { alpha = shown }
                .testTag("alphascroller")
                // Grabbed only while it shows, and only up and down: a sideways drag still turns the page.
                .pointerInput(count, track) {
                    detectVerticalDragGestures(
                        onDragStart = { if (shown > 0f) dragging = scrolled },
                        onDragEnd = { dragging = null },
                        onDragCancel = { dragging = null },
                    ) { change, amount ->
                        val start = dragging ?: return@detectVerticalDragGestures
                        change.consume()
                        val next = (start + amount / track).coerceIn(0f, 1f)
                        dragging = next
                        scope.launch { listState.scrollToItem(AlphaIndex.indexAt(next, count)) }
                    }
                }
                .padding(start = 12.dp, end = 4.dp)
                .size(THUMB)
                .litFill(true),
            contentAlignment = Alignment.Center,
        ) {
            MetroText(letter.lowercase(), size = 28.sp, color = onAccent(), modifier = Modifier.testTag("alphascroller:letter"))
        }
    }
}

private val THUMB = 44.dp
private const val IDLE_MS = 1_400L
