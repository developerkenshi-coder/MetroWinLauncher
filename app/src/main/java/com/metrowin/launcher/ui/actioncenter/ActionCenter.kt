package com.metrowin.launcher.ui.actioncenter

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppSquare
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.gesture.PullPanel
import com.metrowin.launcher.ui.gesture.pushUpConnection
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.TileWhite
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The Windows Phone Action Center, pulled down from the top of Start: four quick buttons that open out to
 * the rest, a link to all settings, and the notifications, each of which swipes away to the right.
 *
 * [panel] says how far it is pulled down (0 hidden, 1 fully open). It follows the finger one to one, and
 * a drag up anywhere on it, including past the end of the notifications, pushes it back up.
 */
@Composable
fun ActionCenter(
    slots: List<QuickAction>,
    states: Map<QuickAction, Boolean?>,
    notifications: List<NotificationEntry>,
    appsByPackage: Map<String, AppItem>,
    hasNotificationAccess: Boolean,
    panel: PullPanel,
    onAction: (QuickAction) -> Unit,
    onAllSettings: () -> Unit,
    onOpenNotification: (NotificationEntry) -> Unit,
    onDismissNotification: (NotificationEntry) -> Unit,
    onClearAll: () -> Unit,
    onTurnOnNotificationAccess: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    var expanded by remember { mutableStateOf(false) }
    val pushUp = remember(panel) { pushUpConnection(panel) }
    Box(
        modifier
            .fillMaxSize()
            // The only place the progress is read, and in the draw phase: pulling never recomposes the panel.
            .graphicsLayer { translationY = -(1f - panel.progress.coerceIn(0f, 1f)) * size.height }
            .background(colors.background)
            .nestedScroll(pushUp)
            // Dragging on the buttons and the headings, which do not scroll, moves the panel too.
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> panel.dragByPx(delta) },
                onDragStopped = { velocity -> panel.release(velocity) },
            )
            // Swallow touches so Start underneath does not react.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickRow(slots, states, onAction)
                if (expanded) {
                    val others = QuickAction.entries.filter { it !in slots }
                    others.chunked(QuickAction.SLOTS).forEach { row -> QuickRow(row, states, onAction) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExpandChevron(expanded) { expanded = !expanded }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.metroPress(onClick = onAllSettings).padding(horizontal = 8.dp, vertical = 8.dp)) {
                        MetroText(stringResource(R.string.ac_all_settings), size = 17.sp, weight = FontWeight.Normal, color = LocalAccent.current)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetroText(stringResource(R.string.ac_notifications), size = 26.sp, modifier = Modifier.weight(1f))
                if (notifications.isNotEmpty()) {
                    Box(Modifier.metroPress(onClick = onClearAll).padding(8.dp)) {
                        MetroText(stringResource(R.string.ac_clear_all), size = 17.sp, weight = FontWeight.Normal, color = LocalAccent.current)
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !hasNotificationAccess -> Column(
                        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MetroText(
                            stringResource(R.string.ac_access_needed),
                            size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, lineHeight = 22.sp,
                        )
                        MetroButton(stringResource(R.string.ac_turn_on), onClick = onTurnOnNotificationAccess)
                    }
                    notifications.isEmpty() -> MetroText(
                        stringResource(R.string.ac_empty),
                        size = 17.sp, weight = FontWeight.Normal, color = colors.subtle,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(notifications, key = { it.key }) { entry ->
                            NotificationRow(entry, appsByPackage[entry.packageName], onOpenNotification, onDismissNotification)
                        }
                    }
                }
            }

            PullUpHandle(onClose)
        }
    }
}

@Composable
private fun QuickRow(actions: List<QuickAction>, states: Map<QuickAction, Boolean?>, onAction: (QuickAction) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.forEach { action ->
            QuickButton(action, states[action], onAction, Modifier.weight(1f))
        }
        repeat(QuickAction.SLOTS - actions.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** A button of the Action Center: in the accent colour when what it controls is on, grey when it is off. */
@Composable
fun QuickButton(action: QuickAction, on: Boolean?, onClick: (QuickAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val lit = on == true
    Box(
        modifier
            .aspectRatio(1f)
            .testTag("quick:${action.id}")
            .metroPress(onClick = { onClick(action) })
            .background(if (lit) LocalAccent.current else colors.inactive),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(action.glyph, 40.dp, color = if (lit) TileWhite else colors.foreground)
        MetroText(
            stringResource(action.label),
            size = 11.sp, weight = FontWeight.Normal,
            color = if (lit) TileWhite else colors.subtle,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 4.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ExpandChevron(expanded: Boolean, onClick: () -> Unit) {
    val color = LocalMetroColors.current.foreground
    Box(Modifier.size(44.dp).metroPress(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(18.dp, 10.dp)) {
            val stroke = 2.4.dp.toPx()
            val tipY = if (expanded) 0f else size.height
            val baseY = size.height - tipY
            drawLine(color, Offset(0f, baseY), Offset(size.width / 2, tipY), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width, baseY), Offset(size.width / 2, tipY), stroke, StrokeCap.Round)
        }
    }
}

/** The bar at the bottom of the panel: tap it to put the panel away (dragging it is handled by the whole panel). */
@Composable
private fun PullUpHandle(onClose: () -> Unit) {
    val colors = LocalMetroColors.current
    val currentClose by rememberUpdatedState(onClose)
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) { detectTapGestures { currentClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(48.dp, 4.dp).background(colors.subtle))
    }
}

@Composable
private fun NotificationRow(
    entry: NotificationEntry,
    app: AppItem?,
    onOpen: (NotificationEntry) -> Unit,
    onDismiss: (NotificationEntry) -> Unit,
) {
    val colors = LocalMetroColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("notification:${entry.key}")
            .swipeToDismiss { onDismiss(entry) }
            .metroPress(onClick = { onOpen(entry) })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (app != null) {
            AppSquare(app, 44.dp)
        } else {
            Box(Modifier.size(44.dp).background(colors.surface), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Bell, 26.dp, color = colors.subtle)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (entry.title.isNotBlank()) MetroText(entry.title, size = 18.sp, weight = FontWeight.Normal)
            if (entry.text.isNotBlank()) {
                MetroText(entry.text, size = 15.sp, color = colors.subtle, maxLines = 2)
            }
            MetroText(
                text = DateUtils.getRelativeTimeSpanString(
                    entry.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString(),
                size = 12.sp, weight = FontWeight.Normal, color = colors.subtle,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Swipe to the right and the row slides off, fading, and [onDismissed] is called. A short swipe springs back. */
@Composable
private fun Modifier.swipeToDismiss(onDismissed: () -> Unit): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    val currentDismissed by rememberUpdatedState(onDismissed)
    return this
        .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
        .graphicsLayer {
            translationX = offset.value
            alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f)
        }
        .draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(-width * 0.15f)) }
            },
            onDragStopped = { velocity ->
                if (offset.value > width * SWIPE_FRACTION || velocity > SWIPE_VELOCITY) {
                    offset.animateTo(width, tween(durationMillis = 180))
                    currentDismissed()
                } else {
                    offset.animateTo(0f, spring())
                }
            },
        )
}

private const val SWIPE_FRACTION = 0.35f
private const val SWIPE_VELOCITY = 1_500f
