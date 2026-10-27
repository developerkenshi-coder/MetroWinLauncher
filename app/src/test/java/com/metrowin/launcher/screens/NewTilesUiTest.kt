package com.metrowin.launcher.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.FinanceData
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.LivePerson
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.data.Quote
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.TileTransition
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.StopwatchState
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.model.TimerState
import com.metrowin.launcher.ui.anim.LocalTileTransition
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.theme.DefaultAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The faces of the tiles of the third round: timer, stopwatch, news, finance, reminders, place, album and group. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NewTilesUiTest : ScreenshotBase() {
    private val now = System.currentTimeMillis()
    private val hour = 60L * 60 * 1000

    private fun pinned(id: String, label: String, glyph: Glyph, data: TileData? = null, live: LiveKind? = null) =
        AppItem(id, label, Launch.Internal("x"), glyph, live = live, pinOnly = true, data = data)

    private fun solid(argb: Int): ImageBitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }.asImageBitmap()

    private fun widthOf(size: TileSize): Dp = when (size) {
        TileSize.Small -> 96.dp
        TileSize.Medium -> 200.dp
        TileSize.Wide -> 408.dp
        TileSize.Large -> 408.dp
    }

    private fun heightOf(size: TileSize): Dp = if (size == TileSize.Small) 96.dp else 200.dp

    private fun showTile(
        item: AppItem, size: TileSize = TileSize.Medium, live: LiveTileData = LiveTileData(), style: TileStyle = TileStyle.Standard,
        transition: TileTransition = TileTransition.Flip,
    ) = show {
        CompositionLocalProvider(LocalTileTransition provides transition) {
            Box(Modifier.size(widthOf(size), heightOf(size)).background(DefaultAccent)) {
                TileFace(item, size, widthOf(size), heightOf(size), live, seed = 0, notifications = 0, style = style)
            }
        }
    }

    private fun textOf(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    // --- the timer

    @Test
    fun anIdleTimerTileShowsItsLengthAndHowToStart() {
        showTile(pinned("timer:1", "Timer", Glyph.Hourglass, TileData.Timer(TimerState(5 * 60_000L))))
        advance(300)
        capture("tile_timer_idle")
        assertEquals("05:00", textOf("timer:time"))
        compose.onNodeWithTag("timer:status").assertExists()
    }

    @Test
    fun aSmallIdleTimerTileKeepsItsGlyph() {
        showTile(pinned("timer:1", "Timer", Glyph.Hourglass, TileData.Timer(TimerState(5 * 60_000L))), TileSize.Small)
        advance(300)
        compose.onAllNodesWithTag("timer:time").assertCountEquals(0)
    }

    @Test
    fun aRunningTimerTileShowsWhatIsLeftAndNoStatusLine() {
        val state = TimerState(10 * 60_000L, endAt = System.currentTimeMillis() + 90_000L)
        showTile(pinned("timer:1", "Timer", Glyph.Hourglass, TileData.Timer(state)))
        advance(300)
        capture("tile_timer_running")
        val shown = textOf("timer:time")
        assertTrue("about a minute and a half is left: $shown", shown == "01:30" || shown == "01:29")
        compose.onAllNodesWithTag("timer:status").assertCountEquals(0)
    }

    @Test
    fun aSmallRunningTimerTileStillCountsDown() {
        val state = TimerState(10 * 60_000L, endAt = System.currentTimeMillis() + 90_000L)
        showTile(pinned("timer:1", "Timer", Glyph.Hourglass, TileData.Timer(state)), TileSize.Small)
        advance(300)
        assertTrue(textOf("timer:time").startsWith("01:"))
    }

    @Test
    fun aTimerThatEndedSaysSo() {
        // The end was a long time ago (a moment in 1970): the timer ran out.
        showTile(pinned("timer:1", "Timer", Glyph.Hourglass, TileData.Timer(TimerState(60_000L, endAt = 1L))))
        advance(300)
        assertEquals("00:00", textOf("timer:time"))
        compose.onNodeWithTag("timer:status").assertExists()
    }

    @Test
    fun aPausedTimerKeepsWhereItStopped() {
        val state = TimerState(10 * 60_000L, endAt = 0L, remainingMs = 4 * 60_000L)
        showTile(pinned("timer:1", "Timer", Glyph.Hourglass, TileData.Timer(state)))
        advance(300)
        assertEquals("04:00", textOf("timer:time"))
        compose.onNodeWithTag("timer:status").assertExists()
    }

    // --- the stopwatch

    @Test
    fun aStopwatchTileShowsTheTimeAndTheLaps() {
        val state = StopwatchState(accumulatedMs = 83_000L, laps = listOf(40_000L, 83_000L))
        showTile(pinned("stopwatch:1", "Stopwatch", Glyph.Stopwatch, TileData.Stopwatch(state)))
        advance(300)
        capture("tile_stopwatch")
        assertEquals("01:23", textOf("stopwatch:time"))
        assertTrue(textOf("stopwatch:laps").contains("2"))
    }

    @Test
    fun aSmallStopwatchAtZeroKeepsItsGlyph() {
        showTile(pinned("stopwatch:1", "Stopwatch", Glyph.Stopwatch, TileData.Stopwatch(StopwatchState())), TileSize.Small)
        advance(300)
        compose.onAllNodesWithTag("stopwatch:time").assertCountEquals(0)
    }

    // --- the news

    private val news = NewsData(
        "Top stories", listOf(NewsItem("Headline one", "ANSA", "https://example.com/1", now - hour), NewsItem("Headline two", "Reuters", "https://example.com/2", now - 2 * hour)),
        now, "key",
    )
    private val newsItem = pinned("news", "News", Glyph.News, TileData.News, LiveKind.News)

    @Test
    fun theNewsTileShowsTheFirstHeadline() {
        showTile(newsItem, TileSize.Wide, LiveTileData(news = news))
        advance(300)
        capture("tile_news")
        assertEquals("Headline one", textOf("news:headline"))
    }

    @Test
    fun aSmallNewsTileAndOneWithoutHeadlinesKeepTheirGlyph() {
        showTile(newsItem, TileSize.Small, LiveTileData(news = news))
        advance(300)
        compose.onAllNodesWithTag("news:headline").assertCountEquals(0)
    }

    @Test
    fun theNewsTileWithoutHeadlinesIsJustItsGlyph() {
        showTile(newsItem, TileSize.Medium, LiveTileData(news = null))
        advance(300)
        compose.onAllNodesWithTag("news:headline").assertCountEquals(0)
    }

    @Test
    fun theHeadlinesTurnOverAfterEachOther() {
        showTile(newsItem, TileSize.Wide, LiveTileData(news = news), transition = TileTransition.Flip)
        advance(300)
        compose.onNodeWithText("Headline one").assertExists()
        advance(4_000)
        compose.onNodeWithText("Headline two").assertExists()
        compose.onAllNodesWithText("Headline one").assertCountEquals(0)
    }

    @Test
    fun theHeadlinesCanSlideInsteadOfTurningOver() {
        showTile(newsItem, TileSize.Wide, LiveTileData(news = news), transition = TileTransition.Slide)
        advance(300)
        compose.onNodeWithText("Headline one").assertExists()
        // While the change goes on both faces are there, one going up and the other coming in from below.
        advance(1_500)
        advance(300)
        compose.onNodeWithText("Headline one").assertExists()
        compose.onNodeWithText("Headline two").assertExists()
        advance(2_500)
        compose.onNodeWithText("Headline two").assertExists()
        compose.onAllNodesWithText("Headline one").assertCountEquals(0)
    }

    // --- the finance

    private val finance = FinanceData(
        "EUR", listOf(Quote("fx:USD", 0.92, 0.4), Quote("fx:GBP", 1.17, -0.2), Quote("crypto:bitcoin", 61_000.0, -1.2)), "2026-10-23", now, "key",
    )
    private val financeItem = pinned("finance", "Finance", Glyph.Finance, TileData.Finance, LiveKind.Finance)

    @Test
    fun theFinanceTileListsTheQuotes() {
        showTile(financeItem, TileSize.Wide, LiveTileData(finance = finance))
        advance(300)
        capture("tile_finance")
        compose.onAllNodesWithTag("finance:row").assertCountEquals(3)
        // A wide tile tells the change too.
        compose.onNodeWithText("+0.40%", substring = true).assertExists()
    }

    @Test
    fun aMediumFinanceTileHasThreeRowsAndAnArrowOnly() {
        showTile(financeItem, TileSize.Medium, LiveTileData(finance = finance))
        advance(300)
        compose.onAllNodesWithTag("finance:row").assertCountEquals(3)
        compose.onAllNodesWithText("+0.40%", substring = true).assertCountEquals(0)
    }

    @Test
    fun aSmallFinanceTileShowsTheFirstValue() {
        showTile(financeItem, TileSize.Small, LiveTileData(finance = finance))
        advance(300)
        compose.onNodeWithTag("finance:value").assertExists()
        compose.onAllNodesWithTag("finance:row").assertCountEquals(0)
    }

    // --- the reminders

    @Test
    fun theRemindersTileCountsAndNamesTheNextOne() {
        val reminders = listOf(Reminder(1, "Call Anna", now + hour), Reminder(2, "Buy milk", now + 2 * hour), Reminder(3, "Old", now - 3 * hour))
        showTile(pinned("reminders", "Reminders", Glyph.Bell, TileData.Reminders), TileSize.Medium, LiveTileData(reminders = reminders))
        advance(300)
        capture("tile_reminders")
        // The one whose time has passed is not "to come".
        assertEquals("2", textOf("reminders:count"))
        assertEquals("Call Anna", textOf("reminders:next"))
    }

    @Test
    fun withNothingToRememberTheRemindersTileIsJustTheBell() {
        showTile(pinned("reminders", "Reminders", Glyph.Bell, TileData.Reminders), TileSize.Medium, LiveTileData(reminders = emptyList()))
        advance(300)
        compose.onAllNodesWithTag("reminders:count").assertCountEquals(0)
    }

    // --- the place

    @Test
    fun aPlaceTileShowsItsAddress() {
        showTile(pinned("place:1", "Home", Glyph.Maps, TileData.Place("Via Roma 1, Torino")), TileSize.Medium)
        advance(300)
        capture("tile_place")
        assertEquals("Via Roma 1, Torino", textOf("place:address"))
    }

    // --- the album and the group

    @Test
    fun anAlbumTileFadesThroughItsOwnPhotos() {
        val red = 0xFFE51400.toInt()
        val blue = 0xFF0050EF.toInt()
        val item = pinned("album:b1", "Holidays", Glyph.Photos, TileData.Album("b1"), LiveKind.Album)
        showTile(item, live = LiveTileData(albums = mapOf("b1" to listOf(solid(red), solid(blue)), "other" to listOf(solid(0xFF00FF00.toInt())))), style = TileStyle.Cycle)
        advance(300)
        assertEquals("the first photo of the album", red, capture("tile_album_1").getPixel(300, 150))
        advance(8_000)
        assertEquals("then the second", blue, capture("tile_album_2").getPixel(300, 150))
    }

    @Test
    fun anAlbumTileWithoutAStyleTurnsOverToItsPhotos() {
        val red = 0xFFE51400.toInt()
        val item = pinned("album:b1", "Holidays", Glyph.Photos, TileData.Album("b1"), LiveKind.Album)
        showTile(item, live = LiveTileData(albums = mapOf("b1" to listOf(solid(red)))), style = TileStyle.Standard)
        advance(3_500)
        assertEquals(red, capture("tile_album_flip").getPixel(300, 150))
    }

    @Test
    fun aGroupTileShowsTheFacesOfItsPeopleOneAfterTheOther() {
        val item = pinned("group:7", "Family", Glyph.People, TileData.Group(7L), LiveKind.Group)
        val people = listOf(LivePerson("Anna", solid(0xFFE51400.toInt())), LivePerson("Luca", solid(0xFF0050EF.toInt())))
        showTile(item, live = LiveTileData(groups = mapOf(7L to people)))
        advance(300)
        compose.onAllNodesWithText("Anna").assertCountEquals(0)
        advance(3_000)
        capture("tile_group")
        compose.onNodeWithText("Anna").assertExists()
    }

    @Test
    fun aGroupWithNoPeopleKeepsItsGlyph() {
        val item = pinned("group:7", "Family", Glyph.People, TileData.Group(7L), LiveKind.Group)
        showTile(item, live = LiveTileData(groups = mapOf(7L to emptyList())))
        advance(3_000)
        compose.onAllNodesWithText("Anna").assertCountEquals(0)
        compose.onNodeWithText("Family").assertExists()
    }
}
