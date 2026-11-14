package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.metrowin.launcher.todo.TodoScreen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class TodoUiTest : ScreenshotBase() {
    @Test
    fun anItemIsAddedAndTicked() {
        show { TodoScreen() }
        advance(1_000)
        compose.onNodeWithTag("todo:empty").assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Call Anna")
        compose.onNodeWithTag("todo:add").performClick()
        // The item is saved on another thread; give it the moment it takes, and the screen the frames to show it.
        repeat(20) { Thread.sleep(50); advance(100) }
        compose.onNodeWithTag("todo:item", useUnmergedTree = true).assertIsDisplayed()
        capture("todo_open")
        compose.onNodeWithTag("todo:box:1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("todo:box:1", useUnmergedTree = true).performClick()
        // The change is saved on another thread; give it the moment it takes, and the screen the frames to show it.
        repeat(20) { Thread.sleep(50); advance(100) }
        compose.onNodeWithTag("todo:empty").assertIsDisplayed()
    }

    @Test
    fun twoQuickAdditionsAreBothKept() {
        show { TodoScreen() }
        advance(1_000)
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("first")
        compose.onNodeWithTag("todo:add").performClick()
        // The field is emptied on the next frame; the test clock is paused, so it is given one.
        advance(200)
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("second")
        compose.onNodeWithTag("todo:add").performClick()
        repeat(20) { Thread.sleep(50); advance(100) }
        val store = com.metrowin.launcher.todo.TodoStore(context)
        val deadline = System.currentTimeMillis() + 3_000
        while (store.load().size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(listOf("first", "second"), store.load().map { it.text })
        assertEquals(listOf(1L, 2L), store.load().map { it.id })
    }
}
