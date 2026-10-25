package com.metrowin.launcher

import com.metrowin.launcher.data.DropTarget
import com.metrowin.launcher.data.Placement
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.TileGrid
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize.Medium
import com.metrowin.launcher.model.TileSize.Small
import com.metrowin.launcher.model.TileSize.Wide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun threeColumnsFitThreeMediumTilesInARow() {
        val layout = TileGrid.place(listOf(Medium, Medium, Medium, Medium), TileGrid.MORE_COLUMNS)
        assertEquals(
            listOf(Placement(0, 0, 2, 2), Placement(2, 0, 2, 2), Placement(4, 0, 2, 2), Placement(0, 2, 2, 2)),
            layout.placements,
        )
    }

    @Test
    fun aWideTileLeavesRoomForAMediumTileInThreeColumns() {
        val layout = TileGrid.place(listOf(Wide, Medium, Wide), TileGrid.MORE_COLUMNS)
        assertEquals(Placement(0, 0, 4, 2), layout.placements[0])
        assertEquals(Placement(4, 0, 2, 2), layout.placements[1])
        // The next wide tile starts a new row: it needs two free blocks side by side.
        assertEquals(Placement(0, 2, 4, 2), layout.placements[2])
    }

    @Test
    fun aWideTileCanSitOnTheLastTwoBlocksOfARow() {
        val layout = TileGrid.place(listOf(Medium, Wide), TileGrid.MORE_COLUMNS)
        assertEquals(Placement(2, 0, 4, 2), layout.placements[1])
    }

    @Test
    fun noTilesOverlapInEitherLayout() {
        val sizes = listOf(Wide, Small, Medium, Small, Small, Wide, Medium, Small, Medium, Small, Small, Small, Wide, Wide)
        for (columns in listOf(TileGrid.DEFAULT_COLUMNS, TileGrid.MORE_COLUMNS)) {
            val taken = HashSet<Pair<Int, Int>>()
            TileGrid.place(sizes, columns).placements.forEach { p ->
                assertEquals("tile sticks out of the grid", true, p.col + p.cols <= columns)
                for (r in p.row until p.row + p.rows) for (c in p.col until p.col + p.cols) {
                    assertEquals("cell ($c,$r) used twice with $columns columns", true, taken.add(c to r))
                }
            }
        }
    }

    @Test
    fun aPortraitPhoneKeepsItsFourOrSixColumns() {
        assertEquals(4, TileGrid.columnsFor(360f, more = false))
        assertEquals(4, TileGrid.columnsFor(320f, more = false))
        assertEquals(4, TileGrid.columnsFor(411f, more = false))
        assertEquals(6, TileGrid.columnsFor(360f, more = true))
    }

    @Test
    fun aWiderScreenGetsMoreColumnsInWholeBlocksInsteadOfBiggerTiles() {
        assertEquals(10, TileGrid.columnsFor(800f, more = false))
        assertEquals(12, TileGrid.columnsFor(800f, more = true))
        for (width in 320..1600 step 40) {
            for (more in listOf(false, true)) {
                val columns = TileGrid.columnsFor(width.toFloat(), more)
                assertEquals("$width dp: whole blocks", 0, columns % 2)
                assertEquals("$width dp: not too many", true, columns in 4..12)
            }
        }
    }

    @Test
    fun theGridNeverNeedsLessThanTheWidthItIsGiven() {
        // A wider screen never gets fewer columns than a narrower one.
        var previous = 0
        for (width in 300..1600 step 10) {
            val columns = TileGrid.columnsFor(width.toFloat(), more = false)
            assertEquals("$width dp", true, columns >= previous)
            previous = columns
        }
    }

    // --- free cells next to small tiles

    @Test
    fun aLoneSmallTileLeavesThreeCellsThatAnotherSmallTileCanTake() {
        // A small tile, a medium one after it, and a small one beyond: the first block has three free cells.
        val sizes = listOf(Small, Medium, Small)
        val targets = TileGrid.dropTargets(sizes, 4, dragged = 2)
        assertEquals(listOf(DropTarget(1, 0, 1, 1, 0), DropTarget(0, 1, 1, 1, 0), DropTarget(1, 1, 1, 1, 0)), targets)
    }

    @Test
    fun theBlockTheTileIsInIsNotATargetAndNeitherIsAFullBlock() {
        assertEquals("only its own block has room", emptyList<DropTarget>(), TileGrid.dropTargets(listOf(Small, Small), 4, dragged = 1))
        assertEquals("the first block is full", emptyList<DropTarget>(), TileGrid.dropTargets(List(5) { Small }, 4, dragged = 4))
    }

    @Test
    fun everyPartlyFilledBlockOffersItsFreeCellsInOrder() {
        // Small, Medium, Small, Medium, then the tile being dragged: blocks 0 and 2 each hold one small tile.
        val sizes = listOf(Small, Medium, Small, Medium, Small)
        val targets = TileGrid.dropTargets(sizes, 4, dragged = 4)
        assertEquals(
            listOf(
                DropTarget(1, 0, 1, 1, 0), DropTarget(0, 1, 1, 1, 0), DropTarget(1, 1, 1, 1, 0),
                DropTarget(1, 2, 1, 1, 2), DropTarget(0, 3, 1, 1, 2), DropTarget(1, 3, 1, 1, 2),
            ),
            targets,
        )
    }

    @Test
    fun aBlockWithTwoSmallTilesHasTwoFreeCells() {
        val targets = TileGrid.dropTargets(listOf(Small, Small, Medium, Small), 4, dragged = 3)
        assertEquals(listOf(DropTarget(0, 1, 1, 1, 1), DropTarget(1, 1, 1, 1, 1)), targets)
    }

    @Test
    fun onlySmallTilesAreDroppedInFreeCellsAndABadIndexHasNoTargets() {
        val sizes = listOf(Small, Medium, Medium)
        assertEquals(emptyList<DropTarget>(), TileGrid.dropTargets(sizes, 4, dragged = 1))
        assertEquals(emptyList<DropTarget>(), TileGrid.dropTargets(sizes, 4, dragged = 7))
        assertEquals(emptyList<DropTarget>(), TileGrid.dropTargets(emptyList(), 4, dragged = 0))
    }

    @Test
    fun withMoreColumnsTheBlocksStayTwoByTwo() {
        // Six columns: three blocks across. The fourth tile (a medium) fills the third block, the small one the fourth.
        val sizes = listOf(Small, Medium, Medium, Small)
        val targets = TileGrid.dropTargets(sizes, 6, dragged = 3)
        assertEquals(listOf(DropTarget(1, 0, 1, 1, 0), DropTarget(0, 1, 1, 1, 0), DropTarget(1, 1, 1, 1, 0)), targets)
    }

    @Test
    fun aSmallTileDroppedThereReallyEndsUpInThatCell() {
        // The target says "after tile 0"; moving the dragged tile there must put it in the free cell that was shown.
        val entries = listOf<StartEntry>(Tile("a", Small), Tile("b", Medium), Tile("c", Small))
        val sizes = entries.map { it.size }
        val target = TileGrid.dropTargets(sizes, 4, dragged = 2).first()
        val moved = StartEntries.moveAfter(entries, "c", entries[target.after].id)
        assertEquals(listOf("a", "c", "b"), moved.map { it.id })
        val layout = TileGrid.place(moved.map { it.size }, 4)
        assertEquals(Placement(target.col, target.row, 1, 1), layout.placements[1])
        // Next to the first one, in the same block, and the medium tile did not move.
        assertEquals(Placement(0, 0, 1, 1), layout.placements[0])
        assertEquals(Placement(2, 0, 2, 2), layout.placements[2])
    }

    @Test
    fun afterTheDropTheBlockIsFullOnlyWhenFourSmallTilesAreInIt() {
        var entries = listOf<StartEntry>(Tile("a", Small), Tile("b", Medium), Tile("c", Small), Tile("d", Small), Tile("e", Small))
        // c, d and e each go next to a.
        for (id in listOf("c", "d", "e")) {
            val index = entries.indexOfFirst { it.id == id }
            val target = TileGrid.dropTargets(entries.map { it.size }, 4, index).firstOrNull() ?: break
            entries = StartEntries.moveAfter(entries, id, entries[target.after].id)
        }
        assertEquals(listOf("a", "e", "d", "c", "b").sorted(), entries.map { it.id }.sorted())
        val layout = TileGrid.place(entries.map { it.size }, 4)
        // The four small tiles share the first block, and the medium tile is after them.
        val smalls = entries.indices.filter { entries[it].size == Small }
        assertEquals(setOf(Placement(0, 0, 1, 1), Placement(1, 0, 1, 1), Placement(0, 1, 1, 1), Placement(1, 1, 1, 1)), smalls.map { layout.placements[it] }.toSet())
        assertEquals(emptyList<DropTarget>(), TileGrid.dropTargets(entries.map { it.size }, 4, smalls.last()))
    }

    // --- the empty room beside four small tiles, and other gaps before the tile

    @Test
    fun theBlockBesideFourSmallTilesBeforeAWideOneTakesAMediumTile() {
        // Four small tiles fill a block, the wide tile cannot start beside them, so that block is empty; the medium tile
        // after the wide one can go there.
        val sizes = listOf(Small, Small, Small, Small, Wide, Medium)
        assertEquals(listOf(DropTarget(2, 0, 2, 2, 3)), TileGrid.dropTargets(sizes, 4, dragged = 5))
    }

    @Test
    fun aSmallTileTakesAnyOfTheFourCellsOfThatEmptyBlock() {
        val sizes = listOf(Small, Small, Small, Small, Wide, Small)
        assertEquals(
            listOf(DropTarget(2, 0, 1, 1, 3), DropTarget(3, 0, 1, 1, 3), DropTarget(2, 1, 1, 1, 3), DropTarget(3, 1, 1, 1, 3)),
            TileGrid.dropTargets(sizes, 4, dragged = 5),
        )
    }

    @Test
    fun aTileDroppedInTheEmptyBlockLandsExactlyThereAndTheRestStaysPut() {
        val entries = listOf<StartEntry>(
            Tile("a", Small), Tile("b", Small), Tile("c", Small), Tile("d", Small), Tile("w", Wide), Tile("m", Medium),
        )
        val target = TileGrid.dropTargets(entries.map { it.size }, 4, dragged = 5).single()
        val moved = StartEntries.moveAfter(entries, "m", entries[target.after].id)
        assertEquals(listOf("a", "b", "c", "d", "m", "w"), moved.map { it.id })
        val layout = TileGrid.place(moved.map { it.size }, 4)
        assertEquals(Placement(target.col, target.row, target.cols, target.rows), layout.placements[4])
        // The wide tile did not move: it still has its own row.
        assertEquals(Placement(0, 2, 4, 2), layout.placements[5])
        assertEquals(4, layout.rows)
    }

    @Test
    fun aWideTileHasNoRoomInASingleBlock() {
        assertEquals(emptyList<DropTarget>(), TileGrid.dropTargets(listOf(Small, Small, Small, Small, Wide), 4, dragged = 4))
    }

    @Test
    fun theRoomBesideALoneMediumTileBeforeAWideOneIsAPlaceToo() {
        // Medium, then wide (which takes the next row), then another medium: the block beside the first is free.
        val sizes = listOf(Medium, Wide, Medium)
        assertEquals(listOf(DropTarget(2, 0, 2, 2, 0)), TileGrid.dropTargets(sizes, 4, dragged = 2))
    }

    @Test
    fun roomAfterTheTileIsNotOffered() {
        // The block beside the first medium tile is empty, but it is after... nothing: the tile there is the first one.
        assertEquals(emptyList<DropTarget>(), TileGrid.dropTargets(listOf(Medium, Wide), 4, dragged = 0))
        // And a tile that has nothing before it has nowhere to go.
        assertEquals(emptyList<DropTarget>(), TileGrid.dropTargets(listOf(Small, Wide, Medium), 4, dragged = 0))
    }

    @Test
    fun nothingIsOfferedWhereATileAlreadySits() {
        // Two mediums side by side, then a third below: no empty room anywhere before the last one.
        assertEquals(emptyList<DropTarget>(), TileGrid.dropTargets(listOf(Medium, Medium, Medium), 4, dragged = 2))
    }

    @Test
    fun theTilesOfAnOpenFolderStayTogetherWithIt() {
        // A small folder (index 1) open with two medium apps after it, then the tile being dragged.
        val sizes = listOf(Small, Small, Medium, Medium, Wide, Medium)
        val leader = listOf(true, true, false, false, true, true)
        val targets = TileGrid.dropTargets(sizes, 4, dragged = 5, leader = leader)
        // Whatever is offered comes after a tile that leads, never after one of the folder's apps.
        assertTrue(targets.all { leader[it.after] })
    }
}
