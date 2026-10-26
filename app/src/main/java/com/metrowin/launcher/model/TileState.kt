package com.metrowin.launcher.model

import androidx.compose.runtime.Immutable

/** Where a timer is in its life. */
enum class TimerStatus { Idle, Running, Paused, Finished }

/**
 * The state of a timer tile, kept as a few numbers so that nothing has to run for it to go on: a running timer is the moment
 * it ends ([endAt], the wall clock, the one the alarm that rings it is set on), and what is left is worked out from the clock
 * when somebody looks. [remainingMs] is what is left while it does not run (all of [durationMs] before it starts).
 */
@Immutable
data class TimerState(val durationMs: Long, val endAt: Long = 0L, val remainingMs: Long = durationMs) {
    val running: Boolean get() = endAt > 0L

    /** What is left at [now]: never more than the length of the timer (the clock may have been set back), never less than zero. */
    fun remaining(now: Long): Long = if (running) (endAt - now).coerceIn(0L, durationMs) else remainingMs.coerceIn(0L, durationMs)

    fun status(now: Long): TimerStatus = when {
        running -> if (endAt <= now) TimerStatus.Finished else TimerStatus.Running
        remainingMs <= 0L -> TimerStatus.Finished
        remainingMs >= durationMs -> TimerStatus.Idle
        else -> TimerStatus.Paused
    }

    /** Starts (or goes on from where it was paused); a finished timer starts again from the beginning. A running one stays as it is. */
    fun start(now: Long): TimerState = when (status(now)) {
        TimerStatus.Running -> this
        TimerStatus.Finished -> copy(endAt = now + durationMs, remainingMs = durationMs)
        else -> copy(endAt = now + remainingMs, remainingMs = remainingMs)
    }

    /** Stops the count where it is. What is not running stays as it is. */
    fun pause(now: Long): TimerState = if (!running) this else copy(endAt = 0L, remainingMs = remaining(now))

    /** Back to the whole length, not running. */
    fun reset(): TimerState = copy(endAt = 0L, remainingMs = durationMs)

    /** The same timer with another length, not running. */
    fun withDuration(ms: Long): TimerState = TimerState(ms.coerceIn(MIN_MS, MAX_MS))

    fun encode(): String = "$durationMs:$endAt:$remainingMs"

    companion object {
        const val MIN_MS = 1_000L
        const val MAX_MS = 24L * 60 * 60 * 1000
        const val DEFAULT_MS = 5L * 60 * 1000

        /** The lengths offered first, in minutes. */
        val PRESET_MINUTES = listOf(1, 3, 5, 10, 15, 30, 60)

        val Default = TimerState(DEFAULT_MS)

        /** The state written by [encode]; anything else gives the default timer back. */
        fun decode(text: String?): TimerState {
            val parts = text?.split(':') ?: return Default
            if (parts.size != 3) return Default
            val duration = parts[0].toLongOrNull()?.coerceIn(MIN_MS, MAX_MS) ?: return Default
            val endAt = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: return Default
            val remaining = parts[2].toLongOrNull()?.coerceIn(0L, duration) ?: return Default
            return TimerState(duration, endAt, remaining)
        }
    }
}

/**
 * The state of a stopwatch tile: the time it counted before ([accumulatedMs]) and, while it runs, when it started on the two
 * clocks of the phone. The time since start is taken from the clock that keeps counting while the phone sleeps ([startedElapsed],
 * `elapsedRealtime`), which a change of the date does not move; after a reboot that clock starts again, and the wall clock
 * ([startedWall]) is used instead. [laps] are the totals at which the lap button was pressed.
 */
@Immutable
data class StopwatchState(
    val accumulatedMs: Long = 0L,
    val startedElapsed: Long = NOT_RUNNING,
    val startedWall: Long = 0L,
    val bootCount: Int = 0,
    val laps: List<Long> = emptyList(),
) {
    val running: Boolean get() = startedElapsed != NOT_RUNNING

    /** The time counted at the moment given by the two clocks (and [boot], how many times the phone has started). */
    fun elapsed(nowElapsed: Long, nowWall: Long, boot: Int = bootCount): Long {
        if (!running) return accumulatedMs
        val sameBoot = boot == bootCount && nowElapsed >= startedElapsed
        val since = if (sameBoot) nowElapsed - startedElapsed else (nowWall - startedWall).coerceAtLeast(0L)
        return (accumulatedMs + since).coerceAtMost(MAX_MS)
    }

    fun start(nowElapsed: Long, nowWall: Long, boot: Int): StopwatchState =
        if (running) this else copy(startedElapsed = nowElapsed, startedWall = nowWall, bootCount = boot)

    fun stop(nowElapsed: Long, nowWall: Long, boot: Int): StopwatchState =
        if (!running) this else copy(accumulatedMs = elapsed(nowElapsed, nowWall, boot), startedElapsed = NOT_RUNNING, startedWall = 0L)

    /** Marks a lap (only while it runs); the last [MAX_LAPS] are kept. */
    fun lap(nowElapsed: Long, nowWall: Long, boot: Int): StopwatchState =
        if (!running) this else copy(laps = (laps + elapsed(nowElapsed, nowWall, boot)).takeLast(MAX_LAPS))

    fun reset(): StopwatchState = StopwatchState()

    /** The length of each lap, the oldest first: the difference between one total and the one before it. */
    fun lapLengths(): List<Long> = laps.mapIndexed { i, total -> total - (laps.getOrNull(i - 1) ?: 0L) }

    fun encode(): String = "$accumulatedMs;$startedElapsed;$startedWall;$bootCount;${laps.joinToString(",")}"

    companion object {
        const val NOT_RUNNING = -1L
        const val MAX_LAPS = 30
        const val MAX_MS = 99L * 60 * 60 * 1000

        /** The state written by [encode]; anything else gives a stopped stopwatch at zero. */
        fun decode(text: String?): StopwatchState {
            val parts = text?.split(';') ?: return StopwatchState()
            if (parts.size != 5) return StopwatchState()
            val accumulated = parts[0].toLongOrNull()?.coerceIn(0L, MAX_MS) ?: return StopwatchState()
            val elapsed = parts[1].toLongOrNull() ?: return StopwatchState()
            val wall = parts[2].toLongOrNull()?.coerceAtLeast(0L) ?: return StopwatchState()
            val boot = parts[3].toIntOrNull() ?: return StopwatchState()
            val laps = parts[4].split(',').mapNotNull { it.toLongOrNull()?.takeIf { lap -> lap in 0..MAX_MS } }.takeLast(MAX_LAPS)
            return StopwatchState(accumulated, if (elapsed < 0L) NOT_RUNNING else elapsed, wall, boot, laps)
        }
    }
}
