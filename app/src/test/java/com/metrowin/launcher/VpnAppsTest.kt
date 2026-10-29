package com.metrowin.launcher

import com.metrowin.launcher.data.Connections
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.VpnApp
import com.metrowin.launcher.data.VpnApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnAppsTest {
    private val wireguard = VpnApp("com.wireguard.android", "WireGuard")
    private val nord = VpnApp("com.nordvpn.android", "NordVPN")
    private val mine = VpnApp("org.example.zzz", "Aaa")

    @Test
    fun theOnlyVpnAppIsTakenWithoutAChoice() {
        assertEquals(wireguard, VpnApps.pick("", listOf(wireguard)))
    }

    @Test
    fun severalAppsAskForAChoiceUntilOneIsSaved() {
        assertNull(VpnApps.pick("", listOf(wireguard, nord)))
        assertEquals(nord, VpnApps.pick("com.nordvpn.android", listOf(wireguard, nord)))
    }

    @Test
    fun aSavedAppThatWasUninstalledFallsBackToTheOnlyOneOrAsksAgain() {
        assertEquals(wireguard, VpnApps.pick("gone", listOf(wireguard)))
        assertNull(VpnApps.pick("gone", listOf(wireguard, nord)))
        assertNull(VpnApps.pick("gone", emptyList()))
    }

    @Test
    fun knownAppsComeFirstInTheOrderOfTheList() {
        assertEquals(listOf(wireguard, nord, mine), VpnApps.sorted(listOf(mine, nord, wireguard)))
    }

    @Test
    fun theServerIsTheTextOfTheNotificationOrItsTitleWhenThatIsNotTheAppName() {
        assertEquals("Connected to Milan", Connections.vpnServer("WireGuard", "Connected to Milan", "WireGuard"))
        assertEquals("Milan", Connections.vpnServer("Milan", "", "WireGuard"))
        assertNull(Connections.vpnServer("wireguard", " ", "WireGuard"))
    }

    @Test
    fun theChosenAppSurvivesSavingAndTheHotspotKeepsItsButton() {
        val saved = SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(vpnApp = "com.nordvpn.android")))
        assertEquals("com.nordvpn.android", saved?.vpnApp)
        assertEquals("hotspot", QuickAction.Hotspot.id)
    }
}
