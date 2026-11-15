package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The "i" of a category and of a setting: a box with the text, drawn over the settings, that closes again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SettingsInfoUiTest : ScreenshotBase() {
    private var section by mutableStateOf<SettingsSection?>(null)

    @Composable
    private fun Settings() {
        SettingsScreen(
            env = SettingsEnv(LauncherSettings(), onChange = {}),
            section = section, onSectionChange = { section = it },
            sectionAway = remember { Animatable(if (section != null) 0f else 1f) },
        )
    }

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun theInfoOfASettingOpensABoxWithItsTextAndClosesAgain() {
        section = SettingsSection.StartTheme
        show { Settings() }
        advance(1_500)
        tapScrolled("info:theme.oled")
        advance(1_500)
        capture("settings_info_box")
        compose.onNodeWithTag("info:theme.oled:text").assertExists()
        compose.onNodeWithTag("info:theme.oled:close").performClick()
        advance(900)
        assertEquals(0, count("info:theme.oled:text"))
    }

    @Test
    fun theInfoOfACategoryOpensFromTheHomeAndDoesNotOpenTheCategory() {
        section = null
        show { Settings() }
        advance(1_500)
        tapScrolled("info:cat:Look")
        advance(1_500)
        compose.onNodeWithTag("info:cat:Look:text").assertExists()
        assertEquals("the row did not open", null, section)
    }

    @Test
    fun withBigTextTheInfoSitsUnderTheSettingInsteadOfBesideIt() {
        section = SettingsSection.StartTheme
        show(textScale = 1.3f) { Settings() }
        advance(1_500)
        tapScrolled("info:theme.oled")
        advance(1_500)
        compose.onNodeWithTag("info:theme.oled:text").assertExists()
    }
}
