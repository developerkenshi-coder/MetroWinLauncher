package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.LaunchAnimation
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class LaunchExpandTest : ScreenshotBase() {
    private val items = listOf("A", "B").map { AppItem(it, "App $it", Launch.Action("x")) }

    @Test
    fun aTileSaysWhereItIsJustBeforeItIsOpened() {
        val events = mutableListOf<String>()
        var bounds: Rect? = null
        show {
            StartScreen(
                cells = items.map { AppCell(Tile(it.id, TileSize.Medium), it) },
                live = LiveTileData(), badges = emptyMap(), columns = 4, background = null,
                enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(),
                actions = StartActions(
                    onClick = { events += "click:${it.key}" }, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
                    onMove = { _, _ -> }, onMerge = { _, _ -> }, onBackgroundTap = {}, onNextPage = {},
                    onTileBounds = { bounds = it; events += "bounds" },
                ),
            )
        }
        advance(2_500)
        compose.onNodeWithTag("tile:B").performClick()
        advance(100)
        assertEquals(listOf("bounds", "click:B"), events)
        // The second tile of the first row: right of the first, below the top, as wide as a medium tile (about 165 dp).
        val rect = bounds!!
        val density = context.resources.displayMetrics.density
        assertTrue("left ${rect.left}", rect.left > 150f * density)
        assertTrue("top ${rect.top}", rect.top >= 0f)
        assertEquals(165f * density, rect.width, 6f * density)
        assertEquals(rect.width, rect.height, 2f)
    }

    @Test
    fun theScreenPageChoosesHowAnAppOpens() {
        var current = LauncherSettings()
        show {
            var settings by remember { mutableStateOf(current) }
            val away = remember { Animatable(0f) }
            SettingsScreen(
                env = SettingsEnv(settings = settings, onChange = { change -> settings = change(settings); current = settings }),
                section = SettingsSection.Screen, onSectionChange = {}, sectionAway = away,
            )
        }
        advance(500)
        compose.onNodeWithTag("screen:launch:1").performClick()
        advance(100)
        assertEquals(LaunchAnimation.Expand, current.launchAnimation)
        compose.onNodeWithTag("screen:launch:0").performClick()
        advance(100)
        assertEquals(LaunchAnimation.Turnstile, current.launchAnimation)
    }
}
