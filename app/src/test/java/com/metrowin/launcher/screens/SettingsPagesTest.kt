package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.centerX
import androidx.compose.ui.geometry.Offset
import com.metrowin.launcher.data.AboutInfo
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ThemeMode
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.EditBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pages of the settings and the edit bar of Start, with real taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SettingsPagesTest : ScreenshotBase() {
    private var current = LauncherSettings()

    /** The settings opened on [section], holding the settings the way the app does. */
    @Composable
    private fun Page(section: SettingsSection, apps: List<AppItem> = emptyList(), about: AboutInfo = AboutInfo("", 0L, "", 0, "", 0)) {
        var settings by remember { mutableStateOf(current) }
        val away = remember { Animatable(0f) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings,
                onChange = { change ->
                    settings = change(settings)
                    current = settings
                },
                apps = apps,
                about = about,
            ),
            section = section,
            onSectionChange = {},
            sectionAway = away,
        )
    }

    /** Scrolls the page until the control is on screen (clear of the title that stays put), then taps it. */
    private fun tap(tag: String) {
        scrollIntoView(tag, topMargin = 0.2f)
        compose.onNodeWithTag(tag).performClick()
    }

    private fun open(
        section: SettingsSection,
        settings: LauncherSettings = LauncherSettings(),
        apps: List<AppItem> = emptyList(),
        about: AboutInfo = AboutInfo("", 0L, "", 0, "", 0),
    ) {
        current = settings
        show { Page(section, apps, about) }
        advance(500)
    }

    // --- start+theme and the start screen

    @Test
    fun theThemeCanBeFixedOrAutomatic() {
        open(SettingsSection.StartTheme)
        capture("settings_theme_modes")
        tap("theme:mode:1")
        assertEquals(ThemeMode.Light, current.themeMode)
        tap("theme:mode:2")
        assertEquals(ThemeMode.System, current.themeMode)
        tap("theme:mode:3")
        assertEquals(ThemeMode.Schedule, current.themeMode)
        tap("theme:mode:0")
        assertEquals(ThemeMode.Dark, current.themeMode)
    }

    @Test
    fun theHoursOfTheScheduleAppearOnlyWhenTheThemeFollowsTheHour() {
        open(SettingsSection.StartTheme)
        compose.onAllNodesWithTag("theme:from").assertCountEquals(0)
        tap("theme:mode:3")
        advance(100)
        compose.onNodeWithTag("theme:from").assertExists()
        compose.onNodeWithTag("theme:to").assertExists()
    }

    @Test
    fun theHoursWrapAroundMidnight() {
        open(SettingsSection.StartTheme, LauncherSettings(themeMode = ThemeMode.Schedule, darkFromHour = 23, darkToHour = 0))
        tap("theme:from:plus")
        assertEquals(0, current.darkFromHour)
        tap("theme:to:minus")
        assertEquals(23, current.darkToHour)
    }

    @Test
    fun theNumberOfStartScreensStaysBetweenOneAndThree() {
        open(SettingsSection.Start)
        tap("start:pages:minus")
        assertEquals("one is the least", 1, current.startPages)
        tap("start:pages:plus")
        tap("start:pages:plus")
        assertEquals(3, current.startPages)
        tap("start:pages:plus")
        assertEquals("three is the most", 3, current.startPages)
    }

    @Test
    fun theSwitchesThatNeedAPhotoDoNothingWithoutOne() {
        open(SettingsSection.StartTheme)
        tap("theme:autoaccent")
        tap("theme:parallax")
        assertFalse(current.autoAccent)
        assertTrue("parallax stays as it was", current.parallax)
    }

    @Test
    fun withAPhotoTheAccentCanFollowItAndPickingAColourStopsThat() {
        open(SettingsSection.StartTheme, LauncherSettings(backgroundRevision = 1))
        tap("theme:autoaccent")
        assertTrue(current.autoAccent)
        tap("theme:parallax")
        assertFalse(current.parallax)
    }

    @Test
    fun theClockIsASwitch() {
        open(SettingsSection.Start)
        tap("start:clock")
        assertTrue(current.showClock)
    }

    // --- gestures

    @Test
    fun theGesturePageHasTheEdgeAndTapSwitches() {
        open(SettingsSection.Gestures)
        capture("settings_gestures")
        tap("gestures:edgepull")
        assertFalse(current.edgePull)
        tap("gestures:edgepan")
        assertFalse(current.edgeBackPan)
        tap("gestures:sound")
        assertTrue(current.tapSound)
        tap("gestures:haptics")
        assertTrue(current.tapHaptics)
    }

    // --- apps

    private fun app(id: String, label: String) = AppItem(id, label, Launch.Action("x"), removable = true)

    @Test
    fun theHiddenAppsPageListsThemAndBringsBackTheOneYouTouch() {
        val apps = listOf(app("a", "Alpha"), app("b", "Beta"), app("c", "Gamma"))
        open(SettingsSection.Apps, LauncherSettings(hiddenApps = setOf("b", "c")), apps)
        capture("settings_apps_hidden")
        compose.onNodeWithTag("apps:hidden:b").assertExists()
        compose.onNodeWithTag("apps:hidden:c").assertExists()
        compose.onAllNodesWithTag("apps:hidden:a").assertCountEquals(0)
        tap("apps:hidden:b")
        assertEquals(setOf("c"), current.hiddenApps)
        advance(300)
        compose.onAllNodesWithTag("apps:hidden:b").assertCountEquals(0)
    }

    @Test
    fun withNothingHiddenThePageSaysHowToHideAnApp() {
        open(SettingsSection.Apps, LauncherSettings(), listOf(app("a", "Alpha")))
        compose.onNodeWithTag("apps:hidden:none").assertExists()
    }

    // --- backup and about

    @Test
    fun theBackupPageHasTheTwoButtons() {
        open(SettingsSection.Backup)
        capture("settings_backup")
        compose.onNodeWithTag("backup:export").assertExists()
        compose.onNodeWithTag("backup:import").assertExists()
    }

    @Test
    fun withNoBackupFolderTheOnlyThingToDoIsToChooseOne() {
        open(SettingsSection.Backup)
        compose.onNodeWithTag("backup:folder").assertExists()
        compose.onAllNodesWithTag("backup:now").assertCountEquals(0)
        compose.onAllNodesWithTag("backup:every:header").assertCountEquals(0)
    }

    @Test
    fun withAFolderTheBackupCanBeScheduledMadeNowAndRestored() {
        var now = 0
        var restore = 0
        var forget = 0
        current = LauncherSettings(backupFolder = "content://tree/backup")
        show {
            var settings by remember { mutableStateOf(current) }
            SettingsScreen(
                env = SettingsEnv(
                    settings = settings,
                    onChange = { change ->
                        settings = change(settings)
                        current = settings
                    },
                    backupFolderName = "Backups",
                    onBackupNow = { now++ },
                    onRestoreBackup = { restore++ },
                    onClearBackupFolder = { forget++ },
                ),
                section = SettingsSection.Backup,
                onSectionChange = {},
                sectionAway = remember { Animatable(0f) },
            )
        }
        advance(500)
        capture("settings_backup_folder")
        compose.onNodeWithText("Backups").assertExists()
        tap("backup:every:header")
        advance(100)
        tap("backup:every:2") // every week
        assertEquals(7, current.backupEveryDays)
        tap("backup:now")
        tap("backup:restore")
        tap("backup:forget")
        assertEquals(listOf(1, 1, 1), listOf(now, restore, forget))
    }

    @Test
    fun theAboutPageShowsTheVersionAndThePhone() {
        open(SettingsSection.About, about = AboutInfo("1.2.3", 45L, "14", 34, "Fairphone FP5", 120))
        capture("settings_about")
        compose.onNodeWithText("1.2.3 (45)").assertExists()
        compose.onNodeWithText("14 (API 34)").assertExists()
        compose.onNodeWithText("Fairphone FP5").assertExists()
        compose.onNodeWithText("120 Hz").assertExists()
    }

    @Test
    fun theAboutPageLeavesOutTheRefreshRateWhenItIsNotKnown() {
        open(SettingsSection.About, about = AboutInfo("1.0", 1L, "14", 34, "Phone", 0))
        compose.onAllNodesWithText("0 Hz").assertCountEquals(0)
    }

    // --- the edit bar

    @Test
    fun theEditBarSetsTheSizeYouTouch() {
        var size = TileSize.Medium
        val pageMoves = mutableListOf<Int>()
        var done = 0
        show {
            Box(Modifier.fillMaxSize()) {
                EditBar(
                    size = size, page = 0, pageCount = 3,
                    onSize = { size = it }, onMovePage = { pageMoves += it }, onDone = { done++ },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        advance(300)
        capture("edit_bar")
        compose.onNodeWithTag("edit:size:Wide").performClick()
        assertEquals(TileSize.Wide, size)
        compose.onNodeWithTag("edit:size:Small").performClick()
        assertEquals(TileSize.Small, size)
        compose.onNodeWithTag("edit:page:next").performClick()
        assertEquals(listOf(1), pageMoves)
        // On the first screen there is no previous one to move to.
        compose.onNodeWithTag("edit:page:previous").performClick()
        assertEquals(listOf(1), pageMoves)
        compose.onNodeWithTag("edit:done").performClick()
        assertEquals(1, done)
    }

    @Test
    fun theEditBarOffersNoPageButtonsWithASingleStartScreen() {
        show {
            Box(Modifier.fillMaxSize()) {
                EditBar(TileSize.Medium, page = 0, pageCount = 1, onSize = {}, onMovePage = {}, onDone = {}, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        advance(300)
        compose.onNodeWithTag("edit:size:Medium").assertIsDisplayed()
        compose.onAllNodesWithTag("edit:page:next").assertCountEquals(0)
    }
}
