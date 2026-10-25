package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.IconPackInfo
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class IconsPageTest : ScreenshotBase() {
    private var current = LauncherSettings()

    @Composable
    private fun Page(packs: List<IconPackInfo>) {
        var settings by remember { mutableStateOf(current) }
        val away = remember { Animatable(0f) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings,
                onChange = { change ->
                    settings = change(settings)
                    current = settings
                },
                iconPacks = packs,
            ),
            section = SettingsSection.Icons,
            onSectionChange = {},
            sectionAway = away,
        )
    }

    @Test
    fun withoutPacksThePageSaysSoAndStillOffersThemeFiles() {
        show { Page(emptyList()) }
        advance(500)
        capture("settings_icons_none")
        compose.onNodeWithTag("icons:none").assertExists()
        compose.onNodeWithTag("theme:export").assertExists()
        compose.onNodeWithTag("theme:import").assertExists()
    }

    @Test
    fun aPackIsChosenAndTheOwnIconsBroughtBack() {
        show { Page(listOf(IconPackInfo("com.pack.one", "Pack One"), IconPackInfo("com.pack.two", "Pack Two"))) }
        advance(500)
        capture("settings_icons")
        compose.onAllNodesWithTag("icons:none").assertCountEquals(0)
        compose.onNodeWithTag("icons:pack:2").performClick()
        advance(100)
        assertEquals("com.pack.two", current.iconPack)
        compose.onNodeWithTag("icons:pack:0").performClick()
        advance(100)
        assertEquals("", current.iconPack)
    }
}
