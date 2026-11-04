package com.metrowin.launcher

import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.NavButtons
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.TileGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartColumnsNavTest {
    @Test
    fun withoutACustomCountTheColumnsAreWhatTheyWere() {
        assertEquals(4, TileGrid.columnsFor(360f, false))
        assertEquals(6, TileGrid.columnsFor(360f, true))
    }

    @Test
    fun aCustomCountReplacesThemButNeverGoesBeyondWhatTheScreenHolds() {
        assertEquals(6, TileGrid.columnsFor(360f, false, custom = 6))
        assertEquals(4, TileGrid.columnsFor(360f, true, custom = 4))
        val max = TileGrid.customMax(360f)
        assertEquals("a count too big is cut to what fits", max, TileGrid.columnsFor(360f, false, custom = 12))
        assertTrue("the tiles stay big enough to touch", TileGrid.unitDp(360f, max) >= 44f)
        assertEquals("a very narrow screen keeps the four of the default", 4, TileGrid.columnsFor(200f, false, custom = 12))
    }

    @Test
    fun theChoicesStartWithAutomaticAndStayEven() {
        val choices = TileGrid.customChoices(360f)
        assertEquals(0, choices.first())
        assertTrue(choices.drop(1).all { it % 2 == 0 && it >= 4 })
        assertTrue("a wider screen offers more", TileGrid.customChoices(800f).size > choices.size)
    }

    @Test
    fun theSettingsKeepTheNewValuesAndCutBadOnes() {
        val s = SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(tileColumns = 6, freeLayout = true, navSpacing = 70)), LauncherSettings())!!
        assertEquals(6, s.tileColumns)
        assertTrue(s.freeLayout)
        assertEquals(70, s.navSpacing)
        val odd = SettingsCodec.decode("""{"tileColumns":7,"navSpacing":500}""", LauncherSettings())!!
        assertEquals("an odd count is made even", 6, odd.tileColumns)
        assertEquals(LauncherSettings.NAV_SPACING_MAX, odd.navSpacing)
        assertEquals("a file written before has none of them", LauncherSettings(), SettingsCodec.decode("{}", LauncherSettings())!!)
    }

    @Test
    fun theButtonsOfTheBarAreAThirdApartByDefaultAndCloserWhenAsked() {
        assertEquals(listOf(60f, 180f, 300f), NavButtons.centers(360f, 100).toList().map { Math.round(it).toFloat() })
        val close = NavButtons.centers(360f, 50)
        assertEquals(120f, close[0], 0.01f)
        assertEquals(240f, close[2], 0.01f)
    }

    @Test
    fun aTouchHitsTheNearestButtonAndTheDefaultKeepsTheThirds() {
        for (x in listOf(5f, 119f, 121f, 241f, 355f)) assertEquals((x / 120f).toInt().coerceIn(0, 2), NavButtons.nearest(x, 360f, 100))
        assertEquals("the nearest centre wins", 1, NavButtons.nearest(160f, 360f, 50))
        assertEquals(0, NavButtons.nearest(10f, 360f, 50))
    }
}
