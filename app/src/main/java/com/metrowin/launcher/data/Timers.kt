package com.metrowin.launcher.data

import android.content.Context
import android.provider.Settings
import java.util.Locale

/** How the timer and the stopwatch write time, and when their numbers have to change. Pure, so it is tested. */
object TimerFormat {
    /** What is left on a timer: "04:59", "1:04:59". Rounded up to the second, so it reads 00:01 until it really is over. */
    fun countdown(ms: Long): String = clock((ms.coerceAtLeast(0L) + 999L) / 1000L)

    /** The time of a stopwatch: "01:23", "1:01:23"; with [tenths] "01:23.4". */
    fun stopwatch(ms: Long, tenths: Boolean = false): String {
        val total = ms.coerceAtLeast(0L)
        val text = clock(total / 1000L)
        return if (tenths) text + "." + (total % 1000L / 100L) else text
    }

    /** A length given by the user in words: "5 min", "1 h 30 min", "45 s". */
    fun length(ms: Long): String {
        val seconds = (ms / 1000L).coerceAtLeast(0L)
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return listOfNotNull(h.takeIf { it > 0 }?.let { "$it h" }, m.takeIf { it > 0 }?.let { "$it min" }, s.takeIf { it > 0 }?.let { "$it s" })
            .joinToString(" ").ifEmpty { "0 s" }
    }

    private fun clock(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = totalSeconds % 3600 / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }

    /**
     * How long to wait before the number of a running timer changes: until the second boundary of what is left. At least 20 ms,
     * so a tick that fires a little early does not spin.
     */
    fun untilNextCountdownTick(remainingMs: Long): Long {
        val part = remainingMs % 1000L
        return (if (part == 0L) 1000L else part).coerceAtLeast(MIN_WAIT_MS)
    }

    /** The same for a stopwatch, which counts up: until [elapsedMs] reaches the next [stepMs] (1000 for seconds, 100 for tenths). */
    fun untilNextStopwatchTick(elapsedMs: Long, stepMs: Long): Long = (stepMs - elapsedMs % stepMs).coerceAtLeast(MIN_WAIT_MS)

    /** Pieces to add to a timer: the lengths of the buttons "+1 min", "+5 min". */
    val ADDITIONS_MS = listOf(60_000L, 5 * 60_000L)

    /** A timer length from the words of three fields: hours, minutes and seconds typed as text; null when it is nothing or too much. */
    fun parseLength(hours: String, minutes: String, seconds: String): Long? {
        val h = field(hours) ?: return null
        val m = field(minutes) ?: return null
        val s = field(seconds) ?: return null
        val total = (h * 3600 + m * 60 + s) * 1000L
        return total.takeIf { it in com.metrowin.launcher.model.TimerState.MIN_MS..com.metrowin.launcher.model.TimerState.MAX_MS }
    }

    private fun field(text: String): Long? = if (text.isBlank()) 0L else text.trim().toLongOrNull()?.takeIf { it in 0..999 }

    private const val MIN_WAIT_MS = 20L
}

/** How many times the phone has started: a stopwatch that ran across a reboot must not trust the clock that starts again at zero. */
object BootCount {
    fun read(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) }.getOrDefault(0)
}
