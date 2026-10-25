package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.GlobalResults
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.QuickAnswer
import com.metrowin.launcher.data.SearchEngine
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
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

/** The answers in the search box, the recent searches, and the settings that go with them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SearchAnswersUiTest : ScreenshotBase() {
    private val app = AppItem("app:a", "Alpha", Launch.Action("x"))

    @Composable
    private fun Search(
        query: String, answers: List<QuickAnswer>, recent: List<String> = emptyList(), engine: SearchEngine = SearchEngine.DuckDuckGo,
        onAnswer: (QuickAnswer) -> Unit = {}, onQuery: (String) -> Unit = {}, onClear: () -> Unit = {}, onLaunch: (AppItem) -> Unit = {},
        results: List<AppItem> = emptyList(), global: GlobalResults = GlobalResults.Empty, submitted: MutableList<String> = mutableListOf(),
    ) {
        val model = remember { AppListModel(AppSections.group(AppSections.sort(listOf(app)))) }
        AppListScreen(
            model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
            onLaunch = onLaunch, onLongPress = { _, _, _ -> }, onHeaderClick = {},
            query = query, onQueryChange = onQuery, results = results, global = global,
            answers = answers, onAnswer = onAnswer, recentSearches = recent, onClearRecents = onClear, engine = engine,
            onSearchSubmit = { submitted += it },
        )
    }

    @Test
    fun aSumIsAnsweredAboveTheAppsAndTouchingItCopiesTheResult() {
        val touched = mutableListOf<QuickAnswer>()
        show { Search("12*3+4", listOf(QuickAnswer.Calculation("12*3+4", "40")), onAnswer = { touched += it }) }
        advance(900)
        capture("search_answer_sum")
        compose.onNodeWithTag("answer:Calculation").performClick()
        assertEquals(listOf<QuickAnswer>(QuickAnswer.Calculation("12*3+4", "40")), touched)
    }

    @Test
    fun aPhoneNumberOffersTwoRowsAndATimerOne() {
        val touched = mutableListOf<QuickAnswer>()
        show { Search("333123456", listOf(QuickAnswer.Call("333123456"), QuickAnswer.Message("333123456")), onAnswer = { touched += it }) }
        advance(900)
        capture("search_answer_phone")
        compose.onNodeWithTag("answer:Call").performClick()
        compose.onNodeWithTag("answer:Message").performClick()
        assertEquals(listOf<QuickAnswer>(QuickAnswer.Call("333123456"), QuickAnswer.Message("333123456")), touched)
    }

    @Test
    fun everyKindOfAnswerHasARow() {
        val all = listOf(
            QuickAnswer.Conversion("5 km = 3.1 mi", "3.1 mi"), QuickAnswer.Link("https://a.com", "a.com"), QuickAnswer.Email("a@b.it"),
            QuickAnswer.Timer(600), QuickAnswer.Alarm(7, 5),
        )
        show { Search("x", all) }
        advance(900)
        listOf("Conversion", "Link", "Email", "Timer", "Alarm").forEach { compose.onNodeWithTag("answer:$it").assertExists() }
    }

    @Test
    fun withNoAnswerThereIsNoAnswerRow() {
        show { Search("pizza", emptyList()) }
        advance(900)
        compose.onAllNodesWithTag("answer:Calculation").assertCountEquals(0)
    }

    @Test
    fun theWebRowNamesTheEngineAndOpensItsAddress() {
        val launched = mutableListOf<AppItem>()
        show { Search("meteo", emptyList(), engine = SearchEngine.Google, onLaunch = { launched += it }, global = GlobalResults(webQuery = "meteo")) }
        advance(900)
        capture("search_web_engine")
        compose.onNodeWithTag("result:web").performClick()
        assertEquals("https://www.google.com/search?q=meteo", (launched.single().launch as Launch.Link).uri)
    }

    @Test
    fun theRecentSearchesShowWhenTheBoxIsTouchedAndAreForgottenWithClear() {
        var cleared = 0
        var typed = ""
        show { Search("", emptyList(), recent = listOf("pizza", "meteo torino"), onClear = { cleared++ }, onQuery = { typed = it }) }
        advance(900)
        // Not touched yet: the list is as it was.
        compose.onAllNodesWithTag("recent:pizza").assertCountEquals(0)
        compose.onNodeWithTag("search:field").performClick()
        compose.waitForIdle()
        advance(300)
        capture("search_recent")
        compose.onNodeWithTag("recent:pizza").assertExists()
        compose.onNodeWithTag("recent:meteo torino").assertExists()
        compose.onNodeWithTag("recent:meteo torino").performClick()
        assertEquals("meteo torino", typed)
        compose.onNodeWithTag("recent:clear").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun confirmingTheSearchFromTheKeyboardIsReported() {
        val submitted = mutableListOf<String>()
        var query by mutableStateOf("")
        show { Search(query, emptyList(), onQuery = { query = it }, submitted = submitted) }
        advance(900)
        compose.onNodeWithTag("search:field").performTextInput("caffè")
        compose.waitForIdle()
        assertEquals("caffè", query)
    }

    // --- the settings

    private var current = LauncherSettings()
    private var forgotten = 0

    @Composable
    private fun AppsPage() {
        var settings by remember { mutableStateOf(current) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings, onChange = { change -> settings = change(settings); current = settings },
                onClearSearches = { forgotten++ },
            ),
            section = SettingsSection.Search, onSectionChange = {}, sectionAway = remember { Animatable(0f) },
        )
    }

    private fun tap(tag: String) {
        scrollIntoView(tag, topMargin = 0.2f)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        advance(100)
    }

    @Test
    fun theSettingsChooseTheEngineAndSwitchAnswersAndMemoryOff() {
        current = LauncherSettings()
        forgotten = 0
        show { AppsPage() }
        advance(500)
        capture("settings_search")
        tap("search:answers")
        assertFalse(current.searchAnswers)
        tap("search:remember")
        assertFalse(current.rememberSearches)
        tap("search:engine:header")
        tap("search:engine:1")
        assertEquals(SearchEngine.Google, current.searchEngine)
        tap("search:forget")
        assertEquals(1, forgotten)
        assertTrue(LauncherSettings().searchAnswers && LauncherSettings().rememberSearches)
    }
}
