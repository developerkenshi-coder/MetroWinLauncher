package com.metrowin.launcher

import com.metrowin.launcher.data.LiveSources
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
        assertEquals("c", InfoConfig(time = false, calendar = true, photos = false).encode())
        assertEquals(InfoConfig(time = true, calendar = false, photos = true), InfoConfig.decode("tp"))
    }

    @Test
    fun anEmptyOrUnreadableChoiceIsAllThree() {
        assertEquals(InfoConfig(), InfoConfig.decode(""))
        assertEquals(InfoConfig(), InfoConfig.decode("xyz"))
    }

    @Test
    fun theLastPartOnCannotBeSwitchedOff() {
        val onlyTime = InfoConfig(time = true, calendar = false, photos = false)
        assertEquals(onlyTime, onlyTime.toggled(InfoPart.Time))
        assertEquals(InfoConfig(time = true, calendar = true, photos = false), onlyTime.toggled(InfoPart.Calendar))
        assertEquals(InfoConfig(time = false, calendar = true, photos = true), InfoConfig().toggled(InfoPart.Time))
    }

    @Test
    fun theTileAsksToReadOnlyWhatItShows() {
        assertEquals(setOf(LiveKind.Calendar, LiveKind.Photos), InfoConfig().liveKinds())
        assertEquals(setOf(LiveKind.Photos), InfoConfig(time = true, calendar = false, photos = true).liveKinds())
        assertTrue(InfoConfig(time = true, calendar = false, photos = false).liveKinds().isEmpty())
    }

    @Test
    fun theShortcutBecomesATileWithItsChoice() {
        val item = ShortcutDef("info:1", ShortcutKind.Info, "InfoTile", text = "cp").toItem(null)
        assertEquals(TileData.Info(InfoConfig(time = false, calendar = true, photos = true)), item.data)
    }

    @Test
    fun anInfoTileOnStartMakesTheLauncherReadTheCalendarAndThePhotos() {
        val item = ShortcutDef("info:1", ShortcutKind.Info, "InfoTile", text = "tcp").toItem(null)
        val pages = listOf(listOf<StartEntry>(Tile("info:1", TileSize.Wide)))
        assertEquals(setOf(LiveKind.Calendar, LiveKind.Photos), LiveSources.wanted(pages, listOf(item)))
    }
}
