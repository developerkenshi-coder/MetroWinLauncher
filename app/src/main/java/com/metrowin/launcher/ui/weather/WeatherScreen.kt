package com.metrowin.launcher.ui.weather

import com.metrowin.launcher.data.WeatherBlock
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.ui.theme.pageBackground
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.R
import androidx.compose.foundation.horizontalScroll
import com.metrowin.launcher.data.SavedCity
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Forecast page: the place, the weather now in large light type, and the next days. With no city yet it
 * asks for one.
 */
@Composable
fun WeatherScreen(
    weather: WeatherData?,
    place: String,
    /** The other cities that are followed, with their weather once it is read. */
    others: List<Pair<SavedCity, WeatherData?>> = emptyList(),
    /** What is shown besides the temperature and the days (codes of [WeatherBlock]). */
    blocks: Set<String> = WeatherBlock.DEFAULT,
    onRefresh: () -> Unit,
    onChooseCity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    Column(
        modifier
            .fillMaxSize()
            .pageBackground()
            // Swallow touches so nothing underneath reacts while this page is up.
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .cutoutSidesPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        MetroText(
            stringResource(R.string.weather_page_header), size = 14.sp, weight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 28.dp),
        )
        MetroText(
            place.ifBlank { stringResource(R.string.weather_title) }, size = 34.sp, maxLines = 2, lineHeight = 40.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp).testTag("weather:place"),
        )
        if (weather == null) {
            MetroText(
                stringResource(R.string.weather_not_set), size = 17.sp, weight = FontWeight.Normal, color = colors.subtle,
                maxLines = Int.MAX_VALUE, lineHeight = 24.sp, modifier = Modifier.padding(bottom = 20.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                MetroText("${weather.temperature}°", size = 96.sp, modifier = Modifier.testTag("weather:temperature"))
                MetroText(
                    stringResource(weather.condition.label), size = 24.sp, weight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 16.dp, bottom = 18.dp),
                )
            }
            if (WeatherBlock.Rain.code in blocks) {
                OpenMeteo.rainOutlook(weather.condition, weather.hours)?.let { outlook ->
                    MetroText(
                        rainOutlookText(outlook), size = 18.sp, weight = FontWeight.Normal, maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp).testTag("weather:rain"),
                    )
                }
            }
            WeatherDetailsBlock(weather, blocks)
            if (WeatherBlock.Hourly.code in blocks) HourlyRow(weather)
            if (WeatherBlock.Chart.code in blocks) TemperatureChart(weather.hours, Modifier.padding(top = 16.dp))
            weather.air?.let { AirBlock(it, WeatherBlock.Air.code in blocks, WeatherBlock.Pollen.code in blocks) }
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                weather.days.forEachIndexed { index, day ->
                    Row(Modifier.fillMaxWidth().testTag("weather:day:$index"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            MetroText(day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), size = 22.sp)
                            val withExtras = WeatherBlock.DailyExtras.code in blocks
                            val speed = stringResource(if (weather.fahrenheit) R.string.unit_mph else R.string.unit_kmh)
                            MetroText(
                                listOfNotNull(
                                    stringResource(day.condition.label),
                                    day.rainChance?.takeIf { it > 0 }?.let { stringResource(R.string.weather_rain_short, it) },
                                    day.rainMm?.takeIf { withExtras && it >= 0.1 }?.let { "${String.format(Locale.getDefault(), "%.1f", it)} mm" },
                                    day.windMax?.takeIf { withExtras }?.let { "$it $speed" },
                                ).joinToString(" · "),
                                size = 15.sp, weight = FontWeight.Normal, color = colors.subtle,
                            )
                        }
                        MetroText("${day.high}°", size = 26.sp)
                        MetroText("${day.low}°", size = 26.sp, color = colors.subtle, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }
            MetroText(
                stringResource(
                    R.string.weather_updated,
                    DateUtils.getRelativeTimeSpanString(
                        weather.fetchedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                ),
                size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )
        }
        if (others.isNotEmpty()) {
            MetroText(stringResource(R.string.weather_other_cities), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                others.forEachIndexed { index, (city, forecast) ->
                    Row(Modifier.fillMaxWidth().featherIn(index, others.size).testTag("weather:other:$index"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            MetroText(city.name.substringBefore(','), size = 22.sp, maxLines = 1)
                            MetroText(
                                forecast?.let { stringResource(it.condition.label) } ?: stringResource(R.string.weather_loading),
                                size = 15.sp, weight = FontWeight.Normal, color = colors.subtle,
                            )
                        }
                        if (forecast != null) MetroText("${forecast.temperature}°", size = 34.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            if (weather != null) {
                MetroButton(stringResource(R.string.weather_refresh), Modifier.weight(1f).testTag("weather:refresh"), onClick = onRefresh)
            }
            MetroButton(stringResource(R.string.weather_choose_city), Modifier.weight(1f).testTag("weather:choose"), onClick = onChooseCity)
        }
    }
}

/** The next hours in a row that scrolls sideways: the hour, the temperature, and the chance of rain when there is some. */
@Composable
private fun HourlyRow(weather: WeatherData) {
    if (weather.hours.isEmpty()) return
    val colors = LocalMetroColors.current
    val clock = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
    MetroText(stringResource(R.string.weather_hourly), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("weather:hourly"), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        weather.hours.forEachIndexed { index, hour ->
            Column(Modifier.featherIn(index, weather), horizontalAlignment = Alignment.CenterHorizontally) {
                MetroText(hour.time.format(clock), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
                MetroText("${hour.temperature}°", size = 24.sp)
                MetroText(
                    hour.rainChance?.takeIf { it > 0 }?.let { stringResource(R.string.weather_rain_short, it) } ?: stringResource(hour.condition.label),
                    size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1,
                )
            }
        }
    }
}

/** What it is like now besides the temperature: what is felt, wind, humidity, rain, the sun and the UV index, two to a line. */
@Composable
private fun WeatherDetailsBlock(weather: WeatherData, blocks: Set<String>) {
    val d = weather.details
    val colors = LocalMetroColors.current
    val unit = stringResource(if (weather.fahrenheit) R.string.unit_mph else R.string.unit_kmh)
    val time = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
    val basics = if (WeatherBlock.Details.code !in blocks) emptyList() else listOfNotNull(
        d.feelsLike?.let { stringResource(R.string.weather_feels) to "$it°" },
        d.wind?.let { stringResource(R.string.weather_wind) to "$it $unit" },
        d.humidity?.let { stringResource(R.string.weather_humidity) to "$it%" },
        d.rainChance?.let { stringResource(R.string.weather_rain_chance) to "$it%" },
        d.sunrise?.let { stringResource(R.string.weather_sunrise) to it.format(time) },
        d.sunset?.let { stringResource(R.string.weather_sunset) to it.format(time) },
        d.uvIndex?.let { stringResource(R.string.weather_uv) to it.toString() },
    )
    val cells = basics + extraCells(weather, blocks)
    if (cells.isEmpty()) return
    Column(Modifier.padding(top = 20.dp).testTag("weather:details"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        MetroText(label, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                        MetroText(value, size = 22.sp)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
