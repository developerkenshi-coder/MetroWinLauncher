package com.metrowin.launcher.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.HourForecast
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.start.InfoFace
import com.metrowin.launcher.ui.start.adviceOf
import com.metrowin.launcher.ui.start.openInfoPart
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/** The faces of the info tile as the tile shows them: what is left out, what comes first, and that reduced motion does not hide faces. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class InfoFaceUiTest : ScreenshotBase() {
    private val item = AppItem("shortcut:info:1", "InfoTile", Launch.Internal("info"))
    private val now get() = System.currentTimeMillis()

    private fun tile(config: InfoConfig, live: LiveTileData, reduceMotion: Boolean = false) {
        show(reduceMotion = reduceMotion) {
            CompositionLocalProvider(LocalMetroColors provides LocalMetroColors.current.onTile()) {
                InfoFace(item, TileSize.Wide, 300.dp, 150.dp, config, live, seed = 0)
            }
        }
        advance(300)
    }

    private fun event(title: String, inMinutes: Long) = LiveEvent(title, now + inMinutes * 60_000L, false, endMillis = now + (inMinutes + 60) * 60_000L)

    @Test
    fun withNoEventsTheCalendarFaceIsLeftOutAndTheTimeStays() {
        tile(InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Calendar)), LiveTileData())
        compose.onNodeWithTag("worldclock:time").assertExists()
        compose.onNodeWithTag("event:title").assertDoesNotExist()
        advance(30_000)
        compose.onNodeWithTag("worldclock:time").assertExists()
    }

    @Test
    fun anEventWithinTheHourComesFirstAndAloneWhenTheTileIsSmart() {
        val live = LiveTileData(events = listOf(event("Dentist", 20)), nextEvent = event("Dentist", 20))
        tile(InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Calendar), smart = true), live)
        compose.onNodeWithTag("event:title").assertIsDisplayed()
        compose.onNodeWithTag("worldclock:time").assertDoesNotExist()
    }

    @Test
    fun withReducedMotionTheTileStillTurnsThroughItsFaces() {
        val live = LiveTileData(events = listOf(event("Dentist", 300)), nextEvent = event("Dentist", 300))
        tile(InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Calendar)), live, reduceMotion = true)
        compose.onNodeWithTag("worldclock:time").assertExists()
        advance(12_000)
        compose.onNodeWithTag("event:title").assertExists()
    }

    @Test
    fun anEventThatIsOverIsNotShown() {
        val over = LiveEvent("Old", now - 3 * 3_600_000L, false, endMillis = now - 2 * 3_600_000L)
        tile(InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Calendar)), LiveTileData(events = listOf(over), nextEvent = over))
        advance(12_000)
        compose.onNodeWithTag("event:title").assertDoesNotExist()
    }

    @Test
    fun theTitleOfAnEventCanBeHidden() {
        val live = LiveTileData(events = listOf(event("Oncology visit", 20)), nextEvent = event("Oncology visit", 20))
        tile(InfoConfig(parts = listOf(InfoPart.Calendar), hideTitles = true), live)
        compose.onNodeWithTag("event:title").assertIsDisplayed()
        // The words of the title are not on the tile.
        compose.onNodeWithTag("event:title").assertTextEquals(context.getString(com.metrowin.launcher.R.string.infot_event_hidden))
    }

    @Test
    fun theMeetingButtonsAreThereWhenTheEventHasALinkAndAPlace() {
        val e = LiveEvent("Stand-up", now + 20 * 60_000L, false, endMillis = now + 50 * 60_000L, location = "Via Roma 1", link = "https://meet.google.com/abc")
        tile(InfoConfig(parts = listOf(InfoPart.Calendar)), LiveTileData(events = listOf(e), nextEvent = e))
        compose.onNodeWithTag("event:join").assertExists()
        compose.onNodeWithTag("event:go").assertExists()
    }

    @Test
    fun theNewFacesSayWhatTheyHold() {
        val live = LiveTileData(missedCalls = 2, unreadMail = 3)
        tile(InfoConfig(parts = listOf(InfoPart.Waiting)), live)
        compose.onNodeWithTag("info:waiting").assertExists()
    }

    @Test
    fun aDashboardPutsTheTimeAndTheEventSideBySide() {
        val live = LiveTileData(events = listOf(event("Dentist", 20)), nextEvent = event("Dentist", 20))
        tile(InfoConfig(parts = listOf(InfoPart.Time, InfoPart.Calendar), layout = com.metrowin.launcher.model.InfoLayout.Dashboard), live)
        compose.onNodeWithTag("info:dashboard").assertIsDisplayed()
    }

    // --- the tap and the advice

    @Test
    fun aTouchOnTheWeatherOpensTheForecastAndWhatHasNoPageOpensNothing() {
        var forecast = 0
        assertTrue(openInfoPart(context, InfoPart.Weather, openWeather = { forecast++ }))
        assertEquals(1, forecast)
        assertFalse(openInfoPart(context, InfoPart.Steps))
        assertFalse(openInfoPart(context, null))
        assertFalse(openInfoPart(context, InfoPart.Published, InfoConfig(published = "no.such.app")))
    }

    private fun weather(celsius: Int, condition: WeatherCondition = WeatherCondition.Clear, hours: List<HourForecast> = emptyList()) =
        WeatherData(celsius, condition, true, emptyList(), false, 0L, hours = hours)

    @Test
    fun theWeatherGivesAdviceOnlyWhenItHasSome() {
        assertNull(adviceOf(null))
        assertNull(adviceOf(weather(18)))
        assertEquals(com.metrowin.launcher.R.string.infot_advice_cold, adviceOf(weather(3)))
        assertEquals(com.metrowin.launcher.R.string.infot_advice_hot, adviceOf(weather(33)))
        val rain = listOf(HourForecast(LocalTime.NOON, 12, WeatherCondition.Rain), HourForecast(LocalTime.NOON.plusHours(1), 12, WeatherCondition.Rain), HourForecast(LocalTime.NOON.plusHours(2), 12, WeatherCondition.Rain))
        assertEquals(com.metrowin.launcher.R.string.infot_advice_umbrella, adviceOf(weather(18, hours = rain)))
        assertTrue(LocalDate.now().year > 2000)
    }
}
