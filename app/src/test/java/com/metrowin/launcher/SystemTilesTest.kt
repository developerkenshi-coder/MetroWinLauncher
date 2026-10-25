package com.metrowin.launcher

import com.metrowin.launcher.data.DayForecast
import com.metrowin.launcher.data.LiveSources
import com.metrowin.launcher.data.NextAlarm
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.data.WeatherCondition
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.data.WeatherDetails
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** The space and the alarm on their tiles, and the details of the weather. */
class SystemTilesTest {
    private val us = Locale.US
    private val italy = Locale.ITALY

    // --- storage

    @Test
    fun sizesAreWrittenAsAndroidsSettingsWriteThem() {
        assertEquals("512 kB", StorageInfo.format(512_000, us))
        assertEquals("512 MB", StorageInfo.format(512_000_000, us))
        assertEquals("7.4 GB", StorageInfo.format(7_400_000_000, us))
        assertEquals("46 GB", StorageInfo.format(46_000_000_000, us))
        assertEquals("128 GB", StorageInfo.format(128_000_000_000, us))
        assertEquals("1.2 TB", StorageInfo.format(1_200_000_000_000, us))
        assertEquals("7,4 GB", StorageInfo.format(7_400_000_000, italy))
        assertEquals("0 kB", StorageInfo.format(-5, us))
    }

    @Test
    fun theUsedShareIsAPercentageOfTheTotal() {
        assertEquals(64, StorageInfo(46_000_000_000, 128_000_000_000).usedPercent)
        assertEquals(0, StorageInfo(100, 100).usedPercent)
        assertEquals(100, StorageInfo(0, 100).usedPercent)
        assertEquals("no total means nothing", 0, StorageInfo(10, 0).usedPercent)
        assertEquals("more free than the whole is capped", 0, StorageInfo(200, 100).usedPercent)
    }

    // --- the next alarm

    private val zone = ZoneId.of("Europe/Rome")
    private fun at(date: LocalDate, time: LocalTime) = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun anAlarmLaterTodayIsToday() {
        val day = LocalDate.of(2026, 3, 2) // a Monday
        val next = NextAlarm.describe(at(day, LocalTime.of(18, 30)), at(day, LocalTime.of(9, 0)), zone)!!
        assertEquals(NextAlarm.Day.Today, next.day)
        assertEquals(LocalTime.of(18, 30), next.time)
    }

    @Test
    fun anAlarmTomorrowMorningIsTomorrowEvenWhenItIsLessThanADayAway() {
        val day = LocalDate.of(2026, 3, 2)
        val next = NextAlarm.describe(at(day.plusDays(1), LocalTime.of(7, 0)), at(day, LocalTime.of(23, 0)), zone)!!
        assertEquals(NextAlarm.Day.Tomorrow, next.day)
    }

    @Test
    fun aFartherAlarmIsToldByTheDayOfTheWeek() {
        val day = LocalDate.of(2026, 3, 2)
        val next = NextAlarm.describe(at(day.plusDays(4), LocalTime.of(6, 45)), at(day, LocalTime.of(10, 0)), zone)!!
        assertEquals(NextAlarm.Day.Weekday(DayOfWeek.FRIDAY), next.day)
        assertEquals(LocalTime.of(6, 45), next.time)
    }

    @Test
    fun noAlarmAndSecondsAreHandled() {
        assertNull(NextAlarm.describe(0L, 1_000L, zone))
        assertNull(NextAlarm.describe(-5L, 1_000L, zone))
        val day = LocalDate.of(2026, 3, 2)
        val withSeconds = NextAlarm.describe(at(day, LocalTime.of(7, 0, 42)), at(day, LocalTime.of(6, 0)), zone)!!
        assertEquals(LocalTime.of(7, 0), withSeconds.time)
        // An alarm already past (the clock not updated yet) is told as today, not as a day in the past.
        assertEquals(NextAlarm.Day.Today, NextAlarm.describe(at(day, LocalTime.of(5, 0)), at(day, LocalTime.of(6, 0)), zone)!!.day)
    }

    // --- they are read only when a tile of their kind is on Start

    @Test
    fun theStorageAndAlarmTilesAreAmongTheLiveSources() {
        val storage = AppItem("stock:storagesense", "Storage", Launch.Action("x"), live = LiveKind.Storage)
        val alarm = AppItem("stock:alarms", "Alarms", Launch.Action("x"), live = LiveKind.Alarm)
        val other = AppItem("a", "A", Launch.Action("x"))
        val pages = listOf(listOf(Tile("stock:storagesense", TileSize.Medium), Tile("a", TileSize.Medium)))
        assertEquals(setOf(LiveKind.Storage), LiveSources.wanted(pages, listOf(storage, alarm, other)))
        assertTrue(LiveKind.Alarm in LiveSources.all && LiveKind.Storage in LiveSources.all)
    }

