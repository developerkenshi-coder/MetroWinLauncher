package com.metrowin.launcher.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.metrowin.launcher.data.AppSections
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.NearPlace
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.data.Today
import com.metrowin.launcher.data.TodayAction
import com.metrowin.launcher.data.TodayExtras
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.apps.AppListModel
import com.metrowin.launcher.ui.apps.AppListScreen
import com.metrowin.launcher.ui.gesture.PanoramaTitles
import com.metrowin.launcher.ui.start.EditBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Today card, the "…" of the edit bar and the panorama titles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class TodayUiTest : ScreenshotBase() {
    private val app = AppItem("app:a", "Alpha", Launch.Action("x"))
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    private val weather = WeatherData(18, WeatherCondition.PartlyCloudy, true, emptyList(), false, now)

    private fun fullDay() = Today.info(
        TodayExtras(now, LiveEvent("Team meeting", now + 2 * hour, false), now + 10 * hour),
        weather,
        listOf(Reminder(1, "Call Anna", now + hour), Reminder(2, "Buy milk", now + 3 * hour), Reminder(3, "Missed one", now - 3 * hour)),
        now,
    )

    @Composable
    private fun List(
        query: String = "",
        info: com.metrowin.launcher.data.TodayInfo? = fullDay(),
        onToday: (TodayAction) -> Unit = {},
        card: Boolean = true,
    ) {
        val model = remember(card) { AppListModel(AppSections.group(AppSections.sort(listOf(app))), today = card) }
        AppListScreen(
            model = model, listState = rememberLazyListState(), replayToken = 0, exiting = false,
            onLaunch = {}, onLongPress = { _, _, _ -> }, onHeaderClick = {},
            query = query, onQueryChange = {}, results = emptyList(),
            today = info, onToday = onToday,
        )
    }

    // --- the Today card

    @Test
    fun theCardTellsTheDayTheWeatherTheEventTheRemindersAndTheAlarm() {
        show { List() }
        advance(900)
        capture("today_card")
        listOf(
            "today", "today:date", "today:weather", "today:event", "today:reminder:1", "today:reminder:2", "today:missed", "today:alarm",
        ).forEach { compose.onNodeWithTag(it).assertExists() }
        // Only the two nearest reminders are written out.
        compose.onAllNodesWithTag("today:reminder:3").assertCountEquals(0)
        NearPlace.entries.forEach { compose.onNodeWithTag("today:near:${it.name}").assertExists() }
        compose.onNodeWithText("Call Anna", substring = true).assertExists()
        compose.onNodeWithText("18°", substring = true).assertExists()
    }

    @Test
    fun theCardIsAboveTheSearchBox() {
        show { List() }
        advance(900)
        val card = compose.onNodeWithTag("today").getBoundsInRoot()
        val field = compose.onNodeWithTag("search:field").getBoundsInRoot()
        assertTrue("the card ends at ${card.bottom}, the box starts at ${field.top}", card.bottom <= field.top)
    }

    @Test
    fun aQuietDayLeavesTheDateAndThePlaces() {
        show { List(info = Today.info(TodayExtras(), null, emptyList(), now)) }
        advance(900)
        compose.onNodeWithTag("today:date").assertExists()
        listOf("today:weather", "today:event", "today:alarm", "today:missed").forEach { compose.onAllNodesWithTag(it).assertCountEquals(0) }
        compose.onNodeWithTag("today:near:Pharmacy").assertExists()
    }

    @Test
    fun everyLineOpensWhatItTellsAbout() {
        val done = mutableListOf<TodayAction>()
        show { List(onToday = { done += it }) }
        advance(900)
        listOf("today:date", "today:event", "today:weather", "today:reminder:1", "today:alarm", "today:near:Pharmacy").forEach {
            compose.onNodeWithTag(it).performClick()
        }
        assertEquals(
            listOf(
                TodayAction.Calendar, TodayAction.Calendar, TodayAction.Weather, TodayAction.Reminders, TodayAction.Clock,
                TodayAction.Near("Pharmacy"),
            ),
            done,
        )
    }

    @Test
    fun theCardGoesAwayWhenSomethingIsTypedAndWithoutTheModelsRow() {
        show { List(query = "alp") }
        advance(900)
        compose.onAllNodesWithTag("today").assertCountEquals(0)
    }

    @Test
    fun theSwitchOfTheCardTakesItAway() {
        show { List(card = false, info = null) }
        advance(900)
        compose.onAllNodesWithTag("today").assertCountEquals(0)
        compose.onNodeWithTag("search:field").assertExists()
    }

    private fun cardIn(style: ThemeStyle) {
        show(style = style) { List() }
        advance(900)
        compose.onNodeWithTag("today").assertIsDisplayed()
        compose.onNodeWithTag("today:near:Pharmacy").assertIsDisplayed()
        capture("today_card_${style.name.lowercase()}")
    }

    @Test
    fun theCardReadsInTheClassicLook() = cardIn(ThemeStyle.Classic)

    @Test
    fun theCardReadsInTheGlassLook() = cardIn(ThemeStyle.Glass)

    @Test
    fun theCardReadsInTheDroidLook() = cardIn(ThemeStyle.Droid)

    // --- the "…" of the edit bar

    @Composable
    private fun Bar(withMore: Boolean) {
        var labels by remember { mutableStateOf(false) }
        EditBar(
            size = TileSize.Medium, page = 0, pageCount = 1, onSize = {}, onMovePage = {}, onDone = {},
            onAdd = {}, onFolder = {}, onLabels = if (withMore) ({ labels = !labels }) else null, labels = labels,
        )
    }

    @Test
    fun theDotsShowTheNamesOfTheButtonsAndHideThemAgain() {
        show { Box(Modifier.fillMaxSize()) { Bar(withMore = true) } }
        advance(300)
        compose.onNodeWithTag("edit:more").assertExists()
        listOf("add", "folder", "small", "medium", "wide").forEach { compose.onAllNodesWithText(it).assertCountEquals(0) }
        compose.onNodeWithTag("edit:more").performClick()
        compose.waitForIdle()
        advance(300)
        capture("edit_bar_labels")
        listOf("add", "folder", "small", "medium", "wide").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithTag("edit:more").performClick()
        compose.waitForIdle()
        advance(300)
        listOf("add", "folder", "small", "medium", "wide").forEach { compose.onAllNodesWithText(it).assertCountEquals(0) }
    }

    @Test
    fun withTheBarSwitchedOffThereAreNoDotsAndNoNames() {
        show { Box(Modifier.fillMaxSize()) { Bar(withMore = false) } }
        advance(300)
        compose.onAllNodesWithTag("edit:more").assertCountEquals(0)
        compose.onNodeWithTag("edit:done").assertExists()
    }

    // --- the panorama titles

    @Composable
    private fun Panorama(state: PagerState) {
        Box(Modifier.fillMaxSize()) {
            PanoramaTitles(state, startPages = 2, startTitle = "start", appsTitle = "apps")
            HorizontalPager(state, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
        }
    }

    private fun titleX(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.x

    @Test
    fun theTitlesStayBehindWhileThePagesSlideOverThemAtHalfTheSpeed() {
        lateinit var state: PagerState
        show {
            state = rememberPagerState { 3 }
            Panorama(state)
        }
        advance(300)
        capture("panorama_start")
        val width = compose.onRoot().fetchSemanticsNode().size.width.toFloat()
        // On the first page: the first title is at the edge, the second one is far to the right (two pages, half the speed).
        assertEquals(0f, titleX("panorama:start"), 2f)
        assertEquals(width, titleX("panorama:apps"), 2f)
        // Half a page of swipe: the pages moved 0.5 wide, the titles a quarter.
        compose.runOnUiThread { state.dispatchRawDelta(width / 2f) }
        advance(100)
        assertEquals(-width / 4f, titleX("panorama:start"), 4f)
        assertEquals(width - width / 4f, titleX("panorama:apps"), 4f)
    }

    @Test
    fun onTheLastPageTheLastTitleIsAtTheEdge() {
        lateinit var state: PagerState
        show {
            state = rememberPagerState(initialPage = 2) { 3 }
            Panorama(state)
        }
        advance(300)
        capture("panorama_apps")
        val width = compose.onRoot().fetchSemanticsNode().size.width.toFloat()
        assertEquals(0f, titleX("panorama:apps"), 2f)
        assertEquals(-width, titleX("panorama:start"), 2f)
    }
}
