package com.metrowin.launcher

import com.metrowin.launcher.data.BluetoothPeer
import com.metrowin.launcher.data.ConnectionPanel
import com.metrowin.launcher.data.Connections
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.WifiNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionsTest {
    @Test
    fun theSignalGoesFromNoBarsToFour() {
        assertEquals(0, Connections.bars(-100))
        assertEquals(0, Connections.bars(-120))
        assertEquals(4, Connections.bars(-55))
        assertEquals(4, Connections.bars(-30))
        assertEquals(2, Connections.bars(-70))
    }

    @Test
    fun aNetworkThatAsksForAKeyIsSecured() {
        assertTrue(Connections.isSecured("[WPA2-PSK-CCMP][ESS]"))
        assertTrue(Connections.isSecured("[RSN-SAE-CCMP][ESS]"))
        assertTrue(Connections.isSecured("[WEP][ESS]"))
        assertFalse(Connections.isSecured("[ESS]"))
        assertFalse(Connections.isSecured(""))
    }

    @Test
    fun theNameOfTheConnectedNetworkLosesItsQuotesAndHiddenOnesAreNull() {
        assertEquals("Casa 5G", Connections.cleanSsid("\"Casa 5G\""))
        assertEquals("Casa", Connections.cleanSsid(" Casa "))
        assertNull(Connections.cleanSsid("<unknown ssid>"))
        assertNull(Connections.cleanSsid("\"\""))
        assertNull(Connections.cleanSsid(null))
    }

    @Test
    fun theNetworksAreOneLinePerNameWithTheConnectedOneFirstThenTheStrongest() {
        val found = listOf(
            WifiNetwork("Bar", 1, secured = true),
            WifiNetwork("Casa", 2, secured = true),
            WifiNetwork("Casa", 4, secured = true),
            WifiNetwork("Ufficio", 3, secured = false),
            WifiNetwork("", 4, secured = false),
        )
        val list = Connections.networks(found, connected = "Bar")
        assertEquals(listOf("Bar", "Casa", "Ufficio"), list.map { it.name })
        assertTrue(list[0].connected)
        assertEquals(4, list.first { it.name == "Casa" }.bars)
        assertEquals(1, list.count { it.connected })
    }

    @Test
    fun theConnectedNetworkIsListedEvenWhenTheScanMissedIt() {
        val list = Connections.networks(listOf(WifiNetwork("Altra", 3, secured = true)), connected = "Casa", connectedBars = 2)
        assertEquals(listOf("Casa", "Altra"), list.map { it.name })
        assertEquals(2, list[0].bars)
        assertTrue(list[0].connected)
    }

    @Test
    fun theDevicesAreTheConnectedOnesFirstThenTheOtherPairedOnes() {
        val paired = listOf(
            BluetoothPeer("Zaino", "00:03", connected = false),
            BluetoothPeer("Auto", "00:01", connected = false),
            BluetoothPeer("Cuffie", "00:02", connected = false),
        )
        val connected = listOf(BluetoothPeer("Cuffie", "00:02", connected = true), BluetoothPeer("Orologio", "00:09", connected = true))
        val list = Connections.devices(paired, connected)
        assertEquals(listOf("Cuffie", "Orologio", "Auto", "Zaino"), list.map { it.name })
        assertEquals(listOf(true, true, false, false), list.map { it.connected })
    }

    @Test
    fun theBluetoothButtonNamesTheDeviceAndCountsTheOthers() {
        assertNull(Connections.bluetoothCaption(emptyList()))
        assertEquals("Cuffie", Connections.bluetoothCaption(listOf("Cuffie")))
        assertEquals("Cuffie +2", Connections.bluetoothCaption(listOf("Cuffie", "Auto", "Orologio")))
        assertEquals("Auto", Connections.bluetoothCaption(listOf("", "Auto")))
    }

    @Test
    fun onlyTheWifiAndBluetoothButtonsHaveACaption() {
        val panel = ConnectionPanel(wifiName = "Casa", devices = listOf(BluetoothPeer("Cuffie", "1", true), BluetoothPeer("Auto", "2", false)))
        assertEquals("Casa", panel.captionFor(QuickAction.Wifi))
        assertEquals("Cuffie", panel.captionFor(QuickAction.Bluetooth))
        assertNull(panel.captionFor(QuickAction.Flashlight))
        assertNull(ConnectionPanel().captionFor(QuickAction.Wifi))
    }
}
