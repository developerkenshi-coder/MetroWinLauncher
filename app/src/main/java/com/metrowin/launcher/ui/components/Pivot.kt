package com.metrowin.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** The name of the app in small capitals above its pivot ("SETTINGS" over "system", as in Windows Phone); null for no line. */
val LocalAppTitle = staticCompositionLocalOf<String?> { null }

/**
 * The titles of a Windows Phone pivot: large, side by side, the current one in full colour and the next
 * one peeking in at the edge. They slide with the pages, so swiping the pages moves the titles.
 * As in the Windows Phone settings, a title is touched too: the pivot swings to its page.
 */
@Composable
fun PivotHeader(
    titles: List<String>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = 44.sp,
    /** A colour for the titles (the current one in full, the others fainter) instead of the foreground; null keeps the usual look. */
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = LocalMetroColors.current
    val scope = rememberCoroutineScope()
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

    Column(modifier) {
        LocalAppTitle.current?.let { appTitle ->
            MetroText(appTitle.uppercase(), size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
        }
        Row(
            Modifier
                .clipToBounds()
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .offset { IntOffset(-shift.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            titles.forEachIndexed { index, title ->
                MetroText(
                    text = title,
                    size = size,
                    color = if (color == null) lerp(colors.foreground, colors.subtle, abs(position - index).coerceIn(0f, 1f))
                    else color.copy(alpha = 1f - 0.45f * abs(position - index).coerceIn(0f, 1f)),
                    modifier = Modifier
                        .onSizeChanged { widths[index] = it.width }
                        // A title is a tab of the pivot (the outer role wins over the button one metroPress sets), and says which page it is on.
                        .semantics { heading(); role = Role.Tab; selected = index == pagerState.currentPage }
                        .metroPress(onClick = { scope.launch { pagerState.animateScrollToPage(index) } }),
                )
            }
        }
    }
}
