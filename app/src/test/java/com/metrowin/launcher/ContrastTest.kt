package com.metrowin.launcher

import androidx.compose.ui.graphics.Color
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.theme.ColorMath
import com.metrowin.launcher.ui.theme.Danger
import com.metrowin.launcher.ui.theme.DarkColors
import com.metrowin.launcher.ui.theme.LightColors
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.MetroColors
import com.metrowin.launcher.ui.theme.Palettes
import com.metrowin.launcher.ui.theme.Themes
import com.metrowin.launcher.ui.theme.highContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text of the launcher is readable (WCAG 4.5:1) in every theme, palette and accent it offers. */
class ContrastTest {
    private fun assertText(what: String, text: Color, on: Color, minimum: Float = 4.5f) {
        val ratio = ColorMath.contrast(text, on)
        assertTrue("$what: contrast $ratio is below $minimum", ratio >= minimum)
    }

    private fun readable(name: String, colors: MetroColors) {
        assertText("$name foreground", colors.foreground, colors.background)
        assertText("$name subtle on background", colors.subtle, colors.background)
        assertText("$name foreground on surface", colors.foreground, colors.surface)
        assertText("$name subtle on surface", colors.subtle, colors.surface)
        colors.containers.zip(colors.onContainers).forEachIndexed { i, (box, ink) -> assertText("$name container $i", ink, box) }
    }

    @Test
    fun theRatioIsTheWcagOne() {
        assertEquals(21f, ColorMath.contrast(Color.White, Color.Black), 0.01f)
        assertEquals(1f, ColorMath.contrast(Color.Red, Color.Red), 0.001f)
    }

    @Test
    fun theTwoThemesAndTheirHighContrastAreReadable() {
        for ((name, colors) in listOf("dark" to DarkColors, "light" to LightColors)) {
            readable(name, colors)
            readable("$name high", colors.highContrast())
            assertText("$name high subtle", colors.highContrast().subtle, colors.highContrast().background, 7f)
        }
    }

    @Test
    fun everyAccentOfModernDroidGivesReadablePalettesInBothThemes() {
        for (seed in MetroAccents) for (dark in listOf(true, false)) {
            val colors = Palettes.colors(Palettes.fromSeed(seed), dark)
            readable("droid ${seed.value} dark=$dark", colors)
            readable("droid ${seed.value} dark=$dark high", colors.highContrast())
        }
    }

    @Test
    fun theInkOnAnAccentReadsOnEveryAccent() {
        for (accent in MetroAccents) assertText("ink on ${accent.value}", ColorMath.inkOn(accent), accent)
    }

    @Test
    fun theInkOnTheModernDroidAccentAndOnItsPaleTonesReads() {
        for (seed in MetroAccents) for (dark in listOf(true, false)) {
            val palette = Palettes.fromSeed(seed)
            // The accent of the look (what a lit button, a chosen tile color or an icon color is painted with) and every tone a tile can take.
            val boxes = listOf(Palettes.accent(palette, dark)) + listOf(palette.accent1, palette.accent2, palette.accent3).flatMap { listOf(10, 30, 80, 90, 95).map(it::tone) }
            for (box in boxes) assertText("ink on droid ${seed.value} dark=$dark $box", ColorMath.inkOn(box), box)
        }
    }

    @Test
    fun anAccentUsedAsTextReadsOnThePageOfEveryTheme() {
        for (accent in MetroAccents) for (colors in listOf(DarkColors, LightColors)) {
            assertText("accent ${accent.value} text on ${colors.background}", ColorMath.readableOn(accent, colors.background), colors.background, 3f)
        }
        // One that already reads is left alone.
        assertEquals(Color(0xFF0050EF), ColorMath.readableOn(Color(0xFF0050EF), Color.White))
    }

    @Test
    fun everyStyleInBothThemesOnOledAndInHighContrastReadsWithEveryAccent() {
        for (kind in ThemeStyle.values()) for (dark in listOf(true, false)) for (oled in listOf(false, true)) for (accent in MetroAccents) {
            val resolved = Themes.resolve(kind, dark, accent, null, oled)
            val name = "$kind dark=$dark oled=$oled accent=${accent.value}"
            for ((label, colors) in listOf("" to resolved.colors, " high" to resolved.colors.highContrast())) {
                readable(name + label, colors)
                // The accent as a heading (large text) and the danger red as a big glyph or a bar.
                assertText("$name$label accent", ColorMath.readableOn(resolved.accent, colors.background), colors.background, 3f)
                assertText("$name$label danger", ColorMath.readableOn(Danger, colors.background), colors.background, 3f)
            }
            assertText("$name high subtle", resolved.colors.highContrast().subtle, resolved.colors.highContrast().background, 7f)
        }
    }
}
