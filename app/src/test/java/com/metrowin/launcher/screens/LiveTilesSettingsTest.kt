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
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.notifications.LiveReportLine
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

/** What the user chooses for the live tiles of other apps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class LiveTilesSettingsTest : ScreenshotBase() {
    private var current = LauncherSettings()

    private val apps = listOf(
        AppItem("app:wa", "WhatsApp", Launch.Action("x"), packageName = "com.whatsapp"),
        AppItem("app:ig", "Instagram", Launch.Action("x"), packageName = "com.instagram.android"),
        AppItem("app:rd", "Reddit", Launch.Action("x"), packageName = "com.reddit.frontpage"),
    )

    @Composable
    private fun Page(access: Boolean = true, live: Set<String> = emptySet(), report: List<LiveReportLine> = emptyList()) {
        var settings by remember { mutableStateOf(current) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings,
                onChange = { change -> settings = change(settings); current = settings },
                notificationAccess = access,
                apps = apps,
                liveTilePackages = live,
                liveReport = report,
                onManageNotificationAccess = { accessAsked++ },
            ),
            section = SettingsSection.LiveTiles,
            onSectionChange = {},
            sectionAway = remember { Animatable(0f) },
        )
    }

    private var accessAsked = 0

    private fun open(settings: LauncherSettings, access: Boolean = true, live: Set<String> = emptySet(), report: List<LiveReportLine> = emptyList()) {
        current = settings
        accessAsked = 0
        show { Page(access, live, report) }
        advance(500)
    }

    private fun tap(tag: String) {
        scrollIntoView(tag, topMargin = 0.2f)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        advance(100)
    }

    @Test
    fun withTheMainSwitchOffThePageSaysSoAndNothingChanges() {
        open(LauncherSettings(notificationPeek = false))
        scrollIntoView("live:off", topMargin = 0.2f)
        capture("settings_live_tiles_off")
        compose.onNodeWithTag("live:off").assertExists()
        tap("live:hidetext")
        assertFalse("a switch that is off does nothing", current.liveHideText)
    }

    @Test
    fun theMainSwitchIsOnThePageItself() {
        open(LauncherSettings(notificationPeek = false))
        scrollIntoView("live:master", topMargin = 0.2f)
        compose.onNodeWithTag("live:master").assertExists()
        tap("live:master")
        assertTrue(current.notificationPeek)
        compose.onAllNodesWithTag("live:off").assertCountEquals(0)
        tap("live:master")
        assertFalse(current.notificationPeek)
    }

    @Test
    fun withoutAccessThePageHasAButtonThatAsksForIt() {
        open(LauncherSettings(notificationPeek = true), access = false)
        scrollIntoView("live:access", topMargin = 0.2f)
        capture("settings_live_tiles_access")
        tap("live:access")
        assertEquals(1, accessAsked)
    }

    @Test
    fun withAccessThereIsNoButton() {
        open(LauncherSettings(notificationPeek = true), access = true)
        compose.onAllNodesWithTag("live:access").assertCountEquals(0)
    }

    @Test
    fun thePageTellsWhatTheLauncherReadsFromEachApp() {
        open(
            LauncherSettings(notificationPeek = true),
            report = listOf(
                LiveReportLine("com.whatsapp", conversations = 2, messages = 5, others = 0, pictures = 1, playing = false, progress = 0, ongoing = 0),
                LiveReportLine("com.reddit.frontpage", conversations = 0, messages = 0, others = 3, pictures = 0, playing = false, progress = 0, ongoing = 0),
            ),
        )
        scrollIntoView("live:report:com.whatsapp", topMargin = 0.2f)
        capture("settings_live_tiles_report")
        compose.onNodeWithTag("live:report:com.whatsapp").assertExists()
        compose.onNodeWithTag("live:report:com.reddit.frontpage").assertExists()
        compose.onAllNodesWithTag("live:report:none").assertCountEquals(0)
    }

    @Test
    fun withNothingReadThePageExplainsWhy() {
        open(LauncherSettings(notificationPeek = true))
        scrollIntoView("live:report:none", topMargin = 0.2f)
        compose.onNodeWithTag("live:report:none").assertExists()
    }

    @Test
    fun withTheSwitchOffTheReportIsEmptyEvenIfNotificationsAreUp() {
        open(
            LauncherSettings(notificationPeek = false),
            report = listOf(LiveReportLine("com.whatsapp", 1, 1, 0, 0, false, 0, 0)),
        )
        scrollIntoView("live:report:none", topMargin = 0.2f)
        compose.onAllNodesWithTag("live:report:com.whatsapp").assertCountEquals(0)
    }

    @Test
    fun withoutNotificationAccessItIsOffToo() {
        open(LauncherSettings(notificationPeek = true), access = false)
        compose.onNodeWithTag("live:off").assertExists()
    }

    @Test
    fun theSwitchesChooseWhatTheTilesShow() {
        open(LauncherSettings(notificationPeek = true))
        compose.onAllNodesWithTag("live:off").assertCountEquals(0)
        tap("live:hidetext")
        assertTrue(current.liveHideText)
        tap("live:pictures")
        assertFalse(current.livePictures)
        tap("live:playing")
        assertFalse(current.liveNowPlaying)
        tap("live:progress")
        assertFalse(current.liveProgress)
        tap("live:messages")
        assertFalse(current.liveMessages)
    }

    @Test
    fun theAppsWithLiveContentCanBeSwitchedOffAndOnAgain() {
        open(LauncherSettings(notificationPeek = true), live = setOf("com.whatsapp", "com.reddit.frontpage"))
        scrollIntoView("live:app:com.whatsapp", topMargin = 0.2f)
        capture("settings_live_tiles_apps")
        compose.onAllNodesWithTag("live:app:com.instagram.android").assertCountEquals(0) // nothing to show: not listed
        tap("live:app:com.whatsapp")
        assertEquals(setOf("com.whatsapp"), current.liveOffApps)
        tap("live:app:com.whatsapp")
        assertEquals(emptySet<String>(), current.liveOffApps)
    }

    @Test
    fun anAppSwitchedOffStaysInTheListEvenWhenItShowsNothingNow() {
        open(LauncherSettings(notificationPeek = true, liveOffApps = setOf("com.instagram.android")))
        scrollIntoView("live:app:com.instagram.android", topMargin = 0.2f)
        compose.onNodeWithTag("live:app:com.instagram.android").assertExists()
        tap("live:app:com.instagram.android")
        assertEquals(emptySet<String>(), current.liveOffApps)
    }

    @Test
    fun withNoAppThePageSaysThereIsNone() {
        open(LauncherSettings(notificationPeek = true))
        scrollIntoView("live:none", topMargin = 0.2f)
        compose.onNodeWithTag("live:none").assertExists()
    }
}
