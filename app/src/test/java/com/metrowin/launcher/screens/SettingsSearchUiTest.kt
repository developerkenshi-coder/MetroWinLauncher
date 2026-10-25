package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.ContactMatch
import com.metrowin.launcher.data.GlobalResults
import com.metrowin.launcher.data.LauncherSettingMatch
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.TileMatch
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
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

/** Finding a setting from the box on top of the settings, from the search of the app list, and being taken to it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SettingsSearchUiTest : ScreenshotBase() {
    private var section by mutableStateOf<SettingsSection?>(null)
    private var target by mutableStateOf<String?>(null)

    /** The settings held the way LauncherApp holds them: the open section and the setting it was opened for. */
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

    // --- the box on top of the settings

    @Test
    fun typingListsTheSettingsThatMatchInPlaceOfTheSections() {
        section = null
        target = null
        show { Settings() }
        advance(600)
        compose.onNodeWithTag("settings:section:StartTheme").assertExists()
        type("oled")
        capture("settings_search_results")
        compose.onNodeWithTag("settings:result:theme.oled").assertExists()
        compose.onAllNodesWithTag("settings:section:StartTheme").assertCountEquals(0)
    }

    @Test
    fun touchingAResultOpensItsSectionForThatSetting() {
        section = null
        target = null
        show { Settings() }
        advance(600)
        type("oled")
        compose.onNodeWithTag("settings:result:theme.oled").performClick()
        compose.waitForIdle()
        advance(800)
        assertEquals(SettingsSection.StartTheme, section)
        assertEquals("theme.oled", target)
        compose.onNodeWithTag("theme:oled").assertExists()
    }

    @Test
    fun aSectionIsAResultToo() {
        section = null
        target = null
        show { Settings() }
        advance(600)
        type("gestures")
        compose.onNodeWithTag("settings:result:Gestures").assertExists()
        compose.onNodeWithTag("settings:result:Gestures").performClick()
        compose.waitForIdle()
        advance(800)
        assertEquals(SettingsSection.Gestures, section)
        assertNull("a whole section has no setting to scroll to", target)
    }

    @Test
    fun whenNothingMatchesThePageSaysSo() {
        section = null
        target = null
        show { Settings() }
        advance(600)
        type("zzzzzz")
        compose.onNodeWithTag("settings:search:none").assertExists()
        compose.onAllNodesWithTag("settings:section:StartTheme").assertCountEquals(0)
    }

    // --- being taken to the setting

    private fun visibleOnScreen(tag: String): Boolean {
        val screen = compose.onRoot().fetchSemanticsNode().size.height
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        return node.positionInRoot.y >= 0f && node.positionInRoot.y + node.size.height <= screen
    }

    @Test
    fun aPageOpenedForASettingScrollsToItAndAPageOpenedAloneStaysAtTheTop() {
        // The last setting of the start screen page is below the fold.
        section = SettingsSection.Start
        target = null
        show { Settings() }
        advance(3_000)
        assertTrue("without a target the last setting is out of sight", !visibleOnScreen("start:resetstart"))
    }

    @Test
    fun aPageOpenedForASettingBringsItIntoView() {
        section = SettingsSection.Start
        target = "start.reset"
        show { Settings() }
        compose.waitForIdle()
        advance(3_000)
        compose.waitForIdle()
        capture("settings_target_revealed")
        assertTrue("the setting asked for is on the screen", visibleOnScreen("start:resetstart"))
    }

    @Test
    fun aTargetOfAnotherSectionIsLeftAlone() {
        section = SettingsSection.Start
        target = "gest.edgepull"
        show { Settings() }
        compose.waitForIdle()
        advance(3_000)
        assertTrue("the page does not scroll for a setting that is not on it", !visibleOnScreen("start:resetstart"))
    }

    // --- the search of the app list

    private val note = AppItem("shortcut:note:1", "Spesa", Launch.Internal("note"), pinOnly = true, note = "latte\npane")

    @Composable
    private fun List(global: GlobalResults, onLaunch: (AppItem) -> Unit = {}, onOpenSetting: (LauncherSettingMatch) -> Unit = {}) {
        val model = remember { AppListModel(AppSections.group(emptyList())) }
        AppListScreen(
            model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
            onLaunch = onLaunch, onLongPress = { _, _, _ -> }, onHeaderClick = {},
            query = "pane", onQueryChange = {}, results = emptyList(), global = global, onOpenSetting = onOpenSetting,
        )
    }

    private val oled = LauncherSettingMatch("theme.oled", "StartTheme", "OLED black", "Launcher settings › start+theme")

    @Test
    fun theSettingsOfTheLauncherAndTheTilesOfStartAreRowsOfTheSearch() {
        show {
            List(
                GlobalResults(
                    launcherSettings = listOf(oled), tiles = listOf(TileMatch(note, "pane")),
                    contacts = listOf(ContactMatch("Anna", "content://c/1")),
                ),
            )
        }
        advance(1_200)
        capture("search_launcher_rows")
        compose.onNodeWithTag("result:lsetting:theme.oled").assertExists()
        compose.onNodeWithTag("result:tile:shortcut:note:1").assertExists()
        val setting = compose.onNodeWithTag("result:lsetting:theme.oled").fetchSemanticsNode().positionInRoot.y
        val tile = compose.onNodeWithTag("result:tile:shortcut:note:1").fetchSemanticsNode().positionInRoot.y
        val contact = compose.onNodeWithTag("result:contact:content://c/1").fetchSemanticsNode().positionInRoot.y
        assertTrue("the settings of the launcher come first, then Start, then the contacts", setting < tile && tile < contact)
    }

    @Test
    fun touchingASettingOfTheLauncherOpensItAndTouchingATileOpensTheTile() {
        val opened = mutableListOf<LauncherSettingMatch>()
        val launched = mutableListOf<AppItem>()
        show { List(GlobalResults(launcherSettings = listOf(oled), tiles = listOf(TileMatch(note, "pane"))), { launched += it }, { opened += it }) }
        advance(1_200)
        compose.onNodeWithTag("result:lsetting:theme.oled").performClick()
        assertEquals(listOf(oled), opened)
        compose.onNodeWithTag("result:tile:shortcut:note:1").performClick()
        assertEquals(listOf(note), launched)
        assertTrue("opening a setting does not launch anything", launched.size == 1)
    }

    @Test
    fun aSectionResultTagsItselfBySection() {
        val section = LauncherSettingMatch(null, "Gestures", "gestures", "Edge pulls, swipes, taps")
        show { List(GlobalResults(launcherSettings = listOf(section))) }
        advance(1_200)
        compose.onNodeWithTag("result:lsetting:section:Gestures").assertExists()
    }
}
