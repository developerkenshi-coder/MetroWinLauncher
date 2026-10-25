package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.AppShortcut
import com.metrowin.launcher.data.EventMatch
import com.metrowin.launcher.data.GlobalResults
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
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
import java.time.LocalDate
import java.time.LocalTime

/** The new answers and results in the search, the compact list, the store entries of the app menu and the settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SearchMoreUiTest : ScreenshotBase() {
    private val apps = listOf("Alpha", "Beta").map { AppItem("app:$it", it, Launch.Action("x")) }

    @Composable
    private fun Search(
        query: String = "x",
        answers: List<QuickAnswer> = emptyList(),
        global: GlobalResults = GlobalResults.Empty,
        compact: Boolean = false,
        onAnswer: (QuickAnswer) -> Unit = {},
        onLaunch: (AppItem) -> Unit = {},
    ) {
        val model = remember { AppListModel(AppSections.group(AppSections.sort(apps))) }
        AppListScreen(
            model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
            onLaunch = onLaunch, onLongPress = { _, _, _ -> }, onHeaderClick = {},
            query = query, onQueryChange = {}, results = emptyList(), global = global,
            answers = answers, onAnswer = onAnswer, compact = compact,
        )
    }

    // --- the answers

    @Test
    fun theTimeInACityShowsTheTimeTheCityAndTheDay() {
        show { Search(answers = listOf(QuickAnswer.WorldTime("Tokyo", LocalTime.of(7, 30), 1))) }
        advance(900)
        capture("answer_worldtime")
        compose.onNodeWithTag("answer:WorldTime").assertTextContains("in Tokyo", substring = true)
        compose.onNodeWithTag("answer:WorldTime").assertTextContains("tomorrow", substring = true)
    }

    @Test
    fun theTimeInACityOnTheSameDayOrTheDayBeforeSaysSo() {
        show { Search(answers = listOf(QuickAnswer.WorldTime("Honolulu", LocalTime.of(19, 0), -1))) }
        advance(900)
        compose.onNodeWithTag("answer:WorldTime").assertTextContains("yesterday", substring = true)
    }

    @Test
    fun aDateAheadSaysTheDateAndHowFar() {
        show { Search(answers = listOf(QuickAnswer.DateOffset(LocalDate.of(2026, 12, 25), 30))) }
        advance(900)
        capture("answer_date_offset")
        compose.onNodeWithTag("answer:DateOffset").assertTextContains("December 25, 2026", substring = true)
        compose.onNodeWithTag("answer:DateOffset").assertTextContains("in 30 days", substring = true)
    }

    @Test
    fun aDateBehindSaysHowLongAgo() {
        show { Search(answers = listOf(QuickAnswer.DateOffset(LocalDate.of(2026, 9, 10), -1))) }
        advance(900)
        compose.onNodeWithTag("answer:DateOffset").assertTextContains("1 day ago", substring = true)
    }

    @Test
    fun theDaysToADateAreTheTitle() {
        show { Search(answers = listOf(QuickAnswer.DaysUntil(LocalDate.of(2026, 12, 25), 61))) }
        advance(900)
        capture("answer_days_until")
        compose.onNodeWithTag("answer:DaysUntil").assertTextContains("61 days", substring = true)
        compose.onNodeWithTag("answer:DaysUntil").assertTextContains("until", substring = true)
    }

    @Test
    fun theDaysToADateTodayAndSinceADateInThePast() {
        show { Search(answers = listOf(QuickAnswer.DaysUntil(LocalDate.of(2026, 10, 25), 0), QuickAnswer.DaysUntil(LocalDate.of(2020, 1, 1), -300))) }
        advance(900)
        compose.onAllNodesWithText("today").assertCountEquals(1)
        compose.onAllNodesWithText("300 days").assertCountEquals(1)
    }

    @Test
    fun aThrowShowsTheTotalAndTheRollsAndAsksToBeThrownAgain() {
        val touched = mutableListOf<QuickAnswer>()
        val dice = QuickAnswer.Dice(2, 6, listOf(3, 4), 2)
        show { Search(answers = listOf(dice), onAnswer = { touched += it }) }
        advance(900)
        capture("answer_dice")
        compose.onNodeWithTag("answer:Dice").assertTextContains("9", substring = true)
        compose.onNodeWithTag("answer:Dice").assertTextContains("2d6+2: 3 + 4 + 2", substring = true)
        compose.onNodeWithTag("answer:Dice").assertTextContains("touch to roll again", substring = true)
        compose.onNodeWithTag("answer:Dice").performClick()
        assertEquals(listOf<QuickAnswer>(dice), touched)
    }

    @Test
    fun aSingleDieHasNoSumUnderIt() {
        show { Search(answers = listOf(QuickAnswer.Dice(1, 20, listOf(17), 0))) }
        advance(900)
        compose.onNodeWithTag("answer:Dice").assertTextContains("d20: touch to roll again", substring = true)
        compose.onAllNodesWithText("17").assertCountEquals(1)
    }

    @Test
    fun aCoinSaysHeadsOrTails() {
        show { Search(answers = listOf(QuickAnswer.Coin(true))) }
        advance(900)
        capture("answer_coin")
        compose.onNodeWithTag("answer:Coin").assertTextContains("heads", substring = true)
        compose.onNodeWithTag("answer:Coin").assertTextContains("touch to flip again", substring = true)
    }

    @Test
    fun aTailsCoin() {
        show { Search(answers = listOf(QuickAnswer.Coin(false))) }
        advance(900)
        compose.onNodeWithTag("answer:Coin").assertTextContains("tails", substring = true)
    }

    @Test
    fun aCurrencyAnswerNamesTheDayOfTheRates() {
        val touched = mutableListOf<QuickAnswer>()
        val answer = QuickAnswer.Currency("100 USD = 92.59 EUR", "92.59", "2026-10-23")
        show { Search(answers = listOf(answer), onAnswer = { touched += it }) }
        advance(900)
        capture("answer_currency")
        compose.onNodeWithTag("answer:Currency").assertTextContains("100 USD = 92.59 EUR", substring = true)
        compose.onNodeWithTag("answer:Currency").assertTextContains("rates of 2026-10-23", substring = true)
        compose.onNodeWithTag("answer:Currency").performClick()
        assertEquals(listOf<QuickAnswer>(answer), touched)
    }

    // --- events of the calendar

    @Test
    fun theEventsThatMatchAreListedUnderTheirHeadingAndOpenInTheCalendar() {
        val launched = mutableListOf<AppItem>()
        val events = listOf(
            EventMatch(7L, "Dentist", 1_800_000_000_000L, allDay = false),
            EventMatch(8L, "Dentist (again)", 1_800_100_000_000L, allDay = true),
        )
        show { Search(query = "dent", global = GlobalResults(events = events), onLaunch = { launched += it }) }
        advance(900)
        capture("search_events")
        compose.onAllNodesWithText("calendar").assertCountEquals(1)
        compose.onNodeWithTag("result:event:7").assertTextContains("Dentist", substring = true)
        compose.onNodeWithTag("result:event:8").assertExists()
        compose.onNodeWithTag("result:event:7").performClick()
        val opened = launched.single()
        assertEquals("content://com.android.calendar/events/7", (opened.launch as Launch.Link).uri)
    }

    @Test
    fun withNoEventsThereIsNoCalendarHeading() {
        show { Search(query = "dent", global = GlobalResults(webQuery = "dent")) }
        advance(900)
        compose.onAllNodesWithText("calendar").assertCountEquals(0)
        assertFalse(GlobalResults(events = listOf(EventMatch(1, "a", 0, false))).isEmpty)
        assertTrue(GlobalResults().isEmpty)
    }

    // --- the compact list

    private fun rowHeight(compact: Boolean): Float {
        show { Search(query = "", compact = compact) }
        advance(900)
        return compose.onNodeWithTag("app:app:Alpha").getUnclippedBoundsInRoot().height.value
    }

    @Test
    fun theCompactListHasTighterRows() {
        val normal = rowHeight(compact = false)
        assertEquals(64f, normal, 0.5f)
    }

    @Test
    fun theCompactRowsAreSmaller() {
        val compact = rowHeight(compact = true)
        assertEquals(48f, compact, 0.5f)
        capture("list_compact")
    }

    // --- the app menu

    private val shortcut = AppShortcut("compose", "com.example.mail", "New message", android.os.Process.myUserHandle())

    @Test
    fun theMenuOffersTheStoreAndTheShareSheetWhenTheAppHasAPage() {
        var store = 0
        var share = 0
        show {
            AppContextMenu(
                state = MenuState(AppItem("com.example.mail", "Mail", Launch.Action("x"), Glyph.Mail), top = 100f, height = 80f),
                pinned = false, onPinToggle = {}, onUninstall = {}, onDismiss = {},
                onStore = { store++ }, onShare = { share++ },
            )
        }
        advance(300)
        capture("app_menu_store")
        compose.onNodeWithTag("menu:store").performClick()
        compose.onNodeWithTag("menu:share").performClick()
        assertEquals(1, store)
        assertEquals(1, share)
    }

    @Test
    fun theMenuOfAnEntryWithoutAPageHasNeither() {
        show {
            AppContextMenu(
                state = MenuState(AppItem("settings", "Settings", Launch.Action("x"), Glyph.Settings), top = 100f, height = 80f),
                pinned = false, onPinToggle = {}, onUninstall = {}, onDismiss = {}, shortcuts = listOf(shortcut),
            )
        }
        advance(300)
        compose.onAllNodesWithTag("menu:store").assertCountEquals(0)
        compose.onAllNodesWithTag("menu:share").assertCountEquals(0)
    }

    @Test
    fun theCompactMenuDrawsTheRowAsTheCompactListDoes() {
        show {
            AppContextMenu(
                state = MenuState(AppItem("com.example.mail", "Mail", Launch.Action("x"), Glyph.Mail), top = 100f, height = 48f),
                pinned = false, onPinToggle = {}, onUninstall = {}, onDismiss = {}, compact = true,
            )
        }
        advance(300)
        compose.onAllNodesWithText("Mail").assertCountEquals(1)
    }

    // --- the settings

    private var current = LauncherSettings()

    @Composable
    private fun AppsPage(section: SettingsSection = SettingsSection.Search) {
        var settings by remember { mutableStateOf(current) }
        SettingsScreen(
            env = SettingsEnv(settings = settings, onChange = { change -> settings = change(settings); current = settings }),
            section = section, onSectionChange = {}, sectionAway = remember { Animatable(0f) },
        )
    }

    private fun tap(tag: String) {
        scrollIntoView(tag, topMargin = 0.2f)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        advance(100)
    }

    @Test
    fun theCompactListIsSwitchedInTheSettings() {
        current = LauncherSettings()
        show { AppsPage(SettingsSection.Apps) }
        advance(500)
        tap("apps:compact")
        assertTrue(current.compactList)
    }

    @Test
    fun theCurrencyAndTheCalendarAreSwitchedInTheSettings() {
        current = LauncherSettings()
        show { AppsPage() }
        advance(500)
        capture("settings_search_more")
        tap("search:currency")
        assertTrue(current.searchCurrency)
        tap("search:calendar")
        assertTrue(current.searchCalendar)
        tap("search:currency")
        assertFalse(current.searchCurrency)
    }

    @Test
    fun currencyConversionsNeedTheAnswersToBeOn() {
        current = LauncherSettings(searchAnswers = false)
        show { AppsPage() }
        advance(500)
        tap("search:currency")
        assertFalse("the switch is disabled while the answers are off", current.searchCurrency)
    }
}
