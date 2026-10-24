package com.metrowin.launcher.ui.gesture

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * Pulling down from the very top of the screen opens [panel], wherever the launcher is scrolled to and
 * on every page, as the Windows Phone Action Center did. The gesture is watched before the content gets
 * it (the Initial pass), and only claimed once the finger has clearly moved down more than sideways,
 * so taps, long presses and sideways swipes underneath work as before.
 *
 * [zonePx] is how far from the top edge the finger has to start.
 */
fun Modifier.pullDownFromTop(panel: PullPanel, zonePx: Float, enabled: Boolean): Modifier =
    if (!enabled) this else pointerInput(panel, zonePx) { watchTopEdge(panel, zonePx) }

private suspend fun PointerInputScope.watchTopEdge(panel: PullPanel, zonePx: Float) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.y > zonePx) return@awaitEachGesture

        val slop = viewConfiguration.touchSlop
        val longPress = viewConfiguration.longPressTimeoutMillis
        val velocity = VelocityTracker().apply { addPointerInputChange(down) }
        var total = Offset.Zero
        var claimed = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // A second finger is a pinch or a two-finger gesture of someone else.
            if (event.changes.size > 1 && !claimed) return@awaitEachGesture
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture

            if (change.changedToUp() || !change.pressed) {
                if (claimed) {
                    velocity.addPointerInputChange(change)
                    change.consume()
                    panel.release(velocity.calculateVelocity().y)
                }
                return@awaitEachGesture
            }

            velocity.addPointerInputChange(change)
            val delta = change.positionChange()
            if (claimed) {
                panel.dragByPx(delta.y)
                change.consume()
                continue
            }

            total += delta
            when {
                // Held still: that is a long press, and the tile under it will want it.
                change.uptimeMillis - down.uptimeMillis > longPress -> return@awaitEachGesture
                // Sideways first: the pager's gesture.
                abs(total.x) > slop && abs(total.x) > abs(total.y) -> return@awaitEachGesture
                // Up: nothing to pull.
                total.y < -slop -> return@awaitEachGesture
                total.y > slop -> {
                    claimed = true
                    panel.dragByPx(total.y - slop)
                    change.consume()
                }
            }
        }
    }
}

/**
 * Pulling Start down while it is already at the top hands the extra distance to [panel] instead of
 * stretching the content, and pushing back up takes it away again before Start scrolls. [blocked] says
 * when the panel must not move (an app is opening, a menu is up, the page is not Start).
 */
fun pullDownConnection(panel: PullPanel, startScroll: ScrollState, blocked: () -> Boolean): NestedScrollConnection =
    object : NestedScrollConnection {
        /** True from the moment this gesture takes the panel until the finger lets go. */
        private var holding = false

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || !holding) return Offset.Zero
            if (panel.progress <= 0f && available.y <= 0f) {
                // Pushed all the way back up: the panel is put away and Start scrolls again.
                holding = false
                panel.release(0f)
                return Offset.Zero
            }
            // The panel is under the finger: it gets every movement, up or down, before Start does.
            return Offset(0f, panel.dragByPx(available.y))
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f || blocked() || startScroll.value != 0) return Offset.Zero
            holding = true
            panel.dragByPx(available.y)
            // Everything left over is taken, so the content does not stretch while the panel moves.
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (!holding) return Velocity.Zero
            holding = false
            panel.release(available.y)
            return available
        }
    }

/**
 * The other way round, on the open panel: when its list has nothing left to scroll, pushing further up
 * pushes the panel up with it, and the panel keeps the finger until it lets go (moving back down brings
 * the panel down again before the list moves).
 */
fun pushUpConnection(panel: PullPanel): NestedScrollConnection = object : NestedScrollConnection {
    private var holding = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !holding) return Offset.Zero
        return Offset(0f, panel.dragByPx(available.y))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
        holding = true
        panel.dragByPx(available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!holding) return Velocity.Zero
        holding = false
        panel.release(available.y)
        return available
    }
}
