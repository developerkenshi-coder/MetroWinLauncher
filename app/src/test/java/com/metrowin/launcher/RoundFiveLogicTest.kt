package com.metrowin.launcher

import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationHub
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The order of the buttons as a whole, and the notifications of an app that was opened. */
class RoundFiveLogicTest {
    private fun entry(pkg: String, id: Int) = NotificationEntry("$pkg|$id", pkg, "title $id", "text", id.toLong(), null, false)

    @Before
    fun on() {
        NotificationHub.clearOnOpen = true
        NotificationHub.update(emptyList())
    }

    @After
    fun off() {
        NotificationHub.clearOnOpen = false
        NotificationHub.update(emptyList())
    }

    @Test
    fun theOrderKeepsEveryButtonAndTheFirstFourAreTheOnesOnTop() {
        val order = QuickAction.order(listOf("vpn", "cast", "nonsense", "vpn"))
        assertEquals(QuickAction.entries.size, order.size)
        assertEquals(QuickAction.entries.toSet(), order.toSet())
        assertEquals(listOf(QuickAction.Vpn, QuickAction.Cast), order.take(2))
        assertEquals(order.take(QuickAction.SLOTS), QuickAction.slots(listOf("vpn", "cast", "nonsense", "vpn")))
        // An order that was kept is read back as it was.
        val moved = QuickAction.move(order, order.lastIndex, 0)
        assertEquals(moved, QuickAction.order(moved.map { it.id }))
    }

    @Test
    fun anAppThatIsOpenedTakesItsNotificationsAndTheOnesThatComeRightAfter() {
        NotificationHub.update(listOf(entry("chat", 1), entry("mail", 2)))
        NotificationHub.appOpened("chat")
        assertEquals(listOf("mail|2"), NotificationHub.entries.value.map { it.key })
        // One that reaches the list a moment later is for what the user is looking at.
        NotificationHub.update(listOf(entry("mail", 2), entry("chat", 3)))
        assertEquals(listOf("mail|2"), NotificationHub.entries.value.map { it.key })
        assertTrue(NotificationHub.entries.value.none { it.packageName == "chat" })
    }

    @Test
    fun nothingIsTakenWhenTheSettingIsOff() {
        NotificationHub.clearOnOpen = false
        NotificationHub.update(listOf(entry("chat", 1)))
        NotificationHub.appOpened("chat")
        assertEquals(1, NotificationHub.entries.value.size)
    }
}
