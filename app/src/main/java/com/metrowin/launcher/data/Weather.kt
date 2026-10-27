package com.metrowin.launcher.data

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrowin.launcher.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
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
data class DayForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val high: Int,
    val low: Int,
    /** The chance of rain that day, 0 to 100, when the forecast says. */
    val rainChance: Int? = null,
    /** The rain of the day in millimetres, and the strongest wind of the day (km/h, mph for the Fahrenheit users). */
    val rainMm: Double? = null,
    val windMax: Int? = null,
)

/** What the weather is like now besides the temperature: every part is null when the forecast does not give it. */
@Immutable
data class WeatherDetails(
    val feelsLike: Int? = null,
    /** Relative humidity, percent. */
    val humidity: Int? = null,
    /** The wind, in km/h (mph for the Fahrenheit users: see [WeatherData.fahrenheit]). */
    val wind: Int? = null,
    /** The chance of rain today, 0 to 100. */
    val rainChance: Int? = null,
    val sunrise: LocalTime? = null,
    val sunset: LocalTime? = null,
    val uvIndex: Int? = null,
    /** The gusts and the direction the wind comes from (degrees, 0 is north). */
    val gusts: Int? = null,
    val windDirection: Int? = null,
    /** Air pressure at sea level in hPa, cloud cover in percent, dew point in degrees, how far one sees in km. */
    val pressure: Int? = null,
    val cloudCover: Int? = null,
    val dewPoint: Int? = null,
    val visibilityKm: Double? = null,
    /** Rain expected today in millimetres, and the hours of sun expected today. */
    val rainMm: Double? = null,
    val sunshineHours: Double? = null,
) {
    val isEmpty: Boolean get() = feelsLike == null && humidity == null && wind == null && rainChance == null && sunrise == null && sunset == null && uvIndex == null

    /** The day is as long as the sun is up. */
    val dayLength: java.time.Duration? get() = if (sunrise != null && sunset != null && sunset > sunrise) java.time.Duration.between(sunrise, sunset) else null
}

/** One hour of the forecast: the hour of the day, the temperature, the sky and the chance of rain. */
@Immutable
data class HourForecast(
    val time: LocalTime,
    val temperature: Int,
    val condition: WeatherCondition,
    val rainChance: Int? = null,
    /** The rain of the hour in millimetres, and the wind. */
    val rainMm: Double? = null,
    val wind: Int? = null,
)

/**
 * The air now: the European air quality index (0 good, over 100 extremely poor), the particles and gases in micrograms per cubic metre,
 * and the pollen in grains per cubic metre for each plant that has some (Europe only: elsewhere the list is empty).
 */
@Immutable
data class AirQuality(
    val aqi: Int? = null,
    val pm25: Int? = null,
    val pm10: Int? = null,
    val ozone: Int? = null,
    val no2: Int? = null,
    val pollen: List<Pair<String, Int>> = emptyList(),
)

/** Raining now (and for how many hours more, null: all the day) or dry now (and how many hours until it rains, null: not in the day). */
@Immutable
data class RainOutlook(val raining: Boolean, val hours: Int?)

/** The parts of the forecast that can be shown or hidden: the page and the tile read the same list (see [LauncherSettings.weatherBlocks]). */
enum class WeatherBlock(val code: String, @param:StringRes val label: Int, val default: Boolean = true) {
    Rain("rain", R.string.wx_rain),
    Hourly("hourly", R.string.wx_hourly),
    Chart("chart", R.string.wx_chart),
    Details("details", R.string.wx_details),
    Wind("wind", R.string.wx_wind),
    Pressure("pressure", R.string.wx_pressure),
    Clouds("clouds", R.string.wx_clouds),
    Visibility("visibility", R.string.wx_visibility),
    Dew("dew", R.string.wx_dew),
    RainAmount("rainmm", R.string.wx_rainmm),
    Sunshine("sunshine", R.string.wx_sunshine),
    DayLength("daylength", R.string.wx_daylength),
    Moon("moon", R.string.wx_moon),
    DailyExtras("daily", R.string.wx_daily),
    Air("air", R.string.wx_air, default = false),
    Pollen("pollen", R.string.wx_pollen, default = false);

