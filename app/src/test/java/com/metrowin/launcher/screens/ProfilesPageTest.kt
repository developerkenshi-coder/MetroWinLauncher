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
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ProfileInfo
import com.metrowin.launcher.data.ProfileType
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The page for the work profile and the private space. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class ProfilesPageTest : ScreenshotBase() {
    private var current = LauncherSettings()
    private val paused = mutableListOf<Pair<Long, Boolean>>()

    @Composable
    private fun Page(profiles: List<ProfileInfo>) {
        var settings by remember { mutableStateOf(current) }
        val away = remember { Animatable(0f) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings,
                onChange = { change ->
                    settings = change(settings)
                    current = settings
                },
                profiles = profiles,
                onSetProfilePaused = { serial, value -> paused += serial to value },
            ),
            section = SettingsSection.Profiles,
            onSectionChange = {},
            sectionAway = away,
        )
    }

    private fun open(vararg profiles: ProfileInfo) {
        show { Page(profiles.toList()) }
        advance(500)
    }

    @Test
    fun aPhoneWithoutProfilesSaysSo() {
        open()
        compose.onNodeWithTag("profiles:none").assertExists()
        capture("settings_profiles_none")
    }

    @Test
    fun aRunningProfileCanBePausedAndAPausedOneResumed() {
        open(ProfileInfo(10, ProfileType.Work, paused = false), ProfileInfo(11, ProfileType.Private, paused = true))
        capture("settings_profiles")
        compose.onNodeWithTag("profiles:toggle:10").performClick()
        compose.onNodeWithTag("profiles:toggle:11").performClick()
        assertEquals(listOf(10L to true, 11L to false), paused)
    }

    @Test
    fun eachProfileHasItsOwnSwitchForItsAppsInTheList() {
        open(ProfileInfo(10, ProfileType.Work, paused = false), ProfileInfo(11, ProfileType.Private, paused = false))
        compose.onNodeWithTag("profiles:show:11").performClick()
        advance(100)
        assertFalse(current.showPrivateApps)
        assertEquals("the work apps are left alone", true, current.showWorkApps)
        compose.onNodeWithTag("profiles:show:10").performClick()
        advance(100)
        assertFalse(current.showWorkApps)
    }

    @Test
    fun withProfilesThereIsNoEmptyNotice() {
        open(ProfileInfo(10, ProfileType.Work, paused = false))
        compose.onAllNodesWithTag("profiles:none").assertCountEquals(0)
    }
}
