package com.metrowin.launcher

import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LauncherStatus
import com.metrowin.launcher.data.LayoutBackup
import com.metrowin.launcher.data.LayoutBackupCodec
import com.metrowin.launcher.data.SafeStart
import com.metrowin.launcher.shizukuterminal.SavedNames
import com.metrowin.launcher.shizukuterminal.suggestions
import com.metrowin.launcher.wallet.Card
import com.metrowin.launcher.wallet.SortMode
import com.metrowin.launcher.wallet.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoM7Test {
    @Test fun twoQuickCrashesInARowTurnSafeModeOn() {
        var count = 0
        count = SafeStart.nextCount(count, 3_000)
        assertFalse(SafeStart.shouldBeOn(count))
        count = SafeStart.nextCount(count, 8_000)
        assertTrue(SafeStart.shouldBeOn(count))
    }

    @Test fun aCrashAfterALongRunStartsTheCountAgain() {
        assertEquals(0, SafeStart.nextCount(1, SafeStart.QUICK_MS + 1))
    }

    @Test fun walletSortsByUsesWhenAsked() {
        fun card(id: String, uses: Int) = Card(id, id, "1", uses = uses)
        val sorted = Wallet.sorted(listOf(card("a", 1), card("b", 5), card("c", 5)), SortMode.MostUsed)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test fun keptCommandsHaveNamesThatAreSavedAndFound() {
        val names = mapOf("dumpsys battery" to "Power", "wm size" to "")
        val back = SavedNames.decode(SavedNames.encode(names))
        assertEquals(mapOf("dumpsys battery" to "Power"), back)
        assertEquals(emptyMap<String, String>(), SavedNames.decode("not json"))
        assertEquals(emptyMap<String, String>(), SavedNames.decode(null))
        val saved = listOf("dumpsys battery", "uptime")
        assertEquals(listOf("dumpsys battery"), suggestions(saved, "pow", back))
        assertEquals(emptyList<String>(), suggestions(saved, "pow"))
    }

    @Test fun theStatusReportHasOneLinePerFactAndTheLastError() {
        val text = LauncherStatus.report("MetroWin 1 (2)", listOf("Contacts" to "yes", "Calendar" to "no"), null, "Last error")
        assertEquals("MetroWin 1 (2)\nContacts: yes\nCalendar: no\nLast error: -", text)
        assertTrue(LauncherStatus.report("h", emptyList(), "boom", "Err").endsWith("Err: boom"))
    }

    @Test fun aBackupFileOfVersionOneStillOpens() {
        val current = LauncherSettings()
        val written = LayoutBackupCodec.encode(LayoutBackup(listOf(emptyList()), emptyList(), current), exportedAt = 1L)
        // The first version had no extras and said version 1.
        val old = org.json.JSONObject(written).apply { put("version", 1); remove("extras") }.toString()
        val restored = LayoutBackupCodec.decode(old, current)
        assertNotNull(restored)
        assertNull(restored!!.extras)
        // A file from a later version is refused, not guessed at.
        val newer = org.json.JSONObject(written).apply { put("version", LayoutBackupCodec.VERSION + 1) }.toString()
        assertNull(LayoutBackupCodec.decode(newer, current))
    }
}
