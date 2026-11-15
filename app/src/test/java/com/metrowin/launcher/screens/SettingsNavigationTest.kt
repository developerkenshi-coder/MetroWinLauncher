package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.ui.settings.SettingsCategory
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Moving around the settings: the home lists the categories, a category is a pivot of its pages, Back lands where you came from, the
 * search says where a setting is, a wide screen shows the list and the category together, and big text keeps everything reachable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SettingsNavigationTest : ScreenshotBase() {
    private var section by mutableStateOf<SettingsSection?>(null)
    private var target by mutableStateOf<String?>(null)

    /** The settings held the way LauncherApp holds them: the open page and the setting it was opened for. */
    @Composable
    private fun Settings() {
        SettingsScreen(
            env = SettingsEnv(LauncherSettings(), onChange = {}),
            section = section, onSectionChange = { section = it },
            sectionAway = remember { Animatable(if (section != null) 0f else 1f) },
            target = target, onTargetChange = { target = it },
        )
    }

    private fun type(query: String) {
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("settings:search"))).performTextInput(query)
        compose.waitForIdle()
        advance(300)
    }

    @Test
    fun aCategoryOpensOnItsFirstPageAndAPivotSwipeTellsTheCaller() {
        section = null
        show { Settings() }
        advance(1_500)
        tapScrolled("settings:category:Gestures")
        advance(1_000)
        assertEquals("the first page of the category", SettingsSection.Gestures, section)
        compose.onNodeWithTag("gestures:edgepull").assertExists()
        // The next page of the pivot is the next one of the category, and the caller (who saves the page) is told.
        compose.onRoot().performTouchInput { swipeLeft() }
        advance(1_000)
        assertEquals(SettingsSection.Feel, section)
        compose.onNodeWithTag("gestures:sound").assertExists()
    }

    @Test
    fun backFromAPageLandsOnTheHomeWithTheSearchYouCameFrom() {
        section = null
        target = null
        show { Settings() }
        advance(600)
        type("icon pack")
        compose.onNodeWithTag("settings:result:icons.pack").performClick()
        compose.waitForIdle()
        advance(900)
        assertEquals(SettingsSection.Icons, section)
        assertEquals("icons.pack", target)
        // Back closes the category (the caller sets null): the results are there again, the same ones.
        section = null
        compose.waitForIdle()
        advance(900)
        assertNull(target)
        compose.onNodeWithTag("settings:result:icons.pack").assertExists()
    }

    @Test
    fun theSearchSaysWhereASettingIsAsCategoryAndPage() {
        section = null
        target = null
        show { Settings() }
        advance(600)
        type("oled")
        compose.onNodeWithTag("settings:result:theme.oled").assertExists()
        compose.onNodeWithText("look and themes › theme and colors").assertExists()
        // A category with one page is named once.
        type("")
    }

    @Test
    fun aCategoryIsFoundByItsNameToo() {
        section = null
        show { Settings() }
        advance(600)
        type("privacy")
        compose.onNodeWithTag("settings:result:cat:Privacy").assertExists()
        compose.onNodeWithTag("settings:result:cat:Privacy").performClick()
        compose.waitForIdle()
        advance(900)
        assertEquals(SettingsSection.Privacy, section)
    }

    @Test
    fun bigTextKeepsTheHomeAndTheCategoryReachable() {
        section = null
        show(textScale = 1.3f) { Settings() }
        advance(1_500)
        capture("settings_home_big_text")
        SettingsCategory.entries.forEach { compose.onNodeWithTag("settings:category:${it.name}").assertExists() }
        tapScrolled("settings:category:Apps")
        advance(1_000)
        // Three pages in the pivot: the title of the third is in the tree and the page under it is the first.
        compose.onNodeWithText("search").assertExists()
        compose.onNodeWithTag("apps:frequent").assertExists()
    }
}

/** An expanded window (840 dp and more) keeps the list at the left and opens the category beside it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w900dp-h700dp-xxhdpi")
class SettingsTwoPanesTest : ScreenshotBase() {
    private var section by mutableStateOf<SettingsSection?>(null)

    @Composable
    private fun Settings() {
        SettingsScreen(
            env = SettingsEnv(LauncherSettings(), onChange = {}),
            section = section, onSectionChange = { section = it },
            sectionAway = remember { Animatable(if (section != null) 0f else 1f) },
        )
    }

    @Test
    fun aWideScreenShowsTheListAndTheCategoryTogether() {
        section = SettingsSection.Start
        show(size = DpSize(900.dp, 700.dp)) { Settings() }
        advance(1_500)
        capture("settings_two_panes")
        compose.onNodeWithTag("settings:category:Look").assertExists()
        compose.onNodeWithTag("settings:title").assertExists()
        compose.onNodeWithTag("start:pages:plus").assertExists()
        tapScrolled("settings:category:Gestures")
        advance(1_000)
        assertEquals(SettingsSection.Gestures, section)
        compose.onNodeWithTag("gestures:edgepull").assertExists()
        // The list stays where it was: the next category is one touch away, with the first page of its pivot open.
        compose.onNodeWithTag("settings:category:Gestures").assertExists()
    }
}
