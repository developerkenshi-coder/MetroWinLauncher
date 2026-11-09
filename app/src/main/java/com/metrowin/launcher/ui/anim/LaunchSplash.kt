package com.metrowin.launcher.ui.anim

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.ui.components.AppIcon
import com.metrowin.launcher.ui.theme.LocalAccent

/**
 * The splash screen of Windows Phone while an app of someone else starts: its icon on the accent colour. It sits behind the window of the
 * app, which comes in over it, and stays until the app is up (the launcher drops it when it is back).
 */
@Composable
fun LaunchSplash(item: AppItem, modifier: Modifier = Modifier) {
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, metroTween(durationMillis = 160, easing = Motion.Decelerate)) }
    Box(
        modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(LocalAccent.current).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) { AppIcon(item, 96.dp) }
}
