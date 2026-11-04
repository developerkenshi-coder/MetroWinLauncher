package com.metrowin.launcher

import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.StartPages
import com.metrowin.launcher.data.TileCodec
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPagesTest {
    private fun t(id: String, size: TileSize = TileSize.Medium) = Tile(id, size)
    private fun ids(page: List<StartEntry>) = page.map { it.id }

    private val pages = listOf(
        listOf<StartEntry>(t("a"), t("b"), Folder("f", "", TileSize.Medium, listOf("c", "d"))),
        listOf<StartEntry>(t("e")),
        emptyList(),
    )

    @Test
    fun anEntryIsFoundOnTheScreenThatHoldsIt() {
        assertEquals(0, StartPages.pageOf(pages, "a"))
        assertEquals(0, StartPages.pageOf(pages, "f"))
        assertEquals("an app inside a folder", 0, StartPages.pageOf(pages, "c"))
        assertEquals(1, StartPages.pageOf(pages, "e"))
        assertEquals(-1, StartPages.pageOf(pages, "nope"))
    }

    @Test
    fun pinningGoesToTheChosenScreenAndOnlyOnce() {
        val pinned = StartPages.pin(pages, "g", page = 2)
        assertEquals(listOf("g"), ids(pinned[2]))
        assertSame("already pinned somewhere: nothing changes", pinned, StartPages.pin(pinned, "g", page = 0))
        assertSame(pages, StartPages.pin(pages, "c", page = 2))
    }

    @Test
    fun aScreenNumberOutOfRangeIsBroughtIn() {
        assertEquals(listOf("g"), ids(StartPages.pin(pages, "g", page = 9)[2]))
        assertEquals(listOf("a", "b", "f", "g").size, StartPages.pin(pages, "g", page = -4)[0].size)
    }

    @Test
    fun unpinningFindsTheAppOnAnyScreen() {
        assertEquals(listOf("a", "f"), ids(StartPages.unpin(pages, "b")[0]))
        assertTrue(StartPages.unpin(pages, "e")[1].isEmpty())
    }

    @Test
    fun anOperationOnlyChangesTheScreenThatHoldsTheEntry() {
        val resized = StartPages.update(pages, "e") { StartEntries.setSize(it, "e", TileSize.Wide) }
        assertEquals(TileSize.Wide, resized[1].single().size)
        assertSame(pages[0], resized[0])
        assertSame("no screen holds it: nothing changes", pages, StartPages.update(pages, "nope") { emptyList() })
    }

    @Test
    fun movingToAnotherScreenAppendsThereAndLeavesTheOldOne() {
        val moved = StartPages.moveToPage(pages, "a", 1)
        assertEquals(listOf("b", "f"), ids(moved[0]))
        assertEquals(listOf("e", "a"), ids(moved[1]))
    }

    @Test
    fun aFolderMovesWithItsApps() {
        val moved = StartPages.moveToPage(pages, "f", 2)
        assertEquals(listOf("f"), ids(moved[2]))
        assertEquals(2, (moved[2].single() as Folder).children.size)
    }

    @Test
    fun movingToTheSameScreenOrOfAnAppInsideAFolderChangesNothing() {
        assertSame(pages, StartPages.moveToPage(pages, "a", 0))
        assertSame("c is inside a folder, not an entry of the screen", pages, StartPages.moveToPage(pages, "c", 1))
    }

    @Test
    fun theMainScreenFollowsTheHomeWhenTheSecondaryStartChangesSide() {
        // Two screens, home on the first: the secondary Start to the left puts the main screen second and the other first.
        val left = StartPages.movePage(pages, from = 0, to = 1)
        assertEquals(ids(pages[0]), ids(left[1]))
        assertEquals(ids(pages[1]), ids(left[0]))
        // And back to the right.
        assertEquals(pages, StartPages.movePage(left, from = 1, to = 0))
        assertSame("the same place changes nothing", pages, StartPages.movePage(pages, 0, 0))
        assertSame("a place that is not there changes nothing", pages, StartPages.movePage(pages, 0, 5))
    }

    @Test
    fun aSectionIsFromItsTitleToTheNextOne() {
        val entries = listOf<StartEntry>(
            Tile("a", TileSize.Medium), Tile("b", TileSize.Medium),
            Tile("c", TileSize.Medium, group = "Social"), Tile("d", TileSize.Medium), Folder("f", "", TileSize.Medium, listOf("x", "y")),
            Tile("e", TileSize.Medium, group = "Work"), Tile("g", TileSize.Medium),
        )
        assertEquals(setOf("c", "d", "f"), StartEntries.sectionIds(entries, "d"))
        assertEquals("the tile that carries the title", setOf("c", "d", "f"), StartEntries.sectionIds(entries, "c"))
        assertEquals("before any title", setOf("a", "b"), StartEntries.sectionIds(entries, "b"))
        assertEquals(setOf("e", "g"), StartEntries.sectionIds(entries, "g"))
        assertTrue(StartEntries.sectionIds(entries, "nope").isEmpty())
    }

    @Test
    fun severalEntriesMoveToAnotherScreenKeepingTheirOrder() {
        val moved = StartPages.moveManyToPage(pages, setOf("b", "a", "e"), target = 2)
        assertEquals(listOf("f"), ids(moved[0]))
        assertTrue(moved[1].isEmpty())
        assertEquals("in the order they had on screen, then the screen of e", listOf("a", "b", "e"), ids(moved[2]))
        assertEquals(StartPages.all(pages).map { it.id }.sorted(), StartPages.all(moved).map { it.id }.sorted())
        assertSame("nothing to move", pages, StartPages.moveManyToPage(pages, emptySet(), 1))
    }

    @Test
    fun theMainScreenIsTheHomeUnlessTheHomeIsEmptyAndAnotherIsNot() {
        assertEquals(0, StartPages.mainPage(pages, home = 0))
        assertEquals("the home is empty, the fullest screen is the one in use", 0, StartPages.mainPage(pages, home = 2))
        assertEquals(2, StartPages.mainPage(listOf(emptyList(), emptyList(), emptyList<StartEntry>()), home = 2))
    }

    @Test
    fun resizingTheNumberOfScreensKeepsEveryEntry() {
        val fewer = StartPages.resize(pages, 1)
        assertEquals(1, fewer.size)
        assertEquals(listOf("a", "b", "f", "e"), ids(fewer[0]))
        val more = StartPages.resize(fewer, 3)
        assertEquals(3, more.size)
        assertTrue(more[1].isEmpty() && more[2].isEmpty())
        assertEquals(StartPages.all(pages).map { it.id }.sorted(), StartPages.all(fewer).map { it.id }.sorted())
    }

    @Test
    fun pruningWorksOnEveryScreen() {
        val pruned = StartPages.prune(pages, known = setOf("a", "c", "d"))
        assertEquals(listOf("a", "f"), ids(pruned[0]))
        assertTrue(pruned[1].isEmpty())
    }

    @Test
    fun pinnedIdsCountTheAppsInsideFolders() {
        assertEquals(setOf("a", "b", "f", "c", "d", "e"), StartPages.pinnedIds(pages))
    }

    // --- sizes and styles

    @Test
    fun theSizeCanBeSetDirectly() {
        val entries = listOf<StartEntry>(t("a"), Folder("f", "", TileSize.Medium, listOf("c", "d")))
        assertEquals(TileSize.Small, StartEntries.setSize(entries, "a", TileSize.Small)[0].size)
        assertEquals(TileSize.Wide, StartEntries.setSize(entries, "f", TileSize.Wide)[1].size)
    }

    @Test
    fun theStyleIsOnlyForTiles() {
        val entries = listOf<StartEntry>(t("a"), Folder("f", "", TileSize.Medium, listOf("c", "d")))
        assertEquals(TileStyle.Iconic, (StartEntries.setStyle(entries, "a", TileStyle.Iconic)[0] as Tile).style)
        assertEquals(entries[1], StartEntries.setStyle(entries, "f", TileStyle.Cycle)[1])
    }

    @Test
    fun resizingKeepsTheStyle() {
        val entries = listOf<StartEntry>(Tile("a", TileSize.Medium, TileStyle.Iconic))
        assertEquals(TileStyle.Iconic, (StartEntries.resize(entries, "a")[0] as Tile).style)
    }

    // --- saving

    @Test
    fun theStyleSurvivesSavingAndAStandardTileStaysLikeVersionOne() {
        val entries = listOf<StartEntry>(Tile("a", TileSize.Wide, TileStyle.Cycle), Tile("b", TileSize.Small))
        val json = TileCodec.encode(entries)
        assertEquals(entries, TileCodec.decode(json))
        assertFalse("a standard tile writes no style", json.substringAfter("\"b\"").contains("style"))
    }

    @Test
    fun severalScreensSurviveSaving() {
        assertEquals(pages, TileCodec.decodePages(TileCodec.encodePages(pages)))
    }

    @Test
    fun aSingleListSavedByAnOlderVersionIsTheFirstScreen() {
        val old = TileCodec.encode(pages[0])
        assertEquals(listOf(pages[0]), TileCodec.decodePages(old))
    }

    @Test
    fun brokenDataIsRefused() {
        assertNull(TileCodec.decodePages("{nope"))
        assertNotNull(TileCodec.decodePages("[]"))
    }

    // --- an app into a folder

    @Test
    fun anAppThatIsPinnedAsATileMovesIntoTheFolder() {
        val result = StartPages.addToFolder(pages, "e", "f")
        assertEquals(listOf("c", "d", "e"), (result[0].last() as Folder).children)
        assertTrue("the tile it was is gone", result[1].isEmpty())
    }

    @Test
    fun anAppThatIsNotOnStartIsPinnedInsideTheFolder() {
        val result = StartPages.addToFolder(pages, "z", "f")
        assertEquals(listOf("c", "d", "z"), (result[0].last() as Folder).children)
        assertEquals(StartPages.pinnedIds(pages) + "z", StartPages.pinnedIds(result))
    }

    @Test
    fun anAppFromAnotherFolderLeavesIt() {
        val two = listOf<List<StartEntry>>(
            listOf(Folder("f", "", TileSize.Medium, listOf("c", "d")), Folder("g", "", TileSize.Medium, listOf("p", "q", "r"))),
        )
        val result = StartPages.addToFolder(two, "q", "f")
        val g = result[0].first { it.id == "g" } as Folder
        assertEquals(listOf("p", "r"), g.children)
        assertEquals(listOf("c", "d", "q"), (result[0].first { it.id == "f" } as Folder).children)
    }

    @Test
    fun theFolderItLeavesBecomesATileWhenOnlyOneAppIsLeftInIt() {
        val result = StartPages.addToFolder(pages, "c", "g")  // there is no folder g
        assertSame(pages, result)
        val two = listOf<List<StartEntry>>(listOf(Folder("f", "", TileSize.Medium, listOf("a", "b")), Folder("g", "", TileSize.Medium, listOf("x", "y"))))
        val moved = StartPages.addToFolder(two, "a", "g")
        assertEquals(listOf("b", "g"), ids(moved[0]))
        assertTrue(moved[0].first() is Tile)
    }

    @Test
    fun anAppAlreadyInTheFolderOrAMissingFolderChangeNothing() {
        assertSame(pages, StartPages.addToFolder(pages, "c", "f"))
        assertSame(pages, StartPages.addToFolder(pages, "a", "nope"))
        // A tile is not a folder.
        assertSame(pages, StartPages.addToFolder(pages, "a", "b"))
    }

    @Test
    fun theFolderStaysOnItsScreenWhateverScreenTheAppCameFrom() {
        val result = StartPages.addToFolder(pages, "e", "f")
        assertEquals(StartPages.pageOf(pages, "f"), StartPages.pageOf(result, "f"))
        assertEquals(0, StartPages.pageOf(result, "e"))
    }
}
