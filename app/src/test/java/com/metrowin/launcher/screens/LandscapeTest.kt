package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.OrientationMode
import com.metrowin.launcher.data.TileGrid
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.AppCell
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The launcher on a screen that is wider than tall: tiles keep their size and more of them fit across. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xxhdpi")
class LandscapeTest : ScreenshotBase() {
    private val landscape = DpSize(800.dp, 360.dp)
    private val items = ('A'..'H').map { AppItem("$it", "App $it", Launch.Action("x")) }

    private fun startScreen(columns: Int) = show(size = landscape) {
        StartScreen(
            cells = items.map { AppCell(Tile(it.id, TileSize.Medium), it) },
            live = LiveTileData(), badges = emptyMap(), columns = columns, background = null,
            enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(),
            actions = StartActions(
                onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
                onMove = { _, _ -> }, onMerge = { _, _ -> }, onBackgroundTap = {}, onNextPage = {},
            ),
        )
    }

    private val density get() = context.resources.displayMetrics.density

    private fun widthOfTile(key: String): Float = compose.onNodeWithTag("tile:$key").fetchSemanticsNode().size.width / density

    @Test
    fun tilesInLandscapeAreAboutTheSizeTheyHaveInPortrait() {
        startScreen(columns = TileGrid.columnsFor(800f, more = false))
        advance(2_500)
        capture("start_landscape")
        // A medium tile is two units and a gap wide: about 165 dp on a 360 dp phone.
        val medium = widthOfTile("A")
        assertTrue("medium tile is $medium dp wide", medium in 130f..175f)
    }

    @Test
    fun withTheFourPortraitColumnsTheTilesWouldBeHuge() {
        startScreen(columns = 4)
        advance(2_500)
        assertTrue("it is the reason columns come from the width", widthOfTile("A") > 300f)
    }

    @Test
    fun fiveMediumTilesShareTheFirstRowAndTheSixthStartsTheNext() {
        val columns = TileGrid.columnsFor(800f, more = false)
        val rows = TileGrid.place(List(8) { TileSize.Medium }, columns).placements.map { it.row }
        assertEquals(listOf(0, 0, 0, 0, 0, 2, 2, 2), rows)
        startScreen(columns)
        advance(2_500)
        val shot = capture("start_landscape_rows")
        fun accentAt(xDp: Float, yDp: Float) = isAccent(shot.getPixel((xDp * density).toInt(), (yDp * density).toInt()))
        // The unit is 72.2 dp, a medium tile 150.3 dp and its pitch 156.3 dp; the grid starts at (12, 16) dp.
        assertTrue("fifth tile of the first row", accentAt(12f + 4 * 156.3f + 10f, 16f + 10f))
        assertTrue("sixth tile, on the second row", accentAt(12f + 10f, 16f + 156.3f + 10f))
        assertTrue("the fifth tile ends before the edge of the screen", !accentAt(795f, 16f + 10f))
    }

    @Test
    fun theAppListFillsTheWideScreen() {
        show(size = landscape) {
            val model = remember { AppListModel(AppSections.group(AppSections.sort(items))) }
            AppListScreen(
                model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
                onLaunch = {}, onLongPress = { _, _, _ -> }, onHeaderClick = {},
                query = "", onQueryChange = {}, results = emptyList(), modifier = Modifier.fillMaxSize(),
            )
        }
        advance(1_500)
        capture("apps_landscape")
        compose.onNodeWithTag("app:A").assertExists()
    }

    // --- the settings page

    private var current = LauncherSettings()

    @Test
    fun theScreenPageChoosesTheRotationAndTheRefreshRate() {
        show(size = landscape) {
            var settings by remember { mutableStateOf(current) }
            val away = remember { Animatable(0f) }
            SettingsScreen(
                env = SettingsEnv(settings = settings, onChange = { change -> settings = change(settings); current = settings }),
                section = SettingsSection.Screen, onSectionChange = {}, sectionAway = away,
            )
        }
        advance(500)
        capture("settings_screen")
        compose.onNodeWithTag("screen:orientation:1").performClick()
        advance(100)
        assertEquals(OrientationMode.Auto, current.orientation)
        compose.onNodeWithTag("screen:orientation:2").performClick()
        advance(100)
        assertEquals(OrientationMode.Landscape, current.orientation)
        compose.onNodeWithTag("screen:refresh").performClick()
        advance(100)
        assertEquals(false, current.highRefreshRate)
    }
}
