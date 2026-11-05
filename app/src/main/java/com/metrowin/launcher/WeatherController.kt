package com.metrowin.launcher

import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.data.PowerPolicy
import com.metrowin.launcher.data.SavedCities
import com.metrowin.launcher.data.SavedCity
import com.metrowin.launcher.data.Throttle
import com.metrowin.launcher.data.WeatherBlock
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The weather of the chosen city and of the other cities that are followed: what is on show, and when it is read again. */
internal class WeatherController(
    private val repository: WeatherRepository,
    private val scope: CoroutineScope,
    private val settings: StateFlow<LauncherSettings>,
    private val change: ((LauncherSettings) -> LauncherSettings) -> Unit,
    private val powerSaving: StateFlow<Boolean>,
    private val throttle: Throttle,
) {
    private val _weather = MutableStateFlow(repository.cached())
    val weather: StateFlow<WeatherData?> = _weather.asStateFlow()
    private var job: Job? = null

    /** The weather of the other cities that are followed, by [SavedCity.key]; what is not there yet is being fetched. */
    private val _cityWeather = MutableStateFlow<Map<String, WeatherData>>(emptyMap())
    val cityWeather: StateFlow<Map<String, WeatherData>> = _cityWeather.asStateFlow()

    /** Another city or another unit: the weather on show is not the one asked for, so it goes and is read again. */
    fun onSettingsChanged(previous: LauncherSettings, updated: LauncherSettings) {
        val movedWeather = updated.weatherLatitude != previous.weatherLatitude && !(updated.weatherLatitude.isNaN() && previous.weatherLatitude.isNaN()) ||
            updated.weatherLongitude != previous.weatherLongitude && !(updated.weatherLongitude.isNaN() && previous.weatherLongitude.isNaN()) ||
            updated.weatherFahrenheit != previous.weatherFahrenheit
        if (updated.weatherFahrenheit != previous.weatherFahrenheit) _cityWeather.value = emptyMap()
        if (movedWeather) {
            _weather.value = null
            refresh(force = true)
        }
    }

    /** Reads the weather of the followed cities when the Forecast page is opened: not before, and not again within a few minutes. */
    fun refreshCities(force: Boolean = false) {
        val s = settings.value
        _cityWeather.update { old -> old.filterKeys { key -> s.weatherCities.any { it.key == key } } }
        if (s.weatherCities.isEmpty()) return
        val saving = powerSaving.value
        if (!force && !throttle.due("cities", PowerPolicy.weatherRetryMs(saving))) return
        scope.launch {
            s.weatherCities.forEach { city ->
                val fetched = withContext(Dispatchers.IO) { repository.fetch(city.latitude, city.longitude, s.weatherFahrenheit) }
                if (fetched != null) _cityWeather.update { it + (city.key to fetched) }
            }
        }
    }

    /** Follows [place] as well: the Forecast page shows it under the city of the tile. */
    fun follow(place: PlaceMatch) {
        change { s ->
            if (s.weatherCities.size >= SavedCities.MAX || s.weatherCities.any { it.latitude == place.latitude && it.longitude == place.longitude }) s
            else s.copy(weatherCities = s.weatherCities + SavedCity(place.displayName, place.latitude, place.longitude))
        }
        refreshCities(force = true)
    }

    fun unfollow(city: SavedCity) {
        change { s -> s.copy(weatherCities = s.weatherCities - city) }
        _cityWeather.update { it - city.key }
    }

    /** Fetches the weather of the chosen city, unless what is there is recent. [force] fetches in any case. */
    fun refresh(force: Boolean = false) {
        val s = settings.value
        if (s.weatherLatitude.isNaN() || s.weatherLongitude.isNaN()) {
            _weather.value = null
            repository.save(null)
            return
        }
        val current = _weather.value
        val saving = powerSaving.value
        val unitChanged = current != null && current.fahrenheit != s.weatherFahrenheit
        val wantsAir = WeatherBlock.Air.code in s.weatherBlocks || WeatherBlock.Pollen.code in s.weatherBlocks
        // The air is a second answer: switched on later, it is asked for at the next visit even if the forecast is recent.
        val stale = current == null || OpenMeteo.isStale(current, System.currentTimeMillis(), PowerPolicy.weatherMaxAgeMs(saving)) ||
            (wantsAir && current.air == null)
        if (!force && !unitChanged && !stale) return
        // Done or not, the next try waits: with no connection every return to Start would otherwise go to the network again.
        if (!force && !unitChanged && !throttle.due("weather", PowerPolicy.weatherRetryMs(saving))) return
        job?.cancel()
        job = scope.launch {
            val fetched = withContext(Dispatchers.IO) {
                repository.fetch(s.weatherLatitude, s.weatherLongitude, s.weatherFahrenheit)?.let { forecast ->
                    if (wantsAir) forecast.copy(air = repository.fetchAir(s.weatherLatitude, s.weatherLongitude)) else forecast
                }
            }
            // Nothing new (no connection): the weather already there stays, however old.
            if (fetched != null) {
                _weather.value = fetched
                repository.save(fetched)
            }
        }
    }
}