    companion object {
        val DEFAULT: Set<String> = entries.filter { it.default }.map { it.code }.toSet()
        val CODES: Set<String> = entries.map { it.code }.toSet()
    }
}

/** A city the user follows besides the one of the tile (see [LauncherSettings.weatherCities]). */
@Immutable
data class SavedCity(val name: String, val latitude: Double, val longitude: Double) {
    val key: String get() = "$latitude,$longitude"
}

object SavedCities {
    const val MAX = 5

    fun encode(cities: List<SavedCity>): String = JSONArray().apply {
        cities.forEach { put(JSONObject().put("n", it.name).put("la", it.latitude).put("lo", it.longitude)) }
    }.toString()

    fun decode(json: String?): List<SavedCity> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            if (!o.has("la") || !o.has("lo")) return@mapNotNull null
            SavedCity(o.optString("n"), o.getDouble("la"), o.getDouble("lo"))
        }.distinctBy { it.key }.take(MAX)
    }.getOrDefault(emptyList())
}

/** The weather apps the phone may have: the live tile shows the forecast whichever of them is pinned. */
object WeatherApps {
    private val KNOWN = setOf(
        "com.google.android.apps.weather", "com.sec.android.daemonapp", "com.miui.weather2", "com.huawei.android.totemweather",
        "com.oneplus.weather", "com.coloros.weather2", "com.accuweather.android", "com.weather.Weather", "com.apalon.weatherlive.free",
        "com.yahoo.mobile.client.android.weather", "org.breezyweather", "com.microsoft.amp.apps.bingweather", "com.vivo.weather",
        "com.nothing.weather", "net.sourceforge.opencamera.weather",
    )

