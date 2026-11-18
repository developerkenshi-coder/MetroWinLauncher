package com.metrowin.launcher.ui.actioncenter

import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import com.metrowin.launcher.ui.anim.metroTween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.metrowin.launcher.ui.components.topBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.MetroTextField
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
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.panel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A sideways fling faster than this (px/s) takes the banner away. */
private const val FLING_AWAY = 1_200f

/** How long a banner stays when nobody touches it. */
const val BANNER_MS = 5_000L

/**
 * The banner of Windows Phone: a new notification slides down from the top edge of the screen, says who it is from and what, and
 * goes back up by itself (or with a swipe up, or sideways). A touch opens the notification. Only while the launcher is on screen.
 */
@Composable
fun ToastBanner(
    entry: NotificationEntry,
    app: AppItem?,
    hideText: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** The room to leave at the top when the window is given no insets (a window over other apps); null to read them. */
    topInset: androidx.compose.ui.unit.Dp? = null,
    /** A word on the right of the banner that does something (Undo); null for none. */
    action: String? = null,
    onAction: () -> Unit = {},
    /** Answers the notification from the banner: true when it was sent. Null where the banner cannot take the keyboard (a window over other apps) or the answer is off. */
    onReply: ((String) -> Boolean)? = null,
) {
    val scope = rememberCoroutineScope()
    val offset = remember(entry.key) { Animatable(0f) }
    // The finger sideways: the banner follows it, goes away when it is taken far enough or flung, and comes back to its place when the finger goes back.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slide = remember(entry.key) { Animatable(0f) }
    var height by remember { mutableIntStateOf(1) }
    var width by remember { mutableIntStateOf(1) }
    val reduced = LocalReduceMotion.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    var replying by remember(entry.key) { mutableStateOf(false) }
    var answer by remember(entry.key) { mutableStateOf("") }
    var failed by remember(entry.key) { mutableStateOf(false) }
    val typing = remember { FocusRequester() }
    LaunchedEffect(replying) { if (replying) runCatching { typing.requestFocus() } }
    fun send() {
        if (answer.isBlank()) return
        if (onReply?.invoke(answer) == true) { replying = false; currentDismiss() } else failed = true
    }
    // In from the top, a while on screen, then out sideways as on Windows Phone; a swipe up takes it out at once.
    val shown = remember(entry.key) { Animatable(0f) }
    val aside = remember(entry.key) { Animatable(0f) }
    LaunchedEffect(entry.key) {
        shown.animateTo(1f, metroTween(durationMillis = if (reduced) 80 else 260, easing = Motion.Decelerate))
        delay(BANNER_MS)
        // While an answer is being written the banner stays.
        while (replying) delay(250)
        if (reduced) shown.animateTo(0f, metroTween(durationMillis = 80, easing = Motion.Accelerate))
        else aside.animateTo(1f, metroTween(durationMillis = 220, easing = Motion.Accelerate))
        currentDismiss()
    }
    val colors = LocalMetroColors.current
    Row(
        modifier
            .then(if (topInset != null) Modifier.padding(top = topInset) else Modifier.topBarsPadding())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .onSizeChanged { height = it.height.coerceAtLeast(1); width = it.width.coerceAtLeast(1) }
            .graphicsLayer { translationY = -(1f - shown.value) * (height + 80.dp.toPx()) + offset.value; translationX = aside.value * (width + 24.dp.toPx()) + slide.value }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceAtMost(0f)) } },
                onDragStopped = {
                    scope.launch {
                        if (offset.value < -height / 3f) currentDismiss() else offset.animateTo(0f, metroTween(150))
                    }
                },
            )
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> scope.launch { slide.snapTo(slide.value + delta) } },
                onDragStopped = { velocity ->
                    scope.launch {
                        val away = width + with(density) { 24.dp.toPx() }
                        if (kotlin.math.abs(slide.value) > width / 3f || kotlin.math.abs(velocity) > FLING_AWAY) {
                            slide.animateTo(if (slide.value + velocity * 0.1f < 0f) -away else away, metroTween(durationMillis = 160, easing = Motion.Accelerate))
                            currentDismiss()
                        } else slide.animateTo(0f, metroTween(150))
                    }
                },
            )
            .panel()
            .testTag("banner")
            // TalkBack reads a banner when it comes, without the user having to find it.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
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
            if (replying) {
                MetroTextField(answer, { answer = it; failed = false }, stringResource(R.string.notif_reply_hint), Modifier.padding(top = 8.dp).focusRequester(typing).testTag("banner:replyfield"), onSearch = ::send)
                if (failed) MetroText(stringResource(R.string.notif_reply_failed), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.testTag("banner:replyfailed"))
            }
        }
        if (onReply != null && entry.reply != null && action == null) {
            MetroText(
                stringResource(if (replying) R.string.notif_send else R.string.notif_reply), size = 19.sp, weight = FontWeight.SemiBold, color = LocalAccent.current,
                modifier = Modifier.testTag("banner:reply").metroPress(onClick = { if (replying) send() else replying = true }).padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        if (action != null) {
            MetroText(
                action, size = 19.sp, weight = FontWeight.SemiBold, color = LocalAccent.current,
                modifier = Modifier.testTag("banner:action").metroPress(onClick = onAction).padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
            )
        }
    }
}
