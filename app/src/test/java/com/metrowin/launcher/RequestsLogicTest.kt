package com.metrowin.launcher

import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.StartEntries
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The long press of every button of the Action Center, the sizes of the full weather tile, the new settings. */
class RequestsLogicTest {
    @Test
    fun everyButtonHasALongPressScreenOfItsOwnExceptTheTwoThatOpenAList() {
        QuickAction.entries.forEach { action ->
            val screen = QuickAction.holdScreen(action)
            if (action == QuickAction.Wifi || action == QuickAction.Bluetooth) assertNull(action.name, screen) else assertNotNull(action.name, screen)
        }
    }

    @Test
    fun theFullWeatherTileCyclesBetweenMediumAndWideOnly() {
        val start = listOf<com.metrowin.launcher.model.StartEntry>(Tile("w", TileSize.Medium))
        val once = StartEntries.resize(start, "w", allowLarge = true, onlyMediumOrWide = true)
        assertEquals(TileSize.Wide, once.single().size)
        assertEquals(TileSize.Medium, StartEntries.resize(once, "w", allowLarge = true, onlyMediumOrWide = true).single().size)
        // The other tiles still go through every size.
        assertEquals(TileSize.Wide, StartEntries.resize(start, "w").single().size)
    }

    @Test
    fun theNewSettingsSurviveTheFileAndStartOn() {
        val defaults = LauncherSettings()
        assertEquals(true, defaults.todayWeather && defaults.todayNear && defaults.todayDate && defaults.todayAlarm && defaults.panelSounds && defaults.assistantIcon)
        assertEquals("", defaults.galleryApp)
        val changed = defaults.copy(
            todayWeather = false, todayNear = false, todayDate = false, todayAlarm = false, panelSounds = false, assistantIcon = false,
            galleryApp = "com.example.gallery", galleryAlbums = setOf("11", "22"),
        )
        val back = SettingsCodec.decode(SettingsCodec.encode(changed))!!
        assertFalse(back.todayWeather || back.todayNear || back.todayDate || back.todayAlarm || back.panelSounds || back.assistantIcon)
        assertEquals("com.example.gallery", back.galleryApp)
        assertEquals(setOf("11", "22"), back.galleryAlbums)
    }
}
