package com.metrowin.launcher.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.DayForecast
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.data.WeatherDetails
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.weather.WeatherScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The tiles of storage and alarms, and the details on the weather tile and its page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SystemTilesUiTest : ScreenshotBase() {
    private val storage = AppItem("stock:storagesense", "Storage Sense", Launch.Action("x"), Glyph.Storage, live = LiveKind.Storage)
    private val alarms = AppItem("stock:alarms", "Alarms", Launch.Action("x"), Glyph.Alarm, live = LiveKind.Alarm)
    private val weatherItem = AppItem("launcher:weather", "Forecast", Launch.Internal("weather"), Glyph.Weather, live = LiveKind.Weather)

    private fun showTile(item: AppItem, live: LiveTileData, size: TileSize = TileSize.Medium) = show {
        val w = if (size == TileSize.Wide) 408.dp else if (size == TileSize.Small) 96.dp else 200.dp
        val h = if (size == TileSize.Small) 96.dp else 200.dp
        Box(Modifier.size(w, h).background(DefaultAccent)) { TileFace(item, size, w, h, live, seed = 0, notifications = 0) }
    }

    // --- storage

    @Test
    fun theStorageTileShowsWhatIsFreeAndHowFullThePhoneIs() {
        showTile(storage, LiveTileData(storage = StorageInfo(46_000_000_000, 128_000_000_000)))
        advance(500)
        capture("tile_storage")
        compose.onNodeWithTag("storage:free").assertExists()
        compose.onAllNodesWithText("46 GB").assertCountEquals(1)
        compose.onAllNodesWithText("free of 128 GB").assertCountEquals(1)
    }

    @Test
    fun aSmallStorageTileIsJustTheNumber() {
        showTile(storage, LiveTileData(storage = StorageInfo(7_400_000_000, 64_000_000_000)), TileSize.Small)
        advance(500)
        compose.onAllNodesWithText("7.4 GB").assertCountEquals(1)
        compose.onAllNodesWithText("free of 64 GB").assertCountEquals(0)
    }

    @Test
    fun withNoReadingTheStorageTileKeepsItsGlyph() {
        showTile(storage, LiveTileData(storage = null))
        advance(500)
        compose.onAllNodesWithTag("storage:free").assertCountEquals(0)
        compose.onAllNodesWithText("Storage Sense").assertCountEquals(1)
    }

    // --- alarms

    private fun alarmAt(daysFromToday: Long, time: LocalTime) =
        LocalDateTime.of(LocalDate.now().plusDays(daysFromToday), time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun theAlarmTileShowsWhenTheNextAlarmRings() {
        showTile(alarms, LiveTileData(nextAlarm = alarmAt(1, LocalTime.of(7, 30))))
        advance(500)
        capture("tile_alarm")
        compose.onNodeWithTag("alarm:time").assertExists()
        compose.onAllNodesWithText("tomorrow").assertCountEquals(1)
    }

    @Test
    fun anAlarmLaterTodayIsToday() {
        // 23:59 is later than now in any test run but the last minute of the day; the face says "today" for any alarm not on another day.
        showTile(alarms, LiveTileData(nextAlarm = alarmAt(0, LocalTime.of(23, 59))))
        advance(500)
        compose.onAllNodesWithText("today").assertCountEquals(1)
    }

    @Test
    fun withNoAlarmTheTileIsJustTheMark() {
        showTile(alarms, LiveTileData(nextAlarm = 0L))
        advance(500)
        compose.onAllNodesWithTag("alarm:time").assertCountEquals(0)
        compose.onAllNodesWithText("Alarms").assertCountEquals(1)
    }

    // --- the weather

    private val details = WeatherDetails(
        feelsLike = 10, humidity = 71, wind = 14, rainChance = 30, sunrise = LocalTime.of(7, 12), sunset = LocalTime.of(17, 48), uvIndex = 3,
    )

    private fun weather(details: WeatherDetails) = WeatherData(
        temperature = 13, condition = WeatherCondition.Cloudy, isDay = true,
        days = listOf(
            DayForecast(LocalDate.of(2026, 10, 25), WeatherCondition.Cloudy, 14, 7, rainChance = 30),
            DayForecast(LocalDate.of(2026, 10, 26), WeatherCondition.Rain, 12, 8, rainChance = 85),
            DayForecast(LocalDate.of(2026, 10, 27), WeatherCondition.Clear, 17, 6, rainChance = 0),
        ),
        fahrenheit = false, fetchedAt = 0L, details = details,
    )

    @Test
    fun theForecastPageHasTheDetailsAndTheChanceOfRainOfEachDay() {
        show { WeatherScreen(weather(details), "Torino", onRefresh = {}, onChooseCity = {}) }
        advance(300)
        capture("weather_details")
        compose.onNodeWithTag("weather:details").assertExists()
        compose.onAllNodesWithText("feels like").assertCountEquals(1)
        compose.onAllNodesWithText("14 km/h").assertCountEquals(1)
        compose.onAllNodesWithText("71%").assertCountEquals(1)
        compose.onAllNodesWithText("Rain · rain 85%").assertCountEquals(1)
    }

    @Test
    fun withNoDetailsThePageHasNoDetailsBlock() {
        show { WeatherScreen(weather(WeatherDetails()), "Torino", blocks = com.metrowin.launcher.data.WeatherBlock.DEFAULT - com.metrowin.launcher.data.WeatherBlock.Moon.code, onRefresh = {}, onChooseCity = {}) }
        advance(300)
        compose.onAllNodesWithTag("weather:details").assertCountEquals(0)
    }

    @Test
    fun theWeatherTileTurnsToTheDetailsAfterTheDays() {
        showTile(weatherItem, LiveTileData(weather = weather(details)))
        // The faces turn one after the other: the temperature, the days (from about 2.2 s), then the details (from about 7.9 s).
        advance(8_500)
        compose.onAllNodesWithTag("weather:detail").assertCountEquals(3)
    }

    @Test
    fun aTileWithNoDetailsHasOnlyTheTwoFaces() {
        showTile(weatherItem, LiveTileData(weather = weather(WeatherDetails())))
        advance(20_000)
        compose.onAllNodesWithTag("weather:detail").assertCountEquals(0)
    }
}
