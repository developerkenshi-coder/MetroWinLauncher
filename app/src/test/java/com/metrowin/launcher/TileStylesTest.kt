package com.metrowin.launcher

import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.TilePeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileStylesTest {
    @Test
    fun theStyleButtonGoesStandardIconicThenBack() {
        assertEquals(TileStyle.Iconic, TileStyle.Standard.next(cycleAvailable = false))
        assertEquals(TileStyle.Standard, TileStyle.Iconic.next(cycleAvailable = false))
    }

    @Test
    fun cycleIsOnlyOfferedToTilesWithPhotos() {
        assertEquals(TileStyle.Cycle, TileStyle.Iconic.next(cycleAvailable = true))
        assertEquals(TileStyle.Standard, TileStyle.Cycle.next(cycleAvailable = true))
        // A tile that was cycle and lost its photos still gets back to standard.
        assertEquals(TileStyle.Standard, TileStyle.Cycle.next(cycleAvailable = false))
    }

    private fun entry(key: String, pkg: String, title: String, text: String, time: Long) =
        NotificationEntry(key, pkg, title, text, postTime = time, contentIntent = null, autoCancel = true)

    @Test
    fun eachAppShowsItsNewestNotification() {
        // The hub keeps the newest first.
        val entries = listOf(
            entry("1", "chat", "Giulia", "Ci vediamo alle otto?", 300),
            entry("2", "mail", "Backup", "Finished", 250),
            entry("3", "chat", "Marco", "Ok", 100),
        )
        val peeks = NotificationHub.latestByPackage(entries)
        assertEquals(TilePeek("Giulia", "Ci vediamo alle otto?"), peeks["chat"])
        assertEquals(TilePeek("Backup", "Finished"), peeks["mail"])
        assertEquals(2, peeks.size)
    }

    @Test
    fun noNotificationsMeansNoPeeks() {
        assertTrue(NotificationHub.latestByPackage(emptyList()).isEmpty())
    }
}
