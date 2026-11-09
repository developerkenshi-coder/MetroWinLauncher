package com.metrowin.launcher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalReduceMotion

private const val DOTS = 5

/** Where a dot is along the bar (0..1) when it has gone [u] of its way: fast at the edges, slow through the middle, as on Windows Phone. */
internal fun dotPosition(u: Float): Float {
    val c = u.coerceIn(0f, 1f) - 0.5f
    return 0.5f + 4f * c * c * c
}

/**
 * The progress bar of Windows Phone for something that takes a while: five dots that run across the screen one after the other.
 * Without motion the dots stand in a row.
 */
@Composable
fun LoadingDots(modifier: Modifier = Modifier, color: Color = LocalAccent.current) {
    val reduced = LocalReduceMotion.current
    val t = if (reduced) {
        0f
    } else {
        rememberInfiniteTransition(label = "loadingDots").animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "t").value
    }
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val r = 1.5.dp.toPx()
        val span = size.width - 2 * r
        for (i in 0 until DOTS) {
            val x = if (reduced) (i + 1) / (DOTS + 1f) else dotPosition((t - i * 0.06f) / (1f - (DOTS - 1) * 0.06f).coerceAtLeast(0.1f))
            // Dots that have not started wait at the left edge, the ones that finished at the right one.
            drawCircle(color, r, Offset(r + span * x, size.height / 2f))
        }
    }
}
