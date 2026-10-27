package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SyncPageTest : ScreenshotBase() {
    private var current = LauncherSettings()
    private var synced = 0

    @Composable
    private fun Page() {
        var settings by remember { mutableStateOf(current) }
        val away = remember { Animatable(0f) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings,
                onChange = { change ->
                    settings = change(settings)
                    current = settings
                },
                backupFolderName = "Drive",
                onSyncNow = { synced++ },
            ),
            section = SettingsSection.Backup,
            onSectionChange = {},
            sectionAway = away,
        )
    }

    private fun open(settings: LauncherSettings) {
        current = settings
        show { Page() }
        advance(500)
    }

    @Test
    fun withoutAFolderThePageSaysWhyThereIsNoSync() {
        open(LauncherSettings())
        compose.onNodeWithTag("sync:needs_folder").assertExists()
        compose.onAllNodesWithTag("sync:enable").assertCountEquals(0)
    }

    @Test
    fun withAFolderSyncCanBeTurnedOnAndAskedForAPassphrase() {
        open(LauncherSettings(backupFolder = "content://tree/drive"))
        // A long page: a slow finger, so that the page does not fling past the switch.
        tapScrolled("sync:enable", swipeMs = 1_200L)
        advance(500)
        assertTrue(current.syncEnabled)
        capture("settings_sync")
        compose.onNodeWithTag("sync:passphrase").assertExists()
        compose.onNodeWithTag("sync:now").assertIsNotEnabled()
    }

    @Test
    fun typingAPassphraseEnablesTheButtonAndTheButtonSyncs() {
        open(LauncherSettings(backupFolder = "content://tree/drive", syncEnabled = true))
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("sync:passphrase"))).performTextInput("correct horse")
        advance(100)
        assertEquals("correct horse", current.syncPassphrase)
        tapScrolled("sync:now")
        assertEquals(1, synced)
    }

    @Test
    fun theButtonWaitsForAPassphrase() {
        open(LauncherSettings(backupFolder = "content://tree/drive", syncEnabled = true))
        tapScrolled("sync:now")
        assertEquals(0, synced)
    }
}
