package com.metrowin.launcher

import com.metrowin.launcher.data.FirstLook
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SearchOperators
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.InfoShare
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.settings.SettingToggles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The medium batch M8: the tile opened last goes first, the first-run looks, the tile code, the switch put back, the operator for texts. */
class EighthMediumTest {
    private fun tile(id: String, group: String = "") = Tile(id, TileSize.Medium, group = group)

    @Test fun openedTileGoesToTheFrontOfItsGroupAndTheTitleStays() {
        val entries = listOf(tile("a", "Work"), tile("b"), tile("c"), tile("d", "Play"), tile("e"))
        val result = StartEntries.toFront(entries, "c")
        assertEquals(listOf("c", "a", "b", "d", "e"), result.map { it.id })
        assertEquals(listOf("Work", "", "", "Play", ""), result.map { it.group })
    }

    @Test fun aTileThatLeadsOrIsPlacedByHandDoesNotMove() {
        val entries = listOf(tile("a", "Work"), tile("b"))
        assertEquals(entries, StartEntries.toFront(entries, "a"))
        val placed = listOf(tile("a"), Tile("b", TileSize.Medium, col = 2, row = 0))
        assertEquals(placed, StartEntries.toFront(placed, "b"))
        assertEquals(entries, StartEntries.toFront(entries, "zzz"))
    }

    @Test fun theThreeLooksSetTheirStyle() {
        val base = LauncherSettings(themeStyle = ThemeStyle.Glass)
        assertEquals(ThemeStyle.Classic, FirstLook.Wp.apply(base).themeStyle)
        assertEquals(ThemeStyle.Glass, FirstLook.Glass.apply(LauncherSettings()).themeStyle)
        assertEquals(ThemeStyle.Droid, FirstLook.Droid.apply(LauncherSettings()).themeStyle)
    }

    @Test fun aTileCodeRoundTripsAndKeepsNothingOfThisPhone() {
        val config = InfoConfig(parts = listOf(InfoPart.entries.first()), picked = listOf("content://x"), album = "9", homeZone = "Rome", note = "hi")
        val back = InfoShare.fromCode(InfoShare.toCode(config))
        assertNotNull(back)
        assertEquals(config.parts, back!!.parts)
        assertEquals("hi", back.note)
        assertTrue(back.picked.isEmpty() && back.album.isEmpty() && back.homeZone.isEmpty())
    }

    @Test fun textThatIsNotACodeIsNotATile() {
        assertNull(InfoShare.fromCode("hello"))
        assertNull(InfoShare.fromCode(InfoShare.PREFIX))
    }

    @Test fun oneSwitchGoesBackToItsStartValueAlone() {
        val changed = LauncherSettings(searchMessages = true, startAutoOrder = true)
        assertTrue(SettingToggles.changed("search.messages", changed))
        assertEquals(false, SettingToggles.factory("search.messages"))
        val reset = SettingToggles.reset("search.messages", changed)
        assertFalse(reset.searchMessages)
        assertTrue(reset.startAutoOrder)
        assertFalse(SettingToggles.changed("search.messages", reset))
        assertFalse(SettingToggles.changed("nope", reset))
    }

    @Test fun theMessagesOperatorNarrowsTheSearch() {
        val parsed = SearchOperators.parse("sms: dinner")
        assertEquals(SearchOperators.Scope.Messages, parsed.scope)
        assertEquals("dinner", parsed.text)
    }
}
