package com.metrowin.launcher.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

enum class SwipeDirection { Up, Down }

/** The rule that tells a two-finger swipe from two fingers that just happen to be on the screen. Pure, so it can be tested. */
object TwoFingerSwipe {
    /**
     * [Down] or [Up] when both fingers have moved at least [thresholdPx] the same way, each more up or down than
     * sideways (a pinch or a rotation is not a swipe); null otherwise.
     */
    fun direction(startA: Offset, startB: Offset, nowA: Offset, nowB: Offset, thresholdPx: Float): SwipeDirection? {
        val a = nowA - startA
        val b = nowB - startB
        val vertical = abs(a.y) >= abs(a.x) && abs(b.y) >= abs(b.x)
        if (!vertical) return null
        return when {
            a.y >= thresholdPx && b.y >= thresholdPx -> SwipeDirection.Down
            a.y <= -thresholdPx && b.y <= -thresholdPx -> SwipeDirection.Up
            else -> null
        }
    }
}

/**
 * Calls [onDown] or [onUp] once when two fingers swipe down or up together. The touches are watched before the
 * content gets them and nothing is taken until the swipe is recognised, so one finger does what it always did; once
 * it is, the rest of the touch is taken, so the content under it does not scroll.
 */
@Composable
fun Modifier.twoFingerSwipe(enabled: Boolean, thresholdPx: Float, onDown: () -> Unit, onUp: () -> Unit): Modifier {
    val currentDown by rememberUpdatedState(onDown)
    val currentUp by rememberUpdatedState(onUp)
    if (!enabled) return this
    return pointerInput(thresholdPx) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var ids: Pair<PointerId, PointerId>? = null
            var starts: Pair<Offset, Offset>? = null
            var fired = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) return@awaitEachGesture
                if (fired) {
                    pressed.forEach { it.consume() }
                    continue
                }
                if (ids == null && pressed.size >= 2) {
                    ids = pressed[0].id to pressed[1].id
                    starts = pressed[0].position to pressed[1].position
                }
                val pair = ids ?: continue
                val start = starts ?: continue
                val a = pressed.firstOrNull { it.id == pair.first } ?: continue
                val b = pressed.firstOrNull { it.id == pair.second } ?: continue
                val direction = TwoFingerSwipe.direction(start.first, start.second, a.position, b.position, thresholdPx) ?: continue
                fired = true
                pressed.forEach { it.consume() }
                if (direction == SwipeDirection.Down) currentDown() else currentUp()
            }
        }
    }
}
