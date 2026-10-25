package com.metrowin.launcher

import com.metrowin.launcher.data.LaunchAnimation
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ThemeCodec
import com.metrowin.launcher.data.ThemeMode
import com.metrowin.launcher.data.ThemeStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCodecTest {
    private val styled = LauncherSettings(
        themeMode = ThemeMode.Schedule, darkFromHour = 21, darkToHour = 7, accentIndex = 9, moreTiles = true,
        parallax = false, showClock = true, iconPack = "com.example.pack", highContrast = true,
        launchAnimation = LaunchAnimation.Expand,
    )

    @Test
    fun aThemeSurvivesAnEncodeAndDecode() {
        val applied = ThemeCodec.decode(ThemeCodec.encode(styled), LauncherSettings())!!
        assertEquals(styled.themeMode, applied.themeMode)
        assertEquals(21, applied.darkFromHour)
        assertEquals(7, applied.darkToHour)
        assertEquals(9, applied.accentIndex)
        assertTrue(applied.moreTiles)
        assertFalse(applied.parallax)
        assertTrue(applied.showClock)
        assertEquals("com.example.pack", applied.iconPack)
        assertTrue(applied.highContrast)
        assertEquals(LaunchAnimation.Expand, applied.launchAnimation)
    }

    @Test
    fun theLookAndTheCustomColorTravelWithTheTheme() {
        val mine = LauncherSettings(themeStyle = ThemeStyle.Glass, customAccent = 0xFFAA3355.toInt(), dynamicColor = false)
        val applied = ThemeCodec.decode(ThemeCodec.encode(mine), LauncherSettings(autoAccent = true))!!
        assertEquals(ThemeStyle.Glass, applied.themeStyle)
        assertEquals(0xFFAA3355.toInt(), applied.customAccent)
        assertFalse(applied.dynamicColor)
        // A color of its own would be hidden by the accent that follows the photo.
        assertFalse(applied.autoAccent)
    }

    @Test
    fun aThemeNeverTouchesWhatIsNotLookAndFeel() {
        val mine = LauncherSettings(
            hiddenApps = setOf("a"), searchWeb = false, weatherCity = "Torino", backupFolder = "content://mine",
            syncPassphrase = "secret", kidsMode = true, doubleTapAction = com.metrowin.launcher.data.GestureAction.Torch,
            startPages = 3, accentIndex = 2,
        )
        val applied = ThemeCodec.decode(ThemeCodec.encode(styled), mine)!!
        assertEquals(mine.hiddenApps, applied.hiddenApps)
        assertEquals(mine.searchWeb, applied.searchWeb)
        assertEquals("Torino", applied.weatherCity)
        assertEquals("content://mine", applied.backupFolder)
        assertEquals("secret", applied.syncPassphrase)
        assertTrue(applied.kidsMode)
        assertEquals(mine.doubleTapAction, applied.doubleTapAction)
        assertEquals(3, applied.startPages)
        assertEquals("the theme did change the accent", 9, applied.accentIndex)
    }

    @Test
    fun theFileHoldsNothingButTheThemeKeys() {
        val theme = JSONObject(ThemeCodec.encode(LauncherSettings(syncPassphrase = "secret", backupFolder = "x"))).getJSONObject("theme")
        val keys = theme.keys().asSequence().toSet()
        assertFalse("syncPassphrase" in keys)
        assertFalse("backupFolder" in keys)
        assertFalse("hiddenApps" in keys)
        assertTrue("accentIndex" in keys)
    }

    @Test
    fun aFileCannotSmuggleOtherSettingsIn() {
        val json = """{"format":"metrowin-theme","version":1,"theme":{"accentIndex":3,"kidsMode":true,"syncPassphrase":"x","hiddenApps":["a"]}}"""
        val applied = ThemeCodec.decode(json, LauncherSettings())!!
        assertEquals(3, applied.accentIndex)
        assertFalse(applied.kidsMode)
        assertEquals("", applied.syncPassphrase)
        assertTrue(applied.hiddenApps.isEmpty())
    }

    @Test
    fun anAccentFromAThemeTurnsOffTheAccentThatFollowsThePhoto() {
        val withPhoto = LauncherSettings(autoAccent = true, backgroundRevision = 1)
        assertFalse(ThemeCodec.decode(ThemeCodec.encode(styled), withPhoto)!!.autoAccent)
        // A theme without an accent leaves it alone.
        val noAccent = """{"format":"metrowin-theme","version":1,"theme":{"showClock":true}}"""
        assertTrue(ThemeCodec.decode(noAccent, withPhoto)!!.autoAccent)
    }

    @Test
    fun valuesAreClamped() {
        val json = """{"format":"metrowin-theme","version":1,"theme":{"accentIndex":99,"darkFromHour":40}}"""
        val applied = ThemeCodec.decode(json, LauncherSettings())!!
        assertEquals(19, applied.accentIndex)
        assertEquals(23, applied.darkFromHour)
    }

    @Test
    fun otherFilesAreNotThemes() {
        assertNull(ThemeCodec.decode("not json", LauncherSettings()))
        assertNull(ThemeCodec.decode("""{"format":"metrowin-layout","version":1}""", LauncherSettings()))
        assertNull(ThemeCodec.decode("""{"format":"metrowin-theme","version":99,"theme":{}}""", LauncherSettings()))
        assertNull(ThemeCodec.decode("""{"format":"metrowin-theme","version":1}""", LauncherSettings()))
        assertNotNull(ThemeCodec.decode("""{"format":"metrowin-theme","version":1,"theme":{}}""", LauncherSettings()))
    }
}
