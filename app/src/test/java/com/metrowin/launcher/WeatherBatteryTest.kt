package com.metrowin.launcher

import android.os.BatteryManager
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.DayForecast
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeatherBatteryTest {
    // --- battery

    @Test
    fun theLevelIsAPercentageOfTheScale() {
        assertEquals(87, BatteryInfo.from(87, 100, BatteryManager.BATTERY_STATUS_DISCHARGING).percent)
        assertEquals(50, BatteryInfo.from(1500, 3000, BatteryManager.BATTERY_STATUS_DISCHARGING).percent)
        assertEquals("a scale of zero means nothing", 0, BatteryInfo.from(50, 0, BatteryManager.BATTERY_STATUS_DISCHARGING).percent)
        assertEquals(0, BatteryInfo.from(-1, 100, BatteryManager.BATTERY_STATUS_DISCHARGING).percent)
        assertEquals(100, BatteryInfo.from(120, 100, BatteryManager.BATTERY_STATUS_FULL).percent)
    }

    @Test
    fun theStateComesFromTheStatus() {
        assertEquals(BatteryState.Charging, BatteryInfo.from(50, 100, BatteryManager.BATTERY_STATUS_CHARGING).state)
        assertEquals(BatteryState.Full, BatteryInfo.from(100, 100, BatteryManager.BATTERY_STATUS_FULL).state)
        assertEquals(BatteryState.Discharging, BatteryInfo.from(50, 100, BatteryManager.BATTERY_STATUS_DISCHARGING).state)
        assertEquals("not charging counts as on battery", BatteryState.Discharging, BatteryInfo.from(50, 100, BatteryManager.BATTERY_STATUS_NOT_CHARGING).state)
        assertEquals(BatteryState.Discharging, BatteryInfo.from(50, 100, BatteryManager.BATTERY_STATUS_UNKNOWN).state)
    }

    @Test
    fun theTimeToFullIsOnlyGivenWhileChargingAndRoundsUp() {
        val charging = BatteryManager.BATTERY_STATUS_CHARGING
        assertEquals(35, BatteryInfo.from(50, 100, charging, chargeTimeMillis = 35 * 60_000L).minutesToFull)
        assertEquals(36, BatteryInfo.from(50, 100, charging, chargeTimeMillis = 35 * 60_000L + 1).minutesToFull)
        assertNull("the phone cannot say", BatteryInfo.from(50, 100, charging, chargeTimeMillis = -1).minutesToFull)
        assertNull("not while on battery", BatteryInfo.from(50, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 600_000L).minutesToFull)
    }

    @Test
    fun durationsAreShortAndReadTheSameInManyLanguages() {
        assertEquals("5 min", BatteryInfo.duration(5))
        assertEquals("59 min", BatteryInfo.duration(59))
        assertEquals("1 h 00 min", BatteryInfo.duration(60))
        assertEquals("2 h 05 min", BatteryInfo.duration(125))
        assertEquals("0 min", BatteryInfo.duration(-3))
    }

    // --- weather codes

    @Test
    fun weatherCodesMapOntoTheKindsOfWeather() {
        assertEquals(WeatherCondition.Clear, WeatherCondition.fromCode(0))
        assertEquals(WeatherCondition.PartlyCloudy, WeatherCondition.fromCode(1))
        assertEquals(WeatherCondition.PartlyCloudy, WeatherCondition.fromCode(2))
        assertEquals(WeatherCondition.Cloudy, WeatherCondition.fromCode(3))
        assertEquals(WeatherCondition.Fog, WeatherCondition.fromCode(45))
        assertEquals(WeatherCondition.Drizzle, WeatherCondition.fromCode(53))
        assertEquals(WeatherCondition.Rain, WeatherCondition.fromCode(63))
        assertEquals(WeatherCondition.Rain, WeatherCondition.fromCode(67))
        assertEquals(WeatherCondition.Snow, WeatherCondition.fromCode(73))
        assertEquals(WeatherCondition.Snow, WeatherCondition.fromCode(86))
        assertEquals(WeatherCondition.Showers, WeatherCondition.fromCode(81))
        assertEquals(WeatherCondition.Thunderstorm, WeatherCondition.fromCode(95))
        assertEquals(WeatherCondition.Thunderstorm, WeatherCondition.fromCode(99))
        assertEquals("a code nobody told us about is just cloud", WeatherCondition.Cloudy, WeatherCondition.fromCode(1234))
    }

    // --- the addresses

    @Test
    fun theForecastAddressHasADotInCoordinatesWhateverTheLanguage() {
        val default = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.ITALY) // a comma for decimals
            val url = OpenMeteo.forecastUrl(45.0703, 7.6869, fahrenheit = false)
            assertTrue(url, url.contains("latitude=45.0703&longitude=7.6869"))
            assertTrue(url.contains("temperature_unit=celsius"))
            assertTrue(url.contains("forecast_days=5"))
            assertTrue(OpenMeteo.forecastUrl(-33.87, 151.21, fahrenheit = true).contains("latitude=-33.8700&longitude=151.2100"))
            assertTrue(OpenMeteo.forecastUrl(0.0, 0.0, fahrenheit = true).contains("temperature_unit=fahrenheit"))
        } finally {
            java.util.Locale.setDefault(default)
        }
    }

    @Test
    fun thePlaceNameIsEncodedInTheSearchAddress() {
        val url = OpenMeteo.geocodingUrl("  Reggio Emilia ", "it")
        assertTrue(url, url.contains("name=Reggio+Emilia&"))
        assertTrue(OpenMeteo.geocodingUrl("São Paulo", "pt").contains("name=S%C3%A3o+Paulo&"))
        assertTrue(url.contains("language=it"))
    }

    // --- the answers

    private val forecast = """
        {"latitude":45.07,"longitude":7.69,
         "current":{"time":"2026-10-25T10:00","interval":900,"temperature_2m":12.6,"weather_code":3,"is_day":1},
         "daily":{"time":["2026-10-25","2026-10-26","2026-10-27"],
                  "weather_code":[3,61,0],
                  "temperature_2m_max":[14.2,12.0,16.5],
                  "temperature_2m_min":[7.4,8.1,6.0]}}
    """.trimIndent()

    @Test
    fun aForecastIsReadWithItsCurrentWeatherAndItsDays() {
        val data = OpenMeteo.parseForecast(forecast, fahrenheit = false, now = 1_000L)!!
        assertEquals(13, data.temperature)
        assertEquals(WeatherCondition.Cloudy, data.condition)
        assertTrue(data.isDay)
        assertEquals(1_000L, data.fetchedAt)
        assertEquals(3, data.days.size)
        assertEquals(DayForecast(LocalDate.of(2026, 10, 26), WeatherCondition.Rain, high = 12, low = 8), data.days[1])
        assertEquals(17, data.days[2].high)
    }

    @Test
    fun aForecastWithoutTheCurrentWeatherIsRefused() {
        assertNull(OpenMeteo.parseForecast("""{"daily":{}}""", false, 0L))
        assertNull(OpenMeteo.parseForecast("not json", false, 0L))
        assertNull(OpenMeteo.parseForecast("""{"error":true,"reason":"Latitude must be in range"}""", false, 0L))
    }

    @Test
    fun aDayWithoutTemperaturesIsSkippedAndTheRestStays() {
        val json = """{"current":{"temperature_2m":5.0,"weather_code":0},
            "daily":{"time":["2026-10-25","2026-10-26"],"weather_code":[0,0],
                     "temperature_2m_max":[10.0,null],"temperature_2m_min":[2.0,null]}}"""
        val data = OpenMeteo.parseForecast(json, false, 0L)!!
        assertEquals(1, data.days.size)
        assertEquals(10, data.days.single().high)
    }

    @Test
    fun noDailyDataStillGivesTheCurrentWeather() {
        val data = OpenMeteo.parseForecast("""{"current":{"temperature_2m":-3.6,"weather_code":71,"is_day":0}}""", false, 0L)!!
        assertEquals(-4, data.temperature)
        assertEquals(WeatherCondition.Snow, data.condition)
        assertFalse(data.isDay)
        assertTrue(data.days.isEmpty())
    }

    @Test
    fun placesAreReadBestFirstAndIncompleteOnesAreSkipped() {
        val json = """{"results":[
            {"name":"Torino","latitude":45.07,"longitude":7.68,"country":"Italia","admin1":"Piemonte"},
            {"name":"Nowhere"},
            {"name":"Turin","latitude":43.0,"longitude":-79.0,"country":"Canada"}]}"""
        val places = OpenMeteo.parsePlaces(json)
        assertEquals(2, places.size)
        assertEquals("Torino, Piemonte, Italia", places[0].displayName)
        assertEquals(PlaceMatch("Turin", "", "Canada", 43.0, -79.0), places[1])
        assertEquals("Turin, Canada", places[1].displayName)
    }

    @Test
    fun anAnswerWithNoResultsIsAnEmptyList() {
        assertTrue(OpenMeteo.parsePlaces("""{"generationtime_ms":0.5}""").isEmpty())
        assertTrue(OpenMeteo.parsePlaces("garbage").isEmpty())
    }

    // --- age and the copy kept on the phone

    private val sample = WeatherData(
        temperature = 12, condition = WeatherCondition.Rain, isDay = false, fahrenheit = true, fetchedAt = 5_000L,
        days = listOf(DayForecast(LocalDate.of(2026, 10, 26), WeatherCondition.Clear, 15, 7)),
    )

    @Test
    fun weatherGetsOldAfterHalfAnHour() {
        assertFalse(OpenMeteo.isStale(sample, now = 5_000L + 29 * 60_000))
        assertTrue(OpenMeteo.isStale(sample, now = 5_000L + 31 * 60_000))
        assertTrue("never fetched", OpenMeteo.isStale(null, now = 0L))
        assertTrue("the clock went back: do not trust it", OpenMeteo.isStale(sample, now = 1_000L))
    }

    @Test
    fun theCopyOnThePhoneIsTheSameWeather() {
        assertEquals(sample, OpenMeteo.decode(OpenMeteo.encode(sample)))
        assertNull(OpenMeteo.decode("broken"))
        assertNotNull(OpenMeteo.decode(OpenMeteo.encode(sample.copy(days = emptyList()))))
    }
}
