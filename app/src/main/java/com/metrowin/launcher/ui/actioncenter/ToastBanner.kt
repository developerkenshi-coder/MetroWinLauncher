package com.metrowin.launcher.ui.actioncenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppSquare
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.panel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long a banner stays when nobody touches it. */
const val BANNER_MS = 4_500L

/**
 * The banner of Windows Phone: a new notification slides down from the top edge of the screen, says who it is from and what, and
 * goes back up by itself (or with a swipe up). A touch opens the notification. Only while the launcher is on screen.
 */
@Composable
fun ToastBanner(
    entry: NotificationEntry,
    app: AppItem?,
    hideText: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offset = remember(entry.key) { Animatable(0f) }
    var height by remember { mutableIntStateOf(1) }
    val reduced = LocalReduceMotion.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    // In from the top, a while on screen, out again; a swipe up takes it out at once.
    val shown = remember(entry.key) { Animatable(0f) }
    LaunchedEffect(entry.key) {
        shown.animateTo(1f, tween(durationMillis = if (reduced) 80 else 260, easing = Motion.Decelerate))
        delay(BANNER_MS)
        shown.animateTo(0f, tween(durationMillis = if (reduced) 80 else 200, easing = Motion.Accelerate))
        currentDismiss()
    }
    val colors = LocalMetroColors.current
    Row(
        modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .graphicsLayer { translationY = -(1f - shown.value) * (height + 80.dp.toPx()) + offset.value }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceAtMost(0f)) } },
                onDragStopped = {
                    scope.launch {
                        if (offset.value < -height / 3f) currentDismiss() else offset.animateTo(0f, tween(150))
                    }
                },
            )
            .panel()
            .testTag("banner")
            .metroPress(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) AppSquare(app, 44.dp)
        Column(Modifier.weight(1f).padding(start = if (app != null) 14.dp else 0.dp)) {
            if (entry.title.isNotBlank()) MetroText(entry.title, size = 19.sp, maxLines = 1, modifier = Modifier.testTag("banner:title"))
            if (!hideText && entry.text.isNotBlank()) {
                MetroText(entry.text, size = 15.sp, weight = FontWeight.Normal, maxLines = 2, color = colors.subtle, modifier = Modifier.testTag("banner:text"))
            }
        }
    }
}
