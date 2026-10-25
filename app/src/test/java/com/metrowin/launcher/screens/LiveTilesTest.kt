package com.metrowin.launcher.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.DayForecast
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.weather.WeatherScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The battery and weather tiles, the forecast page, and the settings to choose the city. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class LiveTilesTest : ScreenshotBase() {
    private val batteryItem = AppItem("stock:batterysaver", "Battery Saver", Launch.Action("x"), Glyph.Battery, live = LiveKind.Battery)
    private val weatherItem = AppItem("launcher:weather", "Forecast", Launch.Internal("weather"), Glyph.Weather, live = LiveKind.Weather)

    private val sunny = WeatherData(
        temperature = 21, condition = WeatherCondition.Clear, isDay = true, fahrenheit = false, fetchedAt = System.currentTimeMillis(),
        days = listOf(
            DayForecast(LocalDate.of(2026, 10, 25), WeatherCondition.Clear, 22, 11),
            DayForecast(LocalDate.of(2026, 10, 26), WeatherCondition.Rain, 17, 9),
            DayForecast(LocalDate.of(2026, 10, 27), WeatherCondition.Cloudy, 15, 8),
            DayForecast(LocalDate.of(2026, 10, 28), WeatherCondition.Snow, 3, -2),
        ),
    )

    private fun showTile(item: AppItem, live: LiveTileData, size: TileSize = TileSize.Medium) = show {
        val width = if (size == TileSize.Wide) 408.dp else 200.dp
        Box(Modifier.size(width, 200.dp).background(DefaultAccent)) {
            TileFace(item, size, width, 200.dp, live, seed = 0, notifications = 0)
        }
    }

    // --- battery

    @Test
    fun theBatteryTileShowsTheChargeAndABarThatFillsWithIt() {
        var percent by mutableIntStateOf(100)
        show {
            Box(Modifier.size(200.dp, 200.dp).background(DefaultAccent)) {
                TileFace(
                    batteryItem, TileSize.Medium, 200.dp, 200.dp,
                    LiveTileData(battery = BatteryInfo(percent, BatteryState.Discharging)), seed = 0, notifications = 0,
                )
            }
        }
        advance(300)
        compose.onNodeWithText("100%").assertExists()
        val full = capture("tile_battery_full")
        compose.runOnUiThread {
            percent = 10
            Snapshot.sendApplyNotifications()
        }
        advance(300)
        compose.onNodeWithText("10%").assertExists()
        val low = capture("tile_battery_low")
        fun whiteInBar(b: android.graphics.Bitmap) = (300..460).sumOf { y -> (300..440).count { x -> b.getPixel(x, y) == 0xFFFFFFFF.toInt() } }
        val fullWhite = whiteInBar(full)
        val lowWhite = whiteInBar(low)
        assertTrue("the full bar is white inside ($fullWhite vs $lowWhite)", fullWhite > lowWhite + 800)
    }

    @Test
    fun theBatteryTileFlipsToHowItIsCharging() {
        val battery = BatteryInfo(40, BatteryState.Charging, minutesToFull = 95)
        showTile(batteryItem, LiveTileData(battery = battery))
        advance(300)
        compose.onAllNodesWithText("in carica").assertCountEquals(0)
        compose.onAllNodesWithText("charging").assertCountEquals(0)
        advance(4_500)
        capture("tile_battery_charging")
        compose.onNodeWithText("charging").assertExists()
        compose.onNodeWithText("1 h 35 min until full").assertExists()
    }

    @Test
    fun withoutAReadingTheBatteryTileKeepsItsGlyph() {
        showTile(batteryItem, LiveTileData(battery = null))
        advance(300)
        compose.onNodeWithText("Battery Saver").assertExists()
    }

    // --- weather

    @Test
    fun theWeatherTileShowsTheTemperatureAndTheWeather() {
        showTile(weatherItem, LiveTileData(weather = sunny))
        advance(300)
        capture("tile_weather")
        compose.onNodeWithText("21°").assertExists()
        compose.onNodeWithText("Clear").assertExists()
    }

    @Test
    fun theWeatherTileFlipsToTheNextDays() {
        showTile(weatherItem, LiveTileData(weather = sunny), TileSize.Wide)
        advance(300)
        compose.onAllNodesWithText("Mon").assertCountEquals(0)
        advance(4_500)
        capture("tile_weather_days")
        // The forecast starts tomorrow: Monday 26 October 2026, then Tuesday and Wednesday.
        compose.onNodeWithText("Mon").assertExists()
        compose.onNodeWithText("17° 9°").assertExists()
        compose.onNodeWithText("Tue").assertExists()
        compose.onNodeWithText("Wed").assertExists()
    }

    @Test
    fun aSmallWeatherTileShowsOnlyTheTemperature() {
        showTile(weatherItem, LiveTileData(weather = sunny), TileSize.Small)
        advance(5_000)
        compose.onNodeWithText("21°").assertExists()
        compose.onAllNodesWithText("Clear").assertCountEquals(0)
    }

    @Test
    fun withoutWeatherTheTileKeepsItsGlyphAndName() {
        showTile(weatherItem, LiveTileData(weather = null))
        advance(300)
        compose.onNodeWithText("Forecast").assertExists()
    }

    // --- the forecast page

    @Test
    fun theForecastPageShowsTheWeatherAndTheDays() {
        var refreshed = 0
        var chose = 0
        show { WeatherScreen(sunny, "Torino, Piemonte, Italia", onRefresh = { refreshed++ }, onChooseCity = { chose++ }) }
        advance(300)
        capture("weather_page")
        compose.onNodeWithTag("weather:place").assertExists()
        compose.onNodeWithTag("weather:temperature").assertExists()
        compose.onNodeWithTag("weather:day:0").assertExists()
        compose.onNodeWithTag("weather:day:3").assertExists()
        compose.onNodeWithTag("weather:refresh").performClick()
        assertEquals(1, refreshed)
        compose.onNodeWithTag("weather:choose").performClick()
        assertEquals(1, chose)
    }

    @Test
    fun withNoCityThePageAsksForOneAndHasNoRefresh() {
        show { WeatherScreen(null, "", onRefresh = {}, onChooseCity = {}) }
        advance(300)
        capture("weather_page_empty")
        compose.onAllNodesWithTag("weather:refresh").assertCountEquals(0)
        compose.onNodeWithTag("weather:choose").assertExists()
        compose.onNodeWithText("Choose a city to see the weather here and on the tile.").assertExists()
    }

    // --- choosing the city

    private var current = LauncherSettings()
    private var chosen: PlaceMatch? = null
    private var locationAsked = 0

    @Composable
    private fun LiveTilesSettings(search: suspend (String) -> List<PlaceMatch>?) {
        var settings by remember { mutableStateOf(current) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings,
                onChange = { change ->
                    settings = change(settings)
                    current = settings
                },
                searchPlaces = search,
                onChoosePlace = { chosen = it },
                onUseLocation = { locationAsked++ },
            ),
            section = SettingsSection.LiveTiles,
            onSectionChange = {},
            sectionAway = remember { Animatable(0f) },
        )
    }

    private val turin = PlaceMatch("Torino", "Piemonte", "Italia", 45.07, 7.68)
    private val turinCanada = PlaceMatch("Turin", "", "Canada", 43.0, -79.0)

    private fun typeAndSearch(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithTag("weather:search").performClick()
        advance(300)
    }

    @Test
    fun aSearchListsTheCitiesAndTouchingOneChoosesIt() {
        val asked = mutableListOf<String>()
        current = LauncherSettings()
        show { LiveTilesSettings { asked += it; listOf(turin, turinCanada) } }
        advance(500)
        typeAndSearch("Tor")
        assertEquals(listOf("Tor"), asked)
        capture("settings_live_tiles_results")
        compose.onNodeWithTag("weather:result:0").assertExists()
        compose.onNodeWithTag("weather:result:1").assertExists()
        compose.onNodeWithTag("weather:result:1").performClick()
        assertEquals(turinCanada, chosen)
        advance(300)
        compose.onAllNodesWithTag("weather:result:0").assertCountEquals(0)
    }

    @Test
    fun aSearchThatFindsNothingSaysSo() {
        show { LiveTilesSettings { emptyList() } }
        advance(500)
        typeAndSearch("Zzzz")
        compose.onNodeWithTag("weather:none").assertExists()
        compose.onAllNodesWithTag("weather:failed").assertCountEquals(0)
    }

    @Test
    fun aSearchThatCouldNotBeMadeSaysThereMayBeNoConnection() {
        show { LiveTilesSettings { null } }
        advance(500)
        typeAndSearch("Torino")
        compose.onNodeWithTag("weather:failed").assertExists()
        compose.onAllNodesWithTag("weather:none").assertCountEquals(0)
    }

    @Test
    fun anEmptySearchAsksNothing() {
        val asked = mutableListOf<String>()
        show { LiveTilesSettings { asked += it; emptyList() } }
        advance(500)
        compose.onNodeWithTag("weather:search").performClick()
        advance(300)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun theUnitsAreASwitch() {
        current = LauncherSettings()
        show { LiveTilesSettings { emptyList() } }
        advance(500)
        // The switch is below the search results on a short page: swipe the page up to it if needed.
        compose.onNodeWithTag("weather:fahrenheit").performClick()
        assertTrue(current.weatherFahrenheit)
        assertFalse(LauncherSettings().weatherFahrenheit)
        assertNull(chosen)
    }
}
