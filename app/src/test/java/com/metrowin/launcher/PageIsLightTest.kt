package com.metrowin.launcher

import androidx.compose.ui.graphics.Color
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.theme.DarkColors
import com.metrowin.launcher.ui.theme.GlassColors
import com.metrowin.launcher.ui.theme.LightColors
import com.metrowin.launcher.ui.theme.pageIsLight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The icons of the system bars follow the color the page really has at the top, not only the dark flag. */
class PageIsLightTest {
    private val yellow = Color(0xFFFFEB00)
    private val blue = Color(0xFF0050EF)

    @Test
    fun classicFollowsTheBackground() {
        assertFalse(pageIsLight(ThemeStyle.Classic, DarkColors, yellow, false))
        assertTrue(pageIsLight(ThemeStyle.Classic, LightColors, blue, false))
    }

    @Test
    fun glassDarkPageUnderAPaleAccentPoolNeedsDarkIcons() {
        assertTrue(pageIsLight(ThemeStyle.Glass, GlassColors.dark, yellow, false))
        assertFalse(pageIsLight(ThemeStyle.Glass, GlassColors.dark, blue, false))
        // OLED: the pool is a faint glow, the page stays black.
        assertFalse(pageIsLight(ThemeStyle.Glass, GlassColors.dark, yellow, true))
    }

    @Test
    fun glassLightPageStaysLight() {
        assertTrue(pageIsLight(ThemeStyle.Glass, GlassColors.light, blue, false))
    }
}
