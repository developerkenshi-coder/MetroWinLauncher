package com.metrowin.launcher.data

import android.content.Context
import androidx.core.content.edit

/** Fetches the weather and finds places on Open-Meteo, and keeps the last weather so the tile has something offline. */
class WeatherRepository(context: Context) {
    private val prefs = context.getSharedPreferences("metrowin", Context.MODE_PRIVATE)

    /** The weather fetched last, or null if there never was one. */
    fun cached(): WeatherData? = prefs.getString(KEY_CACHE, null)?.let(OpenMeteo::decode)

    fun save(data: WeatherData?) = prefs.edit {
        if (data == null) remove(KEY_CACHE) else putString(KEY_CACHE, OpenMeteo.encode(data))
    }

    /** Blocking, call it off the main thread. Null when there is no connection or the answer is not a forecast. */
    fun fetch(latitude: Double, longitude: Double, fahrenheit: Boolean): WeatherData? =
        get(OpenMeteo.forecastUrl(latitude, longitude, fahrenheit))
            ?.let { OpenMeteo.parseForecast(it, fahrenheit, System.currentTimeMillis()) }

    /** Blocking. Null when the search could not be made, an empty list when nothing matches. */
    fun search(name: String, language: String): List<PlaceMatch>? {
        if (name.isBlank()) return emptyList()
        return get(OpenMeteo.geocodingUrl(name, language))?.let(OpenMeteo::parsePlaces)
    }

    /** Only the two Open-Meteo hosts (see [SafeHttp]). */
    private fun get(address: String): String? = SafeHttp.get(address, HOSTS)

    private companion object {
        const val KEY_CACHE = "weather_cache"
        val HOSTS = setOf("api.open-meteo.com", "geocoding-api.open-meteo.com")
    }
}
