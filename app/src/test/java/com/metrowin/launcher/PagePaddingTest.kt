package com.metrowin.launcher

import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.PagePadding
import com.metrowin.launcher.data.SectionReset
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.TileGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePaddingTest {
    private val widths = (240..1600 step 20).map { it.toFloat() }

    @Test
    fun theStepsGoFromNarrowToWideAroundTheDesign() {
        assertEquals(6f, PagePadding.Compact.sideDp(12f, 360f), 0f)
        assertEquals(12f, PagePadding.Normal.sideDp(12f, 360f), 0f)
        assertEquals(18f, PagePadding.Wide.sideDp(12f, 360f), 0f)
    }

    @Test
    fun noStepLeavesAPageTooNarrowOrWithNoMarginAtAll() {
        for (width in widths) for (base in listOf(0f, 12f, 24f)) {
            for (pad in PagePadding.entries) {
                val side = pad.sideDp(base, width)
                assertTrue("$pad $base on $width: $side", side >= minOf(PagePadding.MIN_SIDE_DP, base))
                // The content keeps its room, unless the design itself leaves less.
                val room = width - 2 * side
                assertTrue("$pad $base on $width: $room", room >= minOf(PagePadding.MIN_CONTENT_DP, width - 2 * base))
            }
            assertTrue(PagePadding.Compact.sideDp(base, width) <= PagePadding.Normal.sideDp(base, width))
            assertTrue(PagePadding.Normal.sideDp(base, width) <= PagePadding.Wide.sideDp(base, width))
        }
        // Even when the window is smaller than it should ever be (split screen), wide does not go past the design.
        assertEquals(12f, PagePadding.Wide.sideDp(12f, 200f), 0f)
    }

    @Test
    fun theTilesOfStartKeepFillingTheWidthWhateverTheStep() {
        for (width in widths.filter { it >= 320f }) for (more in listOf(false, true)) for (pad in PagePadding.entries) {
            val sides = TileGrid.sidesFor(pad, width)
            val columns = TileGrid.columnsFor(width, more, sides)
            assertEquals("$pad $width", 0, columns % 2)
            assertTrue("$pad $width: $columns columns", columns in 4..12)
            val unit = TileGrid.unitDp(width, columns, sidesDp = sides)
            // The tiles and the gaps add up to the width the pages leave, and a tile is still big enough to touch.
            assertEquals(width - sides, unit * columns + 6f * (columns - 1), 0.01f)
            assertTrue("$pad $width more=$more: unit $unit", unit >= 40f)
            // Normal is what the grid always was.
            if (pad == PagePadding.Normal) assertEquals(TileGrid.columnsFor(width, more), columns)
        }
        assertEquals(24f, TileGrid.sidesFor(PagePadding.Normal, 360f), 0f)
    }

    @Test
    fun theSettingComesBackWithTheCodecAndAnOddCodeIsNormal() {
        for (pad in PagePadding.entries) {
            assertEquals(pad, SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(pagePadding = pad)))!!.pagePadding)
            assertEquals(pad, PagePadding.fromCode(pad.code))
        }
        assertEquals(PagePadding.Normal, PagePadding.fromCode("huge"))
        assertEquals(PagePadding.Normal, PagePadding.fromCode(null))
    }
}
