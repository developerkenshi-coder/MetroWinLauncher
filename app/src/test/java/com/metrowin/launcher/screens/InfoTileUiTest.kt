package com.metrowin.launcher.screens

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPace
import com.metrowin.launcher.ui.start.InfoTileDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The choice of the info tile: every switch is kept at once, and the zone and the album open their pickers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class InfoTileUiTest : ScreenshotBase() {
    private val changes = ArrayList<InfoConfig>()
    private var zoneAsked = 0
    private var albumAsked = 0
    private var closed = 0

    private fun open(initial: InfoConfig = InfoConfig()) {
        show { InfoTileDialog(initial, { changes += it }, { zoneAsked++ }, { albumAsked++ }, { closed++ }) }
        advance(400)
    }

    @Test
    fun theFacesAreSwitchedOnAndOffAndTheLastOneStays() {
        open(InfoConfig(time = true, calendar = true, photos = false))
        scrollIntoView("info:memories")
        compose.onNodeWithTag("info:memories").performClick()
        advance(200)
        assertTrue(changes.last().memories)
        scrollIntoView("info:alarm")
        compose.onNodeWithTag("info:alarm").performClick()
        advance(200)
        assertTrue(changes.last().alarm)
        scrollIntoView("info:time")
        compose.onNodeWithTag("info:time").performClick()
        advance(200)
        assertEquals(false, changes.last().time)
    }

    @Test
    fun theSmartAndBatterySwitchesAreKept() {
        open()
        scrollIntoView("info:smart")
        compose.onNodeWithTag("info:smart").performClick()
        advance(200)
        assertTrue(changes.last().smart)
        scrollIntoView("info:saver")
        compose.onNodeWithTag("info:saver").performClick()
        advance(200)
        assertTrue(changes.last().saver && changes.last().smart)
    }

    @Test
    fun thePaceIsChosen() {
        open()
        scrollIntoView("info:pace:fast")
        compose.onNodeWithTag("info:pace:fast").performClick()
        advance(200)
        assertEquals(InfoPace.Fast, changes.last().pace)
        compose.onNodeWithTag("info:pace:slow").performClick()
        advance(200)
        assertEquals(InfoPace.Slow, changes.last().pace)
    }

    @Test
    fun theZoneAndTheAlbumOpenTheirPickersAndCanBeReset() {
        open(InfoConfig(zone = "Asia/Tokyo", album = "5", albumName = "Trip"))
        scrollIntoView("info:zone")
        compose.onNodeWithTag("info:zone").performClick()
        assertEquals(1, zoneAsked)
        scrollIntoView("info:album")
        compose.onNodeWithTag("info:album").performClick()
        assertEquals(1, albumAsked)
        scrollIntoView("info:zone:reset")
        compose.onNodeWithTag("info:zone:reset").performClick()
        advance(200)
        assertEquals("", changes.last().zone)
        scrollIntoView("info:album:reset")
        compose.onNodeWithTag("info:album:reset").performClick()
        advance(200)
        assertEquals("", changes.last().album)
        assertEquals("", changes.last().albumName)
    }

    @Test
    fun doneCloses() {
        open()
        scrollIntoView("info:done")
        compose.onNodeWithTag("info:done").performClick()
        assertEquals(1, closed)
    }
}
