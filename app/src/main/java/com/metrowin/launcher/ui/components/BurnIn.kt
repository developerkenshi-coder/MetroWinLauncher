package com.metrowin.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay

/** The places the screen is moved to, in dp, one after the other: a ring of a few pixels around the middle, so no pixel shows the same thing for long. */
object BurnIn {
    val steps = listOf(0 to 0, 3 to 0, 3 to 3, 0 to 3, -3 to 3, -3 to 0, -3 to -3, 0 to -3, 3 to -3)

    /** How often the screen moves to the next place. */
    const val EVERY_MS = 60_000L

    /** The place number [tick] of the ring, wrapping round. */
    fun at(tick: Int): Pair<Int, Int> = steps[tick.mod(steps.size)]
}

/**
 * Moves everything inside by a few dp, slowly and rarely, so that the parts of the screen that never change (the tiles, the bars of the
 * phone's own colours) do not burn into an OLED panel. Nothing is moved when [enabled] is off.
 */
@Composable
fun BurnInShift(enabled: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            x.snapTo(0f); y.snapTo(0f)
            return@LaunchedEffect
        }
        var tick = 0
        while (true) {
            delay(BurnIn.EVERY_MS)
            tick++
            val (dx, dy) = BurnIn.at(tick)
            x.animateTo(dx * density, tween(2_000))
            y.animateTo(dy * density, tween(2_000))
        }
    }
    Box(Modifier.graphicsLayer { translationX = x.value; translationY = y.value }) { content() }
}
