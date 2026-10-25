package com.metrowin.launcher

import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationHub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionCenterLogicTest {
    private fun entry(key: String, pkg: String) =
        NotificationEntry(key, pkg, "title", "text", postTime = 0, contentIntent = null, autoCancel = true)

    @Test
    fun countsAreKeptPerApp() {
        val counts = NotificationHub.countsByPackage(listOf(entry("1", "a"), entry("2", "b"), entry("3", "a")))
        assertEquals(mapOf("a" to 2, "b" to 1), counts)
        assertEquals(emptyMap<String, Int>(), NotificationHub.countsByPackage(emptyList()))
    }

    @Test
    fun onlyUsefulNotificationsAreShown() {
        fun worth(ongoing: Boolean = false, summary: Boolean = false, ours: Boolean = false, title: String? = "Hi", text: String? = "x") =
            NotificationHub.isWorthShowing(ongoing, summary, ours, title, text)
        assertTrue(worth())
        assertTrue(worth(title = null, text = "only text"))
        assertFalse("music players and downloads are ongoing", worth(ongoing = true))
        assertFalse("a group summary would repeat its children", worth(summary = true))
        assertFalse("our own", worth(ours = true))
        assertFalse("nothing to read", worth(title = " ", text = null))
    }

    @Test
    fun slotsAreAlwaysFourDistinctButtons() {
        assertEquals(QuickAction.defaults, QuickAction.slots(emptyList()))
        assertEquals(QuickAction.SLOTS, QuickAction.slots(listOf("wifi", "wifi", "nonsense")).distinct().size)
        assertEquals(listOf(QuickAction.Vpn, QuickAction.Cast), QuickAction.slots(listOf("vpn", "cast")).take(2))
        assertEquals(QuickAction.SLOTS, QuickAction.slots(QuickAction.entries.map { it.id }).size)
    }

    @Test
    fun assigningAButtonSwapsItWithItsOldSlot() {
        val slots = listOf(QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.Airplane, QuickAction.Flashlight)
        // A button that is not shown yet replaces the one in the slot.
        assertEquals(
            listOf(QuickAction.Wifi, QuickAction.Vpn, QuickAction.Airplane, QuickAction.Flashlight),
            QuickAction.assign(slots, 1, QuickAction.Vpn),
        )
        // One that is shown already trades places, so nothing shows twice.
        assertEquals(
            listOf(QuickAction.Flashlight, QuickAction.Bluetooth, QuickAction.Airplane, QuickAction.Wifi),
            QuickAction.assign(slots, 0, QuickAction.Flashlight),
        )
    }

    @Test
    fun theLaunchersSettingsAreOneOfTheFourDefaultButtons() {
        assertTrue(QuickAction.LauncherSettings in QuickAction.defaults)
        assertEquals(QuickAction.defaults, QuickAction.slots(emptyList()))
        assertEquals(QuickAction.LauncherSettings, QuickAction.fromId("launcher"))
    }

    @Test
    fun theUntouchedOldDefaultMovesOnButAChoiceIsKept() {
        val old = listOf("wifi", "bluetooth", "airplane", "flashlight")
        assertEquals(QuickAction.defaults.map { it.id }, QuickAction.migrate(old))
        val chosen = listOf("vpn", "cast", "wifi", "camera")
        assertEquals(chosen, QuickAction.migrate(chosen))
        // The same four in another order were put there by the user.
        val reordered = listOf("flashlight", "airplane", "bluetooth", "wifi")
        assertEquals(reordered, QuickAction.migrate(reordered))
    }
}
