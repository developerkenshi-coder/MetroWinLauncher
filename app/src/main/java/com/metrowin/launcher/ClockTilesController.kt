package com.metrowin.launcher

import android.app.Application
import com.metrowin.launcher.data.BootCount
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.model.StopwatchState
import com.metrowin.launcher.model.TimerState
import com.metrowin.launcher.model.TimerStatus
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.notifications.Alerts

/**
 * The timer and the stopwatch tiles: each keeps its state as the text of its shortcut. The timer is a moment kept in the tile and one alarm
 * of Android; nothing runs while it counts. The stopwatch keeps the two clocks of the phone at the start, and does the arithmetic when somebody looks.
 */
internal class ClockTilesController(
    private val app: Application,
    private val shortcuts: () -> List<ShortcutDef>,
    private val add: (ShortcutDef, Int, TileSize) -> Unit,
    private val update: (String, (ShortcutDef) -> ShortcutDef) -> Unit,
) {
    /** Pins a timer of five minutes and gives back its id, so its page can open on it. */
    fun addTimer(page: Int): String {
        val id = ShortcutDef.newId(ShortcutKind.Timer, "timer")
        add(ShortcutDef(id, ShortcutKind.Timer, app.getString(R.string.timer_default_label), text = TimerState.Default.encode()), page, TileSize.Medium)
        return id
    }

    fun timerStart(id: String) = changeTimer(id) { it.start(System.currentTimeMillis()) }

    fun timerPause(id: String) = changeTimer(id) { it.pause(System.currentTimeMillis()) }

    fun timerReset(id: String) = changeTimer(id) { it.reset() }

    /** Another length for the timer, from the beginning. */
    fun timerLength(id: String, millis: Long) = changeTimer(id) { it.withDuration(millis) }

    /** Adds [millis] to the timer that runs (or to the one that waits), up to the longest one. */
    fun timerAdd(id: String, millis: Long) = changeTimer(id) { state ->
        val now = System.currentTimeMillis()
        if (state.running && state.status(now) == TimerStatus.Running) {
            val longer = (state.durationMs + millis).coerceAtMost(TimerState.MAX_MS)
            state.copy(durationMs = longer, endAt = state.endAt + (longer - state.durationMs))
        } else {
            state.withDuration(state.durationMs + millis)
        }
    }

    private fun changeTimer(id: String, change: (TimerState) -> TimerState) {
        val def = shortcuts().firstOrNull { it.id == id && it.kind == ShortcutKind.Timer } ?: return
        val after = change(TimerState.decode(def.text))
        update(id) { it.copy(text = after.encode()) }
        // The alarm that rings it is set while it counts, and taken back when it stops; the notification of a timer that rang goes with a reset.
        if (after.running && after.status(System.currentTimeMillis()) == TimerStatus.Running) {
            Alerts.scheduleTimer(app, id, def.label, after.endAt)
        } else {
            Alerts.cancelTimer(app, id)
        }
    }

    fun addStopwatch(page: Int): String {
        val id = ShortcutDef.newId(ShortcutKind.Stopwatch, "stopwatch")
        add(ShortcutDef(id, ShortcutKind.Stopwatch, app.getString(R.string.stopwatch_default_label), text = StopwatchState().encode()), page, TileSize.Medium)
        return id
    }

    fun stopwatchStart(id: String) = changeStopwatch(id) { it.start(android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot()) }

    fun stopwatchStop(id: String) = changeStopwatch(id) { it.stop(android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot()) }

    fun stopwatchLap(id: String) = changeStopwatch(id) { it.lap(android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot()) }

    fun stopwatchReset(id: String) = changeStopwatch(id) { it.reset() }

    private fun boot() = BootCount.read(app)

    private fun changeStopwatch(id: String, change: (StopwatchState) -> StopwatchState) {
        val def = shortcuts().firstOrNull { it.id == id && it.kind == ShortcutKind.Stopwatch } ?: return
        update(id) { it.copy(text = change(StopwatchState.decode(def.text)).encode()) }
    }
}
