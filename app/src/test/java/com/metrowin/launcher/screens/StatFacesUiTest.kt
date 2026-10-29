package com.metrowin.launcher.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.AppsStats
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.DataUsage
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.MemoryStats
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.data.TileExtras
import com.metrowin.launcher.data.TopApp
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The faces of the Apps, Battery, Data usage and Memory tiles, at the sizes that show different things. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class StatFacesUiTest : ScreenshotBase() {
    private fun item(id: String, data: TileData) = AppItem(id, id, Launch.Internal("none"), Glyph.Star, pinOnly = true, data = data)

    private fun showFace(item: AppItem, extras: TileExtras, size: TileSize) = show {
        Box(Modifier.size(300.dp, 300.dp).background(DefaultAccent)) {
            TileFace(item, size, 300.dp, 300.dp, LiveTileData(extras = extras), seed = 0, notifications = 0)
        }
    }

    @Test
    fun theAppsTileSaysHowManyAndWideAddsTheTopFive() {
        val top = (1..5).map { TopApp(AppItem("app:$it", "App $it", Launch.Action("x"), Glyph.Star), it * 3_600_000L) }
        val extras = TileExtras(apps = AppsStats(installed = 123, top = top), usageAllowed = true)
        showFace(item("a", TileData.AppsRecap), extras, TileSize.Medium)
        advance(300)
        compose.onNodeWithTag("apps:count").assertExists()
        compose.onAllNodesWithTag("apps:top:app:1").assertCountEquals(0) // medium shows the number: the top is on its other face
    }

    @Test
    fun wideShowsTheTopAndAHintWithoutTheUsageAccess() {
        val top = (1..5).map { TopApp(AppItem("app:$it", "App $it", Launch.Action("x"), Glyph.Star), it * 3_600_000L) }
        showFace(item("a", TileData.AppsRecap), TileExtras(apps = AppsStats(123, top), usageAllowed = true), TileSize.Wide)
        advance(300)
        (1..5).forEach { compose.onNodeWithTag("apps:top:app:$it").assertExists() }
    }

    @Test
    fun withoutTheUsageAccessWideSaysHowToGetTheTop() {
        showFace(item("a", TileData.AppsRecap), TileExtras(apps = AppsStats(123, emptyList()), usageAllowed = false), TileSize.Wide)
        advance(300)
        compose.onNodeWithTag("apps:count").assertExists()
        compose.onNodeWithTag("apps:hint").assertExists()
    }

    @Test
    fun theBatteryTileHasTheGraphAndWideAlsoTheEstimate() {
        val extras = TileExtras(battery = BatteryInfo(60, BatteryState.Discharging, minutesLeft = 420))
        showFace(item("b", TileData.BatteryGraph), extras, TileSize.Medium)
        advance(300)
        compose.onNodeWithTag("batterygraph:percent").assertExists()
        compose.onNodeWithTag("batterygraph:graph").assertExists()
        compose.onAllNodesWithTag("batterygraph:estimate").assertCountEquals(0)
    }

    @Test
    fun wideBatteryShowsTheEstimatedDuration() {
        val extras = TileExtras(battery = BatteryInfo(60, BatteryState.Discharging, minutesLeft = 420))
        showFace(item("b", TileData.BatteryGraph), extras, TileSize.Wide)
        advance(300)
        capture("battery_graph_wide")
        compose.onNodeWithTag("batterygraph:estimate").assertExists()
        compose.onNodeWithTag("batterygraph:graph").assertExists()
    }

    @Test
    fun theDataTileShowsTodayAndWideAddsTheMonthlyAverage() {
        val usage = DataUsage(wifiToday = 2_000_000_000L, mobileToday = 300_000_000L, wifiMonthly = 40_000_000_000L, mobileMonthly = 5_000_000_000L)
        showFace(item("d", TileData.DataUse), TileExtras(dataUsage = usage), TileSize.Wide)
        advance(300)
        capture("data_use_wide")
        compose.onNodeWithTag("datause:wifi").assertExists()
        compose.onNodeWithTag("datause:mobile").assertExists()
    }

    @Test
    fun theDataTileAsksForTheAccessUntilItIsGiven() {
        showFace(item("d", TileData.DataUse), TileExtras(dataUsage = null, usageAllowed = false), TileSize.Medium)
        advance(300)
        compose.onAllNodesWithTag("datause:wifi").assertCountEquals(0)
        compose.onNodeWithTag("datause:hint").assertExists()
    }

    @Test
    fun theMemoryTileShowsUsedAndFreeAndWideTheParts() {
        val memory = MemoryStats(StorageInfo(freeBytes = 40_000_000_000L, totalBytes = 128_000_000_000L), 30_000_000_000L, 20_000_000_000L, 10_000_000_000L, 2_000_000_000L)
        showFace(item("m", TileData.MemoryUse), TileExtras(memory = memory), TileSize.Medium)
        advance(300)
        compose.onNodeWithTag("memoryuse:used").assertExists()
        compose.onNodeWithTag("memoryuse:free").assertExists()
        compose.onAllNodesWithTag("memoryuse:bar").assertCountEquals(0)
    }

    @Test
    fun wideMemoryListsWhatTakesTheSpace() {
        val memory = MemoryStats(StorageInfo(freeBytes = 40_000_000_000L, totalBytes = 128_000_000_000L), 30_000_000_000L, 20_000_000_000L, null, 2_000_000_000L)
        showFace(item("m", TileData.MemoryUse), TileExtras(memory = memory), TileSize.Large)
        advance(300)
        capture("memory_large")
        compose.onNodeWithTag("memoryuse:bar").assertExists()
        // The videos the phone would not say are not in the list; the other four are.
        compose.onAllNodesWithTag("memoryuse:part:${com.metrowin.launcher.R.string.tile_mem_videos}").assertCountEquals(0)
        compose.onNodeWithTag("memoryuse:part:${com.metrowin.launcher.R.string.tile_mem_other}").assertExists()
    }
}
