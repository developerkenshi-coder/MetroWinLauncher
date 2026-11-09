package com.metrowin.launcher.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentWidth
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

/**
 * The temperature of the next hours as a thin accent line with a dot for each hour, and the chance of rain as bars under it. The hours are
 * written under it every third one, and the first, now, is the one with the big dot and its word in the accent colour.
 */
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
            val padTop = 10.dp.toPx()
            val padBottom = 10.dp.toPx()
            val usable = size.height - padTop - padBottom
            // Each hour has its own slot, and the dot of it is in the middle, over the label under it.
            val slot = size.width / hours.size
            val range = (high - low).coerceAtLeast(1)
            fun point(i: Int) = Offset((i + 0.5f) * slot, padTop + usable * (1f - (hours[i].temperature - low).toFloat() / range))
            hours.forEachIndexed { i, hour ->
                val chance = (hour.rainChance ?: 0) / 100f
                if (chance > 0f) {
                    val barWidth = (slot * 0.6f).coerceAtLeast(2f)
                    drawRect(
                        colors.subtle.copy(alpha = 0.35f),
                        Offset((i + 0.5f) * slot - barWidth / 2, size.height - chance * size.height * 0.5f),
                        Size(barWidth, chance * size.height * 0.5f),
                    )
                }
            }
            val path = Path()
            hours.indices.forEach { i -> point(i).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
            drawPath(path, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            hours.indices.forEach { i -> if (i > 0) drawCircle(accent, 3.dp.toPx(), point(i)) }
            // Now: a ring round a dot, so it reads as the place the line starts from.
            drawCircle(accent, 7.dp.toPx(), point(0), style = Stroke(width = 2.dp.toPx()))
            drawCircle(accent, 3.5.dp.toPx(), point(0))
        }
        Row(Modifier.fillMaxWidth()) {
            hours.forEachIndexed { i, hour ->
                Box(Modifier.weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    val label = when {
                        i == 0 -> stringResource(R.string.weather_now)
                        i >= 2 && i % 3 == 0 -> hour.time.format(clock)
                        else -> null
                    }
                    if (label != null) {
                        MetroText(
                            label, size = 11.sp, weight = if (i == 0) FontWeight.Bold else FontWeight.Normal, color = if (i == 0) accent else colors.subtle,
                            maxLines = 1, minScale = 1f, modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
        MetroText("$low° – $high°", size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
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
