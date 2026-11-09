package com.metrowin.launcher.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlin.math.abs

/** The angle, in degrees (clockwise), a page that was upright before the phone was turned from [from] to [to] (the values of `Surface.ROTATION_*`, counter-clockwise quarter turns) seems turned by: it comes back to 0. Pure. */
fun turnStart(from: Int, to: Int): Float = when (((to - from) % 4 + 4) % 4) {
    0 -> 0f
    1 -> 90f
    3 -> -90f
    else -> 180f
}

/**
 * The rotation of Windows Phone: when the phone is turned the page does not jump to the new orientation, it comes round from where it was (turned
 * the way the phone was), growing back to full size and light. With reduced motion it just changes. For the activities that look after the
 * change of orientation themselves (see `configChanges` in the manifest) instead of being created again.
 */
@Composable
fun Modifier.rotateOnTurn(): Modifier {
    val reduced = LocalReduceMotion.current
    val view = LocalView.current
    // Read so that the page is composed again when the phone is turned.
    LocalConfiguration.current
    val rotation = view.display?.rotation ?: 0
    var last by remember { mutableIntStateOf(rotation) }
    val turn = remember { Animatable(0f) }
    LaunchedEffect(rotation) {
        val from = last
        last = rotation
        if (reduced || from == rotation) return@LaunchedEffect
        turn.snapTo(turnStart(from, rotation))
        turn.animateTo(0f, metroTween(durationMillis = 420, easing = Motion.Decelerate))
    }
    return graphicsLayer {
        val left = abs(turn.value) / 90f
        rotationZ = turn.value
        val grow = 1f - 0.25f * left.coerceAtMost(1f)
        scaleX = grow
        scaleY = grow
        alpha = 1f - 0.7f * left.coerceAtMost(1f)
    }
}
