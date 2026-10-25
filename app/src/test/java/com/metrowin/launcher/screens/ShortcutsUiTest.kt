package com.metrowin.launcher.screens

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.WidgetProvider
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.start.AddMenu
import com.metrowin.launcher.ui.start.EditBar
import com.metrowin.launcher.ui.start.SiteDialog
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.start.WidgetPicker
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Adding a contact, a web page or a widget to Start. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class ShortcutsUiTest : ScreenshotBase() {
    // --- the add button and its menu

    @Test
    fun theEditBarHasAnAddButtonOnlyWhenItIsGivenOne() {
        var added = 0
        show {
            Box(Modifier.size(360.dp, 760.dp)) {
                EditBar(TileSize.Medium, page = 0, pageCount = 1, onSize = {}, onMovePage = {}, onDone = {}, onAdd = { added++ })
            }
        }
        advance(300)
        compose.onNodeWithTag("edit:add").performClick()
        assertEquals(1, added)
    }

    @Test
    fun withoutAnAddActionThereIsNoAddButton() {
        show { Box(Modifier.size(360.dp, 760.dp)) { EditBar(TileSize.Medium, 0, 1, {}, {}, {}) } }
        advance(300)
        compose.onAllNodesWithTag("edit:add").assertCountEquals(0)
    }

    @Test
    fun theAddMenuOffersAContactAWebsiteAndAWidget() {
        val chosen = mutableListOf<String>()
        show { AddMenu(onContact = { chosen += "contact" }, onSite = { chosen += "site" }, onWidget = { chosen += "widget" }, onDismiss = {}) }
        advance(300)
        capture("add_menu")
        compose.onNodeWithTag("add:contact").performClick()
        compose.onNodeWithTag("add:site").performClick()
        compose.onNodeWithTag("add:widget").performClick()
        assertEquals(listOf("contact", "site", "widget"), chosen)
    }

    // --- the web page dialog

    @Test
    fun theSiteDialogPassesWhatWasTypedAndClosesWhenTheAddressIsGood() {
        val got = mutableListOf<Pair<String, String>>()
        show { SiteDialog(onDone = { t, a -> got += t to a; true }, onCancel = {}) }
        advance(300)
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("News")
        fields[1].performTextInput("example.com")
        compose.onNodeWithTag("site:ok").performClick()
        assertEquals(listOf("News" to "example.com"), got)
        compose.onAllNodesWithTag("site:invalid").assertCountEquals(0)
    }

    @Test
    fun anAddressThatIsRefusedStaysInTheDialogWithAMessage() {
        show { SiteDialog(onDone = { _, _ -> false }, onCancel = {}) }
        advance(300)
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("hello")
        compose.onNodeWithTag("site:ok").performClick()
        advance(100)
        capture("site_dialog_invalid")
        compose.onNodeWithTag("site:invalid").assertExists()
    }

    @Test
    fun typingAgainTakesTheMessageAway() {
        show { SiteDialog(onDone = { _, _ -> false }, onCancel = {}) }
        advance(300)
        val address = compose.onAllNodes(hasSetTextAction())[1]
        address.performTextInput("hello")
        compose.onNodeWithTag("site:ok").performClick()
        advance(100)
        compose.onNodeWithTag("site:invalid").assertExists()
        address.performTextInput("x")
        advance(100)
        compose.onAllNodesWithTag("site:invalid").assertCountEquals(0)
    }

    // --- widgets

    private val clock = WidgetProvider("Clock", "Clock app", ComponentName("com.example.clock", ".ClockWidget"), 250, 110, needsSetup = false)
    private val notes = WidgetProvider("", "Notes", ComponentName("com.example.notes", ".NotesWidget"), 110, 110, needsSetup = true)

    @Test
    fun thePickerListsTheWidgetsAndHandsBackTheOneYouTouch() {
        val picked = mutableListOf<WidgetProvider>()
        show { WidgetPicker(listOf(clock, notes), onPick = { picked += it }) }
        advance(300)
        capture("widget_picker")
        compose.onNodeWithTag("widget:com.example.clock/.ClockWidget").performClick()
        assertEquals(listOf(clock), picked)
        // A widget without a name of its own is listed under the name of its app.
        // The row is one button for TalkBack, saying the name of the widget and then the name of its app.
        compose.onNodeWithTag("widget:com.example.notes/.NotesWidget").assertTextEquals("Notes", "Notes")
    }

    @Test
    fun withNoWidgetsThePickerSaysSo() {
        show { WidgetPicker(emptyList(), onPick = {}) }
        advance(300)
        compose.onNodeWithText("This phone has no widgets to add.").assertExists()
    }

    @Test
    fun aWidgetTileWithNoHostShowsItsGlyphAndName() {
        val widget = AppItem("shortcut:widget:7", "Clock", Launch.Widget(7), Glyph.Widget, pinOnly = true)
        show {
            Box(Modifier.size(200.dp).background(DefaultAccent)) {
                TileFace(widget, TileSize.Medium, 200.dp, 200.dp, LiveTileData(), seed = 0, notifications = 0)
            }
        }
        advance(300)
        compose.onNodeWithText("Clock").assertExists()
        assertTrue(isAccent(capture("tile_widget_placeholder").getPixel(12, 12)))
    }

    @Test
    fun aContactTileShowsItsNameBeforeThePhotoIsRead() {
        val contact = AppItem("shortcut:contact:1", "Giulia", Launch.Link("content://x"), Glyph.People, pinOnly = true, photoPath = "/no/such/photo.jpg")
        show {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.size(200.dp).background(DefaultAccent)) {
                    TileFace(contact, TileSize.Medium, 200.dp, 200.dp, LiveTileData(), seed = 0, notifications = 0)
                }
            }
        }
        advance(300)
        // The file is not there: the tile keeps its glyph and name.
        compose.onNodeWithText("Giulia").assertExists()
    }
}
