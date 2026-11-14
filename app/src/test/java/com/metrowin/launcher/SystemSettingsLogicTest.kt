package com.metrowin.launcher

import com.metrowin.launcher.systemsettings.SettingsLogic
import com.metrowin.launcher.systemsettings.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSettingsLogicTest {
    @Test
    fun signalBarsFollowWifiManager() {
        assertEquals(4, SettingsLogic.signalBars(-40))
        assertEquals(4, SettingsLogic.signalBars(-55))
        assertEquals(2, SettingsLogic.signalBars(-75))
        assertEquals(0, SettingsLogic.signalBars(-100))
        assertEquals(0, SettingsLogic.signalBars(-120))
    }

    @Test
    fun onlyASecuredNetworkNeedsEightCharacters() {
        assertTrue(SettingsLogic.passwordOk(false, ""))
        assertFalse(SettingsLogic.passwordOk(true, "1234567"))
        assertTrue(SettingsLogic.passwordOk(true, "12345678"))
    }

    @Test
    fun connectedNetworkIsReadFromStatus() {
        assertEquals("Casa 5G", SettingsLogic.connectedSsid("Wifi is enabled\nWifi is connected to \"Casa 5G\"\nWifiInfo: ..."))
        assertNull(SettingsLogic.connectedSsid("Wifi is enabled\nWifi is disconnected"))
    }

    @Test
    fun zonesShowCityAndOffset() {
        assertEquals("Buenos Aires", SettingsLogic.city("America/Argentina/Buenos_Aires"))
        val at = 1_700_000_000_000L
        assertEquals("Rome (UTC+1)", SettingsLogic.zoneLabel("Europe/Rome", at))
        assertEquals("Kolkata (UTC+5:30)", SettingsLogic.zoneLabel("Asia/Kolkata", at))
        assertEquals("New York (UTC-5)", SettingsLogic.zoneLabel("America/New_York", at))
    }

    @Test
    fun zoneSearchFindsByCityAndNeedsTwoLetters() {
        val ids = listOf("Europe/Rome", "Europe/Paris", "America/Argentina/Buenos_Aires", "UTC")
        assertEquals(listOf("America/Argentina/Buenos_Aires"), SettingsLogic.zoneMatches(ids, "buenos"))
        assertEquals(listOf("Europe/Paris", "Europe/Rome"), SettingsLogic.zoneMatches(ids, "europe"))
        assertTrue(SettingsLogic.zoneMatches(ids, "r").isEmpty())
    }

    @Test
    fun roamingKeyIsPerSubscriptionOnlyWhenTheMakerKeepsIt() {
        assertEquals("data_roaming3", SettingsLogic.roamingKey(3, true))
        assertEquals("data_roaming", SettingsLogic.roamingKey(3, false))
        assertEquals("data_roaming", SettingsLogic.roamingKey(-1, true))
    }

    @Test
    fun logNeverHoldsAPassword() {
        val line = SettingsLogic.describe(arrayOf("cmd", "wifi", "connect-network", "Casa", "wpa2", "segretissima", "-h"))
        assertEquals("cmd wifi connect-network Casa", line)
        assertEquals("svc wifi enable", SettingsLogic.describe(arrayOf("svc", "wifi", "enable")))
    }

    @Test
    fun logKeepsTheLastFifty() {
        val lines = (1..60).fold(emptyList<String>()) { acc, i -> SettingsLogic.appendHistory(acc, "c$i") }
        assertEquals(50, lines.size)
        assertEquals("c60", lines.first())
    }

    @Test
    fun pagesFollowTheOptions() {
        assertEquals(listOf(Page.Sounds, Page.Info), SettingsLogic.pages(reduced = true, more = true))
        assertEquals(5, SettingsLogic.pages(reduced = false, more = false).size)
        assertTrue(Page.System in SettingsLogic.pages(reduced = false, more = true))
    }
}
