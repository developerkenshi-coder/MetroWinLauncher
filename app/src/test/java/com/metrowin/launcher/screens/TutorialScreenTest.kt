package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.centerX
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.metrowin.launcher.data.Need
import com.metrowin.launcher.data.Tutorial
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.tutorial.TutorialScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class TutorialScreenTest : ScreenshotBase() {
    private val requested = mutableListOf<Need>()
    private var finished = 0
    private lateinit var pager: PagerState

    @Composable
    private fun Tour(met: Set<Need> = emptySet(), denied: Set<Need> = emptySet()) {
        pager = rememberPagerState { Tutorial.steps.size }
        TutorialScreen(pager, met, onRequest = { requested += it }, onFinish = { finished++ }, denied = denied)
    }

    private fun open(met: Set<Need> = emptySet(), denied: Set<Need> = emptySet()) {
        show { Tour(met, denied) }
        advance(300)
    }

    private fun next() {
        compose.onNodeWithTag("tutorial:next").performClick()
        advance(800)
    }

    private fun goTo(step: String) {
        val target = Tutorial.steps.indexOfFirst { it.id == step }
        repeat(target - pager.currentPage) { next() }
    }

    @Test
    fun theTourStartsWithTheWelcomeAndNoRequests() {
        open()
        capture("tutorial_welcome")
        compose.onNodeWithTag("tutorial:step:welcome").assertExists()
        compose.onAllNodesWithTag("tutorial:allow:HomeApp").assertCountEquals(0)
        compose.onNodeWithTag("tutorial:skip").assertExists()
        compose.onNodeWithTag("tutorial:next").assertTextEquals("next")
    }

    @Test
    fun nextGoesThroughEveryPageInOrder() {
        open()
        Tutorial.steps.forEachIndexed { index, step ->
            compose.onNodeWithTag("tutorial:step:${step.id}").assertExists()
            assertEquals(index, pager.currentPage)
            if (index < Tutorial.steps.lastIndex) next()
        }
    }

    @Test
    fun theLastPageHasNoSkipAndItsButtonLeavesTheTour() {
        open()
        goTo("settings")
        capture("tutorial_settings")
        compose.onAllNodesWithTag("tutorial:skip").assertCountEquals(0)
        compose.onNodeWithTag("tutorial:next").assertTextEquals("let's go")
        compose.onNodeWithTag("tutorial:next").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun skipLeavesTheTourFromAnyPage() {
        open()
        goTo("apps")
        compose.onNodeWithTag("tutorial:skip").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun aPageAsksOnlyForWhatItNeedsAndTheButtonAsksForThatOne() {
        open()
        goTo("home")
        capture("tutorial_home")
        compose.onNodeWithTag("tutorial:need:HomeApp").assertExists()
        compose.onNodeWithTag("tutorial:allow:HomeApp").performClick()
        assertEquals(listOf(Need.HomeApp), requested)
    }

    @Test
    fun theLiveTilesPageAsksForFourThingsOneByOne() {
        open()
        goTo("live")
        capture("tutorial_live")
        listOf(Need.Photos, Need.Calendar, Need.Contacts, Need.CallLog).forEach { compose.onNodeWithTag("tutorial:need:${it.name}").assertExists() }
        compose.onNodeWithTag("tutorial:allow:Contacts").performClick()
        compose.onNodeWithTag("tutorial:allow:Photos").performClick()
        assertEquals(listOf(Need.Contacts, Need.Photos), requested)
    }

    @Test
    fun whatIsAlreadyAllowedSaysSoAndHasNoButton() {
        open(met = setOf(Need.Photos, Need.Calendar))
        goTo("live")
        compose.onNodeWithTag("tutorial:allowed:Photos").assertTextEquals("allowed")
        compose.onNodeWithTag("tutorial:allowed:Calendar").assertExists()
        compose.onAllNodesWithTag("tutorial:allow:Photos").assertCountEquals(0)
        compose.onNodeWithTag("tutorial:allow:Contacts").assertExists()
    }

    @Test
    fun aPermissionAndroidWillNotAskAgainOffersTheSettingsInstead() {
        open(denied = setOf(Need.Microphone))
        goTo("apps")
        compose.onNodeWithTag("tutorial:allow:Microphone").assertTextEquals("open settings")
        compose.onNodeWithTag("tutorial:allow:Microphone").performClick()
        assertEquals(listOf(Need.Microphone), requested)
    }

    @Test
    fun theNeedsThatAreGrantedInTheSettingsSayWhere() {
        open()
        goTo("action")
        compose.onNodeWithTag("tutorial:allow:NotificationAccess").assertTextEquals("open settings")
        goTo("gestures")
        compose.onNodeWithTag("tutorial:allow:LockService").assertTextEquals("open settings")
    }

    @Test
    fun theKeepAlivePageAsksForTheBattery() {
        open()
        goTo("alive")
        capture("tutorial_alive")
        compose.onNodeWithTag("tutorial:allow:Battery").assertTextEquals("allow")
        compose.onNodeWithTag("tutorial:allow:Battery").performClick()
        assertEquals(listOf(Need.Battery), requested)
    }

    @Test
    fun aSwipeTurnsThePageToo() {
        open()
        compose.onNodeWithTag("tutorial:pager").performTouchInput { swipe(Offset(width * 0.8f, centerY), Offset(width * 0.1f, centerY), durationMillis = 200) }
        advance(1_000)
        assertEquals(1, pager.currentPage)
    }

    @Test
    fun everyPageShowsItsTitle() {
        open()
        Tutorial.steps.forEachIndexed { index, step ->
            compose.onNodeWithTag("tutorial:title").assertExists()
            assertTrue("page ${step.id}", pager.currentPage == index)
            if (index < Tutorial.steps.lastIndex) next()
        }
    }

    // --- replaying it

    @Test
    fun theAboutPageCanShowTheTourAgain() {
        var shown = 0
        show {
            val away = remember { Animatable(0f) }
            var settings by remember { mutableStateOf(com.metrowin.launcher.data.LauncherSettings()) }
            SettingsScreen(
                env = SettingsEnv(settings = settings, onChange = { change -> settings = change(settings) }, onShowTutorial = { shown++ }),
                section = SettingsSection.About, onSectionChange = {}, sectionAway = away,
            )
        }
        advance(500)
        tapScrolled("about:tutorial")
        assertEquals(1, shown)
    }
}
