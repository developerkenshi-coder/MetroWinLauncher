package com.metrowin.launcher

import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoLayout
import com.metrowin.launcher.model.InfoPace
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.InfoPreset
import com.metrowin.launcher.model.InfoRule
import com.metrowin.launcher.model.InfoSlot
import com.metrowin.launcher.model.InfoTrigger
import com.metrowin.launcher.model.RuleKind
import com.metrowin.launcher.model.TriggerKind
import com.metrowin.launcher.model.infoConfigFromShareText
import com.metrowin.launcher.model.shareable
import com.metrowin.launcher.model.toShareText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an info tile keeps, as a string that older builds still read and newer ones do not cut down. */
class InfoConfigTest {
    private val everything = InfoConfig(
        parts = listOf(InfoPart.Todo, InfoPart.Time, InfoPart.Calendar, InfoPart.Weather, InfoPart.Photos),
        smart = true, saver = true, zones = listOf("Asia/Tokyo", "America/New_York"), zoneNames = listOf("Tokyo; Japan", "New York"),
        album = "123", albumName = "Trip, 2024 = fun; ok", pace = InfoPace.Fast, dwell = false, soonMinutes = 30, nightFrom = 22, nightTo = 7,
        smartBattery = false, smartRain = false,
        slots = listOf(InfoSlot(6, listOf(InfoPart.Calendar, InfoPart.Weather)), InfoSlot(20, listOf(InfoPart.Photos))),
        triggers = listOf(InfoTrigger(TriggerKind.Wifi, "Casa, 5G|x", listOf(InfoPart.Photos)), InfoTrigger(TriggerKind.Bluetooth, "", listOf(InfoPart.Parking))),
        rules = listOf(InfoRule(InfoPart.Battery, RuleKind.BatteryBelow, 15), InfoRule(InfoPart.Todo, RuleKind.NotBetween, 22, 7)),
        calendars = listOf(3L, 9L), hideTitles = true, hidePhotos = true, noShots = true, excluded = listOf("11", "12"), favorites = true,
        picked = listOf("content://media/picker/0/a;b=c", "content://x/1"), memoryDays = 3, photoBackground = true, analog = true, quickTimers = true,
        homeZone = "Europe/Rome", travel = true, dnd = true, kids = true, dots = true, tint = true, layout = InfoLayout.Dashboard,
        weatherCity = "45.1,7.6", weatherCityName = "Torino, Italia", countdownDay = 20000L, countdownName = "Esame", note = "n1", published = "com.x.y", widget = 42, briefHour = 7,
    )

    @Test
    fun everyChoiceIsKeptAndReadBack() {
        assertEquals(everything, InfoConfig.decode(everything.encode()))
    }

    @Test
    fun aTileMadeBeforeTheNewChoicesStaysAsItWas() {
        assertEquals(InfoConfig(), InfoConfig.decode("tcp"))
        assertEquals(InfoConfig(parts = listOf(InfoPart.Calendar)), InfoConfig.decode("c"))
        val old = InfoConfig.decode("tcaS;v=3;z=Asia/Tokyo;a=5;n=Trip")
        assertEquals(listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Alarm), old.parts)
        assertTrue(old.smart)
        assertEquals(listOf("Asia/Tokyo"), old.zones)
        assertEquals("Trip", old.albumName)
        assertEquals(InfoPace.Fast, old.pace)
    }

    @Test
    fun theOldFacesKeepTheirLettersForAnOlderBuild() {
        // The letters of the first seven are written, in the old order, even when the user put them in another.
        val config = InfoConfig(parts = listOf(InfoPart.Photos, InfoPart.Todo, InfoPart.Time))
        val encoded = config.encode()
        assertTrue(encoded.startsWith("tp;"))
        assertEquals(config, InfoConfig.decode(encoded))
    }

    @Test
    fun theFacesAreMovedAndTheOrderIsKept() {
        val three = InfoConfig()
        assertEquals(listOf(InfoPart.Calendar, InfoPart.Time, InfoPart.Photos), three.moved(InfoPart.Time, 1).parts)
        assertEquals(three, three.moved(InfoPart.Time, -1))
        assertEquals(listOf(InfoPart.Photos, InfoPart.Time, InfoPart.Calendar), three.moved(InfoPart.Photos, -2).parts)
        assertEquals(three.moved(InfoPart.Time, 1), InfoConfig.decode(three.moved(InfoPart.Time, 1).encode()))
    }

    @Test
    fun theLastFaceOnStaysOnWhateverElseChanges() {
        val one = InfoConfig(parts = listOf(InfoPart.Memories))
        assertEquals(one, one.toggled(InfoPart.Memories))
        assertEquals(listOf(InfoPart.Memories, InfoPart.Alarm), one.toggled(InfoPart.Alarm).parts)
    }

    @Test
    fun aKeyOfANewerBuildIsKept() {
        val read = InfoConfig.decode("tcp;future=1;other=a%3Bb")
        assertEquals(mapOf("future" to "1", "other" to "a%3Bb"), read.extra)
        assertTrue(read.encode().contains(";future=1;other=a%3Bb"))
    }

    @Test
    fun valuesThatCannotBeReadAreMadeSafe() {
        val read = InfoConfig.decode("tcp;sm=9999;n0=77;n1=-3;mw=40;bf=99;sl=99:time|7:nothing;ru=time.zzz.1.1;cx=wifi~:")
        assertEquals(240, read.soonMinutes)
        assertEquals(23, read.nightFrom)
        assertEquals(0, read.nightTo)
        assertEquals(7, read.memoryDays)
        assertEquals(-1, read.briefHour)
        assertTrue(read.slots.isEmpty())
        assertTrue(read.rules.isEmpty())
        assertTrue(read.triggers.isEmpty())
    }

    @Test
    fun aNameWithTheCharactersOfTheStringSurvives() {
        val config = InfoConfig(album = "5", albumName = "a;b=c|d,e%f+g~h:i")
        assertEquals("a;b=c|d,e%f+g~h:i", InfoConfig.decode(config.encode()).albumName)
    }

    @Test
    fun theDefaultsAreNotWritten() {
        assertEquals("tcp", InfoConfig().encode())
        assertFalse(InfoConfig(smart = true).encode().contains(";"))
    }

    @Test
    fun whatIsPassedOnHasNothingOfThisPhone() {
        val shared = everything.shareable()
        assertEquals("", shared.album)
        assertTrue(shared.picked.isEmpty() && shared.excluded.isEmpty() && shared.calendars.isEmpty())
        assertEquals("", shared.note + shared.published + shared.weatherCity + shared.homeZone + shared.countdownName)
        assertEquals(0, shared.widget)
        assertTrue(shared.triggers.all { it.value.isEmpty() })
        assertFalse(shared.toShareText().contains("Casa"))
        // What is left is the shape: faces, order, switches.
        assertEquals(everything.parts, shared.parts)
        assertEquals(everything.smart, shared.smart)
    }

    @Test
    fun aSharedTextIsReadBackAndAnythingElseIsNot() {
        val text = InfoPreset.Morning.config.toShareText()
        assertTrue(text.startsWith("metrowin-tile:"))
        assertEquals(InfoPreset.Morning.config.shareable(), infoConfigFromShareText("  $text\n"))
        assertNull(infoConfigFromShareText("hello"))
        assertNull(infoConfigFromShareText("tcp"))
    }

    @Test
    fun everyModelIsANormalTile() {
        InfoPreset.entries.forEach { preset ->
            assertTrue(preset.config.parts.isNotEmpty())
            assertEquals(preset.config, InfoConfig.decode(preset.config.encode()))
        }
    }
}
