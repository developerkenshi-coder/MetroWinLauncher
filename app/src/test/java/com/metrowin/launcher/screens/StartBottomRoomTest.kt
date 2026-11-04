package com.metrowin.launcher.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The edit bar covers the bottom of Start: the room left under the last row lets the last tiles scroll above it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class StartBottomRoomTest : ScreenshotBase() {
    @Test
    fun theRoomUnderTheLastRowGrowsTheScrollByThatMuch() {
        val items = (1..16).map { AppItem("a$it", "App $it", Launch.Action("x"), Glyph.entries[5]) }
        val room = mutableStateOf(0.dp)
        lateinit var scroll: ScrollState
        show {
            scroll = rememberScrollState()
            StartScreen(
                cells = buildStartCells(items.map { Tile(it.id, TileSize.Medium) }, items, null), live = LiveTileData(), badges = emptyMap(), columns = 4,
                background = null, enterToken = 0, exitRequest = null, editingKey = null, scrollState = scroll,
                actions = StartActions(onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {}, onMove = { _, _ -> }, onMerge = { _, _ -> }, onBackgroundTap = {}, onNextPage = {}),
                bottomRoom = room.value,
            )
        }
        advance(2_500)
        val before = scroll.maxValue
        room.value = 200.dp
        compose.waitForIdle()
        advance(500)
        val grown = scroll.maxValue - before
        val px = 200 * compose.density.density
        assertTrue("the scroll grew by the room ($grown of $px px)", grown >= px - 2)
    }
}
