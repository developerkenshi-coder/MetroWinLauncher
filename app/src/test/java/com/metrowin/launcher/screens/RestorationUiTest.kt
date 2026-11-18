package com.metrowin.launcher.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.metrowin.launcher.calendar.CalEvent
import com.metrowin.launcher.calendar.CalendarEnv
import com.metrowin.launcher.calendar.CalendarOptions
import com.metrowin.launcher.calendar.CalendarRepository
import com.metrowin.launcher.calendar.EventForm
import com.metrowin.launcher.calendar.LocalCal
import com.metrowin.launcher.notes.NotesScreen
import com.metrowin.launcher.todo.EditForm
import com.metrowin.launcher.todo.TodoItem
import com.metrowin.launcher.todo.TodoScreen
import com.metrowin.launcher.ui.theme.MetroTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the user was doing is still there when the screen is recreated (the process of the app was killed in the background, or the
 * system set the activity up again): the same restore the system does, made by [StateRestorationTester].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class RestorationUiTest : ScreenshotBase() {
    private val tester by lazy { StateRestorationTester(compose) }

    private fun start(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        tester.setContent { MetroTheme { content() } }
    }

    /** The text field inside a box with this tag (the tag is on the frame of the field, not on the node that takes the text). */
    private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    private fun waitForDisk() = repeat(20) { Thread.sleep(50); advance(100) }

    @Test
    fun aTodoTypedAndNotAddedIsKept() {
        start { TodoScreen() }
        advance(1_000)
        compose.onNode(hasSetTextAction()).performTextInput("Call Anna")
        advance(200)
        tester.emulateSavedInstanceStateRestore()
        advance(1_000)
        compose.onNode(hasSetTextAction()).assertTextContains("Call Anna")
    }

    @Test
    fun anItemInTheEditorKeepsWhatWasTyped() {
        val item = TodoItem(id = 7L, text = "Pay rent", note = "")
        start { EditForm(item, emptyList(), 0L, {}, {}) }
        advance(500)
        field("todo:edit:note").performTextInput("by Friday")
        field("todo:edit:newsub").performTextInput("bank")
        advance(200)
        tester.emulateSavedInstanceStateRestore()
        advance(500)
        field("todo:edit:text").assertTextContains("Pay rent")
        field("todo:edit:note").assertTextContains("by Friday")
        field("todo:edit:newsub").assertTextContains("bank")
    }

    @Test
    fun theNoteThatWasOpenIsOpenAgain() {
        start { NotesScreen() }
        advance(900)
        compose.onNodeWithTag("notes:empty").assertIsDisplayed()
        compose.onNodeWithText("New").performClick()
        advance(900)
        compose.onNodeWithTag("notes:title").performTextInput("Shopping")
        advance(900)
        waitForDisk()
        tester.emulateSavedInstanceStateRestore()
        waitForDisk()
        compose.onNodeWithTag("notes:title").assertTextContains("Shopping")
    }

    @Test
    fun anEventInTheFormKeepsWhatWasTyped() {
        val zone = ZoneId.systemDefault()
        val env = CalendarEnv(CalendarOptions(context), LocalDate.now(), zone, DayOfWeek.MONDAY, true, java.text.DateFormat.getTimeInstance(), emptyList())
        val event = CalEvent(0L, 0L, "", 1_700_000_000_000L, 1_700_003_600_000L, false)
        start { CompositionLocalProvider(LocalCal provides env) { EventForm(event, CalendarRepository(context), {}, {}, {}, {}) } }
        advance(500)
        field("cal:edit:title").performTextInput("Dentist")
        advance(200)
        tester.emulateSavedInstanceStateRestore()
        advance(500)
        field("cal:edit:title").assertTextContains("Dentist")
    }
}
