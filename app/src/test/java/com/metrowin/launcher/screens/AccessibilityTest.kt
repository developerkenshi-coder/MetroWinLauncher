package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.AppCell
import com.metrowin.launcher.ui.start.FolderCell
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartCell
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.theme.LocalMetroColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class AccessibilityTest : ScreenshotBase() {
    // --- what a button says to TalkBack

    @Test
    fun aPressedThingCanBeActivatedWithoutATouch() {
        var clicks = 0
        var longClicks = 0
        show {
            Box(Modifier.size(100.dp).testTag("b").metroPress(onClick = { clicks++ }, onLongClick = { longClicks++ }))
        }
        advance(100)
        compose.onNodeWithTag("b").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("b").performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(1, clicks)
        assertEquals(1, longClicks)
    }

    @Test
    fun aDisabledPressedThingSaysItIsDisabled() {
        var clicks = 0
        show { Box(Modifier.size(100.dp).testTag("b").metroPress(onClick = { clicks++ }, enabled = false)) }
        advance(100)
        compose.onNodeWithTag("b").assertIsNotEnabled()
        assertEquals(0, clicks)
    }

    @Test
    fun theLongPressHasANameForTheActionsMenu() {
        show { Box(Modifier.size(100.dp).testTag("b").metroPress(onClick = {}, onLongClick = {}, longClickLabel = "More options")) }
        advance(100)
        val label = compose.onNodeWithTag("b").fetchSemanticsNode().config.getOrNull(SemanticsActions.OnLongClick)?.label
        assertEquals("More options", label)
    }

    // --- tiles

    private val unpinned = mutableListOf<String>()
    private val resized = mutableListOf<String>()
    private val moves = mutableListOf<Pair<String, String>>()
    private val renamed = mutableListOf<String>()

    private val apps = listOf("A", "B", "C").map { AppItem(it, "App $it", Launch.Action("x"), packageName = "pkg.$it") }

    private fun actions() = StartActions(
        onClick = {}, onLongClick = {}, onUnpin = { unpinned += it.entryId }, onResize = { resized += it.entryId },
        onRename = { renamed += it.entryId }, onMove = { a, b -> moves += a to b }, onMerge = { _, _ -> },
        onBackgroundTap = {}, onNextPage = {},
    )

    private fun showTiles(cells: List<StartCell>, badges: Map<String, Int> = emptyMap(), readOnly: Boolean = false, reduce: Boolean = false, contrast: Boolean = false) =
        show(reduceMotion = reduce, highContrast = contrast) {
            StartScreen(
                cells = cells, live = LiveTileData(), badges = badges, columns = 4, background = null,
                enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(),
                actions = actions(), readOnly = readOnly,
            )
        }

    private val three get() = apps.map { AppCell(Tile(it.id, TileSize.Medium), it) }

    private fun labelsOf(tag: String): List<String> =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

    private fun perform(tag: String, label: String) {
        val action = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)!!.first { it.label == label }
        compose.runOnUiThread { action.action() }
    }

    @Test
    fun aTileSaysHowManyNotificationsItHas() {
        showTiles(three, badges = mapOf("pkg.A" to 3))
        advance(2_500)
        compose.onNodeWithTag("tile:A").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "App A, 3 notifications"))
        assertNull(compose.onNodeWithTag("tile:B").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun aTileOffersTheActionsThatNeedNoDrag() {
        showTiles(three)
        advance(2_500)
        // The first tile has nothing earlier, the last nothing later, the one in the middle has both.
        assertEquals(listOf("Unpin from Start", "Change size", "Move later"), labelsOf("tile:A"))
        assertEquals(listOf("Unpin from Start", "Change size", "Move earlier", "Move later"), labelsOf("tile:B"))
        assertEquals(listOf("Unpin from Start", "Change size", "Move earlier"), labelsOf("tile:C"))
    }

    @Test
    fun theActionsDoWhatTheirNamesSay() {
        showTiles(three)
        advance(2_500)
        perform("tile:B", "Unpin from Start")
        perform("tile:B", "Change size")
        perform("tile:B", "Move earlier")
        perform("tile:B", "Move later")
        assertEquals(listOf("B"), unpinned)
        assertEquals(listOf("B"), resized)
        assertEquals(listOf("B" to "A", "B" to "C"), moves)
    }

    @Test
    fun aFolderCanBeRenamedAndTakenApartAndSaysHowManyAppsItHolds() {
        val folder = FolderCell(Folder("f1", "Games", TileSize.Medium, listOf("A", "B")), apps.take(2), expanded = false)
        showTiles(listOf(folder) + apps.drop(2).map { AppCell(Tile(it.id, TileSize.Medium), it) })
        advance(2_500)
        assertTrue("Rename folder" in labelsOf("tile:f1"))
        assertTrue("Take the folder apart" in labelsOf("tile:f1"))
        compose.onNodeWithTag("tile:f1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Games, folder with 2 apps"))
        perform("tile:f1", "Rename folder")
        perform("tile:f1", "Take the folder apart")
        assertEquals(listOf("f1"), renamed)
        assertEquals(listOf("f1"), unpinned)
    }

    @Test
    fun theKidsCornerOffersNoEditingActions() {
        showTiles(three, readOnly = true)
        advance(2_500)
        assertTrue(labelsOf("tile:A").isEmpty())
    }

    @Test
    fun theTilesAreWhereTheyAreForAnAccessibilityService() {
        showTiles(three)
        advance(2_500)
        // Each tile's semantic bounds follow the tile: they used to all sit at the origin of the grid.
        val a = compose.onNodeWithTag("tile:A").fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithTag("tile:B").fetchSemanticsNode().boundsInRoot
        assertTrue("B is to the right of A (${a.left} / ${b.left})", b.left > a.left + 100f)
        assertNotNull(a)
    }

    // --- less motion

    @Test
    fun withReducedMotionAllTilesAreThereAlmostAtOnce() {
        showTiles(three, reduce = true)
        advance(200)
        val reduced = capture("tiles_reduced_motion").accentShare()
        advance(2_500)
        val settled = capture("tiles_settled").accentShare()
        assertTrue("all tiles are in after 200 ms ($reduced of $settled)", reduced > settled * 0.95f)
    }

    @Test
    fun withoutReducedMotionTheTilesComeInOneAfterAnother() {
        showTiles(three)
        advance(200)
        val normal = capture("tiles_normal_motion").accentShare()
        advance(2_500)
        val settled = capture("tiles_settled_normal").accentShare()
        assertTrue("the tiles are still arriving after 200 ms ($normal of $settled)", normal < settled * 0.9f)
    }

    @Test
    fun withReducedMotionALiveTileKeepsItsFirstFace() {
        show(reduceMotion = true) { FlipContainer(faces = 2, seed = 0) { face -> androidx.compose.foundation.text.BasicText("face$face") } }
        advance(8_000)
        compose.onNodeWithText("face0").assertExists()
        compose.onNodeWithText("face1").assertDoesNotExist()
    }

    @Test
    fun withoutReducedMotionALiveTileFlips() {
        show { FlipContainer(faces = 2, seed = 0) { face -> androidx.compose.foundation.text.BasicText("face$face") } }
        advance(3_000)
        compose.onNodeWithText("face1").assertExists()
    }

    // --- more contrast

    @Test
    fun highContrastPutsAWhiteFrameOnTheTiles() {
        showTiles(three, contrast = true)
        advance(2_500)
        val shot = capture("tiles_high_contrast")
        val density = context.resources.displayMetrics.density
        val x = (12f * density + 2f).toInt() // just inside the left edge of the first tile
        val y = ((16f + 60f) * density).toInt()
        assertEquals("the frame is white", 0xFFFFFFFF.toInt(), shot.getPixel(x, y))
        // Inside the frame the tile keeps its colour.
        assertTrue(isAccent(shot.getPixel((12f * density + 12f * density).toInt(), y)))
    }

    @Test
    fun withoutItThereIsNoFrame() {
        showTiles(three)
        advance(2_500)
        val shot = capture("tiles_normal")
        val density = context.resources.displayMetrics.density
        assertTrue(isAccent(shot.getPixel((12f * density + 2f).toInt(), ((16f + 60f) * density).toInt())))
    }

    // --- the settings page

    @Test
    fun theAccessibilityPageHasItsTwoSwitches() {
        var current = LauncherSettings()
        show {
            var settings by remember { mutableStateOf(current) }
            val away = remember { Animatable(0f) }
            SettingsScreen(
                env = SettingsEnv(settings = settings, onChange = { change -> settings = change(settings); current = settings }),
                section = SettingsSection.Accessibility, onSectionChange = {}, sectionAway = away,
            )
        }
        advance(500)
        capture("settings_accessibility")
        tapScrolled("access:motion")
        advance(100)
        assertTrue(current.reduceMotion)
        tapScrolled("access:contrast")
        advance(100)
        assertTrue(current.highContrast)
        assertFalse(LauncherSettings().reduceMotion || LauncherSettings().highContrast)
        assertEquals(LocalMetroColors.javaClass, LocalMetroColors.javaClass)
    }
}