    // --- the details of the weather

    private val forecast = """
        {"current":{"temperature_2m":12.6,"weather_code":3,"is_day":1,"apparent_temperature":10.4,"relative_humidity_2m":71,"wind_speed_10m":14.2},
         "daily":{"time":["2026-10-25","2026-10-26"],"weather_code":[3,61],"temperature_2m_max":[14.2,12.0],"temperature_2m_min":[7.4,8.1],
                  "precipitation_probability_max":[30,85],"sunrise":["2026-10-25T07:12","2026-10-26T07:13"],
                  "sunset":["2026-10-25T17:48","2026-10-26T17:46"],"uv_index_max":[3.4,2.0]}}
    """.trimIndent()

    @Test
    fun theDetailsOfTodayAreRead() {
        val d = OpenMeteo.parseForecast(forecast, false, 0L)!!.details
        assertEquals(10, d.feelsLike)
        assertEquals(71, d.humidity)
        assertEquals(14, d.wind)
        assertEquals(30, d.rainChance)
        assertEquals(LocalTime.of(7, 12), d.sunrise)
        assertEquals(LocalTime.of(17, 48), d.sunset)
        assertEquals(3, d.uvIndex)
        assertFalse(d.isEmpty)
    }

    @Test
    fun everyDayHasItsChanceOfRain() {
        val days = OpenMeteo.parseForecast(forecast, false, 0L)!!.days
        assertEquals(DayForecast(LocalDate.of(2026, 10, 26), WeatherCondition.Rain, 12, 8, rainChance = 85), days[1])
        assertEquals(30, days[0].rainChance)
    }

    @Test
    fun aForecastWithNoDetailsStillGivesTheWeatherAndEmptyDetails() {
        val data = OpenMeteo.parseForecast("""{"current":{"temperature_2m":5.0,"weather_code":0}}""", false, 0L)!!
        assertTrue(data.details.isEmpty)
        assertNull(data.details.feelsLike)
    }

    @Test
    fun brokenValuesAreLeftOutAndTheRestIsKept() {
        val json = """{"current":{"temperature_2m":5.0,"weather_code":0,"relative_humidity_2m":null,"wind_speed_10m":-3,"apparent_temperature":4.0},
            "daily":{"time":["2026-10-25"],"temperature_2m_max":[8.0],"temperature_2m_min":[1.0],"sunrise":["nonsense"],"precipitation_probability_max":[250]}}"""
        val d = OpenMeteo.parseForecast(json, false, 0L)!!.details
        assertNull(d.humidity)
        assertNull(d.sunrise)
        assertEquals("a wind cannot be negative", 0, d.wind)
        assertEquals(4, d.feelsLike)
        assertEquals("a chance cannot go over a hundred", 100, d.rainChance)
    }

    @Test
    fun theAddressAsksForTheDetailsAndTheWindInTheUnitOfTheUser() {
        val celsius = OpenMeteo.forecastUrl(45.0, 7.0, fahrenheit = false)
        assertTrue(celsius, celsius.contains("apparent_temperature") && celsius.contains("sunrise") && celsius.contains("wind_speed_unit=kmh"))
        assertTrue(OpenMeteo.forecastUrl(45.0, 7.0, fahrenheit = true).contains("wind_speed_unit=mph"))
    }

    @Test
    fun theCopyKeptOnThePhoneHasTheDetailsToo() {
        val data = OpenMeteo.parseForecast(forecast, false, 5L)!!
        assertEquals(data, OpenMeteo.decode(OpenMeteo.encode(data)))
    }

    @Test
    fun aCopyKeptByAnOlderVersionStillReads() {
        val old = """{"t":13,"c":"Cloudy","d":true,"f":false,"at":7,"days":[{"date":"2026-10-25","c":"Rain","hi":12,"lo":8}]}"""
        val data = OpenMeteo.decode(old)
        assertNotNull(data)
        assertEquals(13, data!!.temperature)
        assertTrue(data.details.isEmpty)
        assertNull(data.days.single().rainChance)
    }

    @Test
    fun weatherDataWithDetailsEqualsItself() {
        val details = WeatherDetails(feelsLike = 3, humidity = 40)
        val data = WeatherData(5, WeatherCondition.Clear, true, emptyList(), false, 0L, details)
        assertEquals(details, data.details)
        assertFalse(details.isEmpty)
        assertTrue(WeatherDetails().isEmpty)
        assertEquals(ZoneOffset.UTC, ZoneOffset.UTC)
    }
}
