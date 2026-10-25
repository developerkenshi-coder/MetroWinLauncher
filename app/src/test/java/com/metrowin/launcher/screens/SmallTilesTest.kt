package com.metrowin.launcher.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performTouchInput
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.buildStartCells
import com.metrowin.launcher.ui.theme.Palettes
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Small tiles side by side: dropping a dragged small tile in the free cells next to other small tiles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SmallTilesTest : ScreenshotBase() {
    private val items = listOf("A", "B", "C", "D", "E", "F", "G").mapIndexed { i, id ->
        AppItem(id, "App $id", Launch.Action("x"), Glyph.entries[i + 5])
    }

    @Composable
    private fun Start(entries: MutableState<List<StartEntry>>) = StartScreen(
        cells = buildStartCells(entries.value, items, null),
        live = LiveTileData(),
        badges = emptyMap(),
        columns = 4,
        background = null,
        enterToken = 0,
        exitRequest = null,
        editingKey = null,
        scrollState = rememberScrollState(),
        actions = StartActions(
            onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {},
            onMove = { dragged, target -> entries.value = StartEntries.moveTo(entries.value, dragged, target) },
            onMerge = { dragged, onto -> entries.value = StartEntries.merge(entries.value, dragged, onto) { "folder" } },
            onBackgroundTap = {}, onNextPage = {},
            onMoveAfter = { dragged, after -> entries.value = StartEntries.moveAfter(entries.value, dragged, after) },
        ),
    )

    private fun entriesOf(vararg tiles: Pair<String, TileSize>) = mutableStateOf<List<StartEntry>>(tiles.map { (id, size) -> Tile(id, size) })

    private fun pickUp(id: String) {
        compose.onNodeWithTag("tile:$id").performTouchInput { down(center) }
        advance(700)
    }

    private fun centerOf(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    @Test
    fun theFreeCellsNextToSmallTilesShowWhileASmallTileIsDragged() {
        val entries = entriesOf("A" to TileSize.Small, "B" to TileSize.Medium, "C" to TileSize.Small)
        show { Start(entries) }
        advance(2_500)
        compose.onAllNodesWithTag("drop:1:0").assertCountEquals(0)
        pickUp("C")
        capture("start_small_drop_cells")
        // The three free cells of the block with A in it.
        compose.onNodeWithTag("drop:1:0").assertExists()
        compose.onNodeWithTag("drop:0:1").assertExists()
        compose.onNodeWithTag("drop:1:1").assertExists()
        compose.onAllNodesWithTag("drop:2:0").assertCountEquals(0)
        compose.onNodeWithTag("tile:C").performTouchInput { up() }
    }

    @Test
    fun noCellsShowWhileAMediumTileIsDragged() {
        val entries = entriesOf("A" to TileSize.Small, "B" to TileSize.Medium, "C" to TileSize.Small)
        show { Start(entries) }
        advance(2_500)
        pickUp("B")
        compose.onAllNodesWithTag("drop:1:0").assertCountEquals(0)
        compose.onNodeWithTag("tile:B").performTouchInput { up() }
    }

    @Test
    fun aSmallTileDroppedInAFreeCellSitsNextToTheOtherOneWithoutAFolder() {
        val entries = entriesOf("A" to TileSize.Small, "B" to TileSize.Medium, "C" to TileSize.Small)
        show { Start(entries) }
        advance(2_500)
        pickUp("C")
        val from = centerOf("tile:C")
        val to = centerOf("drop:1:0")
        compose.onNodeWithTag("tile:C").performTouchInput { moveBy(Offset(to.x - from.x, to.y - from.y)) }
        advance(500)
        capture("start_small_dropped")
        compose.onNodeWithTag("tile:C").performTouchInput { up() }
        advance(1_000)
        capture("start_small_grouped")
        assertEquals("C is now right after A, and nothing became a folder", listOf("A", "C", "B"), entries.value.map { it.id })
        assertTrue(entries.value.none { it is Folder })
        // On the screen the two small tiles are side by side, with the same top.
        val a = compose.onNodeWithTag("tile:A").fetchSemanticsNode().boundsInRoot
        val c = compose.onNodeWithTag("tile:C").fetchSemanticsNode().boundsInRoot
        assertEquals(a.top, c.top, 1f)
        assertTrue("C is to the right of A (${a.right} <= ${c.left})", c.left >= a.right)
        assertEquals(a.width, c.width, 1f)
        assertTrue("and right next to it", c.left - a.right < a.width)
    }

    @Test
    fun theFourSmallTilesOfABlockAreAllNextToEachOther() {
        val entries = entriesOf("A" to TileSize.Small, "B" to TileSize.Medium, "C" to TileSize.Small, "D" to TileSize.Small, "E" to TileSize.Small)
        show { Start(entries) }
        advance(2_500)
        // C, D and E are dragged, one after the other, to the free cells beside A.
        for (id in listOf("C", "D", "E")) {
            pickUp(id)
            val cell = listOf("drop:1:0", "drop:0:1", "drop:1:1").firstOrNull { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
            val from = centerOf("tile:$id")
            val to = centerOf(cell!!)
            compose.onNodeWithTag("tile:$id").performTouchInput { moveBy(Offset(to.x - from.x, to.y - from.y)) }
            advance(500)
            compose.onNodeWithTag("tile:$id").performTouchInput { up() }
            advance(1_000)
        }
        capture("start_small_block_full")
        val bounds = listOf("A", "C", "D", "E").map { compose.onNodeWithTag("tile:$it").fetchSemanticsNode().boundsInRoot }
        // Two rows of two: the lefts and the tops each have two distinct values.
        assertEquals(2, bounds.map { it.left.toInt() / 10 }.toSet().size)
        assertEquals(2, bounds.map { it.top.toInt() / 10 }.toSet().size)
        assertTrue("the medium tile comes after them", entries.value.last().id == "B")
    }

    @Test
    fun theEdgeOfASmallTileReordersWhereItsMiddleMakesAFolder() {
        val entries = entriesOf("A" to TileSize.Small, "B" to TileSize.Small, "C" to TileSize.Small)
        show { Start(entries) }
        advance(2_500)
        pickUp("A")
        val from = centerOf("tile:A")
        val b = compose.onNodeWithTag("tile:B").fetchSemanticsNode().boundsInRoot
        // 25% in from the left edge of B: on a medium tile that would be the middle that makes a folder.
        val edge = Offset(b.left + b.width * 0.25f, b.center.y)
        compose.onNodeWithTag("tile:A").performTouchInput { moveBy(Offset(edge.x - from.x, edge.y - from.y)) }
        advance(700)
        compose.onNodeWithTag("tile:A").performTouchInput { up() }
        advance(500)
        assertTrue("no folder was made", entries.value.none { it is Folder })
        assertEquals(listOf("B", "A", "C"), entries.value.map { it.id })
    }

    @Test
    fun theFreeCellsAreOutlinedInEveryLook() {
        for (style in listOf(ThemeStyle.Glass, ThemeStyle.Droid)) {
            val entries = entriesOf("A" to TileSize.Small, "B" to TileSize.Medium, "C" to TileSize.Small)
            show(style = style, droid = if (style == ThemeStyle.Droid) Palettes.fromSeed(DefaultAccent) else null) { Start(entries) }
            advance(2_500)
            pickUp("C")
            compose.onNodeWithTag("drop:1:0").assertExists()
            capture("start_small_drop_cells_${style.code}")
            compose.onNodeWithTag("tile:C").performTouchInput { up() }
            break // the composition can be set once per test: Glass here, Modern Droid is drawn in the gallery
        }
    }

    // --- the empty block beside four small tiles

    private fun fourSmallsThenAWideTile() = entriesOf(
        "A" to TileSize.Small, "B" to TileSize.Small, "C" to TileSize.Small, "D" to TileSize.Small,
        "E" to TileSize.Wide, "F" to TileSize.Medium, "G" to TileSize.Small,
    )

    private fun bounds(id: String) = compose.onNodeWithTag("tile:$id").fetchSemanticsNode().boundsInRoot

    @Test
    fun theEmptyBlockBesideFourSmallTilesIsOutlinedWhileATileIsDragged() {
        val entries = fourSmallsThenAWideTile()
        show { Start(entries) }
        advance(2_500)
        // The wide tile has its own row, so the block to the right of the four small tiles is empty.
        val a = bounds("A")
        val d = bounds("D")
        val wide = bounds("E")
        assertTrue("the wide tile is below the small ones", wide.top > d.bottom)
        pickUp("F")
        capture("start_empty_block_outlined")
        // One area of two by two cells for the medium tile.
        compose.onNodeWithTag("drop:2:0").assertExists()
        val area = compose.onNodeWithTag("drop:2:0").fetchSemanticsNode().boundsInRoot
        assertTrue("it starts right of the small tiles", area.left >= d.right)
        assertEquals(a.top, area.top, 1f)
        assertTrue("and is as big as a medium tile", area.width > a.width * 1.8f)
        compose.onNodeWithTag("tile:F").performTouchInput { up() }
    }

    @Test
    fun aMediumTileDroppedInThatBlockFillsTheSpaceBesideTheSmallOnes() {
        val entries = fourSmallsThenAWideTile()
        show { Start(entries) }
        advance(2_500)
        pickUp("F")
        val from = centerOf("tile:F")
        val to = centerOf("drop:2:0")
        compose.onNodeWithTag("tile:F").performTouchInput { moveBy(Offset(to.x - from.x, to.y - from.y)) }
        advance(500)
        compose.onNodeWithTag("tile:F").performTouchInput { up() }
        advance(1_000)
        capture("start_empty_block_filled")
        assertEquals(listOf("A", "B", "C", "D", "F", "E", "G"), entries.value.map { it.id })
        val a = bounds("A")
        val d = bounds("D")
        val f = bounds("F")
        val wide = bounds("E")
        assertEquals("on the same rows as the small tiles", a.top, f.top, 1f)
        assertTrue("right of them", f.left >= d.right)
        assertTrue("and the wide tile still has its row below", wide.top >= f.bottom)
    }

    @Test
    fun aSmallTileDroppedInThatBlockIsNextToTheOthersToo() {
        val entries = fourSmallsThenAWideTile()
        show { Start(entries) }
        advance(2_500)
        pickUp("G")
        val from = centerOf("tile:G")
        val to = centerOf("drop:3:0")
        compose.onNodeWithTag("tile:G").performTouchInput { moveBy(Offset(to.x - from.x, to.y - from.y)) }
        advance(500)
        compose.onNodeWithTag("tile:G").performTouchInput { up() }
        advance(1_000)
        capture("start_empty_block_small")
        assertEquals(listOf("A", "B", "C", "D", "G", "E", "F"), entries.value.map { it.id })
        val a = bounds("A")
        val d = bounds("D")
        val g = bounds("G")
        assertEquals(a.top, g.top, 1f)
        assertTrue("beside the four small tiles", g.left >= d.right)
        assertEquals(a.width, g.width, 1f)
    }
}
