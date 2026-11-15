package com.metrowin.launcher

import com.metrowin.launcher.data.InfoPriority
import com.metrowin.launcher.data.InfoTileState
import com.metrowin.launcher.data.LiveSources
import com.metrowin.launcher.data.PhotoMemories
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPace
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** The choices of the info tile: more faces, smart order, pace, zone, album, the photos of this day, and several tiles at once. */
class InfoTileMoreTest {
    private val rome = ZoneId.of("Europe/Rome")
    private fun ms(iso: String) = LocalDateTime.parse(iso).atZone(rome).toInstant().toEpochMilli()

    @Test
    fun everyChoiceIsKeptAndReadBack() {
        val config = InfoConfig(
            time = true, calendar = false, photos = true, alarm = true, weather = true, battery = true, memories = true,
            smart = true, saver = true, zone = "Asia/Tokyo", album = "123", albumName = "Trip, 2024", pace = InfoPace.Fast,
        )
        assertEquals(config, InfoConfig.decode(config.encode()))
    }

    @Test
    fun aTileMadeBeforeTheNewChoicesStaysAsItWas() {
        assertEquals(InfoConfig(time = true, calendar = true, photos = true), InfoConfig.decode("tcp"))
        assertEquals(InfoConfig(time = false, calendar = true, photos = false), InfoConfig.decode("c"))
    }

