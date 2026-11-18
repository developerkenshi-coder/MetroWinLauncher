package com.metrowin.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.anim.metroTween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** When a sideways drag counts as "do it": far enough, or fast enough. Pure, so it is tested. */
internal object SwipeRule {
    const val FRACTION = 0.35f
    const val VELOCITY = 1500f

    /** 1 to the right, -1 to the left, 0 when the row springs back. [offset] is where the row is and [velocity] how fast it was going (positive is to the right), in pixels. */
    fun decide(offset: Float, velocity: Float, width: Float, canRight: Boolean, canLeft: Boolean): Int = when {
        canRight && (offset > width * FRACTION || (velocity > VELOCITY && offset > 0f)) -> 1
        canLeft && (offset < -width * FRACTION || (velocity < -VELOCITY && offset < 0f)) -> -1
        else -> 0
    }
}

/**
 * A row that goes away sideways: to the right it does [onRight], to the left [onLeft] (null for a side that does nothing). A strip with the word of the
 * action shows behind it while it moves, the row slides off when far enough, and springs back when let go too soon (or when the action left it in
 * place, as a question asked first does). TalkBack and the switch controls get the two actions in the row's menu.
 */
@Composable
fun SwipeRow(
    modifier: Modifier = Modifier,
    onRight: (() -> Unit)?,
    rightLabel: String,
    rightColor: Color,
    onLeft: (() -> Unit)?,
    leftLabel: String,
    leftColor: Color,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableFloatStateOf(1f) }
    val right by rememberUpdatedState(onRight)
    val left by rememberUpdatedState(onLeft)
    val actions = buildList {
        onRight?.let { add(CustomAccessibilityAction(rightLabel) { it(); true }) }
        onLeft?.let { add(CustomAccessibilityAction(leftLabel) { it(); true }) }
    }
    Box(modifier.onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }.semantics { customActions = actions }) {
        val moving = offset.value
        if (moving != 0f) {
            val toRight = moving > 0f
            Box(Modifier.matchParentSize().background(if (toRight) rightColor else leftColor).padding(horizontal = 20.dp), contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd) {
                MetroText(if (toRight) rightLabel else leftLabel, size = 16.sp, color = Color.White, maxLines = 1)
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value; alpha = 1f - 0.5f * (abs(offset.value) / width).coerceIn(0f, 1f) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val next = offset.value + delta
                            // A side with no action does not give way.
                            offset.snapTo(if ((next > 0f && right == null) || (next < 0f && left == null)) 0f else next)
                        }
                    },
                    onDragStopped = { velocity ->
                        val way = SwipeRule.decide(offset.value, velocity, width, right != null, left != null)
                        if (way == 0) {
                            offset.animateTo(0f, androidx.compose.animation.core.spring())
                        } else {
                            offset.animateTo(way * width, metroTween(durationMillis = 180))
                            (if (way > 0) right else left)?.invoke()
                            // The row is still here if the action did not take it away: it comes back.
                            delay(400)
                            offset.animateTo(0f, androidx.compose.animation.core.spring())
                        }
                    },
                ),
        ) { content() }
    }
}
