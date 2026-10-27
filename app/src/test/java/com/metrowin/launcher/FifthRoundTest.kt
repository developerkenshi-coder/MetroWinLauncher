package com.metrowin.launcher

import com.metrowin.launcher.data.BackupCopies
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.Layouts
import com.metrowin.launcher.data.PhoneInfo
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.SavedLayout
import com.metrowin.launcher.data.SearchOperators
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.TileCodec
import com.metrowin.launcher.data.TileGrid
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.notifications.Mute
import com.metrowin.launcher.notifications.NotificationControl
import com.metrowin.launcher.notifications.NotificationEntry
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FifthRoundTest {
    // --- the Large size and the groups on the grid

    @Test
    fun aLargeTileTakesFourColumnsAndFourRows() {
        val layout = TileGrid.place(listOf(TileSize.Large, TileSize.Medium), columns = 4)
        assertEquals(0, layout.placements[0].col)
        assertEquals(4, layout.placements[0].cols)
        assertEquals(4, layout.placements[0].rows)
        // What follows starts under it.
        assertEquals(4, layout.placements[1].row)
        assertEquals(6, layout.rows)
    }

    @Test
    fun theResizeArrowSkipsLargeWhenItIsOff() {
        assertEquals(TileSize.Large, TileSize.Wide.next(allowLarge = true))
        assertEquals(TileSize.Small, TileSize.Wide.next(allowLarge = false))
        assertEquals(TileSize.Small, TileSize.Large.next())
        assertEquals(listOf(TileSize.Medium), StartEntries.resize(listOf(Tile("a", TileSize.Small)), "a").map { it.size })
        assertEquals(TileSize.Small, StartEntries.resize(listOf(Tile("a", TileSize.Wide)), "a", allowLarge = false).single().size)
    }

    @Test
    fun aGroupStartsOnARowOfItsOwn() {
        val sizes = listOf(TileSize.Small, TileSize.Medium)
        // The medium tile does not fit beside the small one in its block, so it goes to the next block, on the same row.
        val plain = TileGrid.place(sizes, columns = 4)
        assertEquals(0, plain.placements[1].row)
        // Starting a group, it goes under everything that came before.
        val grouped = TileGrid.place(sizes, columns = 4, breaks = listOf(false, true))
        assertEquals(2, grouped.placements[1].row)
        assertEquals(0, grouped.placements[1].col)
    }

    @Test
    fun theTitleOfAGroupIsKeptThroughTheOperations() {
        val entries = listOf<com.metrowin.launcher.model.StartEntry>(Tile("a", TileSize.Medium), Tile("b", TileSize.Medium))
        val titled = StartEntries.setGroup(entries, "a", "  Work  ")
        assertEquals("Work", titled.first { it.id == "a" }.group)
        assertEquals("", StartEntries.setGroup(titled, "a", "   ").first { it.id == "a" }.group)
        assertEquals(StartEntries.MAX_GROUP, StartEntries.setGroup(entries, "a", "x".repeat(100)).first().group.length)
        // A folder made on the tile keeps its title, and a folder undone gives it to the first tile.
        val merged = StartEntries.merge(titled, "b", "a") { "f" }
        assertEquals("Work", (merged.single() as Folder).group)
        assertEquals("Work", StartEntries.release(merged, "f").first().group)
        // And the codec writes it down.
        assertEquals(titled, TileCodec.decode(TileCodec.encode(titled)))
    }

    @Test
    fun theCollageStyleComesAfterTheCycle() {
        assertEquals(TileStyle.Collection, TileStyle.Cycle.next(cycleAvailable = true, collectionAvailable = true))
        assertEquals(TileStyle.Standard, TileStyle.Cycle.next(cycleAvailable = true, collectionAvailable = false))
        assertEquals(TileStyle.Standard, TileStyle.Collection.next(cycleAvailable = true, collectionAvailable = true))
        assertEquals(TileStyle.Collection, TileStyle.fromCode("collection"))
    }

    // --- named layouts

    private fun layout(id: String, hour: Int) = SavedLayout(id, id, hour, "[]", "[]")

    @Test
    fun theScheduleAsksForTheLayoutWhoseHourCameLast() {
        val layouts = listOf(layout("work", 8), layout("home", 18), layout("free", -1))
        assertEquals("work", Layouts.due(layouts, 12)?.id)
        assertEquals("home", Layouts.due(layouts, 20)?.id)
        // Before the first hour of the day it is still the last one of yesterday.
        assertEquals("home", Layouts.due(layouts, 3)?.id)
        assertNull(Layouts.due(listOf(layout("a", -1)), 12))
        assertNull(Layouts.due(emptyList(), 12))
    }

    @Test
    fun theLayoutsGoThroughTheirTextAndBadOnesAreDropped() {
        val layouts = listOf(layout("a", 7), layout("b", -1))
        assertEquals(layouts, Layouts.decode(Layouts.encode(layouts)))
        assertTrue(Layouts.decode("garbage").isEmpty())
        assertTrue(Layouts.decode(null).isEmpty())
        // An hour that does not exist means no hour.
        assertEquals(-1, Layouts.decode("""[{"id":"x","name":"n","hour":99,"pages":"[]","shortcuts":"[]"}]""").single().hour)
        assertTrue(Layouts.decode(Layouts.encode(List(20) { layout("l$it", -1) })).size <= Layouts.MAX)
    }

    // --- the notifications

    private fun entry(pkg: String = "com.app", title: String = "Hello", text: String = "world", at: Long = 1_000L, urgent: Boolean = false) =
        NotificationEntry("$pkg|$title|$at", pkg, title, text, at, null, false, urgent = urgent)

    @Test
    fun aSilencedAppStaysOutOfSightUntilItsTimeIsUp() {
        val settings = LauncherSettings(mutedUntil = mapOf("com.app" to 5_000L))
        val entries = listOf(entry("com.app"), entry("com.other"))
        assertEquals(listOf("com.other"), NotificationControl.forActionCenter(entries, settings, now = 1_000L).map { it.packageName })
        assertEquals(2, NotificationControl.forActionCenter(entries, settings, now = 6_000L).size)
        assertFalse(NotificationControl.showsBanner("com.app", settings, now = 1_000L))
        assertTrue(NotificationControl.showsBanner("com.app", settings, now = 6_000L))
        assertEquals(1, NotificationControl.forLockScreen(entries, settings, now = 1_000L).size)
    }

    @Test
    fun theWordsOfTheFilterLeaveNotificationsOutWhateverTheCase() {
        val on = LauncherSettings(notifFilterOn = true, notifFilterWords = listOf("PROMO", "codice"))
        val entries = listOf(entry(title = "Big promo today"), entry(title = "Il tuo Codice è 1234", at = 2L), entry(title = "Anna", text = "ciao", at = 3L))
        assertEquals(listOf("Anna"), NotificationControl.forActionCenter(entries, on).map { it.title })
        // Off, or with no word, nothing is left out.
        assertEquals(3, NotificationControl.forActionCenter(entries, on.copy(notifFilterOn = false)).size)
        assertEquals(3, NotificationControl.forActionCenter(entries, on.copy(notifFilterWords = emptyList())).size)
        assertFalse(NotificationControl.filtered(entry(title = "plain"), on))
    }

    @Test
    fun messagesAndCallsComeFirstWhenAsked() {
        val entries = listOf(entry(title = "news", at = 30L), entry(title = "message", at = 10L, urgent = true), entry(title = "call", at = 20L, urgent = true))
        assertEquals(listOf("news", "message", "call").sorted(), NotificationControl.forActionCenter(entries, LauncherSettings()).map { it.title }.sorted())
        assertEquals(listOf("call", "message", "news"), NotificationControl.forActionCenter(entries, LauncherSettings(notifPriority = true)).map { it.title })
    }

    @Test
    fun theTimesOfASilenceAreRight() {
        val zone = ZoneId.of("Europe/Rome")
        val now = LocalDateTime.of(2026, 10, 26, 22, 0).atZone(zone).toInstant()
        assertEquals(now.toEpochMilli() + 3_600_000L, Mute.until(Mute.Option.Hour, now, zone))
        assertEquals(now.toEpochMilli() + 8 * 3_600_000L, Mute.until(Mute.Option.EightHours, now, zone))
        // The next 8:00, which is tomorrow at 22:00.
        assertEquals(LocalDateTime.of(2026, 10, 27, 8, 0).atZone(zone).toInstant().toEpochMilli(), Mute.until(Mute.Option.Morning, now, zone))
        assertEquals(Instant.parse("2026-10-27T07:00:00Z").toEpochMilli(), Mute.until(Mute.Option.Morning, now, zone))
    }

    @Test
    fun theTimedQuietModeIsOneOfTheSwitches() {
        assertTrue(QuickAction.QuietTimed in QuickAction.toggles)
        assertEquals("quiettimed", QuickAction.fromId("quiettimed")?.id)
        assertTrue(QuickAction.toggles.all { it in QuickAction.entries })
    }

    // --- the search and the phone

    @Test
    fun anOperatorNarrowsTheSearch() {
        assertEquals(SearchOperators.Parsed(SearchOperators.Scope.Apps, "maps"), SearchOperators.parse("app: maps"))
        assertEquals(SearchOperators.Scope.Contacts, SearchOperators.parse("contatto:anna").scope)
        assertEquals(SearchOperators.Scope.Files, SearchOperators.parse("F: tax").scope)
        assertEquals("tax", SearchOperators.parse("f:  tax").text)
        // Not an operator: left as it was.
        assertEquals(SearchOperators.Parsed(null, "12:30 meeting"), SearchOperators.parse("12:30 meeting"))
        assertEquals(SearchOperators.Parsed(null, "hello: world"), SearchOperators.parse("hello: world"))
        assertEquals(SearchOperators.Parsed(null, "plain"), SearchOperators.parse("plain"))
        val scoped = SearchOperators.parse("web: cats")
        assertTrue(scoped.allows(SearchOperators.Scope.Web))
        assertFalse(scoped.allows(SearchOperators.Scope.Apps))
        assertTrue(SearchOperators.parse("cats").allows(SearchOperators.Scope.Apps))
    }

    @Test
    fun thePhoneIsToldInPlainSizesAndTimes() {
        assertEquals("7.6 GB", PhoneInfo.size(7_600_000_000L))
        assertEquals("512 MB", PhoneInfo.size(512_000_000L))
        assertEquals("—", PhoneInfo.size(0L))
        assertEquals("7 min", PhoneInfo.uptime(7 * 60_000L))
        assertEquals("5 h 12 min", PhoneInfo.uptime((5 * 60 + 12) * 60_000L))
        assertEquals("3 d 4 h", PhoneInfo.uptime(((3 * 24 + 4) * 60) * 60_000L))
    }

    @Test
    fun theBatteryTellsHowLongTheChargeShouldLast() {
        val discharging = android.os.BatteryManager.BATTERY_STATUS_DISCHARGING
        assertEquals(185, BatteryInfo.from(60, 100, discharging, dischargeMillis = 185 * 60_000L).minutesLeft)
        assertNull(BatteryInfo.from(60, 100, discharging).minutesLeft)
        assertNull(BatteryInfo.from(60, 100, android.os.BatteryManager.BATTERY_STATUS_CHARGING, dischargeMillis = 60_000L).minutesLeft)
    }

    // --- the backups and the settings

    @Test
    fun theCopiesOfTheBackupAreNamedByTheMinute() {
        val at = LocalDateTime.of(2026, 10, 26, 9, 5)
        assertEquals("metrowin-layout-20261026-0905.json", BackupCopies.name(at))
        assertEquals(at, BackupCopies.parse(BackupCopies.name(at)))
        assertNull(BackupCopies.parse("metrowin-layout.json"))
        assertNull(BackupCopies.parse("metrowin-layout-oops.json"))
        val names = listOf("metrowin-layout-20261024-0800.json", "metrowin-layout.json", "metrowin-layout-20261026-0905.json", "metrowin-layout-20261025-1200.json", "x.txt")
        assertEquals(listOf("metrowin-layout-20261026-0905.json", "metrowin-layout-20261025-1200.json", "metrowin-layout-20261024-0800.json"), BackupCopies.newestFirst(names))
        assertEquals(listOf("metrowin-layout-20261024-0800.json"), BackupCopies.excess(names, 2))
        assertTrue(BackupCopies.excess(names, 5).isEmpty())
        assertEquals(2, BackupCopies.excess(names, 0).size)
    }

    @Test
    fun theNewSettingsGoThroughTheCodec() {
        val s = LauncherSettings(
            largeTiles = false, groupTitles = false, collectionTiles = false, layoutSchedule = true, calendarAgenda = false, batteryEstimate = false,
            tileMusicControls = false, pageBackgrounds = true, searchNotifications = false, muteFromPanel = false, notifFilterOn = true,
            notifFilterWords = listOf("promo", "codice"), notifPriority = true, dndMinutes = 120, animationSpeed = 150, tiltStrength = 50,
            vibrationStrength = 0, searchOperators = false, backupCopies = 5, showPhoneInfo = false,
            mutedUntil = mapOf("com.app" to 99L), activeLayout = "work",
        )
        val back = SettingsCodec.decode(SettingsCodec.encode(s))!!
        assertEquals(s, back)
        // A file meant for another phone does not carry who is silenced here, nor the layout the schedule put on.
        val portable = SettingsCodec.decode(SettingsCodec.encode(s, portable = true))!!
        assertTrue(portable.mutedUntil.isEmpty())
        assertEquals("", portable.activeLayout)
        assertTrue(portable.notifFilterOn)
        // Numbers out of range are brought in.
        val odd = SettingsCodec.decode("""{"animationSpeed":9000,"tiltStrength":-5,"dndMinutes":0,"backupCopies":99}""")!!
        assertEquals(200, odd.animationSpeed)
        assertEquals(0, odd.tiltStrength)
        assertEquals(5, odd.dndMinutes)
        assertEquals(10, odd.backupCopies)
    }
}
