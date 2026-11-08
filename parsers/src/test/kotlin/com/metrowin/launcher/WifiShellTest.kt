package com.metrowin.launcher

import com.metrowin.launcher.data.WifiShell
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiShellTest {
    private val scan = """
            BSSID              Frequency      RSSI           Age(sec)     SSID                                 Flags
          02:15:b2:00:00:01       2437        -50            129.739    AndroidWifi                       [ESS]
          aa:bb:cc:dd:ee:01       5180        -70              2.000    Home Net                          [WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]
          aa:bb:cc:dd:ee:02       2412        -55              2.000    Home Net                          [WPA2-PSK-CCMP][ESS]
          aa:bb:cc:dd:ee:03       2412        -60              2.000                                       [ESS]
    """.trimIndent()

    @Test
    fun scanKeepsTheStrongestPerNameAndDropsHiddenOnes() {
        val nets = WifiShell.scan(scan)
        assertEquals(listOf("AndroidWifi", "Home Net"), nets.map { it.ssid })
        assertEquals(-50, nets[0].dbm)
        assertFalse(nets[0].secured)
        // The 2.4 GHz one of Home Net is the stronger.
        assertEquals(-55, nets[1].dbm)
        assertTrue(nets[1].secured)
    }

    @Test
    fun savedNetworksComeOnceEach() {
        val text = "Network Id      SSID                         Security type\n0            AndroidWifi                      open\n0            AndroidWifi                      owe^\n3     Home Net     wpa2"
        assertEquals(listOf(0 to "AndroidWifi", 3 to "Home Net"), WifiShell.saved(text))
    }

    @Test
    fun connectGivesTheRightCommand() {
        val open = WifiShell.Net("Cafe", -50, 2412, secured = false, sae = false)
        assertArrayEquals(arrayOf("cmd", "wifi", "connect-network", "Cafe", "open"), WifiShell.connect(open, ""))
        val sae = WifiShell.Net("Safe", -50, 2412, secured = true, sae = true)
        assertArrayEquals(arrayOf("cmd", "wifi", "connect-network", "Safe", "wpa3", "pw12345678"), WifiShell.connect(sae, "pw12345678"))
    }
}
