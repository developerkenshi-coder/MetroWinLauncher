package com.metrowin.launcher

import com.metrowin.launcher.data.HourForecast
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.WeatherBlock
import com.metrowin.launcher.data.WeatherCondition
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SixthRoundTest {
    // --- weather: the extra parts of the forecast

    private val forecast = """
        {"current":{"temperature_2m":18.4,"weather_code":2,"is_day":1,"apparent_temperature":17.0,"relative_humidity_2m":60,
          "wind_speed_10m":12.0,"wind_gusts_10m":31.0,"wind_direction_10m":315,"pressure_msl":1013.4,"cloud_cover":40,
          "dew_point_2m":9.6,"visibility":24140.0},
         "daily":{"time":["2026-10-28","2026-10-29"],"weather_code":[2,61],"temperature_2m_max":[20.1,17.0],"temperature_2m_min":[9.0,8.2],
           "precipitation_probability_max":[20,80],"precipitation_sum":[0.0,6.4],"sunshine_duration":[28800.0,3600.0],
           "wind_speed_10m_max":[18.0,35.0],"sunrise":["2026-10-28T07:12"],"sunset":["2026-10-28T17:30"],"uv_index_max":[3.2,1.0]},
         "hourly":{"time":["2026-10-28T14:00","2026-10-28T15:00","2026-10-28T16:00"],"temperature_2m":[18.0,17.5,17.0],
           "weather_code":[2,2,61],"precipitation_probability":[10,20,70],"precipitation":[0.0,0.0,1.2],"wind_speed_10m":[12.0,14.0,20.0]}}
    """.trimIndent()

    @Test
    fun theExtraPartsOfTheForecastAreRead() {
        val w = OpenMeteo.parseForecast(forecast, fahrenheit = false, now = 1L)!!
        val d = w.details
        assertEquals(31, d.gusts)
        assertEquals(315, d.windDirection)
        assertEquals(1013, d.pressure)
        assertEquals(40, d.cloudCover)
        assertEquals(10, d.dewPoint)
        assertEquals(24.14, d.visibilityKm!!, 0.001)
        assertEquals(0.0, d.rainMm!!, 0.001)
        assertEquals(8.0, d.sunshineHours!!, 0.001)
        assertEquals(10L, d.dayLength!!.toHours())
        assertEquals(6.4, w.days[1].rainMm!!, 0.001)
        assertEquals(35, w.days[1].windMax)
        assertEquals(1.2, w.hours[2].rainMm!!, 0.001)
        assertEquals(20, w.hours[2].wind)
    }

    @Test
    fun theExtraPartsSurviveTheCopyOnThePhone() {
        val w = OpenMeteo.parseForecast(forecast, fahrenheit = false, now = 1L)!!
        val back = OpenMeteo.decode(OpenMeteo.encode(w))!!
        assertEquals(w.details, back.details)
        assertEquals(w.days, back.days)
        assertEquals(w.hours, back.hours)
    }

    @Test
    fun aForecastWithoutTheExtrasStillReads() {
        val w = OpenMeteo.parseForecast("""{"current":{"temperature_2m":5.0,"weather_code":0}}""", false, 1L)!!
        assertNull(w.details.pressure)
        assertNull(w.details.visibilityKm)
        assertNull(w.details.dayLength)
    }

    @Test
    fun theAirAndThePollenAreRead() {
        val air = OpenMeteo.parseAir(
            """{"current":{"european_aqi":42,"pm10":18.4,"pm2_5":9.6,"ozone":88.0,"nitrogen_dioxide":12.0,
               "alder_pollen":0.0,"birch_pollen":120.0,"grass_pollen":null,"olive_pollen":3.0}}""",
        )!!
        assertEquals(42, air.aqi)
        assertEquals(10, air.pm25)
        assertEquals(listOf("birch" to 120, "olive" to 3), air.pollen)
        val w = OpenMeteo.parseForecast(forecast, false, 1L)!!.copy(air = air)
        assertEquals(air, OpenMeteo.decode(OpenMeteo.encode(w))!!.air)
    }

    @Test
    fun anAirAnswerWithNothingInItIsNoAir() {
        assertNull(OpenMeteo.parseAir("""{"current":{"european_aqi":null,"pm10":null}}"""))
        assertNull(OpenMeteo.parseAir("not json"))
    }

    @Test
    fun theAirAndPollenLevels() {
        assertEquals(0, OpenMeteo.aqiLevel(20))
        assertEquals(1, OpenMeteo.aqiLevel(21))
        assertEquals(4, OpenMeteo.aqiLevel(100))
        assertEquals(5, OpenMeteo.aqiLevel(180))
        assertEquals(0, OpenMeteo.pollenLevel(3))
        assertEquals(2, OpenMeteo.pollenLevel(120))
        assertEquals(3, OpenMeteo.pollenLevel(900))
    }

    @Test
    fun theWindDirectionIsOneOfEightPoints() {
        assertEquals(0, OpenMeteo.compassIndex(0))
        assertEquals(0, OpenMeteo.compassIndex(359))
        assertEquals(1, OpenMeteo.compassIndex(45))
        assertEquals(4, OpenMeteo.compassIndex(180))
        assertEquals(7, OpenMeteo.compassIndex(315))
        assertEquals(6, OpenMeteo.compassIndex(-90))
    }

    private fun hour(h: Int, condition: WeatherCondition = WeatherCondition.Cloudy, chance: Int? = 0, mm: Double? = 0.0) =
        HourForecast(LocalTime.of(h, 0), 15, condition, chance, mm)

    @Test
    fun dryNowAndRainLaterTellsWhen() {
        val hours = listOf(hour(10), hour(11), hour(12), hour(13, chance = 70), hour(14, chance = 90))
        val outlook = OpenMeteo.rainOutlook(WeatherCondition.Cloudy, hours)!!
        assertFalse(outlook.raining)
        assertEquals(3, outlook.hours)
    }

    @Test
    fun rainingNowTellsWhenItStops() {
        val wet = WeatherCondition.Rain
        val hours = listOf(hour(10, wet), hour(11, wet), hour(12, mm = 0.0))
        val outlook = OpenMeteo.rainOutlook(wet, hours)!!
        assertTrue(outlook.raining)
        assertEquals(2, outlook.hours)
    }

    @Test
    fun noRainAtAllIsSaidAsSuch() {
        val outlook = OpenMeteo.rainOutlook(WeatherCondition.Clear, listOf(hour(10), hour(11)))!!
        assertFalse(outlook.raining)
        assertNull(outlook.hours)
        assertNull(OpenMeteo.rainOutlook(WeatherCondition.Clear, emptyList()))
    }

    @Test
    fun aMillimetreOfRainCountsEvenWithALowChance() {
        val outlook = OpenMeteo.rainOutlook(WeatherCondition.Cloudy, listOf(hour(10), hour(11, chance = 10, mm = 0.8)))!!
        assertEquals(1, outlook.hours)
    }

    @Test
    fun theWeatherPartsAreKeptInTheSettingsAndUnknownOnesDropped() {
        val s = LauncherSettings(weatherBlocks = setOf("rain", "air"))
        val back = SettingsCodec.decode(SettingsCodec.encode(s))!!
        assertEquals(setOf("rain", "air"), back.weatherBlocks)
        // The air asks for a second answer, so it starts off; the rest starts on.
        assertFalse("air" in LauncherSettings().weatherBlocks)
        assertTrue(WeatherBlock.Rain.code in LauncherSettings().weatherBlocks)
        val odd = SettingsCodec.decode("""{"weatherBlocks":["chart","nonsense"]}""")!!
        assertEquals(setOf("chart"), odd.weatherBlocks)
        // An old file with no list gets the default.
        assertEquals(WeatherBlock.DEFAULT, SettingsCodec.decode("{}")!!.weatherBlocks)
        assertNotNull(OpenMeteo.airUrl(45.07, 7.69))
    }
}
