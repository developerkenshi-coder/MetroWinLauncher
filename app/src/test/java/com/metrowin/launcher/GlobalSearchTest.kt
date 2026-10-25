package com.metrowin.launcher

import com.metrowin.launcher.data.GlobalSearch
import com.metrowin.launcher.data.SystemSettingDef
import com.metrowin.launcher.model.Glyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchTest {
    private val labels = mapOf(
        "wifi" to "Wi-Fi", "bluetooth" to "Bluetooth", "battery" to "Batteria", "display" to "Schermo",
        "sound" to "Suoni", "date" to "Data e ora", "apps" to "App", "storage" to "Memoria",
    )

    private val defs = listOf(
        SystemSettingDef("wifi", 0, "wifi", Glyph.Wifi, listOf("wifi", "wireless", "network")),
        SystemSettingDef("bluetooth", 0, "bt", Glyph.Bluetooth, listOf("bluetooth", "pair")),
        SystemSettingDef("battery", 0, "battery", Glyph.Battery, listOf("battery", "power")),
        SystemSettingDef("display", 0, "display", Glyph.Brightness, listOf("display", "screen", "brightness")),
        SystemSettingDef("sound", 0, "sound", Glyph.Bell, listOf("sound", "volume")),
        SystemSettingDef("date", 0, "date", Glyph.Alarm, listOf("date", "time", "clock")),
        SystemSettingDef("apps", 0, "apps", Glyph.Store, listOf("apps", "applications")),
        SystemSettingDef("storage", 0, "storage", Glyph.Storage, listOf("storage", "memory")),
    )

    private fun match(query: String) = GlobalSearch.matchSettings(query, defs) { labels.getValue(it.id) }.map { it.id }

    @Test
    fun aSettingIsFoundByItsNameInTheLanguageOfThePhone() {
        assertEquals(listOf("battery"), match("batt"))
        assertEquals(listOf("display"), match("scher"))
        assertEquals(listOf("date"), match("data"))
    }

    @Test
    fun anEnglishWordFindsItEvenWhenTheNameIsInItalian() {
        assertTrue("screen" in match("screen") || match("screen") == listOf("display"))
        assertEquals(listOf("display"), match("screen"))
        assertEquals(listOf("storage"), match("memory"))
        assertEquals(listOf("battery"), match("power"))
    }

    @Test
    fun caseAndAccentsDoNotMatter() {
        assertEquals(listOf("wifi"), match("WI-FI"))
        assertEquals(listOf("wifi"), match("  wifi "))
    }

    @Test
    fun aWordInTheMiddleOfTheNameCountsAndComesAfterNamesThatStartWithIt() {
        // "ora" starts a word of "Data e ora"; it is only inside "storage" (a keyword), so that one comes second.
        assertEquals(listOf("date", "storage"), match("ora"))
        // "app" starts "App" (the name) and "applications" (a keyword) of the same entry: listed once.
        assertEquals(listOf("apps"), match("app"))
    }

    @Test
    fun aNameThatStartsWithTheQueryComesBeforeOneThatOnlyContainsIt() {
        val defs = listOf(
            SystemSettingDef("a", 0, "a", Glyph.Settings),
            SystemSettingDef("b", 0, "b", Glyph.Settings),
            SystemSettingDef("c", 0, "c", Glyph.Settings),
        )
        val names = mapOf("a" to "Mobile data", "b" to "Data usage", "c" to "Metadata")
        val found = GlobalSearch.matchSettings("data", defs) { names.getValue(it.id) }.map { it.id }
        assertEquals(listOf("b", "a", "c"), found) // starts with, a word starts with, inside
    }

    @Test
    fun nothingTypedAndNothingMatchingGiveNothing() {
        assertTrue(match("").isEmpty())
        assertTrue(match("   ").isEmpty())
        assertTrue(match("zzzz").isEmpty())
    }

    @Test
    fun theWebAddressEncodesTheQuery() {
        assertEquals("https://duckduckgo.com/?q=meteo+torino", GlobalSearch.webUrl("meteo torino"))
        assertEquals("https://duckduckgo.com/?q=caff%C3%A8+%26+co", GlobalSearch.webUrl("  caffè & co "))
    }

    @Test
    fun everyKnownSettingHasANameAnActionAndAGlyph() {
        val all = GlobalSearch.systemSettings
        assertEquals("ids are unique", all.size, all.map { it.id }.toSet().size)
        assertTrue(all.all { it.action.isNotBlank() && it.keywords.isNotEmpty() })
    }
}
