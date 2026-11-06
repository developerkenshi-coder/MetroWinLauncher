package com.metrowin.launcher

import android.provider.CallLog
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.dialer.NumberBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberBookTest {
    private fun call(number: String, type: Int, date: Long) = RecentCall(number, number, type, date)
    private val missed = CallLog.Calls.MISSED_TYPE
    private val out = CallLog.Calls.OUTGOING_TYPE
    private val inn = CallLog.Calls.INCOMING_TYPE

    @Test fun consecutiveCallsOfTheSameNumberAndKindAreOneRow() {
        val groups = NumberBook.group(listOf(call("+39 333 1", missed, 9), call("+393331", missed, 8), call("+393331", out, 7), call("+393331", missed, 6), call("555", missed, 5)))
        assertEquals(listOf(2, 1, 1, 1), groups.map { it.count })
        assertEquals(9L, groups[0].latest.date)
        assertEquals(listOf(9L, 8L), groups[0].members.map { it.date })
    }

    @Test fun rejectedCountsAsMissedAndNoCallsGiveNoRows() {
        assertEquals(1, NumberBook.group(listOf(call("1", missed, 2), call("1", CallLog.Calls.REJECTED_TYPE, 1))).size)
        assertEquals(2, NumberBook.group(listOf(call("1", inn, 2), call("1", out, 1))).size)
        assertEquals(0, NumberBook.group(emptyList()).size)
    }

    @Test fun aNoteIsKeptUnderTheCleanNumberCutAndRemovedWhenBlank() {
        val one = NumberBook.withNote(emptyMap(), "+39 333-111", "  plumber  ")
        assertEquals(mapOf("+39333111" to "plumber"), one)
        assertEquals(140, NumberBook.withNote(emptyMap(), "1", "x".repeat(500))["1"]!!.length)
        assertEquals(emptyMap<String, String>(), NumberBook.withNote(one, "+39333111", " "))
    }

    @Test fun theOldestNotesGoWhenThereAreTooMany() {
        var notes = emptyMap<String, String>()
        (1..NumberBook.MAX_NOTES + 5).forEach { notes = NumberBook.withNote(notes, it.toString(), "n$it") }
        assertEquals(NumberBook.MAX_NOTES, notes.size)
        assertNull(notes["1"])
        assertEquals("n105", notes["105"])
    }

    @Test fun aKeyHoldsOneNumberAndANumberOneKey() {
        val a = NumberBook.withKey(emptyMap(), '2', "333 111")
        assertEquals(mapOf('2' to "333111"), a)
        val moved = NumberBook.withKey(a, '5', "333111")
        assertEquals(mapOf('5' to "333111"), moved)
        assertEquals('5', NumberBook.keyOf(moved, "333-111"))
        assertEquals(mapOf('5' to "999"), NumberBook.withKey(moved, '5', "999"))
        assertEquals(emptyMap<Char, String>(), NumberBook.withKey(moved, '5', ""))
        assertEquals(moved, NumberBook.withKey(moved, '1', "7"))
        assertNull(NumberBook.keyOf(moved, "1"))
    }
}
