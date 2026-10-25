package com.metrowin.launcher.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.AppCategories
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.AppShortcut
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppContextMenu
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.apps.MenuState
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The groups and the chips on top of the app list, the extra entries of the menu, and the settings that go with them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class AppListGroupsUiTest : ScreenshotBase() {
    private fun app(id: String, label: String, category: AppCategory? = null) =
        AppItem(id, label, Launch.Action("x"), category = category)

    private val alpha = app("app:alpha", "Alpha", AppCategory.Game)
    private val bravo = app("app:bravo", "Bravo", AppCategory.Social)
    private val charlie = app("app:charlie", "Charlie")
    private val all = AppSections.sort(listOf(alpha, bravo, charlie))

    // --- the list

    @Composable
    private fun List(
        category: AppCategory?, onCategory: (AppCategory?) -> Unit,
        frequent: List<AppItem> = emptyList(), fresh: List<AppItem> = emptyList(),
    ) {
        val shown = if (category == null) all else AppCategories.filter(all, category)
        val model = remember(category) { AppListModel(AppSections.group(shown), frequent, fresh, AppCategories.present(all)) }
        AppListScreen(
            model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
            onLaunch = {}, onLongPress = { _, _, _ -> }, onHeaderClick = {},
            query = "", onQueryChange = {}, results = emptyList(),
            categories = AppCategories.present(all), category = category, onCategory = onCategory,
        )
    }

    @Test
    fun theChipsFilterTheListByCategory() {
        var chosen: AppCategory? = null
        show { List(chosen, { chosen = it }) }
        advance(900)
        capture("list_categories")
        compose.onNodeWithTag("cat:all").assertExists()
        compose.onNodeWithTag("cat:game").assertExists()
        compose.onNodeWithTag("cat:social").assertExists()
        compose.onAllNodesWithTag("cat:maps").assertCountEquals(0) // no app of that kind: no chip
        compose.onNodeWithTag("app:app:charlie").assertExists()

        compose.onNodeWithTag("cat:social").performClick()
        compose.waitForIdle()
        assertEquals(AppCategory.Social, chosen)
    }

    @Test
    fun aChosenCategoryShowsOnlyItsAppsAndATouchOnTheChipTakesItOff() {
        var chosen: AppCategory? = AppCategory.Game
        show { List(chosen, { chosen = it }) }
        advance(900)
        compose.onNodeWithTag("app:app:alpha").assertExists()
        compose.onAllNodesWithTag("app:app:bravo").assertCountEquals(0)
        compose.onAllNodesWithTag("app:app:charlie").assertCountEquals(0)
        compose.onNodeWithTag("cat:game").performClick()
        compose.waitForIdle()
        assertEquals(null, chosen)
    }

    @Test
    fun theMostUsedAndNewGroupsShowAtTheTopWithTheirOwnRows() {
        show { List(null, {}, frequent = listOf(bravo), fresh = listOf(charlie)) }
        advance(900)
        capture("list_groups")
        compose.onNodeWithTag("app:f:app:bravo").assertExists()
        compose.onNodeWithTag("app:n:app:charlie").assertExists()
        // The same app is also in the alphabetical list, apart from the group.
        compose.onNodeWithTag("app:app:bravo").assertExists()
    }

    // --- the menu

    private fun menu(
        shortcuts: List<AppShortcut> = emptyList(), renamed: Boolean = false, locked: Boolean? = null, withInfo: Boolean = true,
        calls: MutableList<String>,
    ) = show {
        AppContextMenu(
            state = MenuState(alpha, top = 300f, height = 190f), pinned = false,
            onPinToggle = {}, onUninstall = {}, onDismiss = {},
            shortcuts = shortcuts, onShortcut = { calls += "shortcut:${it.id}" },
            onRename = { calls += "rename" },
            onResetName = if (renamed) ({ calls += "reset" }) else null,
            onInfo = if (withInfo) ({ calls += "info" }) else null,
            locked = locked, onToggleLock = { calls += "lock" },
        )
    }

    private val user = android.os.Process.myUserHandle()

    @Test
    fun theMenuOffersTheShortcutsOfTheAppAndTheNewEntries() {
        val calls = mutableListOf<String>()
        menu(shortcuts = listOf(AppShortcut("compose", "pkg", "New message", user), AppShortcut("scan", "pkg", "Scan code", user)), locked = false, calls = calls)
        advance(300)
        capture("menu_with_shortcuts")
        compose.onNodeWithTag("menu:shortcut:compose").performClick()
        compose.onNodeWithTag("menu:rename").performClick()
        compose.onNodeWithTag("menu:lock").performClick()
        compose.onNodeWithTag("menu:info").performClick()
        assertEquals(listOf("shortcut:compose", "rename", "lock", "info"), calls)
        compose.onAllNodesWithTag("menu:resetname").assertCountEquals(0)
    }

    @Test
    fun anAppWithItsOwnNameHasNoResetAndOneWithAnotherNameHas() {
        val calls = mutableListOf<String>()
        menu(renamed = true, calls = calls)
        advance(300)
        compose.onNodeWithTag("menu:resetname").performClick()
        assertEquals(listOf("reset"), calls)
    }

    @Test
    fun theMenuOfAPageOfTheLauncherHasNoInfoOrLock() {
        val calls = mutableListOf<String>()
        menu(withInfo = false, locked = null, calls = calls)
        advance(300)
        compose.onAllNodesWithTag("menu:info").assertCountEquals(0)
        compose.onAllNodesWithTag("menu:lock").assertCountEquals(0)
    }

    // --- the settings

    private var current = LauncherSettings()

    @Composable
    private fun AppsPage(apps: List<AppItem>) {
        var settings by remember { mutableStateOf(current) }
        SettingsScreen(
            env = SettingsEnv(settings = settings, onChange = { change -> settings = change(settings); current = settings }, apps = apps),
            section = SettingsSection.Apps, onSectionChange = {}, sectionAway = remember { Animatable(0f) },
        )
    }

    private fun tap(tag: String) {
        scrollIntoView(tag, topMargin = 0.2f)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        advance(100)
    }

    @Test
    fun theSettingsSwitchTheGroupsAndTheChipsOff() {
        current = LauncherSettings()
        show { AppsPage(all) }
        advance(500)
        capture("settings_apps_list")
        tap("apps:frequent")
        assertFalse(current.showFrequentApps)
        tap("apps:new")
        assertFalse(current.showNewApps)
        tap("apps:categories")
        assertFalse(current.showCategories)
        tap("apps:categories")
        assertTrue(current.showCategories)
    }

    @Test
    fun renamedAndLockedAppsAreListedAndATouchUndoesThem() {
        current = LauncherSettings(appLabels = mapOf("app:alpha" to "Alfa"), lockedApps = setOf("app:bravo"))
        show { AppsPage(all) }
        advance(500)
        scrollIntoView("apps:renamed:app:alpha", topMargin = 0.2f)
        capture("settings_apps_renamed")
        tap("apps:renamed:app:alpha")
        assertTrue(current.appLabels.isEmpty())
        tap("apps:locked:app:bravo")
        assertTrue(current.lockedApps.isEmpty())
        compose.onNodeWithTag("apps:locked:none").assertExists()
    }
}
