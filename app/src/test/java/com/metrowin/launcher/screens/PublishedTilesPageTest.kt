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
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ThirdPartyTile
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class PublishedTilesPageTest : ScreenshotBase() {
    private var current = LauncherSettings()
    private val cleared = mutableListOf<String>()

    @Composable
    private fun Page(tiles: List<ThirdPartyTile>) {
        var settings by remember { mutableStateOf(current) }
        val away = remember { Animatable(0f) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings,
                onChange = { change ->
                    settings = change(settings)
                    current = settings
                },
                apps = listOf(AppItem("a", "Mail", Launch.Action("x"), packageName = "com.example.mail")),
                publishedTiles = tiles,
                onClearPublishedTile = { cleared += it },
            ),
            section = SettingsSection.LiveTiles,
            onSectionChange = {},
            sectionAway = away,
        )
    }

    @Test
    fun withNothingPublishedThePageSaysSo() {
        show { Page(emptyList()) }
        advance(500)
        tapScrolled("tiles:thirdparty")
        advance(100)
        assertFalse(current.thirdPartyTiles)
        compose.onNodeWithTag("tiles:none").assertExists()
    }

    @Test
    fun publishedTilesAreListedWithTheNameOfTheAppAndCanBeCleared() {
        show { Page(listOf(ThirdPartyTile("com.example.mail", "Inbox", "3 new", 3, 1), ThirdPartyTile("com.example.x", "", "", 0, 0))) }
        advance(500)
        capture("settings_published_tiles")
        compose.onAllNodesWithTag("tiles:none").assertCountEquals(0)
        compose.onNodeWithTag("tiles:published:com.example.mail").assertExists()
        compose.onNodeWithTag("tiles:published:com.example.x").assertExists()
        tapScrolled("tiles:clear:com.example.mail")
        assertEquals(listOf("com.example.mail"), cleared)
    }
}
