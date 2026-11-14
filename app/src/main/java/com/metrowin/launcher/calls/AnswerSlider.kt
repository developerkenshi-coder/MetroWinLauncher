package com.metrowin.launcher.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.dialer.CallGreen
import com.metrowin.launcher.dialer.CallRed
import com.metrowin.launcher.ui.components.MetroText
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Answer and decline as one bar: the square in the middle is dragged to the right to answer and to the left to decline, and lets go back to the
 * middle if it is not taken far enough (a touch in a pocket does not answer). TalkBack gets the same two things as actions.
 */
@Composable
fun AnswerSlider(onAnswer: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    var width by remember { mutableFloatStateOf(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    val answer = stringResource(R.string.incall_answer)
    val decline = stringResource(R.string.incall_decline)
    val knob = 64.dp
    Box(
        modifier.fillMaxWidth().height(knob).testTag("incall:slider").onSizeChanged { width = it.width.toFloat() }
            .semantics { customActions = listOf(CustomAccessibilityAction(answer) { onAnswer(); true }, CustomAccessibilityAction(decline) { onDecline(); true }) }
            .background(Color.Black.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(decline, size = 16.sp, weight = FontWeight.Normal, color = CallRed, modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp))
        MetroText(answer, size = 16.sp, weight = FontWeight.Normal, color = CallGreen, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
        Box(
            Modifier.offset { IntOffset(drag.roundToInt(), 0) }.size(knob)
                .background(if (abs(drag) < 1f) Color.White else if (drag > 0) CallGreen else CallRed)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val reach = (width - knob.toPx()) / 2
                            val taken = drag
                            drag = 0f
                            if (taken > reach * TAKEN) onAnswer() else if (taken < -reach * TAKEN) onDecline()
                        },
                        onDragCancel = { drag = 0f },
                    ) { _, delta ->
                        val reach = (width - knob.toPx()) / 2
                        drag = (drag + delta).coerceIn(-reach, reach)
                    }
                },
        )
    }
}

/** How far of the way to an end the square must be taken, as a fraction, for the call to be answered or declined. */
private const val TAKEN = 0.8f
