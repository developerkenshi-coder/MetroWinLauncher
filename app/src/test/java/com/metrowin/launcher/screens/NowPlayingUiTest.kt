package com.metrowin.launcher.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.music.NowPlayingScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Now Playing with no session at all (as when nothing plays, or the access is off): it says so, offers the access, and the options open. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NowPlayingUiTest : ScreenshotBase() {
    @Test
    fun withoutASessionItShowsTheEmptyPageAndTheWayToTurnTheAccessOn() {
        show { NowPlayingScreen() }
        advance(400)
        capture("np_empty")
        compose.onNodeWithTag("np:empty").assertExists()
        compose.onNodeWithTag("np:access").assertExists()
        compose.onAllNodesWithTag("np:title").assertCountEquals(0)
    }

    @Test
    fun theOptionsOpenFromTheMenuOfTheBar() {
        show { NowPlayingScreen() }
        advance(400)
        compose.onNodeWithText("…").performClick()
        advance(400)
        compose.onNodeWithText("options").performClick()
        // The player turns away and then the options turn in.
        advance(1500)
        compose.onNodeWithTag("np:options").assertExists()
    }
}
