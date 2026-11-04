package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.metrowin.launcher.notes.NotesScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NotesUiTest : ScreenshotBase() {
    private fun settle() = advance(900)

    @Test
    fun aNoteIsWrittenReadAndItsBoxIsTicked() {
        show { NotesScreen() }
        settle()
        compose.onNodeWithTag("notes:empty").assertIsDisplayed()
        compose.onNodeWithText("new").performClick()
        settle()
        compose.onNodeWithTag("notes:title").performTextInput("Shopping")
        compose.onNodeWithTag("notes:body").performTextInput("# List\n☐ milk\n- eggs")
        settle()
        capture("notes_editor")
        compose.onNodeWithText("read").performClick()
        settle()
        compose.onNodeWithTag("notes:read").assertIsDisplayed()
        capture("notes_read")
        compose.onNodeWithTag("notes:box:1", useUnmergedTree = true).performClick()
        settle()
        compose.onNodeWithTag("notes:box:1", useUnmergedTree = true).assertTextEquals("☑")
    }

    @Test
    fun aSketchOpensWithItsTools() {
        show { NotesScreen() }
        settle()
        compose.onNodeWithText("sketch").performClick()
        settle()
        compose.onNodeWithTag("sketch:page").assertIsDisplayed()
        compose.onNodeWithTag("sketch:tool:Rect").performClick()
        settle()
        capture("notes_sketch")
        compose.onAllNodesWithTag("sketch:ink:2").onNodeWithTagFirst().performClick()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onNodeWithTagFirst() = this[0]
}
