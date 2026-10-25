package com.metrowin.launcher.ui.gesture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlin.math.abs

/**
 * One dot per page, the one in view bigger and brighter. The dots follow the finger while the pages move, and
 * the position is only read while drawing, so a swipe does not recompose anything.
 */
@Composable
fun PageDots(state: PagerState, count: Int, modifier: Modifier = Modifier) {
    val color = LocalMetroColors.current.foreground
    Canvas(modifier.width(DOT_SPACING * count).height(12.dp)) {
        val position = state.currentPage + state.currentPageOffsetFraction
        val spacing = DOT_SPACING.toPx()
        for (index in 0 until count) {
            val near = (1f - abs(position - index)).coerceIn(0f, 1f)
            drawCircle(
                color = lerp(color.copy(alpha = 0.3f), color, near),
                radius = (3f + 1.8f * near).dp.toPx(),
                center = Offset(spacing * (index + 0.5f), size.height / 2),
            )
        }
    }
}

private val DOT_SPACING = 16.dp
