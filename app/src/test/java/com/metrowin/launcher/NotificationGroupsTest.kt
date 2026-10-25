package com.metrowin.launcher

import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationGroups
import com.metrowin.launcher.notifications.NotificationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGroupsTest {
    private fun entry(key: String, pkg: String, time: Long) =
        NotificationEntry(key, pkg, "title $key", "text $key", postTime = time, contentIntent = null, autoCancel = true)

    // Newest first, as the service delivers them.
    private val list = listOf(
        entry("a3", "app.a", 9), entry("b1", "app.b", 8), entry("a2", "app.a", 7), entry("c1", "app.c", 6), entry("a1", "app.a", 5),
    )

    @Test
    fun withoutGroupingEveryNotificationIsItsOwnLine() {
        val items = NotificationGroups.build(list, grouped = false)
        assertEquals(list.map { it.key }, items.map { it.key })
        assertTrue(items.all { it is NotificationItem.Single })
    }

    @Test
    fun anAppWithSeveralNotificationsBecomesOneLineAtItsNewestOne() {
        val items = NotificationGroups.build(list, grouped = true)
        assertEquals(listOf("group:app.a", "b1", "c1"), items.map { it.key })
        val group = items[0] as NotificationItem.Group
        assertEquals(listOf("a3", "a2", "a1"), group.entries.map { it.key })
        assertEquals("a3", group.latest.key)
    }

    @Test
    fun anAppWithOneNotificationStaysSingle() {
        val items = NotificationGroups.build(listOf(entry("x", "app.x", 1)), grouped = true)
        assertTrue(items.single() is NotificationItem.Single)
    }

    @Test
    fun theOrderOfTheGroupsFollowsTheirNewest() {
        val items = NotificationGroups.build(
            listOf(entry("b2", "app.b", 9), entry("a2", "app.a", 8), entry("b1", "app.b", 7), entry("a1", "app.a", 6)),
            grouped = true,
        )
        assertEquals(listOf("group:app.b", "group:app.a"), items.map { it.key })
    }

    @Test
    fun noNotificationsNoLines() {
        assertTrue(NotificationGroups.build(emptyList(), grouped = true).isEmpty())
    }

    @Test
    fun everyNotificationIsInExactlyOnePlace() {
        val items = NotificationGroups.build(list, grouped = true)
        val keys = items.flatMap { if (it is NotificationItem.Group) it.entries.map { e -> e.key } else listOf(it.key) }
        assertEquals(list.map { it.key }.sorted(), keys.sorted())
    }
}
