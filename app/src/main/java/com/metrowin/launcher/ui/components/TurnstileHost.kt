package com.metrowin.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.turnstile

/**
 * Shows [content] while [show] is true, swinging in and out like a turnstile: how Windows Phone moves
 * between pages. Nothing is composed while the page is out of sight.
 */
@Composable
fun TurnstileHost(show: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val away = remember { Animatable(1f) }
    var composed by remember { mutableStateOf(show) }
    LaunchedEffect(show) {
        if (show) {
            composed = true
            away.animateTo(0f, tween(durationMillis = 380, easing = Motion.Decelerate))
        } else {
            away.animateTo(1f, tween(durationMillis = 240, easing = Motion.Accelerate))
            composed = false
        }
    }
    if (composed) Box(modifier.graphicsLayer { turnstile(away.value) }) { content() }
}
