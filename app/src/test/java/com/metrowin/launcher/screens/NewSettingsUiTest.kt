package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.metrowin.launcher.data.FinanceItems
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.data.TileTransition
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
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

/** The settings of the third round: what each switch writes, and what stays when a master switch goes off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NewSettingsUiTest : ScreenshotBase() {
    private var current = LauncherSettings()
    private var soundOf: String? = null

    private val chat = AppItem("app:chat", "Chat", Launch.Action("x"), packageName = "com.example.chat")
    private val mail = AppItem("app:mail", "Mail", Launch.Action("x"), packageName = "com.example.mail")

    @Composable
    private fun Page(section: SettingsSection, apps: List<AppItem>) {
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
                notificationAccess = true,
                notificationPackages = setOf("com.example.mail"),
                onAppNotificationSettings = { soundOf = it },
            ),
            section = section,
            onSectionChange = {},
            sectionAway = away,
        )
    }

    private fun open(section: SettingsSection, settings: LauncherSettings = LauncherSettings(), apps: List<AppItem> = listOf(chat, mail)) {
        current = settings
        soundOf = null
        show { Page(section, apps) }
        advance(500)
    }

    private fun tap(tag: String) {
        scrollIntoView(tag, topMargin = 0.2f)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        advance(100)
    }

    // --- the switches of the new features

    @Test
    fun thePanoramaTitlesAreOffUntilAskedFor() {
        open(SettingsSection.StartTheme)
        assertFalse(current.panoramaTitles)
        tap("theme:panorama")
        assertTrue(current.panoramaTitles)
        tap("theme:panorama")
        assertFalse(current.panoramaTitles)
    }

    @Test
    fun theBarWithTheDotsCanBeSwitchedOff() {
        open(SettingsSection.Start)
        assertTrue(current.appBar)
        tap("start:appbar")
        assertFalse(current.appBar)
    }

    @Test
    fun theTodayCardTheAssistantAndTheSmartDialCanBeSwitchedOffOneByOne() {
        open(SettingsSection.Search)
        tap("search:today")
        assertFalse(current.searchToday)
        assertTrue(current.searchAssistant)
        assertTrue(current.searchSmartDial)
        tap("search:assistant")
        tap("search:dial")
        assertFalse(current.searchAssistant)
        assertFalse(current.searchSmartDial)
    }

    @Test
    fun theVolumePanelAndTheBannerCanBeSwitchedOff() {
        open(SettingsSection.Actions)
        assertTrue(current.volumePanel)
        assertTrue(current.toastBanner)
        tap("actions:volumepanel")
        tap("notifications:banner")
        assertFalse(current.volumePanel)
        assertFalse(current.toastBanner)
    }

    // --- the lock screen

    @Test
    fun theMusicOfTheLockScreenCanBeSwitchedOffAndTheQuickStatusHidesItsApps() {
        open(SettingsSection.Lock)
        compose.onNodeWithTag("lock:quickapp:0:header").assertExists()
        tap("lock:music")
        assertFalse(current.lockShowMusic)
        tap("lock:quick")
        assertFalse(current.lockQuickStatus)
        // With the quick status off there are no slots to fill and no detailed status to choose.
        compose.onAllNodesWithTag("lock:quickapp:0:header").assertCountEquals(0)
        compose.onAllNodesWithTag("lock:detail:header").assertCountEquals(0)
    }

    @Test
    fun anAppIsPutInASlotOfTheQuickStatusAndTakenOutAgain() {
        open(SettingsSection.Lock)
        tap("lock:quickapp:0:header")
        tap("lock:quickapp:0:app:chat")
        assertEquals(listOf("com.example.chat"), current.lockQuickApps)
        tap("lock:quickapp:1:header")
        tap("lock:quickapp:1:app:mail")
        assertEquals(listOf("com.example.chat", "com.example.mail"), current.lockQuickApps)
        tap("lock:quickapp:0:clear")
        assertEquals(listOf("com.example.mail"), current.lockQuickApps)
    }

    @Test
    fun theDetailedStatusIsOffUntilAnAppIsChosen() {
        open(SettingsSection.Lock)
        assertEquals("", current.lockDetailApp)
        tap("lock:detail:header")
        tap("lock:detail:app:mail")
        assertEquals("com.example.mail", current.lockDetailApp)
        tap("lock:detail:clear")
        assertEquals("", current.lockDetailApp)
    }

    // --- the live tiles

    @Test
    fun theFacesOfALiveTileCanSlide() {
        open(SettingsSection.LiveTiles)
        assertEquals(TileTransition.Flip, current.tileTransition)
        tap("live:transition:1")
        assertEquals(TileTransition.Slide, current.tileTransition)
        tap("live:transition:2")
        assertEquals(TileTransition.Mixed, current.tileTransition)
    }

    @Test
    fun theNewsComeFromGoogleUntilAnotherSourceIsChosen() {
        open(SettingsSection.LiveTiles)
        assertEquals(NewsSources.GOOGLE_TOP, current.newsSource)
        tap("live:news:header")
        tap("live:news:2")
        assertEquals(NewsSources.choices[2], current.newsSource)
        assertTrue(NewsSources.isGoogle(current.newsSource))
    }

    @Test
    fun aFeedOfOneOwnAsksForAnAddressAndSaysWhetherItIsGood() {
        open(SettingsSection.LiveTiles, LauncherSettings(newsSource = NewsSources.CUSTOM))
        tap("live:newsurl")
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("live:newsurl"))).performTextInput("example.com/feed.xml")
        compose.waitForIdle()
        // The test clock is paused: the page is drawn again when a frame goes by.
        advance(100)
        assertEquals("example.com/feed.xml", current.newsUrl)
        compose.onNodeWithTag("live:newsurl:state").assertExists()
    }

    @Test
    fun aFeedThatIsNotAWebAddressIsSaidToBeWrong() {
        open(SettingsSection.LiveTiles, LauncherSettings(newsSource = NewsSources.CUSTOM, newsUrl = "ftp://example.com/feed"))
        compose.onNodeWithTag("live:newsurl:state").assertExists()
        assertTrue(NewsSources.customUrl(current.newsUrl) == null)
    }

    @Test
    fun theFinanceTileFollowsAtMostFourItemsAndTheFifthTakesThePlaceOfTheOldest() {
        val start = LauncherSettings(financeItems = listOf("fx:USD", "fx:GBP", "fx:JPY", "crypto:bitcoin"))
        open(SettingsSection.LiveTiles, start)
        tap("live:finance:crypto:ethereum")
        assertEquals(FinanceItems.MAX, current.financeItems.size)
        assertEquals(listOf("fx:GBP", "fx:JPY", "crypto:bitcoin", "crypto:ethereum"), current.financeItems)
        // A touch on one that is followed lets it go.
        tap("live:finance:fx:GBP")
        assertEquals(listOf("fx:JPY", "crypto:bitcoin", "crypto:ethereum"), current.financeItems)
    }

    // --- the notifications, app by app

    @Test
    fun anAppIsKeptOutOfTheCenterTheLockScreenAndTheBannerOneAtATime() {
        open(SettingsSection.Actions)
        tap("actions:notifapp:com.example.chat:header")
        tap("actions:notifapp:com.example.chat:center")
        assertEquals(setOf("com.example.chat"), current.notifHideCenter)
        tap("actions:notifapp:com.example.chat:lock")
        assertEquals(setOf("com.example.chat"), current.notifHideLock)
        tap("actions:notifapp:com.example.chat:banner")
        assertEquals(setOf("com.example.chat"), current.notifNoBanner)
        // And back in.
        tap("actions:notifapp:com.example.chat:center")
        assertTrue(current.notifHideCenter.isEmpty())
    }

    @Test
    fun theSoundOfAnAppIsChangedWhereAndroidKeepsIt() {
        open(SettingsSection.Actions)
        tap("actions:notifapp:com.example.mail:header")
        tap("actions:notifapp:com.example.mail:sound")
        assertEquals("com.example.mail", soundOf)
    }

    @Test
    fun withTheMasterSwitchOffTheAppsAreHiddenAndTheChoicesKept() {
        open(SettingsSection.Actions, LauncherSettings(notifHideCenter = setOf("com.example.chat")))
        compose.onNodeWithTag("actions:notifapps:query").assertExists()
        tap("actions:notifcontrol")
        assertFalse(current.notifControl)
        compose.onAllNodesWithTag("actions:notifapps:query").assertCountEquals(0)
        assertEquals(setOf("com.example.chat"), current.notifHideCenter)
    }

    @Test
    fun theAppsWithANotificationUpComeFirst() {
        open(SettingsSection.Actions)
        // "Mail" has a notification up, "Chat" does not: Mail is above, though Chat comes first in the alphabet.
        val mailTop = compose.onNodeWithTag("actions:notifapp:com.example.mail").fetchSemanticsNode().positionInRoot.y
        val chatTop = compose.onNodeWithTag("actions:notifapp:com.example.chat").fetchSemanticsNode().positionInRoot.y
        assertTrue("mail at $mailTop, chat at $chatTop", mailTop < chatTop)
    }
}
