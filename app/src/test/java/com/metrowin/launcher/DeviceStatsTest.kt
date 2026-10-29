package com.metrowin.launcher

import com.metrowin.launcher.data.AddMenuIndex
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.AppStats
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.DataStats
import com.metrowin.launcher.data.MemoryStats
import com.metrowin.launcher.data.ShortcutCodec
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.ui.start.batteryEnd
import com.metrowin.launcher.ui.start.batteryMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The numbers behind the Apps, Battery, Data usage and Memory tiles, and how the four tiles are made and saved. */
class DeviceStatsTest {
    private fun app(pkg: String, pinOnly: Boolean = false) = AppItem(pkg, pkg, Launch.Action("x"), packageName = pkg, pinOnly = pinOnly)

    @Test
    fun theTopIsTheFiveAppsUsedLongestAndLeavesOutTheUnusedAndTheUnknown() {
        val items = listOf("a", "b", "c", "d", "e", "f", "g").map(::app) + app("tile", pinOnly = true)
        val usage = mapOf("a" to 10L, "b" to 70L, "c" to 0L, "d" to 50L, "e" to 40L, "f" to 30L, "g" to 20L, "gone" to 999L, "tile" to 500L)
        assertEquals(listOf("b", "d", "e", "f", "g"), AppStats.top(usage, items).map { it.item.id })
        assertEquals(emptyList<String>(), AppStats.top(emptyMap(), items).map { it.item.id })
    }

    @Test
    fun theMonthlyAverageIsOfTheFullMonthsThatHaveDataElseOfTheCurrentOne() {
        assertEquals(200L, DataStats.monthlyAverage(listOf(100L, 0L, 300L), current = 5L))
        assertEquals(5L, DataStats.monthlyAverage(listOf(0L, 0L), current = 5L))
        assertEquals(5L, DataStats.monthlyAverage(emptyList(), current = 5L))
    }

    @Test
    fun whatIsUsedAndNoneOfTheNamedPartsIsTheOtherFiles() {
        val memory = MemoryStats(StorageInfo(freeBytes = 40, totalBytes = 100), apps = 20, photos = 10, videos = null, music = 5)
        assertEquals(60L, memory.used)
        assertEquals(25L, memory.other)
        // Parts the phone would not say are not taken off: they stay in the rest.
        assertEquals(60L, MemoryStats(StorageInfo(40, 100), null, null, null, null).other)
        // A sum that is more than is used (media counted twice) never goes below zero.
        assertEquals(0L, MemoryStats(StorageInfo(90, 100), 20, 20, null, null).other)
    }

    @Test
    fun theBatteryGraphRunsDownWhileDischargingAndUpWhileChargingOrFull() {
        val discharging = BatteryInfo(40, BatteryState.Discharging, minutesToFull = null, minutesLeft = 300)
        val charging = BatteryInfo(40, BatteryState.Charging, minutesToFull = 45, minutesLeft = null)
        assertEquals(0, batteryEnd(discharging.state))
        assertEquals(100, batteryEnd(charging.state))
        assertEquals(300, batteryMinutes(discharging))
        assertEquals(45, batteryMinutes(charging))
        assertNull("no estimate, no minutes", batteryMinutes(BatteryInfo(40, BatteryState.Discharging)))
    }

    @Test
    fun theFourTilesAreSavedAndOpenWhatTheyShould() {
        val all = listOf(
            ShortcutDef("a", ShortcutKind.AppsRecap, "Apps"), ShortcutDef("b", ShortcutKind.BatteryGraph, "Battery"),
            ShortcutDef("d", ShortcutKind.DataUse, "Data"), ShortcutDef("m", ShortcutKind.MemoryUse, "Memory"),
        )
        assertEquals(all, ShortcutCodec.decode(ShortcutCodec.encode(all)))
        val items = all.map { it.toItem(null) }
        assertEquals(listOf(TileData.AppsRecap, TileData.BatteryGraph, TileData.DataUse, TileData.MemoryUse), items.map { it.data })
        // Three of them read what the usage access gives, and a touch asks for it; the battery one opens the battery screen of the phone.
        assertEquals(Launch.Internal(AppRepository.ROUTE_USAGE), items[0].launch)
        assertEquals(Launch.Action(ShortcutDef.BATTERY_ACTION), items[1].launch)
        assertEquals(Launch.Internal(AppRepository.ROUTE_USAGE), items[2].launch)
        assertEquals(Launch.Internal(AppRepository.ROUTE_USAGE), items[3].launch)
    }

    @Test
    fun theAddMenuKnowsTheFourTiles() {
        listOf("appsrecap", "batterygraph", "datause", "memoryuse").forEach { AddMenuIndex.kind(it) }
        assertEquals(AddMenuIndex.kinds.size, AddMenuIndex.kinds.map { it.id }.toSet().size)
    }
}