    @Test
    fun theFacesTurnInAFixedOrder() {
        val all = InfoConfig(alarm = true, weather = true, battery = true, memories = true)
        assertEquals(
            listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Alarm, InfoPart.Weather, InfoPart.Battery, InfoPart.Photos, InfoPart.Memories),
            all.parts,
        )
    }

    @Test
    fun theLastFaceOnStaysOnWhateverElseChanges() {
        val one = InfoConfig(time = false, calendar = false, photos = false, memories = true)
        assertEquals(one, one.toggled(InfoPart.Memories))
        assertEquals(listOf(InfoPart.Alarm, InfoPart.Memories), one.toggled(InfoPart.Alarm).parts)
    }

    @Test
    fun theTileAsksToReadOnlyWhatItShows() {
        assertEquals(setOf(LiveKind.Calendar, LiveKind.Photos), InfoConfig().liveKinds())
        assertEquals(setOf(LiveKind.Alarm, LiveKind.Memories), InfoConfig(time = true, calendar = false, photos = false, alarm = true, memories = true).liveKinds())
        // An album asks for the albums, not for the latest photos.
        assertEquals(setOf(LiveKind.Album), InfoConfig(time = true, calendar = false, photos = true, album = "9").liveKinds())
    }

    @Test
    fun theAlbumOfAnInfoTileIsAmongThePinnedAlbums() {
        val item = ShortcutDef("info:1", ShortcutKind.Info, "I", text = "tp;a=777").toItem(null)
        val pages = listOf(listOf<StartEntry>(Tile("info:1", TileSize.Wide)))
        assertEquals(setOf("777"), LiveSources.albumIds(pages, listOf(item)))
    }

    @Test
    fun severalInfoTilesEachKeepTheirOwnChoice() {
        val first = ShortcutDef(ShortcutDef.newId(ShortcutKind.Info, "info"), ShortcutKind.Info, "A", text = "t")
        val second = ShortcutDef(ShortcutDef.newId(ShortcutKind.Info, "info"), ShortcutKind.Info, "B", text = "cp;z=Asia/Tokyo")
        assertTrue(first.id != second.id)
        assertEquals(InfoConfig(time = true, calendar = false, photos = false), (first.toItem(null).data as com.metrowin.launcher.model.TileData.Info).config)
        assertEquals("Asia/Tokyo", (second.toItem(null).data as com.metrowin.launcher.model.TileData.Info).config.zone)
        val pages = listOf(listOf<StartEntry>(Tile(first.id, TileSize.Wide), Tile(second.id, TileSize.Medium)))
        assertEquals(setOf(LiveKind.Calendar, LiveKind.Photos), LiveSources.wanted(pages, listOf(first.toItem(null), second.toItem(null))))
    }

    @Test
    fun theTileRemembersWhichFaceIsOnShow() {
        assertNull(InfoTileState.current("nothing"))
        InfoTileState.show("x", InfoPart.Battery)
        assertEquals(InfoPart.Battery, InfoTileState.current("x"))
    }

    // --- smart order

    private val faces = listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Alarm, InfoPart.Photos)

    @Test
    fun notSmartKeepsTheOrder() {
        val now = ms("2026-11-15T10:00:00")
        assertEquals(faces, InfoPriority.order(faces, smart = false, nowMs = now, hour = 10, eventStartMs = now + 600_000, alarmMs = now + 300_000))
    }

    @Test
    fun atNightOnlyTheTime() {
        val now = ms("2026-11-15T23:30:00")
        assertEquals(listOf(InfoPart.Time), InfoPriority.order(faces, true, now, 23, now + 600_000, 0L))
        assertEquals(listOf(InfoPart.Time), InfoPriority.order(faces, true, ms("2026-11-15T05:59:00"), 5, null, 0L))
        assertFalse(InfoPriority.isNight(6))
    }

    @Test
    fun anEventWithinTheHourStaysAlone() {
        val now = ms("2026-11-15T10:00:00")
        assertEquals(listOf(InfoPart.Calendar), InfoPriority.order(faces, true, now, 10, now + 30 * 60_000, 0L))
        // An event just begun still counts; one far away does not.
        assertEquals(listOf(InfoPart.Calendar), InfoPriority.order(faces, true, now, 10, now - 5 * 60_000, 0L))
        assertEquals(faces, InfoPriority.order(faces, true, now, 10, now + 3 * 3_600_000, 0L))
    }

    @Test
    fun anAlarmWithinTheHourStaysAloneAndTheNearerOfTwoComesFirst() {
        val now = ms("2026-11-15T07:00:00")
        assertEquals(listOf(InfoPart.Alarm), InfoPriority.order(faces, true, now, 7, null, now + 20 * 60_000))
        assertEquals(listOf(InfoPart.Alarm, InfoPart.Calendar), InfoPriority.order(faces, true, now, 7, now + 40 * 60_000, now + 10 * 60_000))
    }

    @Test
    fun smartDoesNotCountWhatIsNotAmongTheFaces() {
        val now = ms("2026-11-15T10:00:00")
        val only = listOf(InfoPart.Time, InfoPart.Photos)
        assertEquals(only, InfoPriority.order(only, true, now, 10, now + 600_000, now + 300_000))
        assertEquals(listOf(InfoPart.Time), InfoPriority.order(listOf(InfoPart.Time), true, now, 3, null, 0L))
    }

    // --- the photos of this day

    @Test
    fun aPhotoOfThisDayInAnEarlierYearIsAMemory() {
        val now = ms("2026-11-15T10:00:00")
        assertTrue(PhotoMemories.isMemory(ms("2023-11-15T18:20:00"), now, rome))
        assertTrue(PhotoMemories.isMemory(ms("2010-11-15T00:05:00"), now, rome))
    }

    @Test
    fun aPhotoOfThisYearOrAnotherDayIsNot() {
        val now = ms("2026-11-15T10:00:00")
        assertFalse(PhotoMemories.isMemory(ms("2026-11-15T08:00:00"), now, rome))
        assertFalse(PhotoMemories.isMemory(ms("2024-11-14T23:59:00"), now, rome))
        assertFalse(PhotoMemories.isMemory(ms("2024-12-15T10:00:00"), now, rome))
        assertFalse(PhotoMemories.isMemory(0L, now, rome))
    }

    @Test
    fun theDayIsTheDayWhereThePhoneIsNotInGreenwich() {
        // 23:30 on 14 November in Rome is still the 14th; the same moment in Tokyo is the 15th.
        val taken = ms("2024-11-14T23:30:00")
        val now = ms("2026-11-14T12:00:00")
        assertTrue(PhotoMemories.isMemory(taken, now, rome))
        assertFalse(PhotoMemories.isMemory(taken, now, ZoneId.of("Asia/Tokyo")))
    }
}
