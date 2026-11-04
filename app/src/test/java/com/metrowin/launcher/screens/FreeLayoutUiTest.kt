package com.metrowin.launcher.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Free mode on Start: the dragged tile shows where it lands, goes to the cell it is let go on, and nothing else moves. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class FreeLayoutUiTest : ScreenshotBase() {
    private val items = listOf("A", "B", "C").mapIndexed { i, id -> AppItem(id, "App $id", Launch.Action("x"), Glyph.entries[i + 5]) }
    private var placed: List<Any> = emptyList()

    @Composable
    private fun Start(entries: MutableState<List<StartEntry>>, free: Boolean) = StartScreen(
        cells = buildStartCells(entries.value, items, null), live = LiveTileData(), badges = emptyMap(), columns = 4, background = null,
        enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(), free = free,
        actions = StartActions(
            onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
            onMove = { dragged, target -> entries.value = StartEntries.moveTo(entries.value, dragged, target) },
            onMerge = { _, _ -> }, onBackgroundTap = {}, onNextPage = {},
            onPlace = { id, col, row, current ->
                placed = listOf(id, col, row)
                entries.value = StartEntries.place(entries.value, id, col, row, current)
            },
        ),
    )

    private fun centerOf(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    @Test
    fun aTileLetGoOnAFreeCellStaysThereAndTheOthersDoNotMove() {
        val entries = mutableStateOf<List<StartEntry>>(listOf(Tile("A", TileSize.Medium), Tile("B", TileSize.Medium), Tile("C", TileSize.Medium)))
        show { Start(entries, free = true) }
        advance(2_500)
        val bBefore = compose.onNodeWithTag("tile:B").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("tile:A").performTouchInput { down(center) }
        advance(700)
        val from = centerOf("tile:A")
        // Two rows of tiles down, in the empty room under the others: the cell is lit while the finger is on it.
        compose.onNodeWithTag("tile:A").performTouchInput { moveBy(Offset(0f, bBefore.height * 2f)) }
        advance(500)
        val shown = listOf("free:0:4", "free:0:3", "free:0:5").any { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("the cell where it lands is outlined", shown)
        capture("start_free_drag")
        compose.onNodeWithTag("tile:A").performTouchInput { up() }
        advance(1_000)
        compose.waitForIdle()
        advance(1_000)
        capture("start_free_dropped")
        assertEquals("A", placed[0])
        assertEquals(0, placed[1])
        assertTrue("A is now below, not in the first row (it was at ${from.y})", (placed[2] as Int) >= 3)
        val bAfter = compose.onNodeWithTag("tile:B").fetchSemanticsNode().boundsInRoot
        assertEquals("B did not move up into the gap", bBefore.top, bAfter.top, 1f)
        assertEquals(bBefore.left, bAfter.left, 1f)
        val a = compose.onNodeWithTag("tile:A").fetchSemanticsNode().boundsInRoot
        assertTrue("A is lower than B", a.top > bAfter.top)
    }

    @Test
    fun withTheModeOffADragStillReordersAndShowsNoCell() {
        val entries = mutableStateOf<List<StartEntry>>(listOf(Tile("A", TileSize.Medium), Tile("B", TileSize.Medium), Tile("C", TileSize.Medium)))
        show { Start(entries, free = false) }
        advance(2_500)
        compose.onNodeWithTag("tile:A").performTouchInput { down(center) }
        advance(700)
        val to = centerOf("tile:B")
        val from = centerOf("tile:A")
        compose.onNodeWithTag("tile:A").performTouchInput { moveBy(Offset(to.x - from.x - 0.4f * compose.onNodeWithTag("tile:B").fetchSemanticsNode().boundsInRoot.width, 0f)) }
        advance(500)
        assertEquals(0, compose.onAllNodesWithTag("free:0:0").fetchSemanticsNodes().size)
        compose.onNodeWithTag("tile:A").performTouchInput { up() }
        advance(1_000)
        assertTrue("it swapped with B, as before", entries.value.map { it.id } != listOf("A", "B", "C"))
        assertTrue("and no cell was kept", entries.value.all { it.col == -1 })
    }

    @Test
    fun crossingATileInPassingDoesNotMoveIt() {
        val entries = mutableStateOf<List<StartEntry>>(listOf(Tile("A", TileSize.Medium), Tile("B", TileSize.Medium), Tile("C", TileSize.Medium)))
        show { Start(entries, free = false) }
        advance(2_500)
        compose.onNodeWithTag("tile:A").performTouchInput { down(center) }
        advance(700)
        val to = centerOf("tile:B")
        val from = centerOf("tile:A")
        val width = compose.onNodeWithTag("tile:B").fetchSemanticsNode().boundsInRoot.width
        // Over the edge of B for a blink, and back home before the moment is up.
        compose.onNodeWithTag("tile:A").performTouchInput { moveBy(Offset(to.x - from.x - 0.4f * width, 0f)) }
        advance(40)
        compose.onNodeWithTag("tile:A").performTouchInput { moveBy(Offset(-(to.x - from.x - 0.4f * width), 0f)) }
        advance(40)
        compose.onNodeWithTag("tile:A").performTouchInput { up() }
        advance(1_000)
        assertEquals(listOf("A", "B", "C"), entries.value.map { it.id })
    }
}
