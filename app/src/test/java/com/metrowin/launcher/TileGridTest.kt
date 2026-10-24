package com.metrowin.launcher

import com.metrowin.launcher.data.Placement
import com.metrowin.launcher.data.TileGrid
import com.metrowin.launcher.model.TileSize.Medium
import com.metrowin.launcher.model.TileSize.Small
import com.metrowin.launcher.model.TileSize.Wide
import org.junit.Assert.assertEquals
import org.junit.Test

class TileGridTest {
    @Test
    fun emptyStartHasNoRows() {
        assertEquals(0, TileGrid.place(emptyList()).rows)
    }

    @Test
    fun mediumTilesFillTwoPerRow() {
        val layout = TileGrid.place(listOf(Medium, Medium, Medium))
        assertEquals(
            listOf(Placement(0, 0, 2, 2), Placement(2, 0, 2, 2), Placement(0, 2, 2, 2)),
            layout.placements,
        )
        assertEquals(4, layout.rows)
    }

    @Test
    fun wideTileTakesAFullRowAndLeavesTheBlockBesideALoneMediumEmpty() {
        val layout = TileGrid.place(listOf(Medium, Wide, Medium))
        assertEquals(
            listOf(Placement(0, 0, 2, 2), Placement(0, 2, 4, 2), Placement(0, 4, 2, 2)),
            layout.placements,
        )
    }

    @Test
    fun fourSmallTilesShareOneBlock() {
        val layout = TileGrid.place(List(5) { Small })
        assertEquals(
            listOf(
                Placement(0, 0, 1, 1), Placement(1, 0, 1, 1), Placement(0, 1, 1, 1), Placement(1, 1, 1, 1),
                Placement(2, 0, 1, 1),
            ),
            layout.placements,
        )
        assertEquals(2, layout.rows)
    }

    @Test
    fun partiallyFilledBlockIsNotUsedByAMediumTile() {
        // M | s s     then the next M cannot go on the half-empty block, it starts a new row.
        //   | . .
        val layout = TileGrid.place(listOf(Medium, Small, Small, Medium))
        assertEquals(Placement(2, 0, 1, 1), layout.placements[1])
        assertEquals(Placement(3, 0, 1, 1), layout.placements[2])
        assertEquals(Placement(0, 2, 2, 2), layout.placements[3])
    }

    @Test
    fun tilesNeverJumpBackToAnEarlierBlock() {
        // The three free slots next to the first small tile stay empty: later tiles are appended.
        val layout = TileGrid.place(listOf(Small, Medium, Small))
        assertEquals(Placement(0, 0, 1, 1), layout.placements[0])
        assertEquals(Placement(2, 0, 2, 2), layout.placements[1])
        assertEquals(Placement(0, 2, 1, 1), layout.placements[2])
    }

    @Test
    fun noTilesOverlap() {
        val sizes = listOf(Wide, Small, Medium, Small, Small, Wide, Medium, Small, Medium, Small, Small, Small)
        val taken = HashSet<Pair<Int, Int>>()
        TileGrid.place(sizes).placements.forEach { p ->
            for (r in p.row until p.row + p.rows) for (c in p.col until p.col + p.cols) {
                assertEquals("cell ($c,$r) used twice", true, taken.add(c to r))
            }
        }
    }
}
