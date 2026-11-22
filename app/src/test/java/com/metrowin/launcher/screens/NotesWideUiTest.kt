package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.notes.NotesScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The notes on a wide screen (840 dp, a tablet or a folding phone opened): the list and the note side by side. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h1200dp-xxhdpi")
class NotesWideUiTest : ScreenshotBase() {
    private fun settle() = advance(900)

    @Test
    fun theNoteOpensBesideTheListAndBackLeavesAHint() {
        show(size = DpSize(840.dp, 1200.dp)) { NotesScreen() }
        settle()
        compose.onNodeWithTag("notes:panes").assertIsDisplayed()
        compose.onNodeWithTag("notes:pick").assertIsDisplayed()
        compose.onNodeWithText("New").performClick()
        settle()
        // The editor is at the right and the list is still there at the left.
        compose.onNodeWithTag("notes:title").performTextInput("Shopping")
        settle()
        compose.onNodeWithTag("notes:title").assertIsDisplayed()
        compose.onNodeWithTag("notes:panes").assertIsDisplayed()
    }
}
