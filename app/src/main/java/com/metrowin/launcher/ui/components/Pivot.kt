package com.metrowin.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The titles of a Windows Phone pivot: large, side by side, the current one in full colour and the next
 * one peeking in at the edge. They slide with the pages, so swiping the pages moves the titles.
 */
@Composable
fun PivotHeader(titles: List<String>, pagerState: PagerState, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val widths = remember(titles) { mutableStateListOf<Int>().apply { repeat(titles.size) { add(0) } } }
    val spacing = with(LocalDensity.current) { 24.dp.roundToPx() }
    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction

    fun leftOf(index: Int): Float {
        var x = 0f
        for (k in 0 until index) x += widths[k] + spacing
        return x
    }

    val lower = floor(position).toInt().coerceIn(0, titles.lastIndex)
    val upper = (lower + 1).coerceAtMost(titles.lastIndex)
    val fraction = (position - lower).coerceIn(0f, 1f)
    val shift = leftOf(lower) + (leftOf(upper) - leftOf(lower)) * fraction

    Row(
        modifier
            .clipToBounds()
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .offset { IntOffset(-shift.roundToInt(), 0) },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        titles.forEachIndexed { index, title ->
            MetroText(
                text = title,
                size = 44.sp,
                color = lerp(colors.foreground, colors.subtle, abs(position - index).coerceIn(0f, 1f)),
                modifier = Modifier.onSizeChanged { widths[index] = it.width },
            )
        }
    }
}
