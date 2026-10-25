package com.metrowin.launcher

import com.metrowin.launcher.data.DominantColor
import com.metrowin.launcher.ui.theme.MetroAccents
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DominantColorTest {
    private val accents = MetroAccents.map { it.toArgb() }

    private fun solid(argb: Int, count: Int = 400) = IntArray(count) { argb }

    private fun mix(vararg parts: Pair<Int, Int>): IntArray = parts.flatMap { (argb, n) -> List(n) { argb } }.toIntArray()

    @Test
    fun everyAccentColourIsItsOwnClosestAccent() {
        accents.forEachIndexed { index, argb -> assertEquals("accent #$index", index, DominantColor.nearestIndex(argb, accents)) }
    }

    @Test
    fun aSolidPictureGivesItsColour() {
        val red = DominantColor.of(solid(0xFFE51400.toInt()))!!
        assertEquals(accents.indexOf(0xFFE51400.toInt()), DominantColor.nearestIndex(red, accents))
        val blue = DominantColor.of(solid(0xFF0050EF.toInt()))!!
        assertEquals(accents.indexOf(0xFF0050EF.toInt()), DominantColor.nearestIndex(blue, accents))
    }

    @Test
    fun aMostlyGreyPictureTakesTheColourOfTheLittleThatIsColoured() {
        val pixels = mix(0xFF808080.toInt() to 700, 0xFF60A917.toInt() to 100)
        val dominant = DominantColor.of(pixels)!!
        assertEquals(accents.indexOf(0xFF60A917.toInt()), DominantColor.nearestIndex(dominant, accents))
    }

    @Test
    fun theHeaviestHueWins() {
        val pixels = mix(0xFFE51400.toInt() to 100, 0xFF0050EF.toInt() to 300)
        val dominant = DominantColor.of(pixels)!!
        assertEquals(accents.indexOf(0xFF0050EF.toInt()), DominantColor.nearestIndex(dominant, accents))
    }

    @Test
    fun greyBlackAndWhitePicturesHaveNoColour() {
        assertNull(DominantColor.of(solid(0xFF808080.toInt())))
        assertNull(DominantColor.of(solid(0xFF000000.toInt())))
        assertNull(DominantColor.of(solid(0xFFFFFFFF.toInt())))
        assertNull(DominantColor.of(IntArray(0)))
    }

    @Test
    fun aFewStrayPixelsDoNotMakeATheme() {
        val pixels = mix(0xFF808080.toInt() to 2000, 0xFFE51400.toInt() to 5)
        assertNull(DominantColor.of(pixels))
    }

    @Test
    fun transparentPixelsAreIgnored() {
        val pixels = mix(0x00E51400 to 900, 0xFF0050EF.toInt() to 100)
        val dominant = DominantColor.of(pixels)!!
        assertEquals(accents.indexOf(0xFF0050EF.toInt()), DominantColor.nearestIndex(dominant, accents))
    }

    @Test
    fun aDarkMurkyColourIsNotPreferredOverABrightOne() {
        // Lots of very dark red, a little bright yellow: the bright one is what the eye sees.
        val pixels = mix(0xFF300000.toInt() to 300, 0xFFFFD000.toInt() to 300)
        val dominant = DominantColor.of(pixels)
        assertNotNull(dominant)
        val r = (dominant!! shr 16) and 0xFF
        val g = (dominant shr 8) and 0xFF
        assertTrue("the yellow wins ($r, $g)", g > 150)
    }
}
