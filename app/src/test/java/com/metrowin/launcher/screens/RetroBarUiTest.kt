package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.ActionBarStyle
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.LocalActionBar
import com.metrowin.launcher.ui.start.EditBar
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Retro action bar: the application bar of Windows Phone 8.1. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class RetroBarUiTest : ScreenshotBase() {
    private val clicked = mutableListOf<String>()

    private fun item(name: String) = AppBarItem("x", name) { clicked += name }

    private fun showBar(style: ActionBarStyle = ActionBarStyle.Retro, content: @androidx.compose.runtime.Composable () -> Unit) {
        show { CompositionLocalProvider(LocalActionBar provides style) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { content() } } }
        advance(300)
    }

    @Test
    fun theClosedBarHasRoundButtonsWithoutNamesAndTheDots() {
        showBar { AppBar(listOf(item("Reply"), item("Delete"))) }
        compose.onNodeWithTag("retro:button:0").assertExists()
        compose.onNodeWithTag("retro:button:1").assertExists()
        compose.onNodeWithTag("retro:more").assertExists()
        compose.onAllNodesWithText("Reply").assertCountEquals(0)
    }

    @Test
    fun theDotsOpenTheBarWithTheNamesAndTheLowercaseTextItems() {
        // Five items: the first four are buttons, the fifth a text item.
        showBar { AppBar(listOf(item("One"), item("Two"), item("Three"), item("Four"), item("Fifth"))) }
        compose.onAllNodesWithTag("retro:button:4").assertCountEquals(0)
        compose.onAllNodesWithText("fifth").assertCountEquals(0)
        compose.onNodeWithTag("retro:more").performClick()
        compose.waitForIdle()
        advance(500)
        capture("retro_bar_open")
        compose.onNodeWithText("One").assertExists()
        compose.onNodeWithText("fifth").assertExists()
        compose.onNodeWithTag("retro:item:0").performClick()
        assertEquals(listOf("Fifth"), clicked)
        // A touch on an item closes the bar.
        compose.waitForIdle()
        advance(500)
        compose.onAllNodesWithText("One").assertCountEquals(0)
    }

    @Test
    fun theSuperBarStaysAsItWasAndHasNoDots() {
        showBar(ActionBarStyle.Super) { AppBar(listOf(item("Reply"), item("Delete"))) }
        compose.onAllNodesWithTag("retro:more").assertCountEquals(0)
        compose.onNodeWithText("Reply").assertExists()
    }

    @Test
    fun theSuperBarShowsTheMenuUnderItsOwnDots() {
        showBar(ActionBarStyle.Super) { AppBar(listOf(item("Reply")), menu = listOf(item("Flag"))) }
        compose.onAllNodesWithText("Flag").assertCountEquals(0)
        compose.onNodeWithText("more").performClick()
        compose.waitForIdle()
        advance(300)
        compose.onNodeWithText("Flag").assertExists()
    }

    @Test
    fun theEditBarOfStartInRetroHasItsSizesAsTextItems() {
        var chosen: TileSize? = null
        showBar {
            EditBar(size = TileSize.Medium, page = 0, pageCount = 1, onSize = { chosen = it }, onMovePage = {}, onDone = {}, onAdd = {}, onFolder = {})
        }
        compose.onNodeWithTag("retro:more").performClick()
        compose.waitForIdle()
        advance(500)
        compose.onNodeWithText("wide").assertExists()
        compose.onNodeWithText("wide").performClick()
        assertEquals(TileSize.Wide, chosen)
    }
}
