package com.metrowin.launcher.screens

import android.content.ComponentName
import android.os.Process
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.IconPackInfo
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.settings.IconPickDialog
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
class IconPickUiTest : ScreenshotBase() {
    private var current = LauncherSettings(iconPicks = mapOf("app:a" to "com.pack.one|chrome"))
    private var closed = false
    private val packs = listOf(IconPackInfo("com.pack.one", "Pack One"), IconPackInfo("com.pack.two", "Pack Two"))
    private val icons = listOf("chrome", "ic_gmail", "maps", "photos")

    @Composable
    private fun Dialog(ids: List<String>) {
        var settings by remember { mutableStateOf(current) }
        IconPickDialog(
            ids = ids, settings = settings, packs = packs,
            names = { icons },
            preview = { _, _, _ -> ImageBitmap(8, 8) },
            onChange = { change -> settings = change(settings); current = settings },
            onClose = { closed = true },
        )
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        advance(100)
        compose.waitForIdle()
        advance(300)
    }

    @Test
    fun anIconOfAPackIsPickedForOneApp() {
        show { Dialog(listOf("app:b")) }
        advance(600)
        capture("iconpick_packs")
        tap("iconpick:pack:1")
        advance(300)
        capture("iconpick_grid")
        compose.onAllNodesWithTag("iconpick:icon:maps").assertCountEquals(1)
        tap("iconpick:icon:maps")
        assertEquals("com.pack.two|maps", current.iconPicks["app:b"])
        assertTrue(closed)
    }

    @Test
    fun theGridIsSearchedByNameAndTheIconInUseIsMarked() {
        closed = false
        show { Dialog(listOf("app:a")) }
        advance(600)
        tap("iconpick:pack:0")
        compose.onNodeWithTag("iconpick:icon:chrome").assertIsSelected()
        compose.onNodeWithTag("iconpick:icon:maps").assertIsNotSelected()
        compose.onNode(hasSetTextAction()).performTextInput("gmail")
        advance(300)
        compose.onAllNodesWithTag("iconpick:icon:ic_gmail").assertCountEquals(1)
        compose.onAllNodesWithTag("iconpick:icon:maps").assertCountEquals(0)
        compose.onNodeWithTag("iconpick:icon:ic_gmail").assertHeightIsAtLeast(48.dp)
        compose.onNode(hasSetTextAction()).performTextInput("zzz")
        advance(300)
        compose.onNodeWithTag("iconpick:empty").assertIsDisplayed()
    }

    @Test
    fun theWholePackIsPickedWithoutADrawable() {
        closed = false
        show { Dialog(listOf("app:c")) }
        advance(600)
        tap("iconpick:pack:0")
        tap("iconpick:whole")
        assertEquals("com.pack.one", current.iconPicks["app:c"])
    }

    @Test
    fun severalAppsGetThePackAtOnce() {
        closed = false
        show { Dialog(listOf("app:d", "app:e")) }
        advance(600)
        // With several apps a pack is the whole choice: there is no grid.
        tap("iconpick:pack:1")
        assertEquals("com.pack.two", current.iconPicks["app:d"])
        assertEquals("com.pack.two", current.iconPicks["app:e"])
        assertTrue(closed)
    }

    @Test
    fun severalAppsAreBroughtBackToTheDefaultTogether() {
        current = current.copy(iconPicks = current.iconPicks + mapOf("app:d" to "com.pack.two", "app:e" to "com.pack.two"))
        show { Dialog(listOf("app:d", "app:e")) }
        advance(600)
        tap("iconpick:default")
        assertTrue("app:d" !in current.iconPicks && "app:e" !in current.iconPicks)
        // The app that was not picked keeps its choice.
        assertEquals("com.pack.one|chrome", current.iconPicks["app:a"])
    }

    @Test
    fun theIconOfTheAppItselfIsAChoiceToo() {
        closed = false
        show { Dialog(listOf("app:f")) }
        advance(600)
        tap("iconpick:own")
        assertEquals("", current.iconPicks["app:f"])
    }

    // --- the list of apps in the settings

    private val apps = listOf("a", "b", "c").map {
        AppItem("app:$it", "App ${it.uppercase()}", Launch.Activity(ComponentName("com.app.$it", "com.app.$it.Main"), Process.myUserHandle()), packageName = "com.app.$it")
    }

    @Composable
    private fun Page(onPick: (List<String>) -> Unit) {
        val away = remember { Animatable(0f) }
        SettingsScreen(
            env = SettingsEnv(settings = current, onChange = {}, iconPacks = packs, apps = apps, onPickIcons = onPick),
            section = SettingsSection.StartTheme,
            onSectionChange = {},
            sectionAway = away,
        )
    }

    @Test
    fun appsArePickedTogetherAndHandedToTheDialog() {
        var handed: List<String>? = null
        show { Page { handed = it } }
        advance(500)
        // The app with a choice says which one, under its name.
        compose.onAllNodesWithTag("iconperapp:change").assertCountEquals(0)
        scrollIntoView("iconperapp:app:b")
        compose.onNodeWithTag("iconperapp:app:b").assertHeightIsAtLeast(48.dp)
        tap("iconperapp:app:b")
        tap("iconperapp:app:c")
        compose.onNodeWithTag("iconperapp:app:b").assertIsSelected()
        scrollIntoView("iconperapp:change")
        tap("iconperapp:change")
        assertEquals(setOf("app:b", "app:c"), handed?.toSet())
        // Touching a picked app again lets it go; the clear button empties the choice.
        tap("iconperapp:app:c")
        compose.onNodeWithTag("iconperapp:app:c").assertIsNotSelected()
        tap("iconperapp:clear")
        compose.onAllNodesWithTag("iconperapp:change").assertCountEquals(0)
    }

    @Test
    fun withoutAPackTheListOfAppsIsNotThere() {
        val away = Animatable(0f)
        show { SettingsScreen(env = SettingsEnv(settings = LauncherSettings(), onChange = {}, apps = apps), section = SettingsSection.StartTheme, onSectionChange = {}, sectionAway = away) }
        advance(500)
        compose.onAllNodesWithTag("iconperapp:app:a").assertCountEquals(0)
    }
}
