package com.metrowin.launcher.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AirQuality
import com.metrowin.launcher.data.HourForecast
import com.metrowin.launcher.data.MoonPhase
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.RainOutlook
import com.metrowin.launcher.data.WeatherBlock
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.time.LocalDate
import java.util.Locale

/** One number with a plain decimal point of the language of the phone, one decimal. */
private fun decimal(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

/** The line that says when it rains or stops raining ("Rain expected in 3 h"). */
@Composable
internal fun rainOutlookText(outlook: RainOutlook): String = when {
    outlook.raining && outlook.hours != null -> stringResource(R.string.weather_rain_stops, outlook.hours)
    outlook.raining -> stringResource(R.string.weather_rain_all_day)
    outlook.hours != null -> stringResource(R.string.weather_rain_in, outlook.hours)
    else -> stringResource(R.string.weather_rain_none)
}

/**
 * The cells of the page after the details of today, one for each part that is switched on and that the forecast has:
 * the label and the value, in the order the page shows them.
 */
@Composable
internal fun extraCells(weather: WeatherData, blocks: Set<String>): List<Pair<String, String>> {
    val d = weather.details
    val speed = stringResource(if (weather.fahrenheit) R.string.unit_mph else R.string.unit_kmh)
    val compass = stringArrayResource(R.array.wind_compass)
    fun on(block: WeatherBlock) = block.code in blocks
    return buildList {
        if (on(WeatherBlock.RainAmount)) d.rainMm?.let { add(stringResource(R.string.weather_rain_amount) to "${decimal(it)} mm") }
        if (on(WeatherBlock.DayLength)) {
            d.dayLength?.let { add(stringResource(R.string.weather_day_length) to stringResource(R.string.weather_hours_minutes, it.toHours(), it.toMinutes() % 60)) }
        }
        if (on(WeatherBlock.Sunshine)) d.sunshineHours?.let { add(stringResource(R.string.weather_sunshine) to "${decimal(it)} h") }
        if (on(WeatherBlock.Wind)) {
            d.gusts?.let { add(stringResource(R.string.weather_gusts) to "$it $speed") }
            d.windDirection?.let { add(stringResource(R.string.weather_direction) to "${compass[OpenMeteo.compassIndex(it)]} · $it°") }
        }
        if (on(WeatherBlock.Pressure)) d.pressure?.let { add(stringResource(R.string.weather_pressure) to "$it hPa") }
        if (on(WeatherBlock.Clouds)) d.cloudCover?.let { add(stringResource(R.string.weather_clouds) to "$it%") }
        if (on(WeatherBlock.Visibility)) {
            d.visibilityKm?.let { km ->
                add(stringResource(R.string.weather_visibility) to if (weather.fahrenheit) "${decimal(km * 0.621371)} mi" else "${decimal(km)} km")
            }
        }
        if (on(WeatherBlock.Dew)) d.dewPoint?.let { add(stringResource(R.string.weather_dew) to "$it°") }
        if (on(WeatherBlock.Moon)) {
            val today = LocalDate.now()
            add(stringResource(R.string.weather_moon) to "${stringResource(MoonPhase.name(today).label)} · ${(MoonPhase.illumination(today) * 100).toInt()}%")
        }
    }
}

/** The temperature of the next hours as a line, with the chance of rain as bars under it. */
@Composable
internal fun TemperatureChart(hours: List<HourForecast>, modifier: Modifier = Modifier) {
    if (hours.size < 3) return
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val low = hours.minOf { it.temperature }
    val high = hours.maxOf { it.temperature }
    val description = stringResource(R.string.weather_chart_description, low, high)
    val clock = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
    Column(modifier.fillMaxWidth().testTag("weather:chart"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = description }) {
            val padTop = 6.dp.toPx()
            val padBottom = 6.dp.toPx()
            val usable = size.height - padTop - padBottom
            val step = size.width / (hours.size - 1)
            val range = (high - low).coerceAtLeast(1)
            hours.forEachIndexed { i, hour ->
                val chance = (hour.rainChance ?: 0) / 100f
                if (chance > 0f) {
                    val barWidth = (step * 0.6f).coerceAtLeast(2f)
                    drawRect(
                        colors.subtle.copy(alpha = 0.35f),
                        Offset(i * step - barWidth / 2, size.height - chance * size.height * 0.5f),
                        Size(barWidth, chance * size.height * 0.5f),
                    )
                }
            }
            val path = Path()
            hours.forEachIndexed { i, hour ->
                val point = Offset(i * step, padTop + usable * (1f - (hour.temperature - low).toFloat() / range))
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetroText(hours.first().time.format(clock), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
            MetroText("$low° – $high°", size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
            MetroText(hours.last().time.format(clock), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
        }
    }
}

/** The air: the index with its word, then the particles and gases. */
@Composable
internal fun AirBlock(air: AirQuality, showAir: Boolean, showPollen: Boolean) {
    val colors = LocalMetroColors.current
    val levels = stringArrayResource(R.array.aqi_levels)
    val pollenNames = stringArrayResource(R.array.pollen_names)
    val pollenLevels = stringArrayResource(R.array.pollen_levels)
    if (showAir && air.aqi != null) {
        MetroText(stringResource(R.string.weather_air_title), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
        Column(Modifier.testTag("weather:air"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                MetroText("${air.aqi}", size = 40.sp)
                MetroText(levels[OpenMeteo.aqiLevel(air.aqi)], size = 20.sp, weight = FontWeight.Normal, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
            }
            val parts = listOfNotNull(
                air.pm25?.let { "PM2.5 $it" }, air.pm10?.let { "PM10 $it" }, air.ozone?.let { "O₃ $it" }, air.no2?.let { "NO₂ $it" },
            )
            if (parts.isNotEmpty()) {
                MetroText(
                    parts.joinToString("   ") + "  µg/m³", size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2,
                )
            }
        }
    }
    if (showPollen && air.pollen.isNotEmpty()) {
        MetroText(stringResource(R.string.weather_pollen_title), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
        Column(Modifier.testTag("weather:pollen"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            air.pollen.forEach { (plant, grains) ->
                val name = pollenNames[OpenMeteo.POLLEN.indexOf(plant).coerceAtLeast(0)]
                Row(Modifier.fillMaxWidth()) {
                    MetroText(name, size = 18.sp, modifier = Modifier.weight(1f))
                    MetroText("${pollenLevels[OpenMeteo.pollenLevel(grains)]} · $grains", size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
                }
            }
        }
    }
}
