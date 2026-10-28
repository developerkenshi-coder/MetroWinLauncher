package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.UpdateState
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The updates block of the about page: the state, what the new version brings, and the two buttons. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class UpdateUiTest : ScreenshotBase() {
    @Composable
    private fun About(env: SettingsEnv) {
        val away = remember { Animatable(0f) }
        SettingsScreen(env, SettingsSection.About, onSectionChange = {}, sectionAway = away)
    }

    @Test
    fun aNewVersionShowsItsNotesAndTheButtonThatInstallsIt() {
        var checked = 0
        var installed = 0
        val state = UpdateState.Available("26.4S", "• New weather tile\n• Gallery choice")
        show { About(SettingsEnv(LauncherSettings(), onChange = {}, update = state, onCheckUpdate = { checked++ }, onInstallUpdate = { installed++ })) }
        advance(500)
        capture("settings_update")
        compose.onNodeWithTag("about:update:state").assertExists()
        compose.onNodeWithTag("about:update:notes").assertExists()
        compose.onNodeWithTag("about:update:check").performClick()
        compose.onNodeWithTag("about:update:install").performClick()
        assertEquals(1, checked)
        assertEquals(1, installed)
    }

    @Test
    fun withNothingNewThereAreNoNotesAndNoInstallButton() {
        show { About(SettingsEnv(LauncherSettings(), onChange = {}, update = UpdateState.UpToDate)) }
        advance(500)
        compose.onNodeWithTag("about:update:check").assertExists()
        compose.onAllNodesWithTag("about:update:notes").assertCountEquals(0)
        compose.onAllNodesWithTag("about:update:install").assertCountEquals(0)
    }
}
