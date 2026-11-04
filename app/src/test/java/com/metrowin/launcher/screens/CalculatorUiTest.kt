package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.calculator.CalculatorScreen
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

    @Test
    fun theKeysTypeAndWorkOutAnExpression() {
        show { CalculatorScreen() }
        advance(1_000)
        compose.onNodeWithTag("calc:expression", useUnmergedTree = true).assertTextEquals("0")
        press("calc:7", "calc:×", "calc:8", "calc:+", "calc:2")
        compose.onNodeWithTag("calc:expression", useUnmergedTree = true).assertTextEquals("7×8+2")
        compose.onNodeWithTag("calc:preview", useUnmergedTree = true).assertTextEquals("= 58")
        capture("calculator_typing")
        press("calc:equals")
        compose.onNodeWithTag("calc:expression", useUnmergedTree = true).assertTextEquals("58")
        compose.onNodeWithTag("calc:top", useUnmergedTree = true).assertTextEquals("7×8+2 =")
        press("calc:clear", "calc:1", "calc:div", "calc:0", "calc:equals")
        compose.onNodeWithTag("calc:top", useUnmergedTree = true).assertTextEquals("Cannot do that operation")
    }
}
