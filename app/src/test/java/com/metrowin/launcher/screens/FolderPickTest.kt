package com.metrowin.launcher.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.start.AppCell
import com.metrowin.launcher.ui.start.EditBar
import com.metrowin.launcher.ui.start.FolderCell
import com.metrowin.launcher.ui.start.FolderChoice
import com.metrowin.launcher.ui.start.FolderChooser
import com.metrowin.launcher.ui.start.FolderPickBar
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartCell
import com.metrowin.launcher.ui.start.StartScreen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class FolderPickTest : ScreenshotBase() {
    private val apps = ('A'..'G').map { AppItem("$it", "App $it", Launch.Action("x"), packageName = "pkg.$it") }
    private val tiles get() = apps.take(4).map { AppCell(Tile(it.id, TileSize.Medium), it) }

    private fun actions(onClick: (StartCell) -> Unit = {}) = StartActions(
        onClick = onClick, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {}, onMove = { _, _ -> }, onMerge = { _, _ -> },
        onBackgroundTap = {}, onNextPage = {},
    )

    // --- the tiles while picking

    @Test
    fun theTilesPickedForAFolderCarryAMarkAndTheOthersStepBack() {
        show {
            StartScreen(
                cells = tiles, live = LiveTileData(), badges = emptyMap(), columns = 4, background = null,
                enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(),
                actions = actions(), pickedKeys = setOf("A", "C"),
            )
        }
        advance(2_500)
        capture("folder_pick_tiles")
        // The mark sits on the picked ones, which say so to TalkBack; the others say they are not.
        compose.onAllNodesWithTag("picked", useUnmergedTree = true).assertCountEquals(2)
        fun state(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
        assertEquals("selected", state("tile:A"))
        assertEquals("not selected", state("tile:B"))
        assertEquals("selected", state("tile:C"))
    }

    @Test
    fun tappingATileWhilePickingReportsItAndNothingElse() {
        val clicked = mutableListOf<String>()
        show {
            StartScreen(
                cells = tiles, live = LiveTileData(), badges = emptyMap(), columns = 4, background = null,
                enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(),
                actions = actions { clicked += it.entryId }, pickedKeys = emptySet(),
            )
        }
        advance(2_500)
        compose.onNodeWithTag("tile:B").performClick()
        assertEquals(listOf("B"), clicked)
        compose.onAllNodesWithTag("picked", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun withoutPickingThereAreNoMarks() {
        show {
            StartScreen(
                cells = tiles, live = LiveTileData(), badges = emptyMap(), columns = 4, background = null,
                enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(), actions = actions(),
            )
        }
        advance(2_500)
        compose.onAllNodesWithTag("picked", useUnmergedTree = true).assertCountEquals(0)
    }

    // --- the bar

    @Composable
    private fun Bar(picked: Int, canCreate: Boolean, joining: Boolean = false, onCreate: () -> Unit = {}, onCancel: () -> Unit = {}) =
        FolderPickBar(picked, canCreate, joining, onCreate, onCancel)

    @Test
    fun theBarCountsAndCreatesOnlyWhenThereAreEnough() {
        var created = 0
        show { Bar(picked = 1, canCreate = false, onCreate = { created++ }) }
        advance(200)
        capture("folder_pick_bar_one")
        compose.onNodeWithTag("folderpick:count").assertTextEquals("1 selected")
        compose.onNodeWithTag("folderpick:create").performClick()
        assertEquals("one tile cannot make a folder", 0, created)
    }

    @Test
    fun withEnoughTilesTheButtonCreatesAndCancelGivesUp() {
        var created = 0
        var cancelled = 0
        show { Bar(picked = 3, canCreate = true, onCreate = { created++ }, onCancel = { cancelled++ }) }
        advance(200)
        capture("folder_pick_bar_three")
        compose.onNodeWithTag("folderpick:count").assertTextEquals("3 selected")
        compose.onNodeWithTag("folderpick:create").performClick()
        compose.onNodeWithTag("folderpick:cancel").performClick()
        assertEquals(1, created)
        assertEquals(1, cancelled)
    }

    @Test
    fun theHintChangesWhenAFolderIsAmongThePicked() {
        show { Bar(picked = 2, canCreate = true, joining = true) }
        advance(200)
        compose.onNodeWithTag("folderpick:hint").assertTextEquals("Touch more tiles to add them to the folder")
    }

    @Test
    fun theEditBarHasAFolderButton() {
        var pressed = 0
        show {
            EditBar(
                size = TileSize.Medium, page = 0, pageCount = 1, onSize = {}, onMovePage = {}, onDone = {},
                onFolder = { pressed++ },
            )
        }
        advance(200)
        compose.onNodeWithTag("edit:folder").performClick()
        assertEquals(1, pressed)
    }

    @Test
    fun withoutAHandlerThereIsNoFolderButton() {
        show { EditBar(size = TileSize.Medium, page = 0, pageCount = 1, onSize = {}, onMovePage = {}, onDone = {}) }
        advance(200)
        compose.onAllNodesWithTag("edit:folder").assertCountEquals(0)
    }

    // --- an app into a folder, from the app list

    @Test
    fun theMenuOffersAFolderOnlyWhenThereIsOne() {
        var added = 0
        show {
            AppContextMenu(
                state = MenuState(apps.first(), top = 300f, height = 190f), pinned = false,
                onPinToggle = {}, onUninstall = {}, onDismiss = {}, onAddToFolder = { added++ },
            )
        }
        advance(300)
        compose.onNodeWithTag("menu:folder").performClick()
        assertEquals(1, added)
    }

    @Test
    fun withoutFoldersTheMenuHasNoFolderEntry() {
        show {
            AppContextMenu(state = MenuState(apps.first(), top = 300f, height = 190f), pinned = false, onPinToggle = {}, onUninstall = {}, onDismiss = {})
        }
        advance(300)
        compose.onAllNodesWithTag("menu:folder").assertCountEquals(0)
    }

    @Test
    fun theChooserListsTheFoldersWithTheirSizeAndHandsBackTheOneTouched() {
        val picked = mutableListOf<String>()
        show {
            FolderChooser(
                folders = listOf(FolderChoice("f1", "Games", 3), FolderChoice("f2", "", 1)),
                onPick = { picked += it.id }, onCancel = {},
            )
        }
        advance(300)
        capture("folder_chooser")
        compose.onNodeWithTag("folderchooser:f2").performClick()
        assertEquals(listOf("f2"), picked)
        compose.onNodeWithTag("folderchooser:f1").assertExists()
    }

    @Test
    fun theChooserCanBeCancelled() {
        var cancelled = 0
        show { FolderChooser(listOf(FolderChoice("f1", "Games", 2)), onPick = {}, onCancel = { cancelled++ }) }
        advance(300)
        compose.onNodeWithTag("folderchooser:cancel").performClick()
        assertEquals(1, cancelled)
    }

    // --- the face of a folder with many apps

    private fun folderCell(count: Int): FolderCell {
        val children = apps.take(count)
        return FolderCell(Folder("f", "Games", TileSize.Medium, children.map { it.id }), children, expanded = false)
    }

    private fun showFolder(count: Int) = show {
        StartScreen(
            cells = listOf(folderCell(count)), live = LiveTileData(), badges = emptyMap(), columns = 4, background = null,
            enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(), actions = actions(),
        )
    }

    @Test
    fun aFolderWithMoreThanFourAppsShowsHowManyAreBeyondTheThird() {
        showFolder(7)
        advance(2_500)
        capture("folder_seven_apps")
        // Three apps and a square that says +4: seven in all.
        compose.onNodeWithTag("folder:more", useUnmergedTree = true).assertTextEquals("+4")
    }

    @Test
    fun aFolderWithFourOrFewerAppsShowsThemAll() {
        showFolder(4)
        advance(2_500)
        compose.onAllNodesWithTag("folder:more", useUnmergedTree = true).assertCountEquals(0)
    }
}
