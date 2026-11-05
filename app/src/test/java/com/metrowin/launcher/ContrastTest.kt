package com.metrowin.launcher

import androidx.compose.ui.graphics.Color
import com.metrowin.launcher.ui.theme.ColorMath
import com.metrowin.launcher.ui.theme.DarkColors
import com.metrowin.launcher.ui.theme.LightColors
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.MetroColors
import com.metrowin.launcher.ui.theme.Palettes
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
}
