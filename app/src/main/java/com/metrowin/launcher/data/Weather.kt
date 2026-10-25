package com.metrowin.launcher.data

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** The kinds of weather the tile can tell apart, from the WMO weather codes Open-Meteo reports. */
enum class WeatherCondition(@param:StringRes val label: Int) {
    Clear(R.string.weather_clear),
    PartlyCloudy(R.string.weather_partly),
    Cloudy(R.string.weather_cloudy),
    Fog(R.string.weather_fog),
    Drizzle(R.string.weather_drizzle),
    Rain(R.string.weather_rain),
    Snow(R.string.weather_snow),
    Showers(R.string.weather_showers),
    Thunderstorm(R.string.weather_storm);

    companion object {
        /** https://open-meteo.com/en/docs: 0 clear, 1-2 partly cloudy, 3 overcast, 45/48 fog, 51-57 drizzle, ... */
        fun fromCode(code: Int): WeatherCondition = when (code) {
            0 -> Clear
            1, 2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            in 51..57 -> Drizzle
            in 61..67 -> Rain
            in 71..77, 85, 86 -> Snow
            in 80..82 -> Showers
            in 95..99 -> Thunderstorm
            else -> Cloudy
        }
    }
}

@Immutable
data class DayForecast(val date: LocalDate, val condition: WeatherCondition, val high: Int, val low: Int)

/** What the weather is now and for the next days, in whole degrees of the unit it was asked in. */
@Immutable
data class WeatherData(
    val temperature: Int,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val days: List<DayForecast>,
    val fahrenheit: Boolean,
    /** When it was fetched (epoch milliseconds), to know when it is old. */
    val fetchedAt: Long,
)

/** A place found by name, to pick the city of the weather tile. */
@Immutable
data class PlaceMatch(val name: String, val region: String, val country: String, val latitude: Double, val longitude: Double) {
    /** "Torino, Piemonte, Italia", without the parts that are missing. */
    val displayName: String get() = listOf(name, region, country).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * Open-Meteo (https://open-meteo.com, free, no key, CC BY 4.0): the addresses to ask and the way to read the
 * answers. Kept apart from the network code so that it can be unit tested.
 */
object OpenMeteo {
    const val FORECAST_DAYS = 5

    fun forecastUrl(latitude: Double, longitude: Double, fahrenheit: Boolean): String =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${coordinate(latitude)}&longitude=${coordinate(longitude)}" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=$FORECAST_DAYS" +
            "&temperature_unit=${if (fahrenheit) "fahrenheit" else "celsius"}"

    fun geocodingUrl(name: String, language: String, count: Int = 6): String =
        "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${URLEncoder.encode(name.trim(), "UTF-8")}&count=$count&language=$language&format=json"

    /** Coordinates with a dot as the decimal separator, whatever the language of the phone. */
    private fun coordinate(value: Double) = String.format(Locale.ROOT, "%.4f", value)

    /** Null when [json] is not a forecast with a current temperature. Missing days simply are not listed. */
    fun parseForecast(json: String, fahrenheit: Boolean, now: Long): WeatherData? = runCatching {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val daily = root.optJSONObject("daily")
        WeatherData(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            condition = WeatherCondition.fromCode(current.optInt("weather_code", -1)),
            isDay = current.optInt("is_day", 1) == 1,
            days = daily?.let(::parseDays).orEmpty(),
            fahrenheit = fahrenheit,
            fetchedAt = now,
        )
    }.getOrNull()

    private fun parseDays(daily: JSONObject): List<DayForecast> {
        val dates = daily.optJSONArray("time") ?: return emptyList()
        val codes = daily.optJSONArray("weather_code")
        val highs = daily.optJSONArray("temperature_2m_max")
        val lows = daily.optJSONArray("temperature_2m_min")
        return buildList {
            for (i in 0 until dates.length()) {
                val date = runCatching { LocalDate.parse(dates.getString(i)) }.getOrNull() ?: continue
                if (highs == null || lows == null || highs.isNull(i) || lows.isNull(i)) continue
                add(
                    DayForecast(
                        date = date,
                        condition = WeatherCondition.fromCode(codes?.optInt(i, -1) ?: -1),
                        high = highs.getDouble(i).roundToInt(),
                        low = lows.getDouble(i).roundToInt(),
                    ),
                )
            }
        }
    }

    /** The places of a geocoding answer, best match first. An answer with no results is an empty list. */
    fun parsePlaces(json: String): List<PlaceMatch> = runCatching {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        buildList {
            for (i in 0 until results.length()) {
                val o = results.getJSONObject(i)
                if (!o.has("latitude") || !o.has("longitude")) continue
                add(
                    PlaceMatch(
                        name = o.optString("name"),
                        region = o.optString("admin1"),
                        country = o.optString("country"),
                        latitude = o.getDouble("latitude"),
                        longitude = o.getDouble("longitude"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    /** Weather older than this is fetched again. */
    const val MAX_AGE_MILLIS = 30L * 60_000

    fun isStale(data: WeatherData?, now: Long): Boolean = data == null || now - data.fetchedAt > MAX_AGE_MILLIS || now < data.fetchedAt

    // --- the copy kept on the phone, so the tile shows something with no network

    fun encode(data: WeatherData): String = JSONObject().apply {
        put("t", data.temperature)
        put("c", data.condition.name)
        put("d", data.isDay)
        put("f", data.fahrenheit)
        put("at", data.fetchedAt)
        put(
            "days",
            JSONArray().apply {
                data.days.forEach { day ->
                    put(JSONObject().put("date", day.date.toString()).put("c", day.condition.name).put("hi", day.high).put("lo", day.low))
                }
            },
        )
    }.toString()

    fun decode(json: String): WeatherData? = runCatching {
        val o = JSONObject(json)
        val days = o.getJSONArray("days")
        WeatherData(
            temperature = o.getInt("t"),
            condition = WeatherCondition.valueOf(o.getString("c")),
            isDay = o.getBoolean("d"),
            fahrenheit = o.getBoolean("f"),
            fetchedAt = o.getLong("at"),
            days = List(days.length()) {
                val d = days.getJSONObject(it)
                DayForecast(LocalDate.parse(d.getString("date")), WeatherCondition.valueOf(d.getString("c")), d.getInt("hi"), d.getInt("lo"))
            },
        )
    }.getOrNull()
}
