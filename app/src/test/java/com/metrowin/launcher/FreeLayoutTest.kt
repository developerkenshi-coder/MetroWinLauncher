package com.metrowin.launcher

import com.metrowin.launcher.data.Placement
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.StartPages
import com.metrowin.launcher.data.TileCodec
import com.metrowin.launcher.data.TileGrid
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Free mode: tiles keep the cell they were dropped on, and nothing is lost when the mode is on or off. */
class FreeLayoutTest {
    private val sizes = listOf(TileSize.Medium, TileSize.Small, TileSize.Wide, TileSize.Small)

    @Test
    fun withNoCellsGivenThePlacementIsTheFlowOfBefore() {
        assertEquals(TileGrid.place(sizes, 4), TileGrid.place(sizes, 4, fixed = listOf(null, null, null, null)))
        assertEquals(TileGrid.place(sizes, 6), TileGrid.place(sizes, 6, fixed = emptyList()))
    }

    @Test
    fun aTileWithACellStaysThereAndTheOthersFlowAround() {
        val layout = TileGrid.place(sizes, 4, fixed = listOf(null, 3 to 5, null, null))
        assertEquals(Placement(3, 5, 1, 1), layout.placements[1])
        assertEquals("the others did not move for it", TileGrid.place(listOf(TileSize.Medium, TileSize.Wide, TileSize.Small), 4).placements[0], layout.placements[0])
        assertNoOverlap(layout.placements)
    }

    @Test
    fun aGapIsKeptWhereATileWasTakenOut() {
        // Three medium tiles in a row of two blocks: the first two fixed, the third one dropped far below stays below, and the hole under them stays empty.
        val layout = TileGrid.place(List(3) { TileSize.Medium }, 4, fixed = listOf(0 to 0, 2 to 0, 0 to 6))
        assertEquals(listOf(Placement(0, 0, 2, 2), Placement(2, 0, 2, 2), Placement(0, 6, 2, 2)), layout.placements)
        assertEquals(8, layout.rows)
    }

    @Test
    fun aCellThatDoesNotFitOrIsTakenMakesTheTileFlow() {
        val layout = TileGrid.place(List(3) { TileSize.Medium }, 4, fixed = listOf(0 to 0, 0 to 0, 3 to 0))
        assertEquals("taken by the first", Placement(2, 0, 2, 2), layout.placements[1])
        assertTrue("past the right edge", layout.placements[2].col + 2 <= 4)
        assertNoOverlap(layout.placements)
        val negative = TileGrid.place(listOf(TileSize.Small), 4, fixed = listOf(-3 to 2))
        assertEquals(Placement(0, 0, 1, 1), negative.placements[0])
        val huge = TileGrid.place(listOf(TileSize.Small), 4, fixed = listOf(1 to Int.MAX_VALUE))
        assertEquals(Placement(0, 0, 1, 1), huge.placements[0])
    }

    @Test
    fun theCellsAreSavedAndAnOldFileHasNone() {
        val saved = TileCodec.decode(TileCodec.encode(listOf(Tile("a", TileSize.Small, col = 3, row = 7), Folder("f", "n", TileSize.Medium, listOf("x", "y"), col = 0, row = 2), Tile("b", TileSize.Medium))))!!
        assertEquals(3 to 7, saved[0].col to saved[0].row)
        assertEquals(0 to 2, saved[1].col to saved[1].row)
        assertEquals(-1 to -1, saved[2].col to saved[2].row)
        val old = TileCodec.decode("""[{"id":"a","size":"m"},{"id":"b","size":"w","group":"G"}]""")!!
        assertTrue(old.all { it.col == -1 && it.row == -1 })
        assertFalse("nothing is written for a tile that flows", TileCodec.encode(listOf(Tile("b", TileSize.Medium))).contains("col"))
    }

    @Test
    fun placingATileFixesTheOthersWhereTheyAre() {
        val entries: List<StartEntry> = listOf(Tile("a", TileSize.Medium), Tile("b", TileSize.Medium), Tile("c", TileSize.Small))
        val now = mapOf("a" to (0 to 0), "b" to (2 to 0), "c" to (0 to 2))
        val placed = StartEntries.place(entries, "c", 3, 4, now)
        assertEquals(listOf(0 to 0, 2 to 0, 3 to 4), placed.map { it.col to it.row })
        assertEquals("the order of the list is kept", listOf("a", "b", "c"), placed.map { it.id })
        assertEquals("nothing to place for an unknown tile", entries, StartEntries.place(entries, "zz", 1, 1, now))
    }

    @Test
    fun aFolderMadeOnATileTakesItsCellAndAMovedTileLosesIt() {
        val entries: List<StartEntry> = listOf(Tile("a", TileSize.Medium, col = 2, row = 3), Tile("b", TileSize.Medium, col = 0, row = 0))
        val merged = StartEntries.merge(entries, "b", "a") { "folder" }
        assertEquals(2 to 3, merged.single().let { it.col to it.row })
        val released = StartEntries.release(merged, "folder")
        assertEquals(2 to 3, released[0].let { it.col to it.row })
        assertEquals(-1 to -1, released[1].let { it.col to it.row })
        val pages = StartPages.moveToPage(listOf(entries, emptyList()), "a", 1)
        assertEquals(-1 to -1, pages[1].single().let { it.col to it.row })
        assertNull(pages[0].firstOrNull { it.id == "a" })
    }

    private fun assertNoOverlap(placements: List<Placement>) {
        val cells = HashSet<Pair<Int, Int>>()
        placements.forEach { p -> for (r in p.row until p.row + p.rows) for (c in p.col until p.col + p.cols) assertTrue("cell $c,$r used twice", cells.add(c to r)) }
    }
}
