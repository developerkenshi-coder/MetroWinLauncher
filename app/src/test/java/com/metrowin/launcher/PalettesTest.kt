package com.metrowin.launcher

import androidx.compose.ui.graphics.Color
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.theme.ClassicStyle
import com.metrowin.launcher.ui.theme.ColorMath
import com.metrowin.launcher.ui.theme.DarkColors
import com.metrowin.launcher.ui.theme.DroidStyle
import com.metrowin.launcher.ui.theme.GlassStyle
import com.metrowin.launcher.ui.theme.LightColors
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.Palettes
import com.metrowin.launcher.ui.theme.SystemShades
import com.metrowin.launcher.ui.theme.Themes
import com.metrowin.launcher.ui.theme.styleOf
import com.metrowin.launcher.ui.theme.tileColor
import com.metrowin.launcher.ui.theme.tileInk
import com.metrowin.launcher.ui.theme.toArgbInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class PalettesTest {
    /** WCAG contrast ratio of two colors: 1 (the same) to 21 (black on white). Text needs 4.5. */
    private fun contrast(a: Color, b: Color): Float {
        fun lum(c: Color): Float {
            fun channel(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
            return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
        }
        val (hi, lo) = lum(a).coerceAtLeast(lum(b)) to lum(a).coerceAtMost(lum(b))
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun hsvRoundTripsThroughArgb() {
        listOf(0xFF1BA1E2.toInt(), 0xFFA20025.toInt(), 0xFF808080.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt()).forEach { argb ->
            val hsv = ColorMath.argbToHsv(argb)
            val back = ColorMath.hsvToArgb(hsv[0], hsv[1], hsv[2])
            assertEquals(argb ushr 24, back ushr 24)
            for (shift in listOf(16, 8, 0)) assertTrue(abs(((argb shr shift) and 0xFF) - ((back shr shift) and 0xFF)) <= 1)
        }
    }

    @Test
    fun hsvColorsAreOpaqueAndStayInRange() {
        for (hue in listOf(-30f, 0f, 120f, 359f, 725f)) {
            val argb = ColorMath.hsvToArgb(hue, 1f, 1f)
            assertEquals(0xFF, argb ushr 24)
        }
        assertEquals(0xFF000000.toInt(), ColorMath.hsvToArgb(200f, 0.5f, 0f))
        assertEquals(0xFFFFFFFF.toInt(), ColorMath.hsvToArgb(200f, 0f, 1f))
    }

    @Test
    fun turningTheHueKeepsStrengthAndBrightness() {
        val turned = ColorMath.argbToHsv(ColorMath.rotateHue(Color(0xFF1BA1E2), 90f).toArgbInt())
        val original = ColorMath.argbToHsv(0xFF1BA1E2.toInt())
        assertEquals(original[1], turned[1], 0.02f)
        assertEquals(original[2], turned[2], 0.02f)
        assertEquals((original[0] + 90f) % 360f, turned[0], 1.5f)
    }

    @Test
    fun theSystemShadesAreTheNearestToTheMaterialTone() {
        assertEquals(1000, SystemShades.forTone(0))
        assertEquals(0, SystemShades.forTone(100))
        assertEquals(100, SystemShades.forTone(90))
        assertEquals(600, SystemShades.forTone(40))
        assertEquals(10, SystemShades.forTone(98))
        assertEquals(200, SystemShades.forTone(80))
    }

    @Test
    fun aToneHasTheSamePerceivedLightnessForEveryHue() {
        // Yellow and blue of the same tone must look equally light, which is what keeps the contrast the same.
        fun lum(c: Color): Float {
            fun channel(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
            return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
        }
        val yellow = Palettes.fromSeed(Color(0xFFD8C100)).accent1.tone(50)
        val blue = Palettes.fromSeed(Color(0xFF0050EF)).accent1.tone(50)
        // L* 50 is a luminance of about 0.184.
        assertEquals(0.184f, lum(yellow), 0.01f)
        assertEquals(0.184f, lum(blue), 0.01f)
    }

    @Test
    fun aSeedRampGoesFromDarkToLight() {
        val ramp = Palettes.fromSeed(Color(0xFF1BA1E2)).accent1
        assertTrue(ramp.tone(0).red + ramp.tone(0).green + ramp.tone(0).blue < 0.1f)
        assertTrue(ramp.tone(100).red + ramp.tone(100).green + ramp.tone(100).blue > 2.9f)
        for (t in 0 until 100 step 10) {
            val a = ramp.tone(t)
            val b = ramp.tone(t + 10)
            assertTrue("tone $t should be darker than ${t + 10}", ColorMath.luminance(a.toArgbInt()) <= ColorMath.luminance(b.toArgbInt()))
        }
    }

    @Test
    fun textReadsOnEverySurfaceOfModernDroidForEveryAccent() {
        MetroAccents.forEach { seed ->
            for (dark in listOf(true, false)) {
                val palette = Palettes.fromSeed(seed)
                val colors = Palettes.colors(palette, dark)
                val label = "accent ${Integer.toHexString(seed.toArgbInt())} dark=$dark"
                assertTrue("$label: text on the background", contrast(colors.foreground, colors.background) >= 7f)
                assertTrue("$label: text on a menu", contrast(colors.foreground, colors.surface) >= 7f)
                assertTrue("$label: secondary text on the background", contrast(colors.subtle, colors.background) >= 4.5f)
                colors.containers.indices.forEach { i ->
                    assertTrue("$label: text on tile $i", contrast(colors.tileInk(i), colors.tileColor(i, seed)) >= 4.5f)
                }
                val main = Palettes.accent(palette, dark)
                assertTrue("$label: the main color is visible on the background", contrast(main, colors.background) >= 3f)
            }
        }
    }

    @Test
    fun modernDroidTilesTakeTheContainersInTurn() {
        val colors = Palettes.colors(Palettes.fromSeed(Color(0xFF0050EF)), dark = true)
        assertEquals(3, colors.containers.size)
        assertEquals(colors.tileColor(0, Color.Red), colors.tileColor(3, Color.Red))
        assertEquals(colors.containers[1], colors.tileColor(4, Color.Red))
        assertTrue(colors.tileColor(0, Color.Red) != colors.tileColor(1, Color.Red))
    }

    @Test
    fun theOtherLooksPaintEveryTileInTheAccent() {
        val accent = Color(0xFFD80073)
        listOf(DarkColors, LightColors, Themes.resolve(ThemeStyle.Glass, true, accent, null).colors, Themes.resolve(ThemeStyle.Glass, false, accent, null).colors)
            .forEach { colors ->
                assertEquals(accent, colors.tileColor(0, accent))
                assertEquals(accent, colors.tileColor(7, accent))
                assertEquals(colors.tileContent, colors.tileInk(7))
            }
    }

    @Test
    fun theTextOnGlassReadsOnTheBackdropInTheDarkAndTheLight() {
        val dark = Themes.resolve(ThemeStyle.Glass, true, Color(0xFF1BA1E2), null).colors
        val light = Themes.resolve(ThemeStyle.Glass, false, Color(0xFF1BA1E2), null).colors
        assertTrue(contrast(dark.foreground, dark.background) >= 7f)
        assertTrue(contrast(light.foreground, light.background) >= 7f)
        assertTrue(contrast(dark.subtle, dark.background) >= 4.5f)
        assertTrue(contrast(light.subtle, light.background) >= 4.5f)
    }

    @Test
    fun resolvingALookGivesItsStyleAndModernDroidMakesItsOwnMainColor() {
        val accent = Color(0xFF6A00FF)
        assertSame(ClassicStyle, Themes.resolve(ThemeStyle.Classic, true, accent, null).style)
        assertSame(GlassStyle, Themes.resolve(ThemeStyle.Glass, true, accent, null).style)
        assertSame(DroidStyle, Themes.resolve(ThemeStyle.Droid, true, accent, null).style)
        assertEquals(accent, Themes.resolve(ThemeStyle.Classic, true, accent, null).accent)
        assertEquals(accent, Themes.resolve(ThemeStyle.Glass, false, accent, null).accent)
        // Modern Droid does not use the accent as it is: it takes the tone of its ramp that suits the surface.
        val droid = Themes.resolve(ThemeStyle.Droid, true, accent, null)
        assertTrue(droid.accent != accent)
        assertEquals(Palettes.accent(Palettes.fromSeed(accent), true), droid.accent)
    }

    @Test
    fun everyLookHasItsStyle() {
        ThemeStyle.entries.forEach { assertEquals(it, styleOf(it).kind) }
        assertTrue(ClassicStyle.isClassic)
        assertTrue(!GlassStyle.isClassic && !DroidStyle.isClassic)
    }

    @Test
    fun classicKeepsTheLightTypeAndTheOthersDoNot() {
        assertEquals(androidx.compose.ui.text.font.FontWeight.Light, ClassicStyle.weight(androidx.compose.ui.text.font.FontWeight.Light))
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, GlassStyle.weight(androidx.compose.ui.text.font.FontWeight.Light))
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, DroidStyle.weight(androidx.compose.ui.text.font.FontWeight.Light))
    }
}
