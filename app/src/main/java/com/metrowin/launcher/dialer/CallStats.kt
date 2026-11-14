package com.metrowin.launcher.dialer

import android.provider.CallLog
import com.metrowin.launcher.data.PhoneCalls
import com.metrowin.launcher.data.RecentCall
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Which calls of the history are shown. */
enum class HistoryFilter {
    All, Missed, Received, Sent;

    fun matches(call: RecentCall): Boolean = when (this) {
        All -> true
        Missed -> call.missed
        Received -> call.type == CallLog.Calls.INCOMING_TYPE || call.type == CallLog.Calls.ANSWERED_EXTERNALLY_TYPE
        Sent -> call.outgoing
    }
}

/** The numbers of a stretch of the call log, for the page of statistics. */
data class CallStats(
    val total: Int, val received: Int, val sent: Int, val missed: Int,
    /** Seconds of talk, of all the calls. */
    val talkSeconds: Long, val longestSeconds: Int,
    /** The numbers called or calling most often (name, calls), most first. */
    val top: List<Pair<String, Int>>,
)

/** Pure parts of the history: statistics, the days of the headings, the file of the backup. */
object CallHistory {
    /** The statistics of the calls of [calls] that are not older than [since] (epoch milliseconds). */
    fun stats(calls: List<RecentCall>, since: Long = 0, top: Int = 3): CallStats {
        val inRange = calls.filter { it.date >= since }
        val byNumber = inRange.groupBy { PhoneCalls.clean(it.number) }.entries
            .sortedByDescending { it.value.size }.take(top).map { it.value.first().name to it.value.size }
        return CallStats(
            total = inRange.size,
            received = inRange.count { HistoryFilter.Received.matches(it) },
            sent = inRange.count { it.outgoing },
            missed = inRange.count { it.missed },
            talkSeconds = inRange.sumOf { it.duration.toLong() },
            longestSeconds = inRange.maxOfOrNull { it.duration } ?: 0,
            top = byNumber,
        )
    }

    /** The day of a call, in [zone]: what the headings of the history group on. */
    fun dayOf(date: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()

    /** The calls of [calls] (newest first) as days: each day with its calls in the order they came. */
    fun byDay(calls: List<CallGroup>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, List<CallGroup>>> =
        calls.groupBy { dayOf(it.latest.date, zone) }.toList()

    /** [calls] as a file of comma separated values: when (epoch milliseconds), number, name, way, seconds. A name starting with = + - @ is kept as text. */
    fun csv(calls: List<RecentCall>): String = buildString {
        append("date,number,name,type,seconds\n")
        for (call in calls) {
            val kind = when {
                call.blocked -> "blocked"
                call.rejected -> "rejected"
                call.missed -> "missed"
                call.outgoing -> "outgoing"
                else -> "incoming"
            }
            append(call.date).append(',').append(cell(call.number, guard = false)).append(',').append(cell(call.name, guard = true)).append(',').append(kind).append(',').append(call.duration).append('\n')
        }
    }

    private fun cell(text: String, guard: Boolean): String {
        // A name that starts with one of these is a formula for a spreadsheet: a quote in front keeps it text (a number may start with +).
        val safe = if (guard && text.firstOrNull()?.let { it in "=+-@\t\r" } == true) "'$text" else text
        return if (safe.any { it == ',' || it == '"' || it == '\n' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
