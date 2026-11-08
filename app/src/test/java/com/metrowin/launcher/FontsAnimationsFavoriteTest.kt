package com.metrowin.launcher

import androidx.compose.ui.text.font.FontFamily
import com.metrowin.launcher.data.LaunchAnimation
import com.metrowin.launcher.data.LaunchPlans
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SectionReset
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.ui.theme.AppFonts
import com.metrowin.launcher.ui.theme.ClassicStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FontsAnimationsFavoriteTest {
    @Test
    fun twentyFontsWithOneCodeEach() {
        assertEquals(20, AppFonts.all.size)
        assertEquals(20, AppFonts.all.map { it.code }.toSet().size)
        assertFalse(AppFonts.CUSTOM in AppFonts.all.map { it.code })
    }

    @Test
    fun onlyTheStartOfAFontFileIsAccepted() {
        assertTrue(AppFonts.looksLikeFont(byteArrayOf(0, 1, 0, 0)))
        assertTrue(AppFonts.looksLikeFont("OTTO".toByteArray()))
        assertTrue(AppFonts.looksLikeFont("ttcf".toByteArray()))
        assertFalse(AppFonts.looksLikeFont("%PDF".toByteArray()))
        assertFalse(AppFonts.looksLikeFont(byteArrayOf(0, 1)))
    }

    @Test
    fun aChosenFontReplacesTheLookAndTheFontOfThePhoneComesFirst() {
        val mine = FontFamily.Cursive
        assertSame(mine, ClassicStyle.withType(bold = false, systemFont = false, chosen = mine).font)
        assertSame(FontFamily.Default, ClassicStyle.withType(bold = false, systemFont = true, chosen = mine).font)
        assertSame(ClassicStyle, ClassicStyle.withType(bold = false, systemFont = false, chosen = null))
        assertNotSame(ClassicStyle, ClassicStyle.withType(bold = false, systemFont = false, chosen = mine))
    }

    @Test
    fun theOriginalWindowsPhoneAnimationIsTheDefaultAndEveryStyleHasItsOwnCode() {
        assertEquals(LaunchAnimation.Original, LauncherSettings().launchAnimation)
        assertEquals(LaunchAnimation.Original, LaunchAnimation.fromCode(null))
        assertEquals(8, LaunchAnimation.entries.size)
        assertEquals(LaunchAnimation.entries.size, LaunchAnimation.entries.map { it.code }.toSet().size)
        LaunchAnimation.entries.forEach { assertEquals(it, LaunchAnimation.fromCode(it.code)) }
    }

    @Test
    fun theWindowStylesLeaveStartStillAndStartTheAppAtOnce() {
        listOf(LaunchAnimation.Fade, LaunchAnimation.Slide, LaunchAnimation.Rise, LaunchAnimation.Zoom, LaunchAnimation.Instant).forEach { style ->
            listOf(true, false).forEach { fromStart ->
                val plan = LaunchPlans.decide(style, reduceMotion = false, fromStart = fromStart)
                assertFalse(style.code, plan.tilesTurnAway)
                assertFalse(style.code, plan.expand)
                assertEquals(style.code, 0L, plan.waitMs)
                assertEquals(style, plan.window)
            }
        }
        assertEquals(LaunchAnimation.Turnstile, LaunchPlans.decide(LaunchAnimation.Turnstile, false, true).window)
        // Reduce motion keeps its own plan whatever the style.
        assertEquals(LaunchAnimation.Turnstile, LaunchPlans.decide(LaunchAnimation.Slide, reduceMotion = true, fromStart = true).window)
    }

    @Test
    fun theNewSettingsSurviveTheCodecAndTheDefaultsAreWhatTheyWere() {
        val custom = LauncherSettings(appFont = "lora", fontRevision = 3, favoritePage = 2, launchAnimation = LaunchAnimation.Zoom, startPages = 3)
        assertEquals(custom, SettingsCodec.decode(SettingsCodec.encode(custom)))
        val defaults = LauncherSettings()
        assertEquals("", defaults.appFont)
        assertEquals(-1, defaults.favoritePage)
        // A bad favourite page is brought back inside the range.
        assertEquals(-1, SettingsCodec.decode("""{"favoritePage":-7}""")!!.favoritePage)
        assertEquals(LauncherSettings.MAX_START_PAGES - 1, SettingsCodec.decode("""{"startPages":${LauncherSettings.MAX_START_PAGES},"favoritePage":99}""")!!.favoritePage)
        // A favourite past the screens there are is the last screen, never one that does not exist.
        assertEquals(0, SettingsCodec.decode("""{"favoritePage":99}""")!!.favoritePage)
    }

    @Test
    fun resettingThePagesPutsTheNewSettingsBack() {
        val changed = LauncherSettings(appFont = "lora", fontRevision = 2, favoritePage = 1)
        assertTrue(SectionReset.changed(changed, "Accessibility"))
        assertTrue(SectionReset.changed(changed, "Start"))
        val reset = SectionReset.reset(SectionReset.reset(changed, "Accessibility"), "Start")
        assertEquals("", reset.appFont)
        assertEquals(0, reset.fontRevision)
        assertEquals(-1, reset.favoritePage)
    }
}
