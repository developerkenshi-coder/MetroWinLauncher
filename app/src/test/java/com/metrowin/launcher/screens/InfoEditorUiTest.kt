package com.metrowin.launcher.screens

import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPace
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.toShareText
import com.metrowin.launcher.ui.start.InfoCategory
import com.metrowin.launcher.ui.start.InfoEditor
import com.metrowin.launcher.ui.start.InfoEditorEnv
import com.metrowin.launcher.ui.start.InfoPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The editor of the info tile: a home of categories, a pivot of pages in each, and every choice kept at once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class InfoEditorUiTest : ScreenshotBase() {
    private val changes = ArrayList<InfoConfig>()
    private var closed = 0
    private var page by mutableStateOf<InfoPage?>(null)
    private val item = AppItem("shortcut:info:1", "InfoTile", Launch.Internal("info"))

    private fun open(initial: InfoConfig = InfoConfig(), at: InfoPage? = null, isNew: Boolean = false, height: Int = 760) {
        page = at
        show(size = androidx.compose.ui.unit.DpSize(360.dp, height.dp)) { InfoEditor(item, initial, LiveTileData(), page, { page = it }, InfoEditorEnv(), { changes += it }, { closed++ }, isNew) }
        advance(900)
    }

    private fun click(tag: String) {
        scrollIntoView(tag)
        compose.onNodeWithTag(tag).performClick()
        advance(300)
    }

    @Test
    fun theHomeListsTheCategoriesAndTheTileIsOnShow() {
        open()
        compose.onNodeWithTag("info:preview").assertIsDisplayed()
        InfoCategory.entries.forEach { compose.onNodeWithTag("info:category:${it.name}").assertExists() }
    }

    @Test
    fun aCategoryOpensAsAPivotOfItsPages() {
        open()
        click("info:category:Faces")
        advance(900)
        assertEquals(InfoPage.FacesDay, page)
        compose.onNodeWithTag("info:page:FacesDay").assertExists()
        compose.onNodeWithTag("info:title").assertExists()
    }

    @Test
    fun theInfoOfACategoryIsSaid() {
        open()
        compose.onNodeWithTag("info:cat:Order").performClick()
        advance(300)
        compose.onNodeWithTag("info:cat:Order:text").assertExists()
    }

    @Test
    fun theFacesAreSwitchedOnAndOffAndTheLastOneStays() {
        open(InfoConfig(parts = listOf(InfoPart.Time)), at = InfoPage.FacesDay)
        click("info:time")
        assertTrue(changes.isEmpty() || changes.last().parts == listOf(InfoPart.Time))
        click("info:alarm")
        assertEquals(listOf(InfoPart.Time, InfoPart.Alarm), changes.last().parts)
        click("info:time")
        assertEquals(listOf(InfoPart.Alarm), changes.last().parts)
    }

    @Test
    fun aFaceThatNeedsAPermissionSaysSoAndAsksForIt() {
        open(at = InfoPage.FacesDay)
        compose.onNodeWithTag("info:status:calendar").assertExists()
        compose.onNodeWithTag("info:allow:calendar").assertExists()
    }

    @Test
    fun theFacesAreMovedInTheOrder() {
        open(at = InfoPage.OrderList)
        click("info:down:time")
        assertEquals(listOf(InfoPart.Calendar, InfoPart.Time, InfoPart.Photos), changes.last().parts)
        click("info:up:photos")
        assertEquals(listOf(InfoPart.Calendar, InfoPart.Photos, InfoPart.Time), changes.last().parts)
    }

    @Test
    fun thePaceAndTheEachFaceForAsLongAsItTakesAreChosen() {
        open(at = InfoPage.OrderList)
        click("info:pace:2")
        assertEquals(InfoPace.Fast, changes.last().pace)
        click("info:dwell")
        assertFalse(changes.last().dwell)
    }

    @Test
    fun theSmartSwitchesAndTheHoursAreKept() {
        open(at = InfoPage.Smart)
        click("info:smart")
        assertTrue(changes.last().smart)
        click("info:saver")
        assertTrue(changes.last().saver)
        click("info:soon:minus")
        assertEquals(30, changes.last().soonMinutes)
        click("info:night0:plus")
        assertEquals(0, changes.last().nightFrom)
        click("info:smartbattery")
        assertFalse(changes.last().smartBattery)
    }

    @Test
    fun anHourOfTheDayAndARuleAreAdded() {
        // A tall screen: the dialog of a rule has all its fields in view.
        open(at = InfoPage.Plans, height = 1600)
        click("info:slot:add")
        assertEquals(1, changes.last().slots.size)
        click("info:rule:add")
        compose.onNodeWithTag("info:ruledialog").assertExists()
        advance(600)
        compose.onNodeWithTag("info:rule:done").performClick()
        compose.waitForIdle()
        advance(300)
        compose.onNodeWithTag("info:ruledialog").assertDoesNotExist()
        assertEquals(1, changes.last().rules.size)
        click("info:slot:0:remove")
        assertTrue(changes.last().slots.isEmpty())
    }

    @Test
    fun theClockSwitchesAreKept() {
        open(at = InfoPage.ClockPage)
        click("info:analog")
        assertTrue(changes.last().analog)
        click("info:quicktimers")
        assertTrue(changes.last().quickTimers)
        click("info:clockphoto")
        assertTrue(changes.last().photoBackground)
    }

    @Test
    fun thePrivacySwitchesAreKept() {
        open(at = InfoPage.PrivacyPage)
        click("info:hidetitles")
        assertTrue(changes.last().hideTitles)
        click("info:hidephotos")
        assertTrue(changes.last().hidePhotos)
        click("info:kids")
        assertTrue(changes.last().kids)
    }

    @Test
    fun thePhotosChoicesAreKept() {
        open(at = InfoPage.PhotosPage)
        click("info:favorites")
        assertTrue(changes.last().favorites)
        click("info:noshots")
        assertTrue(changes.last().noShots)
    }

    @Test
    fun theMemoriesTakeTheDaysAround() {
        open(at = InfoPage.MemoriesPage)
        click("info:memdays:plus")
        assertEquals(1, changes.last().memoryDays)
    }

    @Test
    fun theLookChoicesAreKept() {
        open(at = InfoPage.LookPage)
        click("info:dots")
        assertTrue(changes.last().dots)
        click("info:tint")
        assertTrue(changes.last().tint)
        click("info:layout:1")
        assertEquals(com.metrowin.launcher.model.InfoLayout.Dashboard, changes.last().layout)
    }

    @Test
    fun theAutomaticChoicesAreKept() {
        open(at = InfoPage.AutoPage)
        click("info:dnd")
        assertTrue(changes.last().dnd)
        click("info:wifi")
        assertEquals(1, changes.last().triggers.size)
        click("info:bt")
        assertEquals(2, changes.last().triggers.size)
        click("info:brief:plus")
        assertEquals(6, changes.last().briefHour)
    }

    @Test
    fun aNewTileFirstOffersTheModels() {
        open(isNew = true)
        compose.onNodeWithTag("info:models").assertExists()
        compose.onNodeWithTag("info:model:night").performClick()
        advance(300)
        assertTrue(changes.last().dnd)
        assertEquals(listOf(InfoPart.Time, InfoPart.Alarm, InfoPart.Battery), changes.last().parts)
    }

    @Test
    fun aChoiceFromTextIsUsedAndAnythingElseIsRefused() {
        open(at = InfoPage.SharePage)
        scrollIntoView("info:import:use")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("hello")
        click("info:import:use")
        compose.onNodeWithTag("info:import:wrong").assertExists()
        assertTrue(changes.isEmpty())
    }

    @Test
    fun aSharedTextIsTakenBack() {
        val text = InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Todo), smart = true).toShareText()
        open(at = InfoPage.SharePage)
        scrollIntoView("info:import:use")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(text)
        click("info:import:use")
        assertEquals(listOf(InfoPart.Time, InfoPart.Todo), changes.last().parts)
        assertTrue(changes.last().smart)
    }

    @Test
    fun backToTheFirstChoiceAndDone() {
        open(InfoConfig(parts = listOf(InfoPart.Weather), smart = true), at = InfoPage.SharePage)
        click("info:reset")
        assertEquals(InfoConfig(), changes.last())
        page = null
        advance(900)
        scrollIntoView("info:done")
        compose.onNodeWithTag("info:done").performClick()
        assertEquals(1, closed)
    }
}
