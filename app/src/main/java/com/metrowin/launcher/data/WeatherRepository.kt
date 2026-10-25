package com.metrowin.launcher.data

import android.content.Context
import androidx.core.content.edit
import java.net.HttpURLConnection
import java.net.URL

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

    /** Only the two Open-Meteo hosts, over HTTPS, with short timeouts and an answer of at most 256 KB. */
    private fun get(address: String): String? = runCatching {
        val url = URL(address)
        require(url.protocol == "https" && url.host in HOSTS)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "MetroWinLauncher")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (out.size() + read > MAX_BYTES) return null
                    out.write(buffer, 0, read)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val KEY_CACHE = "weather_cache"
        const val TIMEOUT_MS = 8_000
        const val MAX_BYTES = 256 * 1024
        val HOSTS = setOf("api.open-meteo.com", "geocoding-api.open-meteo.com")
    }
}
