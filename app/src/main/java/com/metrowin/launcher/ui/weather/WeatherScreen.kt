package com.metrowin.launcher.ui.weather

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
import com.metrowin.launcher.data.WeatherData
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
            WeatherDetailsBlock(weather)
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                weather.days.forEachIndexed { index, day ->
                    Row(Modifier.fillMaxWidth().testTag("weather:day:$index"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            MetroText(day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), size = 22.sp)
                            MetroText(
                                listOfNotNull(stringResource(day.condition.label), day.rainChance?.takeIf { it > 0 }?.let { stringResource(R.string.weather_rain_short, it) })
                                    .joinToString(" · "),
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            if (weather != null) {
                MetroButton(stringResource(R.string.weather_refresh), Modifier.weight(1f).testTag("weather:refresh"), onClick = onRefresh)
            }
            MetroButton(stringResource(R.string.weather_choose_city), Modifier.weight(1f).testTag("weather:choose"), onClick = onChooseCity)
        }
    }
}

/** What it is like now besides the temperature: what is felt, wind, humidity, rain, the sun and the UV index, two to a line. */
@Composable
private fun WeatherDetailsBlock(weather: WeatherData) {
    val d = weather.details
    if (d.isEmpty) return
    val colors = LocalMetroColors.current
    val unit = stringResource(if (weather.fahrenheit) R.string.unit_mph else R.string.unit_kmh)
    val time = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
    val cells = listOfNotNull(
        d.feelsLike?.let { stringResource(R.string.weather_feels) to "$it°" },
        d.wind?.let { stringResource(R.string.weather_wind) to "$it $unit" },
        d.humidity?.let { stringResource(R.string.weather_humidity) to "$it%" },
        d.rainChance?.let { stringResource(R.string.weather_rain_chance) to "$it%" },
        d.sunrise?.let { stringResource(R.string.weather_sunrise) to it.format(time) },
        d.sunset?.let { stringResource(R.string.weather_sunset) to it.format(time) },
        d.uvIndex?.let { stringResource(R.string.weather_uv) to it.toString() },
    )
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
