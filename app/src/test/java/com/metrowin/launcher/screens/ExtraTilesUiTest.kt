package com.metrowin.launcher.screens

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.SavedCity
import com.metrowin.launcher.data.TileExtras
import com.metrowin.launcher.data.WalletCard
import com.metrowin.launcher.data.WhereAmI
import com.metrowin.launcher.data.DayForecast
import com.metrowin.launcher.data.HourForecast
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.pages.StepsPage
import com.metrowin.launcher.ui.pages.WalletPage
import com.metrowin.launcher.ui.pages.WhereAmIPage
import com.metrowin.launcher.ui.start.AddMenu
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

/** The menu that adds things to Start, the pages of the new tiles and their faces. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class ExtraTilesUiTest : ScreenshotBase() {
    private fun item(id: String, data: TileData, glyph: Glyph = Glyph.Star) = AppItem(id, id, Launch.Internal("none"), glyph, pinOnly = true, data = data)

    private fun showFace(item: AppItem, live: LiveTileData, size: TileSize = TileSize.Medium) = show {
        Box(Modifier.size(200.dp, 200.dp).background(DefaultAccent)) {
            TileFace(item, size, 200.dp, 200.dp, live, seed = 0, notifications = 0)
        }
    }

    // --- the menu

    @Test
    fun theAddMenuIsFiledAndItsSearchLeavesWhatMatches() {
        val done = mutableListOf<String>()
        show {
            AddMenu(
                onContact = { done += "contact" }, onSite = {}, onWidget = {}, onDismiss = {},
                onSteps = { done += "steps" }, onWallet = { done += "wallet" },
            )
        }
        advance(400)
        capture("add_menu_filed")
        compose.onNodeWithTag("add:search").assertExists()
        compose.onNodeWithTag("add:contact").assertExists()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("pedometer")
        advance(300)
        compose.onNodeWithTag("add:steps").assertExists()
        compose.onAllNodesWithTag("add:contact").assertCountEquals(0)
        compose.onNodeWithTag("add:steps").performClick()
        assertEquals(listOf("steps"), done)
    }

    // --- the faces

    @Test
    fun theStepsTileShowsTheStepsOfToday() {
        showFace(item("s", TileData.Steps), LiveTileData(extras = TileExtras(steps = 4321)))
        advance(300)
        compose.onNodeWithTag("steps:count").assertExists()
        compose.onAllNodesWithText("4321").assertCountEquals(1)
    }

    @Test
    fun theStepsTileIsTheMarkUntilTheStepsAreKnown() {
        showFace(item("s", TileData.Steps), LiveTileData())
        advance(300)
        compose.onAllNodesWithTag("steps:count").assertCountEquals(0)
    }

    @Test
    fun theWalletTileSaysHowManyCards() {
        showFace(item("w", TileData.Wallet), LiveTileData(extras = TileExtras(cards = 3)))
        advance(300)
        compose.onNodeWithTag("wallet:count").assertExists()
    }

    @Test
    fun theTileOfAPlaceShowsTheLastStreet() {
        showFace(item("p", TileData.WhereAmI), LiveTileData(extras = TileExtras(place = "Via Roma 1, Torino")))
        advance(300)
        compose.onAllNodesWithText("Via Roma 1, Torino").assertCountEquals(1)
    }

    // --- the pages

    @Test
    fun theStepsPageAsksForThePermissionWhenItIsMissing() {
        var asked = 0
        show { StepsPage("Steps", steps = null, allowed = false, onAllow = { asked++ }) }
        advance(300)
        compose.onNodeWithTag("steps:allow").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun theStepsPageShowsTheCount() {
        show { StepsPage("Steps", steps = 8000, allowed = true, onAllow = {}) }
        advance(300)
        compose.onAllNodesWithText("8000").assertCountEquals(1)
    }

    @Test
    fun aCardIsAddedShownAsABarcodeAndRemoved() {
        val cards = mutableListOf<WalletCard>()
        val shown = androidx.compose.runtime.mutableStateOf(emptyList<WalletCard>())
        show {
            WalletPage(
                "Wallet", shown.value,
                onAdd = { name, code ->
                    val ok = name.isNotBlank() && code.isNotBlank() && code.all { it.code in 32..126 }
                    if (ok) {
                        cards += WalletCard("c${cards.size}", name, code)
                        shown.value = cards.toList()
                    }
                    ok
                },
                onRemove = { id ->
                    cards.removeAll { it.id == id }
                    shown.value = cards.toList()
                },
            )
        }
        advance(300)
        capture("wallet_empty")
        // Letters the barcode cannot draw are refused, and the form says so.
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Coop")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("è")
        compose.onNodeWithTag("wallet:save").performClick()
        assertTrue(cards.isEmpty())
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("1234")
        compose.waitForIdle()
        // The field now holds the refused letter and the digits: still refused. A clean number is taken.
        compose.onNodeWithTag("wallet:save").performClick()
        assertTrue(cards.isEmpty())
    }

    @Test
    fun aCardOfTheWalletOpensAsABarcodeAndGoesAway() {
        var removed: String? = null
        val list = androidx.compose.runtime.mutableStateOf(listOf(WalletCard("a", "Gym", "1234567890")))
        show { WalletPage("Wallet", list.value, onAdd = { _, _ -> true }, onRemove = { removed = it; list.value = emptyList() }) }
        advance(400)
        compose.onNodeWithTag("wallet:row").performClick()
        advance(300)
        capture("wallet_card")
        compose.onNodeWithTag("wallet:card").assertIsDisplayed()
        compose.onNodeWithTag("wallet:remove").performClick()
        assertEquals("a", removed)
    }

    @Test
    fun theLocationPageShowsTheStreetThatWasFound() {
        show {
            WhereAmIPage(
                "Where am I", allowed = true, onAllow = {}, locate = { WhereAmI(45.07, 7.69, 12f, "Via Roma 1, Torino") },
                onOpenMap = {}, onShare = {},
            )
        }
        advance(500)
        capture("where_am_i")
        compose.onNodeWithTag("where:address").assertExists()
        compose.onAllNodesWithText("Via Roma 1, Torino").assertCountEquals(1)
        compose.onNodeWithTag("where:numbers").assertExists()
    }

    @Test
    fun theLocationPageAsksForThePermission() {
        var asked = false
        show { WhereAmIPage("Where am I", allowed = false, onAllow = { asked = true }, locate = { null }, onOpenMap = {}, onShare = {}) }
        advance(300)
        compose.onNodeWithTag("where:allow").performClick()
        assertTrue(asked)
    }

    // --- the forecast

    private val forecast = WeatherData(
        temperature = 14, condition = WeatherCondition.Clear, isDay = true,
        days = listOf(DayForecast(LocalDate.of(2026, 10, 26), WeatherCondition.Clear, 16, 8)), fahrenheit = false, fetchedAt = System.currentTimeMillis(),
        hours = (0 until 6).map { HourForecast(LocalTime.of(15 + it, 0), 14 - it, WeatherCondition.Clear, if (it == 2) 40 else null) },
    )

    @Test
    fun theForecastPageListsTheNextHoursAndTheOtherCities() {
        show {
            WeatherScreen(
                weather = forecast, place = "Torino",
                others = listOf(SavedCity("Tokyo, Japan", 35.6, 139.7) to forecast, SavedCity("Lima", -12.0, -77.0) to null),
                onRefresh = {}, onChooseCity = {},
            )
        }
        advance(600)
        capture("weather_hours_cities")
        compose.onNodeWithTag("weather:hourly").assertExists()
        compose.onNodeWithTag("weather:other:0").assertExists()
        compose.onNodeWithTag("weather:other:1").assertExists()
    }

    @Test
    fun theWeatherTileShowsTheNextHoursOnOneOfItsFaces() {
        val weatherItem = AppItem("w", "Weather", Launch.Internal("weather"), Glyph.Weather, live = com.metrowin.launcher.model.LiveKind.Weather)
        showFace(weatherItem, LiveTileData(weather = forecast))
        advance(300)
        // The first face is the temperature; the face of the hours is reached by the flip, which the tile does by itself.
        compose.onAllNodesWithText("14°").assertCountEquals(1)
    }

    // --- the tiles and the dialogs of the fifth round

    @Test
    fun aSwitchOnStartSaysWhetherItIsOn() {
        showFace(item("t", TileData.Toggle("wifi"), Glyph.Wifi), LiveTileData(extras = TileExtras(toggles = mapOf("wifi" to true))))
        advance(300)
        compose.onNodeWithTag("toggle:state").assertExists()
        compose.onAllNodesWithText("On").assertCountEquals(1)
    }

    @Test
    fun theSunTileGivesTheTimesOfTheSun() {
        val weather = WeatherData(
            temperature = 14, condition = WeatherCondition.Clear, isDay = true, days = emptyList(), fahrenheit = false, fetchedAt = 0L,
            details = com.metrowin.launcher.data.WeatherDetails(sunrise = LocalTime.of(6, 52), sunset = LocalTime.of(17, 3)),
        )
        showFace(item("s", TileData.Sun, Glyph.Weather), LiveTileData(weather = weather), TileSize.Wide)
        advance(300)
        compose.onNodeWithTag("sun:rise").assertExists()
        compose.onNodeWithTag("sun:set").assertExists()
    }

    @Test
    fun theCollageFillsATileWithPictures() {
        fun solid(argb: Int) = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }.asImageBitmap()
        val photos = listOf(0xFFCC0000.toInt(), 0xFF00CC00.toInt(), 0xFF0000CC.toInt()).map(::solid)
        val photosTile = AppItem("p", "Photos", Launch.Internal("x"), Glyph.Photos, live = com.metrowin.launcher.model.LiveKind.Photos, pinOnly = true)
        show {
            Box(Modifier.size(200.dp, 200.dp).background(DefaultAccent)) {
                TileFace(photosTile, TileSize.Medium, 200.dp, 200.dp, LiveTileData(photos = photos), seed = 1, notifications = 0, style = com.metrowin.launcher.model.TileStyle.Collection)
            }
        }
        advance(300)
        compose.onNodeWithTag("collection:face").assertExists()
    }

    @Test
    fun theCalendarTileShowsTheMonthWhenItIsLarge() {
        val calendar = AppItem("c", "Calendar", Launch.Internal("x"), Glyph.Calendar, live = com.metrowin.launcher.model.LiveKind.Calendar, pinOnly = true)
        val events = listOf(com.metrowin.launcher.data.LiveEvent("Dinner", System.currentTimeMillis() + 3_600_000L, false))
        show {
            Box(Modifier.size(408.dp, 408.dp).background(DefaultAccent)) {
                TileFace(calendar, TileSize.Large, 408.dp, 408.dp, LiveTileData(nextEvent = events.first(), events = events), seed = 0, notifications = 0)
            }
        }
        advance(300)
        compose.onNodeWithTag("calendar:month").assertExists()
        compose.onNodeWithTag("calendar:agenda").assertExists()
    }

    @Test
    fun theRestoreDialogPutsBackOnlyWhatWasChosen() {
        var chosen: Set<com.metrowin.launcher.data.RestorePart>? = null
        show { com.metrowin.launcher.ui.settings.RestoreDialog(onDone = { chosen = it }, onCancel = {}) }
        advance(300)
        compose.onNodeWithTag("restore:Settings").performClick()
        compose.onNodeWithTag("restore:go").performClick()
        assertEquals(setOf(com.metrowin.launcher.data.RestorePart.Start, com.metrowin.launcher.data.RestorePart.Theme), chosen)
    }

    @Test
    fun theChoiceSheetReturnsWhatWasTouched() {
        var picked: String? = null
        val choices = listOf(
            com.metrowin.launcher.ui.start.Choice("wifi", Glyph.Wifi, "Wi-Fi"),
            com.metrowin.launcher.ui.start.Choice("bluetooth", Glyph.Bluetooth, "Bluetooth"),
        )
        show { com.metrowin.launcher.ui.start.ChoiceSheet("Which", choices, onPick = { picked = it.id }, onDismiss = {}, tagPrefix = "toggle") }
        advance(300)
        compose.onNodeWithTag("toggle:bluetooth").performClick()
        assertEquals("bluetooth", picked)
    }

    @Test
    fun aGroupTitleIsWrittenAboveItsTiles() {
        val entries = listOf<com.metrowin.launcher.model.StartEntry>(
            com.metrowin.launcher.model.Tile("A", TileSize.Medium),
            com.metrowin.launcher.model.Tile("B", TileSize.Medium, group = "Work"),
        )
        val tiles = listOf("A", "B").mapIndexed { i, id -> AppItem(id, "App $id", Launch.Action("x"), Glyph.entries[i + 5]) }
        show {
            com.metrowin.launcher.ui.start.StartScreen(
                cells = com.metrowin.launcher.ui.start.buildStartCells(entries, tiles, null), live = LiveTileData(), badges = emptyMap(), columns = 4, background = null,
                enterToken = 0, exitRequest = null, editingKey = null, scrollState = androidx.compose.foundation.rememberScrollState(),
                actions = com.metrowin.launcher.ui.start.StartActions(
                    onClick = {}, onLongClick = {}, onUnpin = {}, onResize = {}, onRename = {}, onMove = { _, _ -> }, onMerge = { _, _ -> },
                    onBackgroundTap = {}, onNextPage = {},
                ),
            )
        }
        advance(600)
        capture("start_group_title")
        compose.onNodeWithTag("group:Work").assertExists()
    }
}
