package com.metrowin.launcher

import com.metrowin.launcher.data.LiveSources
import com.metrowin.launcher.data.MemorySpec
import com.metrowin.launcher.data.PhotoSpec
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The info tile: what it shows, how that is kept, and what it makes the launcher read. */
class InfoTileTest {
    @Test
    fun aNewTileShowsAllThreeInTurn() {
        assertEquals(listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Photos), InfoConfig().parts)
    }

    @Test
    fun theChoiceIsKeptAsAShortStringAndReadBack() {
        assertEquals("tcp", InfoConfig().encode())
        assertEquals("c", InfoConfig(parts = listOf(InfoPart.Calendar)).encode())
        assertEquals(InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Photos)), InfoConfig.decode("tp"))
    }

    @Test
    fun anEmptyOrUnreadableChoiceIsAllThree() {
        assertEquals(InfoConfig(), InfoConfig.decode(""))
        assertEquals(InfoConfig(), InfoConfig.decode("xyz"))
    }

    @Test
    fun theLastPartOnCannotBeSwitchedOff() {
        val onlyTime = InfoConfig(parts = listOf(InfoPart.Time))
        assertEquals(onlyTime, onlyTime.toggled(InfoPart.Time))
        assertEquals(listOf(InfoPart.Time, InfoPart.Calendar), onlyTime.toggled(InfoPart.Calendar).parts)
        assertEquals(listOf(InfoPart.Calendar, InfoPart.Photos), InfoConfig().toggled(InfoPart.Time).parts)
    }

    @Test
    fun theTileAsksToReadOnlyWhatItShows() {
        assertEquals(setOf(LiveKind.Calendar, LiveKind.Photos), InfoConfig().liveKinds())
        assertEquals(setOf(LiveKind.Photos), InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Photos)).liveKinds())
        assertTrue(InfoConfig(parts = listOf(InfoPart.Time)).liveKinds().isEmpty())
    }

    @Test
    fun theNewFacesAskForTheirDataToo() {
        val config = InfoConfig(parts = listOf(InfoPart.Todo, InfoPart.Birthdays, InfoPart.Waiting, InfoPart.People, InfoPart.Week, InfoPart.Music))
        assertEquals(setOf(LiveKind.Todo, LiveKind.Birthdays, LiveKind.Phone, LiveKind.Mail, LiveKind.People, LiveKind.Calendar), config.liveKinds())
    }

    @Test
    fun theFacesOfAPlanAreReadToo() {
        val config = InfoConfig(parts = listOf(InfoPart.Time), slots = listOf(com.metrowin.launcher.model.InfoSlot(8, listOf(InfoPart.Alarm))))
        assertEquals(setOf(LiveKind.Alarm), config.liveKinds())
    }

    @Test
    fun theShortcutBecomesATileWithItsChoice() {
        val item = ShortcutDef("info:1", ShortcutKind.Info, "InfoTile", text = "cp").toItem(null)
        assertEquals(TileData.Info(InfoConfig(parts = listOf(InfoPart.Calendar, InfoPart.Photos))), item.data)
    }

    @Test
    fun anInfoTileOnStartMakesTheLauncherReadTheCalendarAndThePhotos() {
        val item = ShortcutDef("info:1", ShortcutKind.Info, "InfoTile", text = "tcp").toItem(null)
        val pages = listOf(listOf<StartEntry>(Tile("info:1", TileSize.Wide)))
        assertEquals(setOf(LiveKind.Calendar, LiveKind.Photos), LiveSources.wanted(pages, listOf(item)))
    }

    @Test
    fun theTileAsksForItsOwnPhotosMemoriesAndCalendars() {
        val item = ShortcutDef("info:1", ShortcutKind.Info, "I", text = "tcpm;a=9;ns=1;x=3,4;mw=2;k=1,2;fv=1").toItem(null)
        val pages = listOf(listOf<StartEntry>(Tile("info:1", TileSize.Wide)))
        val needs = LiveSources.infoNeeds(pages, listOf(item))
        assertEquals(setOf(PhotoSpec("9", favorites = true, noShots = true, excluded = setOf("3", "4"))), needs.photos)
        assertEquals(setOf(MemorySpec("9", noShots = true, excluded = setOf("3", "4"), days = 2)), needs.memories)
        assertEquals(setOf(setOf(1L, 2L)), needs.calendars)
    }

    @Test
    fun twoTilesWithTheSameChoiceAskOnce() {
        val a = ShortcutDef("info:a", ShortcutKind.Info, "A", text = "tp").toItem(null)
        val b = ShortcutDef("info:b", ShortcutKind.Info, "B", text = "tp").toItem(null)
        val pages = listOf(listOf<StartEntry>(Tile("info:a", TileSize.Wide), Tile("info:b", TileSize.Wide)))
        assertEquals(1, LiveSources.infoNeeds(pages, listOf(a, b)).photos.size)
    }

    @Test
    fun noInfoTileAsksForNothing() {
        assertTrue(LiveSources.infoNeeds(listOf(emptyList()), emptyList()).isEmpty)
    }
}
