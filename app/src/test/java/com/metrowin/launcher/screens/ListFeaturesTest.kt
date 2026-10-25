package com.metrowin.launcher.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.AppRepository
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app list's menu, the star of the jump list, and the torch tile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class ListFeaturesTest : ScreenshotBase() {
    private val app = AppItem("app:a", "Alpha", Launch.Action("x"), removable = true)

    // --- the menu

    @Test
    fun theMenuOffersToHideAnAppWhenItCanBeHidden() {
        var hidden = 0
        show {
            AppContextMenu(
                state = MenuState(app, top = 300f, height = 190f), pinned = false,
                onPinToggle = {}, onUninstall = {}, onDismiss = {}, onHide = { hidden++ },
            )
        }
        advance(300)
        capture("menu_with_hide")
        compose.onNodeWithTag("menu:hide").performClick()
        assertEquals(1, hidden)
    }

    @Test
    fun theMenuOfTheLaunchersOwnSettingsHasNoHideEntry() {
        show {
            AppContextMenu(
                state = MenuState(app, top = 300f, height = 190f), pinned = false,
                onPinToggle = {}, onUninstall = {}, onDismiss = {}, onHide = null,
            )
        }
        advance(300)
        compose.onAllNodesWithTag("menu:hide").assertCountEquals(0)
    }

    // --- the star of the jump list

    @Test
    fun theStarTakesYouToTheLastOpenedAppWhenThereIsOne() {
        var recent = 0
        val picked = mutableListOf<String>()
        show {
            JumpList(
                keys = listOf("#", "A", "B"), available = setOf("A"),
                onPick = { picked += it }, onDismiss = {},
                hasRecent = true, onPickRecent = { recent++ },
            )
        }
        advance(900)
        capture("jump_list_star")
        compose.onNodeWithTag("jump:recent").performClick()
        assertEquals(1, recent)
        compose.onNodeWithTag("jump:A").performClick()
        assertEquals(listOf("A"), picked)
    }

    @Test
    fun theStarDoesNothingWhenNothingWasOpenedYet() {
        var recent = 0
        show {
            JumpList(
                keys = listOf("#", "A"), available = setOf("A"),
                onPick = {}, onDismiss = {},
                hasRecent = false, onPickRecent = { recent++ },
            )
        }
        advance(900)
        compose.onNodeWithTag("jump:recent").performClick()
        assertEquals(0, recent)
    }

    @Test
    fun theListKnowsWhereAnAppIs() {
        val items = AppSections.sort(listOf("Delta", "Alpha", "Charlie", "Bravo").map { AppItem("app:$it", it, Launch.Action("x")) })
        val model = AppListModel(AppSections.group(items))
        // Row 0 is the search box, then each letter has its header.
        val alpha = model.indexOfApp("app:Alpha")!!
        val bravo = model.indexOfApp("app:Bravo")!!
        assertTrue("an app comes after its letter header", alpha > model.headerIndex.getValue("A"))
        assertTrue(bravo > alpha)
        assertNull(model.indexOfApp("app:nope"))
    }

    // --- the torch tile

    private val torch = AppItem(AppRepository.TORCH_ID, "Torch", Launch.Internal(AppRepository.ROUTE_TORCH), Glyph.Flashlight, live = LiveKind.Torch)

    private fun showTorch(on: Boolean) = show {
        Box(Modifier.size(200.dp).background(DefaultAccent)) {
            TileFace(torch, TileSize.Medium, 200.dp, 200.dp, LiveTileData(torchOn = on), seed = 0, notifications = 0)
        }
    }

    @Test
    fun theTorchTileIsTheAccentColourWhileTheLightIsOff() {
        showTorch(on = false)
        advance(300)
        val screen = capture("tile_torch_off")
        assertTrue("accent in the corner", isAccent(screen.getPixel(12, 12)))
    }

    @Test
    fun theTorchTileTurnsWhiteWhileTheLightIsOn() {
        showTorch(on = true)
        advance(300)
        val screen = capture("tile_torch_on")
        assertEquals("white in the corner", 0xFFFFFFFF.toInt(), screen.getPixel(12, 12))
        // The glyph is drawn black on it, in the middle.
        val dark = (250..350).sumOf { y -> (250..350).count { x -> screen.getPixel(x, y) and 0xFFFFFF < 0x404040 } }
        assertTrue("the glyph is dark on the white tile ($dark)", dark > 100)
    }
}