    fun matches(packageName: String): Boolean {
        val name = packageName.lowercase(Locale.ROOT)
        return name in KNOWN || name.contains("weather") || name.contains("meteo")
    }
}

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
    val details: WeatherDetails = WeatherDetails(),
    /** The next hours, from the current one. */
    val hours: List<HourForecast> = emptyList(),
    /** The air and the pollen, when they were asked for (see [WeatherBlock.Air]). */
    val air: AirQuality? = null,
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
    const val FORECAST_HOURS = 24

    fun forecastUrl(latitude: Double, longitude: Double, fahrenheit: Boolean): String =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${coordinate(latitude)}&longitude=${coordinate(longitude)}" +
            "&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m," +
            "wind_gusts_10m,wind_direction_10m,pressure_msl,cloud_cover,dew_point_2m,visibility" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max," +
            "precipitation_sum,sunshine_duration,wind_speed_10m_max" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,wind_speed_10m&forecast_hours=$FORECAST_HOURS" +
            "&timezone=auto&forecast_days=$FORECAST_DAYS" +
            "&temperature_unit=${if (fahrenheit) "fahrenheit" else "celsius"}" +
            "&wind_speed_unit=${if (fahrenheit) "mph" else "kmh"}"

    /** The plants whose pollen the air quality answer gives. */
    val POLLEN = listOf("alder", "birch", "grass", "mugwort", "olive", "ragweed")

    fun airUrl(latitude: Double, longitude: Double): String =
        "https://air-quality-api.open-meteo.com/v1/air-quality" +
            "?latitude=${coordinate(latitude)}&longitude=${coordinate(longitude)}" +
            "&current=european_aqi,pm10,pm2_5,ozone,nitrogen_dioxide," + POLLEN.joinToString(",") { "${it}_pollen" } +
            "&timezone=auto"

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
            details = parseDetails(current, daily),
            hours = root.optJSONObject("hourly")?.let(::parseHours).orEmpty(),
        )
    }.getOrNull()

    /** Null when [json] has no current air quality at all (an answer for a place the model does not cover). */
    fun parseAir(json: String): AirQuality? = runCatching {
        val current = JSONObject(json).getJSONObject("current")
        val air = AirQuality(
            aqi = current.intOrNull("european_aqi")?.coerceAtLeast(0),
            pm25 = current.intOrNull("pm2_5")?.coerceAtLeast(0),
            pm10 = current.intOrNull("pm10")?.coerceAtLeast(0),
            ozone = current.intOrNull("ozone")?.coerceAtLeast(0),
            no2 = current.intOrNull("nitrogen_dioxide")?.coerceAtLeast(0),
            pollen = POLLEN.mapNotNull { plant -> current.intOrNull("${plant}_pollen")?.takeIf { it > 0 }?.let { plant to it } },
        )
        air.takeIf { it.aqi != null || it.pm25 != null || it.pm10 != null || it.ozone != null || it.no2 != null || it.pollen.isNotEmpty() }
    }.getOrNull()

    /** 0 good, 1 fair, 2 moderate, 3 poor, 4 very poor, 5 extremely poor (the bands of the European index). */
    fun aqiLevel(aqi: Int): Int = when {
        aqi <= 20 -> 0
        aqi <= 40 -> 1
        aqi <= 60 -> 2
        aqi <= 80 -> 3
        aqi <= 100 -> 4
        else -> 5
    }

    /** 0 low, 1 moderate, 2 high, 3 very high, for grains per cubic metre (one scale for all plants: an approximation). */
    fun pollenLevel(grains: Int): Int = when {
        grains < 10 -> 0
        grains < 50 -> 1
        grains < 200 -> 2
        else -> 3
    }

    /** 0 is north, 1 north-east, 2 east ... 7 north-west. */
    fun compassIndex(degrees: Int): Int = (((degrees % 360) + 360) % 360 + 22) / 45 % 8

    private val WET = setOf(WeatherCondition.Drizzle, WeatherCondition.Rain, WeatherCondition.Showers, WeatherCondition.Snow, WeatherCondition.Thunderstorm)

    /**
     * Is it raining and when does that change, from the hours of the forecast (the first is the current one). Null with no hours.
     * An hour is wet when its sky is, or it has 0.2 mm or more, or a chance of rain of 60 or more.
     */
    fun rainOutlook(now: WeatherCondition, hours: List<HourForecast>): RainOutlook? {
        if (hours.isEmpty()) return null
        val raining = now in WET
        val change = hours.indexOfFirst { h -> (h.condition in WET || (h.rainMm ?: 0.0) >= 0.2 || (h.rainChance ?: 0) >= 60) != raining }
        return RainOutlook(raining, if (change < 0) null else change.coerceAtLeast(1))
    }

    private fun parseHours(hourly: JSONObject): List<HourForecast> {
        val times = hourly.optJSONArray("time") ?: return emptyList()
        val temps = hourly.optJSONArray("temperature_2m") ?: return emptyList()
        val codes = hourly.optJSONArray("weather_code")
        val rain = hourly.optJSONArray("precipitation_probability")
        val mm = hourly.optJSONArray("precipitation")
        val wind = hourly.optJSONArray("wind_speed_10m")
        return buildList {
            for (i in 0 until minOf(times.length(), FORECAST_HOURS)) {
                val time = runCatching { java.time.LocalDateTime.parse(times.getString(i)).toLocalTime() }.getOrNull() ?: continue
                if (temps.isNull(i)) continue
                add(
                    HourForecast(
                        time, temps.getDouble(i).roundToInt(), WeatherCondition.fromCode(codes?.optInt(i, -1) ?: -1),
                        rain?.takeIf { !it.isNull(i) }?.optDouble(i)?.takeIf { !it.isNaN() }?.roundToInt()?.coerceIn(0, 100),
                        rainMm = mm.doubleAt(i)?.coerceAtLeast(0.0),
                        wind = wind.doubleAt(i)?.roundToInt()?.coerceAtLeast(0),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.doubleAt(i: Int): Double? = if (this == null || i >= length() || isNull(i)) null else optDouble(i).takeIf { !it.isNaN() }

    private fun JSONObject.doubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() }?.roundToInt() else null

    private fun org.json.JSONArray.firstTime(): LocalTime? =
        if (length() > 0 && !isNull(0)) runCatching { java.time.LocalDateTime.parse(getString(0)).toLocalTime() }.getOrNull() else null

    private fun org.json.JSONArray.firstInt(): Int? = if (length() > 0 && !isNull(0)) optDouble(0).takeIf { !it.isNaN() }?.roundToInt() else null

    /** The details of today: what is felt, the humidity, the wind, the chance of rain, the sun and the UV index. */
    private fun parseDetails(current: JSONObject, daily: JSONObject?) = WeatherDetails(
        feelsLike = current.intOrNull("apparent_temperature"),
        humidity = current.intOrNull("relative_humidity_2m")?.coerceIn(0, 100),
        wind = current.intOrNull("wind_speed_10m")?.coerceAtLeast(0),
        rainChance = daily?.optJSONArray("precipitation_probability_max")?.firstInt()?.coerceIn(0, 100),
        sunrise = daily?.optJSONArray("sunrise")?.firstTime(),
        sunset = daily?.optJSONArray("sunset")?.firstTime(),
        uvIndex = daily?.optJSONArray("uv_index_max")?.firstInt()?.coerceAtLeast(0),
        gusts = current.intOrNull("wind_gusts_10m")?.coerceAtLeast(0),
        windDirection = current.intOrNull("wind_direction_10m"),
        pressure = current.intOrNull("pressure_msl")?.takeIf { it > 0 },
        cloudCover = current.intOrNull("cloud_cover")?.coerceIn(0, 100),
        dewPoint = current.intOrNull("dew_point_2m"),
        visibilityKm = current.doubleOrNull("visibility")?.let { it / 1000.0 }?.coerceAtLeast(0.0),
        rainMm = daily?.optJSONArray("precipitation_sum").doubleAt(0)?.coerceAtLeast(0.0),
        sunshineHours = daily?.optJSONArray("sunshine_duration").doubleAt(0)?.let { it / 3600.0 }?.coerceAtLeast(0.0),
    )

    private fun parseDays(daily: JSONObject): List<DayForecast> {
        val dates = daily.optJSONArray("time") ?: return emptyList()
        val codes = daily.optJSONArray("weather_code")
        val highs = daily.optJSONArray("temperature_2m_max")
        val lows = daily.optJSONArray("temperature_2m_min")
        val rain = daily.optJSONArray("precipitation_probability_max")
        val mm = daily.optJSONArray("precipitation_sum")
        val wind = daily.optJSONArray("wind_speed_10m_max")
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
                        rainChance = rain?.takeIf { !it.isNull(i) }?.optDouble(i)?.takeIf { !it.isNaN() }?.roundToInt()?.coerceIn(0, 100),
                        rainMm = mm.doubleAt(i)?.coerceAtLeast(0.0),
                        windMax = wind.doubleAt(i)?.roundToInt()?.coerceAtLeast(0),
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

    fun isStale(data: WeatherData?, now: Long, maxAgeMillis: Long = MAX_AGE_MILLIS): Boolean =
        data == null || now - data.fetchedAt > maxAgeMillis || now < data.fetchedAt

    // --- the copy kept on the phone, so the tile shows something with no network

    fun encode(data: WeatherData): String = JSONObject().apply {
        put("t", data.temperature)
        put("c", data.condition.name)
        put("d", data.isDay)
        put("f", data.fahrenheit)
        put("at", data.fetchedAt)
        put(
            "det",
            JSONObject().apply {
                val d = data.details
                d.feelsLike?.let { put("feels", it) }
                d.humidity?.let { put("hum", it) }
                d.wind?.let { put("wind", it) }
                d.rainChance?.let { put("rain", it) }
                d.sunrise?.let { put("rise", it.toString()) }
                d.sunset?.let { put("set", it.toString()) }
                d.uvIndex?.let { put("uv", it) }
                d.gusts?.let { put("gust", it) }
                d.windDirection?.let { put("dir", it) }
                d.pressure?.let { put("hpa", it) }
                d.cloudCover?.let { put("cloud", it) }
                d.dewPoint?.let { put("dew", it) }
                d.visibilityKm?.let { put("vis", it) }
                d.rainMm?.let { put("mm", it) }
                d.sunshineHours?.let { put("sun", it) }
            },
        )
        data.air?.let { a ->
            put(
                "air",
                JSONObject().apply {
                    a.aqi?.let { put("aqi", it) }
                    a.pm25?.let { put("pm25", it) }
                    a.pm10?.let { put("pm10", it) }
                    a.ozone?.let { put("o3", it) }
                    a.no2?.let { put("no2", it) }
                    put("pol", JSONObject().apply { a.pollen.forEach { (plant, grains) -> put(plant, grains) } })
                },
            )
        }
        put(
            "hrs",
            JSONArray().apply {
                data.hours.forEach { h ->
                    put(JSONObject().put("t", h.time.toString()).put("c", h.condition.name).put("v", h.temperature).also { o -> h.rainChance?.let { o.put("r", it) }; h.rainMm?.let { o.put("mm", it) }; h.wind?.let { o.put("w", it) } })
                }
            },
        )
        put(
            "days",
            JSONArray().apply {
                data.days.forEach { day ->
                    put(
                        JSONObject().put("date", day.date.toString()).put("c", day.condition.name).put("hi", day.high).put("lo", day.low)
                            .also { o -> day.rainChance?.let { o.put("rain", it) }; day.rainMm?.let { o.put("mm", it) }; day.windMax?.let { o.put("w", it) } },
                    )
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
                DayForecast(
                    LocalDate.parse(d.getString("date")), WeatherCondition.valueOf(d.getString("c")), d.getInt("hi"), d.getInt("lo"),
                    rainChance = if (d.has("rain")) d.getInt("rain") else null,
                    rainMm = d.doubleOrNull("mm"), windMax = if (d.has("w")) d.getInt("w") else null,
                )
            },
            details = o.optJSONObject("det")?.let { det ->
                WeatherDetails(
                    feelsLike = if (det.has("feels")) det.getInt("feels") else null,
                    humidity = if (det.has("hum")) det.getInt("hum") else null,
                    wind = if (det.has("wind")) det.getInt("wind") else null,
                    rainChance = if (det.has("rain")) det.getInt("rain") else null,
                    sunrise = if (det.has("rise")) LocalTime.parse(det.getString("rise")) else null,
                    sunset = if (det.has("set")) LocalTime.parse(det.getString("set")) else null,
                    uvIndex = if (det.has("uv")) det.getInt("uv") else null,
                    gusts = if (det.has("gust")) det.getInt("gust") else null,
                    windDirection = if (det.has("dir")) det.getInt("dir") else null,
                    pressure = if (det.has("hpa")) det.getInt("hpa") else null,
                    cloudCover = if (det.has("cloud")) det.getInt("cloud") else null,
                    dewPoint = if (det.has("dew")) det.getInt("dew") else null,
                    visibilityKm = det.doubleOrNull("vis"),
                    rainMm = det.doubleOrNull("mm"),
                    sunshineHours = det.doubleOrNull("sun"),
                )
            } ?: WeatherDetails(),
            hours = o.optJSONArray("hrs")?.let { arr ->
                List(arr.length()) {
                    val h = arr.getJSONObject(it)
                    HourForecast(LocalTime.parse(h.getString("t")), h.getInt("v"), WeatherCondition.valueOf(h.getString("c")), if (h.has("r")) h.getInt("r") else null, h.doubleOrNull("mm"), if (h.has("w")) h.getInt("w") else null)
                }
            }.orEmpty(),
            air = o.optJSONObject("air")?.let { a ->
                val pol = a.optJSONObject("pol")
                AirQuality(
                    aqi = if (a.has("aqi")) a.getInt("aqi") else null,
                    pm25 = if (a.has("pm25")) a.getInt("pm25") else null,
                    pm10 = if (a.has("pm10")) a.getInt("pm10") else null,
                    ozone = if (a.has("o3")) a.getInt("o3") else null,
                    no2 = if (a.has("no2")) a.getInt("no2") else null,
                    pollen = POLLEN.mapNotNull { plant -> pol?.takeIf { it.has(plant) }?.let { plant to it.getInt(plant) } },
                )
            },
        )
    }.getOrNull()
}
