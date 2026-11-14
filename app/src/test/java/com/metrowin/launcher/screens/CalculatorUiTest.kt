package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.calculator.CalculatorScreen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class CalculatorUiTest : ScreenshotBase() {
    private fun press(vararg tags: String) = tags.forEach {
        compose.onNodeWithTag(it).performClick()
        advance(400)
    }

    /** The files are read on another thread: the page appears a moment later. */
    private fun open() {
        show { CalculatorScreen() }
        repeat(200) {
            advance(100)
            compose.waitForIdle()
            if (compose.onAllNodes(hasTestTag("calc:expression"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(25)
        }
        error("the calculator did not open")
    }

    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun theKeysTypeAndWorkOutAnExpression() {
        open()
        text("calc:expression").assertTextEquals("0")
        press("calc:7", "calc:×", "calc:8", "calc:+", "calc:2")
        text("calc:expression").assertTextEquals("7×8+2")
        text("calc:preview").assertTextEquals("= 58")
        capture("calculator_typing")
        press("calc:equals")
        text("calc:expression").assertTextEquals("58")
        text("calc:top").assertTextEquals("7×8+2 =")
        press("calc:clear", "calc:1", "calc:div", "calc:0", "calc:equals")
        text("calc:top").assertTextEquals("Cannot do that operation")
    }

    @Test
    fun thousandsAreGroupedAndTheMemoryShowsItsValue() {
        open()
        press("calc:1", "calc:2", "calc:3", "calc:4", "calc:5")
        text("calc:expression").assertTextEquals("12 345")
        press("calc:mplus")
        press("calc:clear")
        text("calc:top").assertTextEquals("M  12 345")
    }

    @Test
    fun theLastKeyPressedIsNotLostWhenTheScreenIsLeft() {
        open()
        press("calc:4", "calc:2")
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        val kept = context.getSharedPreferences("metrowin_calc", android.content.Context.MODE_PRIVATE).getString("expression", null)
        assertEquals("42", kept)
    }
}
