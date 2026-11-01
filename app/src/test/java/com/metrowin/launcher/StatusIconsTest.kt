package com.metrowin.launcher

import com.metrowin.launcher.data.StatusIcons
import com.metrowin.launcher.data.StatusItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusIconsTest {
    @Test fun theKindOfNetworkIsNamedAsAndroidNamesIt() {
        assertEquals("5G", StatusIcons.networkLabel(20))
        assertEquals("4G", StatusIcons.networkLabel(13))
        assertEquals("H+", StatusIcons.networkLabel(15))
        assertEquals("H", StatusIcons.networkLabel(10))
        assertEquals("3G", StatusIcons.networkLabel(3))
        assertEquals("E", StatusIcons.networkLabel(2))
        assertEquals("G", StatusIcons.networkLabel(1))
        assertNull("unknown says nothing", StatusIcons.networkLabel(0))
    }

    @Test fun fiveGOnTopOfFourGIsFiveG() {
        // The phone says LTE as the network and NR (non-standalone) as the display override: Android shows 5G.
        assertEquals("5G", StatusIcons.networkLabel(13, override = 3))
        assertEquals("5G", StatusIcons.networkLabel(13, override = 4))
        assertEquals("4G+", StatusIcons.networkLabel(13, override = 1))
    }

    @Test fun whatIsTurnedOffIsKeptAndPutBack() {
        var hidden = ""
        hidden = StatusItem.withShown(hidden, StatusItem.Wifi, shown = false)
        hidden = StatusItem.withShown(hidden, StatusItem.Network, shown = false)
        assertEquals(setOf("wifi", "network"), StatusItem.hiddenIds(hidden))
        hidden = StatusItem.withShown(hidden, StatusItem.Wifi, shown = true)
        assertEquals(setOf("network"), StatusItem.hiddenIds(hidden))
        assertEquals("nothing hidden shows everything", emptySet<String>(), StatusItem.hiddenIds(""))
    }

    @Test fun everyItemHasAUniqueId() {
        assertEquals(StatusItem.entries.size, StatusItem.entries.map { it.id }.toSet().size)
    }
}
