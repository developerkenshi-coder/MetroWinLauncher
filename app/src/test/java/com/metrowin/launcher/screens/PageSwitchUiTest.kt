package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.onNodeWithTag
import com.metrowin.launcher.ui.components.PageSwitch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class PageSwitchUiTest : ScreenshotBase() {
    private var page by mutableStateOf<String?>(null)

    @Test
    fun theListTurnsAwayForThePageAndComesBackWithIt() {
        show { PageSwitch(page, onBack = { page = null }, list = { Box(Modifier.fillMaxSize().semantics(mergeDescendants = true) { }.testTag("list")) }, detail = { Box(Modifier.fillMaxSize().semantics(mergeDescendants = true) { }.testTag("page:$it")) }) }
        compose.onNodeWithTag("list").assertExists()
        compose.onNodeWithTag("page:a").assertDoesNotExist()

        compose.runOnUiThread { page = "a" }
        compose.waitForIdle()
        advance(1500)
        compose.onNodeWithTag("page:a").assertExists()
        compose.onNodeWithTag("list").assertDoesNotExist()

        compose.runOnUiThread { page = null }
        compose.waitForIdle()
        advance(1500)
        compose.onNodeWithTag("list").assertExists()
        compose.onNodeWithTag("page:a").assertDoesNotExist()
    }
}
