package com.metrowin.launcher

import androidx.compose.ui.graphics.Color
import com.metrowin.launcher.ui.theme.AccessibilityPolicy
import com.metrowin.launcher.ui.theme.DarkColors
import com.metrowin.launcher.ui.theme.LightColors
import com.metrowin.launcher.ui.theme.highContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityPolicyTest {
    @Test
    fun motionIsReducedByTheSettingOrByAndroidRemovingAnimations() {
        assertFalse(AccessibilityPolicy.reduceMotion(setting = false, animatorDurationScale = 1f))
        assertFalse(AccessibilityPolicy.reduceMotion(setting = false, animatorDurationScale = 0.5f))
        assertFalse(AccessibilityPolicy.reduceMotion(setting = false, animatorDurationScale = 10f))
        assertTrue(AccessibilityPolicy.reduceMotion(setting = true, animatorDurationScale = 1f))
        assertTrue(AccessibilityPolicy.reduceMotion(setting = false, animatorDurationScale = 0f))
        assertTrue(AccessibilityPolicy.reduceMotion(setting = true, animatorDurationScale = 0f))
    }

    @Test
    fun highContrastTurnsGreyTextToTheFullColourInBothThemes() {
        assertEquals(DarkColors.foreground, DarkColors.highContrast().subtle)
        assertEquals(LightColors.foreground, LightColors.highContrast().subtle)
        assertNotEquals(DarkColors.subtle, DarkColors.highContrast().subtle)
    }

    @Test
    fun highContrastKeepsTheBackgroundAndTheThemeAndSeparatesTheSurfaces() {
        for ((normal, strong) in listOf(DarkColors to DarkColors.highContrast(), LightColors to LightColors.highContrast())) {
            assertEquals(normal.background, strong.background)
            assertEquals(normal.dark, strong.dark)
            assertNotEquals(normal.surface, strong.surface)
        }
        // The surface is further from the background than it was.
        fun gap(a: Color, b: Color) = kotlin.math.abs(a.red - b.red)
        assertTrue(gap(DarkColors.highContrast().surface, DarkColors.background) > gap(DarkColors.surface, DarkColors.background))
        assertTrue(gap(LightColors.highContrast().surface, LightColors.background) > gap(LightColors.surface, LightColors.background))
    }
}
