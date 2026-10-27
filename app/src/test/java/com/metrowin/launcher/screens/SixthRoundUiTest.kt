package com.metrowin.launcher.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.AirQuality
import com.metrowin.launcher.data.DayForecast
import com.metrowin.launcher.data.HourForecast
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.ParkedCar
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.SectionReset
import com.metrowin.launcher.data.TileExtras
import com.metrowin.launcher.data.WeatherBlock
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.data.WeatherDetails
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.actioncenter.ActionControls
import com.metrowin.launcher.ui.gesture.rememberPullPanel
import com.metrowin.launcher.ui.pages.ParkingPage
import com.metrowin.launcher.ui.pages.StepsPage
import com.metrowin.launcher.ui.settings.ActionsPage
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.start.TileFace
import com.metrowin.launcher.ui.theme.DefaultAccent
import com.metrowin.launcher.ui.weather.WeatherScreen
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pages and faces of the sixth round: the weather with its optional parts, steps, the car, the clock, presets and the order of the buttons. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class SixthRoundUiTest : ScreenshotBase() {
    private val details = WeatherDetails(
        feelsLike = 10, humidity = 71, wind = 14, rainChance = 30, sunrise = LocalTime.of(7, 12), sunset = LocalTime.of(17, 48), uvIndex = 3,
        gusts = 31, windDirection = 315, pressure = 1013, cloudCover = 40, dewPoint = 9, visibilityKm = 24.1, rainMm = 2.4, sunshineHours = 5.5,
    )

    private fun hours(wetFrom: Int?) = List(8) { i ->
        val wet = wetFrom != null && i >= wetFrom
        HourForecast(
            LocalTime.of(10 + i, 0), 13 - i / 2, if (wet) WeatherCondition.Rain else WeatherCondition.Cloudy, if (wet) 80 else 10, if (wet) 1.2 else 0.0, 12,
        )
    }

    private fun weather(wetFrom: Int? = 3, air: AirQuality? = null) = WeatherData(
        temperature = 13, condition = WeatherCondition.Cloudy, isDay = true,
        days = listOf(
            DayForecast(LocalDate.of(2026, 10, 28), WeatherCondition.Cloudy, 14, 7, rainChance = 30, rainMm = 0.4, windMax = 18),
            DayForecast(LocalDate.of(2026, 10, 29), WeatherCondition.Rain, 12, 8, rainChance = 85, rainMm = 6.4, windMax = 35),
        ),
        fahrenheit = false, fetchedAt = 0L, details = details, hours = hours(wetFrom), air = air,
    )

    private val air = AirQuality(aqi = 42, pm25 = 9, pm10 = 18, ozone = 88, no2 = 12, pollen = listOf("birch" to 120, "olive" to 3))

    // --- the weather page

    @Test
    fun theForecastPageShowsEveryPartThatIsSwitchedOn() {
        show { WeatherScreen(weather(air = air), "Torino", blocks = WeatherBlock.CODES, onRefresh = {}, onChooseCity = {}) }
        advance(500)
        capture("weather_all_blocks")
        compose.onNodeWithTag("weather:rain").assertExists()
        compose.onNodeWithTag("weather:chart").assertExists()
        compose.onNodeWithTag("weather:hourly").assertExists()
        compose.onNodeWithTag("weather:air").assertExists()
        compose.onNodeWithTag("weather:pollen").assertExists()
        compose.onAllNodesWithText("1013 hPa").assertCountEquals(1)
        compose.onAllNodesWithText("NW · 315°").assertCountEquals(1)
        compose.onAllNodesWithText("31 km/h").assertCountEquals(1)
        compose.onAllNodesWithText("24.1 km").assertCountEquals(1)
        compose.onAllNodesWithText("2.4 mm").assertCountEquals(1)
        compose.onAllNodesWithText("10 h 36 min").assertCountEquals(1)
        compose.onAllNodesWithText("Rain expected in 3 h").assertCountEquals(1)
    }

    @Test
    fun theForecastPageHidesWhatIsSwitchedOff() {
        show { WeatherScreen(weather(air = air), "Torino", blocks = emptySet(), onRefresh = {}, onChooseCity = {}) }
        advance(500)
        listOf("weather:rain", "weather:chart", "weather:hourly", "weather:air", "weather:pollen", "weather:details").forEach {
            compose.onAllNodesWithTag(it).assertCountEquals(0)
        }
        // The days stay: they are the page.
        compose.onNodeWithTag("weather:day:0").assertExists()
    }

    @Test
    fun theAirAndThePollenAskForTheirOwnSwitches() {
        show { WeatherScreen(weather(air = air), "Torino", blocks = setOf(WeatherBlock.Air.code), onRefresh = {}, onChooseCity = {}) }
        advance(500)
        compose.onNodeWithTag("weather:air").assertExists()
        compose.onAllNodesWithTag("weather:pollen").assertCountEquals(0)
        compose.onAllNodesWithText("Good").assertCountEquals(0)
        compose.onAllNodesWithText("Moderate").assertCountEquals(1)
    }

    @Test
    fun theDaysCanTellTheRainAndTheWind() {
        show { WeatherScreen(weather(), "Torino", blocks = setOf(WeatherBlock.DailyExtras.code), onRefresh = {}, onChooseCity = {}) }
        advance(500)
        compose.onAllNodesWithText("Rain · rain 85% · 6.4 mm · 35 km/h").assertCountEquals(1)
    }

    @Test
    fun withNoRainComingThePageSaysSoAndTheTileHasNoRainFace() {
        show { WeatherScreen(weather(wetFrom = null), "Torino", blocks = setOf(WeatherBlock.Rain.code), onRefresh = {}, onChooseCity = {}) }
        advance(500)
        compose.onAllNodesWithText("No rain in the next 24 hours").assertCountEquals(1)
    }

    private val weatherItem = AppItem("w", "Weather", Launch.Internal("weather"), Glyph.Weather, live = LiveKind.Weather)

    private fun showTile(item: AppItem, live: LiveTileData, size: TileSize = TileSize.Wide, blocks: Set<String> = WeatherBlock.DEFAULT) = show {
        androidx.compose.runtime.CompositionLocalProvider(
            com.metrowin.launcher.ui.start.LocalTileFeatures provides com.metrowin.launcher.ui.start.TileFeatures(weatherBlocks = blocks),
        ) {
            Box(Modifier.size(300.dp, 150.dp).background(DefaultAccent)) { TileFace(item, size, 300.dp, 150.dp, live, seed = 0, notifications = 0) }
        }
    }

    @Test
    fun theWeatherTileTurnsToTheRainWhenItIsComing() {
        showTile(weatherItem, LiveTileData(weather = weather()))
        advance(3_000)
        compose.onNodeWithTag("weather:rain-face").assertExists()
    }

    @Test
    fun theRainFaceIsNotThereWhenItIsSwitchedOff() {
        showTile(weatherItem, LiveTileData(weather = weather()), blocks = WeatherBlock.DEFAULT - WeatherBlock.Rain.code)
        advance(20_000)
        compose.onAllNodesWithTag("weather:rain-face").assertCountEquals(0)
    }

    @Test
    fun theRainFaceIsNotThereWhenNoRainComes() {
        showTile(weatherItem, LiveTileData(weather = weather(wetFrom = null)))
        advance(20_000)
        compose.onAllNodesWithTag("weather:rain-face").assertCountEquals(0)
    }

    @Test
    fun theAirFaceNeedsItsSwitchAndTheAirIndex() {
        showTile(weatherItem, LiveTileData(weather = weather(wetFrom = null, air = air)), blocks = WeatherBlock.DEFAULT + WeatherBlock.Air.code)
        advance(20_000)
        compose.onAllNodesWithTag("weather:air-face").assertCountEquals(1)
    }

    // --- steps

    @Test
    fun theStepsPageShowsTheGoalTheStreakTheWeekAndTheAverage() {
        val today = LocalDate.of(2026, 10, 28)
        val day = today.toEpochDay()
        val history = mapOf(day - 3 to 6000, day - 2 to 9000, day - 1 to 10_000)
        show { StepsPage("Steps", steps = 8200, allowed = true, onAllow = {}, goal = 8000, history = history, today = today) }
        advance(300)
        capture("steps_week")
        // Yesterday, the day before and today are at the goal.
        compose.onNodeWithTag("steps:streak").assertExists()
        compose.onAllNodesWithText("3 days in a row at the goal").assertCountEquals(1)
        compose.onNodeWithTag("steps:week").assertExists()
        // (6000 + 9000 + 10000 + 8200) / 4 and the best day.
        compose.onAllNodesWithText("Average 8300 · best day 10000").assertCountEquals(1)
    }

    @Test
    fun withNoGoalThereIsNoStreak() {
        show { StepsPage("Steps", steps = 8200, allowed = true, onAllow = {}, goal = 0, history = mapOf(1L to 9000)) }
        advance(300)
        compose.onAllNodesWithTag("steps:streak").assertCountEquals(0)
        compose.onAllNodesWithTag("steps:week").assertCountEquals(1)
    }

    @Test
    fun theStepsTileDrawsTheGoalBarOnlyWithAGoal() {
        val item = AppItem("s", "Steps", Launch.Internal("none"), Glyph.Sports, pinOnly = true, data = TileData.Steps)
        fun tile(goal: Int) = show {
            androidx.compose.runtime.CompositionLocalProvider(
                com.metrowin.launcher.ui.start.LocalTileFeatures provides com.metrowin.launcher.ui.start.TileFeatures(stepGoal = goal),
            ) {
                Box(Modifier.size(200.dp, 200.dp).background(DefaultAccent)) {
                    TileFace(item, TileSize.Medium, 200.dp, 200.dp, LiveTileData(extras = TileExtras(steps = 4000)), seed = 0, notifications = 0)
                }
            }
        }
        tile(8000)
        advance(300)
        compose.onNodeWithTag("steps:goal").assertExists()
    }

    // --- the car and the clock

    @Test
    fun theParkingPageShowsTheCarAndForgetsIt() {
        var forgotten = 0
        var walked: ParkedCar? = null
        val car = ParkedCar(45.07, 7.68, "Via Roma 1, Torino", System.currentTimeMillis() - 3_600_000L)
        show { ParkingPage("My car", car, allowed = true, onAllow = {}, park = { null }, onWalk = { walked = it }, onForget = { forgotten++ }) }
        advance(300)
        compose.onAllNodesWithText("Via Roma 1, Torino").assertCountEquals(1)
        compose.onNodeWithTag("parking:walk").performClick()
        assertEquals(car, walked)
        compose.onNodeWithTag("parking:forget").performClick()
        assertEquals(1, forgotten)
    }

    @Test
    fun withNoCarThePageOffersToSaveThePlace() {
        var parked = 0
        show {
            ParkingPage("My car", null, allowed = true, onAllow = {}, park = { parked++; ParkedCar(1.0, 2.0, null, 0L) }, onWalk = {}, onForget = {})
        }
        advance(300)
        compose.onNodeWithTag("parking:none").assertExists()
        compose.onNodeWithTag("parking:here").performClick()
        compose.waitForIdle()
        assertEquals(1, parked)
    }

    @Test
    fun theParkingPageAsksForTheLocationFirst() {
        var asked = 0
        show { ParkingPage("My car", null, allowed = false, onAllow = { asked++ }, park = { null }, onWalk = {}, onForget = {}) }
        advance(300)
        compose.onNodeWithTag("parking:allow").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun theParkingTileShowsWhereAndTheClockHasItsFace() {
        val parking = AppItem("p", "My car", Launch.Internal("none"), Glyph.Maps, pinOnly = true, data = TileData.Parking)
        show {
            Box(Modifier.size(300.dp, 150.dp).background(DefaultAccent)) {
                TileFace(
                    parking, TileSize.Wide, 300.dp, 150.dp,
                    LiveTileData(extras = TileExtras(parked = ParkedCar(1.0, 2.0, "Via Roma 1, Torino", System.currentTimeMillis()))), seed = 0, notifications = 0,
                )
            }
        }
        advance(300)
        compose.onNodeWithTag("parking:line").assertExists()
    }

    @Test
    fun theAnalogClockIsDrawnOnTheTile() {
        val clock = AppItem("c", "Clock", Launch.Internal("none"), Glyph.Alarm, pinOnly = true, data = TileData.AnalogClock)
        show {
            Box(Modifier.size(200.dp, 200.dp).background(DefaultAccent)) { TileFace(clock, TileSize.Medium, 200.dp, 200.dp, LiveTileData(), seed = 0, notifications = 0) }
        }
        advance(300)
        capture("analog_clock")
        compose.onNodeWithTag("analogclock:face").assertExists()
    }

    // --- the Action Center: the presets

    @androidx.compose.runtime.Composable
    private fun Center(profiles: List<String>, onProfile: (Int) -> Unit) {
        ActionCenter(
            slots = listOf(QuickAction.Wifi, QuickAction.Bluetooth, QuickAction.QuietHours, QuickAction.Flashlight),
            states = emptyMap(), notifications = emptyList(), appsByPackage = emptyMap(), hasNotificationAccess = true,
            panel = rememberPullPanel(initialProgress = 1f),
            onAction = {}, onAllSettings = {}, onOpenNotification = {}, onDismissNotification = {},
            onClearAll = {}, onTurnOnNotificationAccess = {}, onClose = {},
            controls = ActionControls(
                volume = 0.4f, ringer = null, brightness = null, canSetBrightness = true,
                onVolume = {}, onRinger = {}, onBrightness = {}, onAllowBrightness = {}, profiles = profiles, onProfile = onProfile,
            ),
        )
    }

    @Test
    fun theVolumePresetsAreButtonsInTheActionCenter() {
        val chosen = mutableListOf<Int>()
        show { Center(listOf("Silent", "Loud")) { chosen += it } }
        advance(500)
        capture("action_center_presets")
        compose.onNodeWithTag("ac:profiles").assertExists()
        compose.onNodeWithTag("ac:profile:1").performClick()
        assertEquals(listOf(1), chosen)
    }

    @Test
    fun withNoPresetsThereIsNoRow() {
        show { Center(emptyList()) {} }
        advance(500)
        compose.onAllNodesWithTag("ac:profiles").assertCountEquals(0)
    }

    // --- the Action Center settings: the order of the buttons and putting a page back

    @Test
    fun aButtonHeldAndDraggedChangesPlaceInTheSettings() {
        var settings = LauncherSettings(quickActions = listOf("wifi", "bluetooth", "flashlight", "rotation"))
        show { ActionsPage(SettingsEnv(settings = settings, onChange = { change -> settings = change(settings) })) }
        advance(300)
        compose.onAllNodesWithTag("quick:wifi")[0].performTouchInput {
            down(center)
            advanceEventTime(700)
            // Past three buttons to the right: the last place.
            moveBy(androidx.compose.ui.geometry.Offset(width * 3.2f, 0f))
            advanceEventTime(100)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("bluetooth", "flashlight", "rotation", "wifi"), settings.quickActions)
    }

    @Test
    fun theVolumePresetsAreSavedFromTheSettings() {
        val saved = mutableListOf<String>()
        show {
            ActionsPage(
                SettingsEnv(
                    settings = LauncherSettings(volumeProfiles = listOf(com.metrowin.launcher.data.VolumeProfile("Night", 0, 20, 60))), onChange = {},
                    onSaveVolumeProfile = { saved += it; true },
                ),
            )
        }
        advance(300)
        scrollIntoView("profiles:name")
        assertEquals(1, compose.onAllNodesWithTag("profiles:row:0").fetchSemanticsNodes().size)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun aChangedPageOffersToBePutBackWithTwoTouches() {
        var settings = LauncherSettings(lockStart = true)
        assertTrue(SectionReset.changed(settings, "Start"))
        val state = mutableStateOf(settings)
        show {
            val away = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
            com.metrowin.launcher.ui.settings.SettingsScreen(
                env = SettingsEnv(settings = state.value, onChange = { change -> settings = change(state.value); state.value = settings }),
                section = com.metrowin.launcher.ui.settings.SettingsSection.Start, onSectionChange = {}, sectionAway = away,
            )
        }
        advance(600)
        // The line is the last one of the page.
        tapScrolled("settings:resetpage")
        advance(100)
        compose.onAllNodesWithText("Touch again to put it back").assertCountEquals(1)
        tapScrolled("settings:resetpage")
        compose.waitForIdle()
        assertEquals(false, settings.lockStart)
        advance(100)
        compose.onAllNodesWithTag("settings:resetpage").assertCountEquals(0)
    }
}
