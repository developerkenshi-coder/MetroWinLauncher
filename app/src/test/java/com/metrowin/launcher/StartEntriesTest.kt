package com.metrowin.launcher

import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.TileCodec
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartEntriesTest {
    private fun tile(id: String, size: TileSize = TileSize.Medium) = Tile(id, size)
    private fun folder(id: String, vararg children: String, name: String = "") =
        Folder(id, name, TileSize.Medium, children.toList())

    private fun ids(entries: List<StartEntry>) = entries.map { it.id }

    @Test
    fun pinAppendsAMediumTileOnce() {
        val pinned = StartEntries.pin(listOf(tile("a")), "b")
        assertEquals(listOf(tile("a"), tile("b")), pinned)
        assertEquals(pinned, StartEntries.pin(pinned, "b"))
    }

    @Test
    fun anAppInsideAFolderCountsAsPinned() {
        val entries = listOf(folder("f", "a", "b"), tile("c"))
        assertTrue(StartEntries.isPinned(entries, "a"))
        assertTrue(StartEntries.isPinned(entries, "c"))
        assertTrue(StartEntries.isPinned(entries, "f"))
        assertFalse(StartEntries.isPinned(entries, "z"))
        // Pinning something that is already in a folder does not add a second tile.
        assertEquals(entries, StartEntries.pin(entries, "a"))
    }

    @Test
    fun resizeCyclesSmallMediumWide() {
        var entries: List<StartEntry> = listOf(tile("a", TileSize.Small), folder("f", "x", "y"))
        entries = StartEntries.resize(entries, "a")
        assertEquals(TileSize.Medium, entries[0].size)
        entries = StartEntries.resize(entries, "a")
        assertEquals(TileSize.Wide, entries[0].size)
        entries = StartEntries.resize(entries, "a")
        assertEquals(TileSize.Large, entries[0].size)
        entries = StartEntries.resize(entries, "a")
        assertEquals(TileSize.Small, entries[0].size)
        assertEquals(TileSize.Wide, StartEntries.resize(entries, "f")[1].size)
        // With the Large size off the arrow goes from Wide back to Small.
        assertEquals(TileSize.Small, StartEntries.resize(listOf(tile("w", TileSize.Wide)), "w", allowLarge = false)[0].size)
    }

    @Test
    fun moveToTakesTheSlotOfTheTarget() {
        val entries = listOf(tile("a"), tile("b"), tile("c"), tile("d"))
        assertEquals(listOf("b", "c", "a", "d"), ids(StartEntries.moveTo(entries, "a", "c")))
        assertEquals(listOf("d", "a", "b", "c"), ids(StartEntries.moveTo(entries, "d", "a")))
        assertEquals(entries, StartEntries.moveTo(entries, "a", "a"))
        assertEquals(entries, StartEntries.moveTo(entries, "a", "missing"))
    }

    @Test
    fun droppingATileOnAnotherMakesAFolderWhereTheTargetWas() {
        val entries = listOf(tile("a"), tile("b"), tile("c"))
        val merged = StartEntries.merge(entries, draggedId = "a", ontoId = "c") { "folder-1" }
        assertEquals(listOf("b", "folder-1"), ids(merged))
        assertEquals(listOf("c", "a"), (merged[1] as Folder).children)
    }

    @Test
    fun droppingATileOnAFolderAddsItToTheFolder() {
        val entries = listOf(tile("a"), folder("f", "x", "y"))
        val merged = StartEntries.merge(entries, draggedId = "a", ontoId = "f")
        assertEquals(listOf("f"), ids(merged))
        assertEquals(listOf("x", "y", "a"), (merged.single() as Folder).children)
    }

    @Test
    fun aFolderCannotGoIntoAFolder() {
        val entries = listOf(folder("f", "x", "y"), folder("g", "p", "q"))
        assertEquals(entries, StartEntries.merge(entries, draggedId = "f", ontoId = "g"))
    }

    @Test
    fun takingAnAppOutOfAFolderPutsItRightAfterIt() {
        val entries = listOf(folder("f", "a", "b", "c"), tile("z"))
        val result = StartEntries.removeFromFolder(entries, "f", "b")
        assertEquals(listOf("f", "b", "z"), ids(result))
        assertEquals(listOf("a", "c"), (result[0] as Folder).children)
    }

    @Test
    fun aFolderLeftWithOneAppBecomesThatAppsTile() {
        val result = StartEntries.removeFromFolder(listOf(folder("f", "a", "b")), "f", "a")
        assertEquals(listOf(tile("b"), tile("a")), result)
    }

    @Test
    fun releasingAFolderLeavesItsTilesInPlace() {
        val result = StartEntries.release(listOf(tile("z"), folder("f", "a", "b"), tile("y")), "f")
        assertEquals(listOf("z", "a", "b", "y"), ids(result))
    }

    @Test
    fun unpinRemovesFromAFolderOrWholeFolders() {
        val entries = listOf(folder("f", "a", "b", "c"), tile("z"))
        assertEquals(listOf("f", "z"), ids(StartEntries.unpin(entries, "a")))
        assertEquals(listOf("z"), ids(StartEntries.unpin(entries, "f")))
        assertEquals(listOf("b", "z"), ids(StartEntries.unpin(listOf(folder("f", "a", "b"), tile("z")), "a")))
    }

    @Test
    fun renameTrimsTheName() {
        val renamed = StartEntries.rename(listOf(folder("f", "a", "b")), "f", "  Games ")
        assertEquals("Games", (renamed.single() as Folder).name)
    }

    @Test
    fun pruneDropsUninstalledAppsAndDissolvesTinyFolders() {
        val entries = listOf(tile("a"), tile("gone"), folder("f", "x", "gone2", "y"), folder("g", "p", "gone3"), folder("h", "gone4", "gone5"))
        val pruned = StartEntries.prune(entries, setOf("a", "x", "y", "p"))
        assertEquals(listOf("a", "f", "p"), ids(pruned))
        assertEquals(listOf("x", "y"), (pruned[1] as Folder).children)
    }

    @Test
    fun codecKeepsFoldersAndStillReadsTheOldTilesOnlyFormat() {
        val entries = listOf(tile("a", TileSize.Wide), folder("f", "x", "y", name = "Work"))
        assertEquals(entries, TileCodec.decode(TileCodec.encode(entries)))
        val old = """[{"id":"stock:phone","size":"m"},{"id":"stock:photos","size":"w"}]"""
        assertEquals(listOf(tile("stock:phone"), tile("stock:photos", TileSize.Wide)), TileCodec.decode(old))
    }

    // --- several tiles into one folder

    @Test
    fun severalTilesMakeOneFolderWhereTheFirstWas() {
        val entries = listOf(tile("a"), tile("b"), tile("c"), tile("d"), tile("e"))
        val grouped = StartEntries.group(entries, setOf("d", "b", "e")) { "f1" }
        // The order of Start decides, not the order of the picking: b, d, e.
        assertEquals(listOf("a", "f1", "c"), ids(grouped))
        assertEquals(listOf("b", "d", "e"), (grouped[1] as Folder).children)
    }

    @Test
    fun theNewFolderIsAMediumTileWithNoNameYet() {
        val grouped = StartEntries.group(listOf(tile("a", TileSize.Wide), tile("b", TileSize.Small)), setOf("a", "b")) { "f" }
        val made = grouped.single() as Folder
        assertEquals(TileSize.Medium, made.size)
        assertEquals("", made.name)
    }

    @Test
    fun aLoneTileOrNothingChangesNothing() {
        val entries = listOf(tile("a"), tile("b"))
        assertEquals(entries, StartEntries.group(entries, setOf("a")))
        assertEquals(entries, StartEntries.group(entries, emptySet()))
        assertEquals(entries, StartEntries.group(entries, setOf("zzz", "yyy")))
    }

    @Test
    fun tilesPickedWithAFolderJoinThatFolder() {
        val entries = listOf(tile("a"), folder("f", "x", "y"), tile("b"), tile("c"))
        val grouped = StartEntries.group(entries, setOf("a", "f", "c"))
        assertEquals(listOf("f", "b"), ids(grouped))
        assertEquals(listOf("x", "y", "a", "c"), (grouped[0] as Folder).children)
    }

    @Test
    fun aFolderOnItsOrTwoFoldersChangeNothing() {
        val entries = listOf(folder("f", "x", "y"), folder("g", "p", "q"), tile("a"))
        assertEquals(entries, StartEntries.group(entries, setOf("f")))
        assertEquals(entries, StartEntries.group(entries, setOf("f", "g", "a")))
    }

    @Test
    fun groupingKeepsTheTileOrderOfEverythingElse() {
        val entries = listOf(tile("a"), folder("f", "x", "y"), tile("b"), tile("c"), tile("d"))
        assertEquals(listOf("a", "f", "g", "d"), ids(StartEntries.group(entries, setOf("b", "c")) { "g" }))
    }

    // --- moveAfter

    @Test
    fun moveAfterPutsATileRightAfterAnother() {
        val entries = listOf(tile("a"), tile("b"), tile("c"), tile("d"))
        assertEquals(listOf("a", "c", "b", "d"), ids(StartEntries.moveAfter(entries, "c", "a")))
        assertEquals(listOf("b", "c", "d", "a"), ids(StartEntries.moveAfter(entries, "a", "c").let { StartEntries.moveAfter(it, "a", "d") }))
        assertEquals(listOf("a", "b", "d", "c"), ids(StartEntries.moveAfter(entries, "c", "d")))
    }

    @Test
    fun moveAfterChangesNothingForTheSameTileAMissingOneOrWhenItIsThereAlready() {
        val entries = listOf(tile("a"), tile("b"), tile("c"))
        assertEquals(entries, StartEntries.moveAfter(entries, "a", "a"))
        assertEquals(entries, StartEntries.moveAfter(entries, "x", "a"))
        assertEquals(entries, StartEntries.moveAfter(entries, "a", "x"))
        assertEquals("already right after it", entries, StartEntries.moveAfter(entries, "b", "a"))
    }

    @Test
    fun moveAfterKeepsFoldersAndSizes() {
        val entries = listOf(tile("a", TileSize.Small), folder("f", "x", "y"), tile("c", TileSize.Wide))
        val moved = StartEntries.moveAfter(entries, "c", "a")
        assertEquals(listOf("a", "c", "f"), ids(moved))
        assertEquals(TileSize.Wide, moved[1].size)
        assertEquals(entries[1], moved[2])
    }
}
