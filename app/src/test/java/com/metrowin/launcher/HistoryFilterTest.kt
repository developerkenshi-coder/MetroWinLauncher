package com.metrowin.launcher

import com.metrowin.launcher.notifications.HistoryFilter
import com.metrowin.launcher.notifications.NotificationEntry
import org.junit.Test
import org.junit.Assert.assertEquals

class HistoryFilterTest {
    private fun e(key: String, pkg: String, title: String, text: String) = NotificationEntry(key, pkg, title, text, 1L, null, true)
    private val all = listOf(e("1", "chat", "Anna", "dinner at eight"), e("2", "mail", "Bank", "your statement"), e("3", "chat", "Luca", "statement of work"))
    private val label = { p: String -> if (p == "chat") "Messenger" else "Postbox" }

    @Test fun noFilterKeepsAll() = assertEquals(3, HistoryFilter.apply(all, "", null, label).size)
    @Test fun oneAppOnly() = assertEquals(listOf("1", "3"), HistoryFilter.apply(all, "", "chat", label).map { it.key })
    @Test fun everyWordMustMatch() = assertEquals(listOf("3"), HistoryFilter.apply(all, "statement luca", null, label).map { it.key })
    @Test fun appNameCounts() = assertEquals(listOf("2"), HistoryFilter.apply(all, "postbox", null, label).map { it.key })
    @Test fun queryAndAppTogether() = assertEquals(listOf("3"), HistoryFilter.apply(all, "statement", "chat", label).map { it.key })
}
