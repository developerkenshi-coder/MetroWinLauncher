package com.metrowin.launcher

import android.provider.CallLog
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.dialer.CallHistory
import com.metrowin.launcher.dialer.HistoryFilter
import com.metrowin.launcher.dialer.NumberBook
import com.metrowin.launcher.dialer.numberSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CallHistoryTest {
    private fun call(type: Int, date: Long, number: String = "333", name: String = "Anna", seconds: Int = 0) = RecentCall(name, number, type, date, id = date, duration = seconds)

    private val log = listOf(
        call(CallLog.Calls.INCOMING_TYPE, 5_000, seconds = 60),
        call(CallLog.Calls.OUTGOING_TYPE, 4_000, seconds = 120),
        call(CallLog.Calls.MISSED_TYPE, 3_000, "444", "Bruno"),
        call(CallLog.Calls.REJECTED_TYPE, 2_000, "444", "Bruno"),
        call(CallLog.Calls.OUTGOING_TYPE, 1_000, seconds = 30),
    )

    @Test
    fun filtersKeepTheirOwnKind() {
        assertEquals(5, log.count { HistoryFilter.All.matches(it) })
        assertEquals(2, log.count { HistoryFilter.Missed.matches(it) })
        assertEquals(1, log.count { HistoryFilter.Received.matches(it) })
        assertEquals(2, log.count { HistoryFilter.Sent.matches(it) })
    }

    @Test
    fun statsCountKindsTimeAndTheMostCalled() {
        val stats = CallHistory.stats(log)
        assertEquals(5, stats.total)
        assertEquals(2, stats.missed)
        assertEquals(210L, stats.talkSeconds)
        assertEquals(120, stats.longestSeconds)
        assertEquals("Anna" to 3, stats.top.first())
    }

    @Test
    fun statsLeaveOutWhatIsOlderThanTheLimit() {
        assertEquals(2, CallHistory.stats(log, since = 4_000).total)
    }

    @Test
    fun daysGroupInTheOrderTheyCome() {
        val day = 24 * 3600 * 1000L
        val days = CallHistory.byDay(NumberBook.group(listOf(call(CallLog.Calls.INCOMING_TYPE, 3 * day + 5), call(CallLog.Calls.OUTGOING_TYPE, 3 * day), call(CallLog.Calls.MISSED_TYPE, day))), ZoneOffset.UTC)
        assertEquals(listOf(LocalDate.ofEpochDay(3), LocalDate.ofEpochDay(1)), days.map { it.first })
        assertEquals(2, days.first().second.size)
    }

    @Test
    fun csvQuotesCommasAndGuardsFormulasInNames() {
        val text = CallHistory.csv(listOf(call(CallLog.Calls.MISSED_TYPE, 7, "+39 333", "=SUM(A1), \"x\"", 0)))
        val row = text.lines()[1]
        assertTrue(row, row.startsWith("7,+39 333,\"'=SUM(A1), \"\"x\"\"\",missed,0"))
    }

    @Test
    fun theNumberShrinksAsItGrows() {
        assertTrue(numberSize(5) > numberSize(15))
        assertTrue(numberSize(15) > numberSize(32))
    }
}
