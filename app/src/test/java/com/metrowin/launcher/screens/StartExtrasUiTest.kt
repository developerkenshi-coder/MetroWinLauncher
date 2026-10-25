package com.metrowin.launcher.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.Countdown
import com.metrowin.launcher.data.GlobalSearch
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.start.CountdownDialog
import com.metrowin.launcher.ui.start.NoteDialog
import com.metrowin.launcher.ui.start.SettingPicker
import com.metrowin.launcher.ui.start.StartActions
import com.metrowin.launcher.ui.start.StartScreen
import com.metrowin.launcher.ui.start.TileColorSheet
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.start.UndoBar
import com.metrowin.launcher.ui.start.buildStartCells
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.toArgbInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The dialogs and faces for the color of a tile, notes, countdowns, settings on Start and the undo bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class StartExtrasUiTest : ScreenshotBase() {
    private val note = AppItem("n", "Note", Launch.Internal("note"), Glyph.OneNote, pinOnly = true, note = "buy milk\nand eggs")
    private fun countdown(daysFromToday: Long) = AppItem(
        "c", "Christmas", Launch.Internal("countdown"), Glyph.Calendar, pinOnly = true,
        targetDay = LocalDate.now().plusDays(daysFromToday).toEpochDay(),
    )

    private fun showFace(item: AppItem, size: TileSize = TileSize.Medium) = show {
        val w = if (size == TileSize.Wide) 408.dp else if (size == TileSize.Small) 96.dp else 200.dp
        val h = if (size == TileSize.Small) 96.dp else 200.dp
        Box(Modifier.size(w, h).background(DefaultAccent)) {
            TileFace(item, size, w, h, LiveTileData(), seed = 0, notifications = 0)
        }
    }

    // --- faces

    @Test
    fun aNoteTileShowsWhatIsWrittenOnIt() {
        showFace(note)
        advance(500)
        capture("tile_note")
        compose.onNodeWithTag("note:text").assertExists()
        compose.onAllNodesWithText("buy milk\nand eggs").assertCountEquals(1)
    }

    @Test
    fun aSmallNoteTileOnlyHasTheMark() {
        showFace(note, TileSize.Small)
        advance(500)
        compose.onAllNodesWithTag("note:text").assertCountEquals(0)
    }

    @Test
    fun aCountdownShowsTheDaysLeftAndWhatTheyAreTo() {
        showFace(countdown(62))
        advance(500)
        capture("tile_countdown")
        compose.onAllNodesWithText("62").assertCountEquals(1)
        compose.onAllNodesWithText("days to Christmas").assertCountEquals(1)
    }

    @Test
    fun aCountdownOnTheDayAndAfterIt() {
        showFace(countdown(0))
        advance(500)
        compose.onAllNodesWithText("today").assertCountEquals(1)
    }

    @Test
    fun aCountdownCountsUpAfterTheDate() {
        showFace(countdown(-3))
        advance(500)
        compose.onAllNodesWithText("3").assertCountEquals(1)
        compose.onAllNodesWithText("days since Christmas").assertCountEquals(1)
    }

    @Test
    fun oneDayLeftSaysDayInTheSingular() {
        showFace(countdown(1))
        advance(500)
        compose.onAllNodesWithText("day to Christmas").assertCountEquals(1)
    }

    @Test
    fun aSmallCountdownIsJustTheNumber() {
        showFace(countdown(10), TileSize.Small)
        advance(500)
        compose.onAllNodesWithText("10").assertCountEquals(1)
        compose.onAllNodesWithText("days to Christmas").assertCountEquals(0)
    }

    // --- a tile with its own color

    private val items = listOf("A", "B").mapIndexed { i, id -> AppItem(id, "App $id", Launch.Action("x"), Glyph.entries[i + 5]) }

    @Composable
    private fun Start(entries: MutableState<List<StartEntry>>) = StartScreen(
        cells = buildStartCells(entries.value, items, null), live = LiveTileData(), badges = emptyMap(), columns = 4, background = null,
        enterToken = 0, exitRequest = null, editingKey = null, scrollState = rememberScrollState(),
        actions = StartActions(
            onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {}, onMove = { _, _ -> }, onMerge = { _, _ -> },
            onBackgroundTap = {}, onNextPage = {},
        ),
    )

    private fun Bitmap.share(argb: Int): Float = pixels().count { it == argb }.toFloat() / (width * height)

    @Test
    fun aTileWithItsOwnColorIsDrawnInIt() {
        val red = MetroAccents[11].toArgbInt()
        val entries = mutableStateOf<List<StartEntry>>(listOf(Tile("A", TileSize.Wide, color = red), Tile("B", TileSize.Wide)))
        show { Start(entries) }
        advance(1500)
        val shot = capture("start_tile_colors")
        assertTrue("the colored tile is red", shot.share(red) > 0.05f)
        assertTrue("the other keeps the accent", shot.accentShare() > 0.05f)
    }

    // --- dialogs

    @Test
    fun theNoteIsWrittenAndSaved() {
        var saved: String? = null
        show { NoteDialog(initial = "", onDone = { saved = it }, onCancel = {}) }
        advance(300)
        compose.onNodeWithTag("note:field").performTextInput("milk")
        compose.onNodeWithTag("note:ok").performClick()
        assertEquals("milk", saved)
    }

    @Test
    fun theNoteDialogStartsFromTheTextThatIsThereAndCanBeCancelled() {
        var cancelled = 0
        show { NoteDialog(initial = "old text", onDone = {}, onCancel = { cancelled++ }) }
        advance(300)
        compose.onAllNodesWithText("old text").assertCountEquals(1)
        compose.onNodeWithTag("note:cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun theCountdownDateIsChosenWithTheButtons() {
        var result: Pair<String, Long>? = null
        val start = LocalDate.of(2026, 1, 31)
        show { CountdownDialog(initialTitle = "", initialDay = start.toEpochDay(), onDone = { t, d -> result = t to d }, onCancel = {}) }
        advance(300)
        capture("countdown_dialog")
        compose.onNode(hasSetTextAction()).performTextInput("Trip")
        compose.onNodeWithTag("countdown:day:plus").performClick()
        compose.onNodeWithTag("countdown:month:plus").performClick()
        compose.onNodeWithTag("countdown:year:plus").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("countdown:ok").performClick()
        // 31 January 2026 plus a day is 1 February, plus a month 1 March, plus a year 1 March 2027.
        assertEquals("Trip" to LocalDate.of(2027, 3, 1).toEpochDay(), result)
    }

    @Test
    fun theSettingsThatCanBePutOnStartAreListedAndOneIsPicked() {
        var picked: String? = null
        val defs = GlobalSearch.systemSettings.take(6).map { it to it.id.uppercase() }
        show { SettingPicker(settings = defs, onPick = { picked = it.id }, onDismiss = {}) }
        advance(300)
        capture("setting_picker")
        compose.onNodeWithTag("setting:wifi").performClick()
        assertEquals("wifi", picked)
    }

    @Test
    fun theColorSheetOffersTheTwentyColorsAndTheThemesOwn() {
        val picks = mutableListOf<Int>()
        show { TileColorSheet(current = MetroAccents[2].toArgbInt(), onPick = { picks += it }, onDismiss = {}) }
        advance(300)
        capture("tile_color_sheet")
        (0 until 20).forEach { compose.onNodeWithTag("color:$it").assertExists() }
        compose.onNodeWithTag("color:7").performClick()
        compose.onNodeWithTag("color:default").performClick()
        assertEquals(listOf(MetroAccents[7].toArgbInt(), 0), picks)
    }

    @Test
    fun theUndoBarSaysWhatWasDoneAndUndoesIt() {
        var undone = 0
        show { UndoBar(message = "Tile removed", onUndo = { undone++ }) }
        advance(300)
        capture("undo_bar")
        compose.onAllNodesWithText("Tile removed").assertCountEquals(1)
        compose.onNodeWithTag("undo:button").performClick()
        assertEquals(1, undone)
    }
}
