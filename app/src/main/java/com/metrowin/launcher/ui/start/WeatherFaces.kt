package com.metrowin.launcher.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.DayForecast
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.WeatherBlock
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.weather.rainOutlookText
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale



/** What the weather is like now besides the temperature: what is felt, the wind and the humidity. */
@Composable
internal fun WeatherDetailsFace(weather: WeatherData) {
    val details = weather.details
    val unit = stringResource(if (weather.fahrenheit) R.string.unit_mph else R.string.unit_kmh)
    val lines = listOfNotNull(
        details.feelsLike?.let { stringResource(R.string.weather_feels_like, it) },
        details.wind?.let { stringResource(R.string.weather_wind_line, it, unit) },
        details.humidity?.let { stringResource(R.string.weather_humidity_line, it) },
        details.rainChance?.let { stringResource(R.string.weather_rain_line, it) },
    ).take(3)
    Column(Modifier.padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { MetroText(it, size = 19.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("weather:detail")) }
    }
}


/** The next hours: as many as the tile has room for, the hour over the temperature. */
@Composable
internal fun HoursFace(weather: WeatherData, width: Dp) {
    val fontScale = LocalDensity.current.fontScale
    val shown = weather.hours.drop(1).filterIndexed { i, _ -> i % 2 == 1 }.take(((width - 20.dp) / (56.dp * fontScale + 10.dp)).toInt().coerceIn(2, 5))
    val clock = remember { java.time.format.DateTimeFormatter.ofPattern("H") }
    Row(Modifier.fillMaxHeight().padding(start = FACE_MARGIN, top = FACE_MARGIN, bottom = NAME_ROOM), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { hour ->
            Column {
                MetroText(hour.time.format(clock) + "h", size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.tileContent.copy(alpha = 0.85f), modifier = Modifier.testTag("weather:hour"))
                MetroText("${hour.temperature}°", size = 20.sp)
            }
        }
    }
}


/** The weather tile: the temperature in large type with the weather under it, and on the other face the next days. */
@Composable
internal fun WeatherFace(item: AppItem, size: TileSize, width: Dp, height: Dp, weather: WeatherData?, seed: Int) {
    if (weather == null) {
        StaticFace(item, size, width, height)
        return
    }
    val withDays = size != TileSize.Small && weather.days.size >= 2
    val blocks = LocalTileFeatures.current.weatherBlocks
    val withDetails = size != TileSize.Small && !weather.details.isEmpty && WeatherBlock.Details.code in blocks
    val withHours = size != TileSize.Small && weather.hours.size >= 3 && WeatherBlock.Hourly.code in blocks
    // Only when it rains or is about to: a face that says "no rain" every time would be a face nobody needs.
    val outlook = if (size != TileSize.Small && WeatherBlock.Rain.code in blocks) {
        OpenMeteo.rainOutlook(weather.condition, weather.hours)?.takeIf { it.raining || it.hours != null }
    } else {
        null
    }
    val withAir = size != TileSize.Small && WeatherBlock.Air.code in blocks && weather.air?.aqi != null
    // The faces after the first, in order: the rain, the next hours, the next days, the details, the air.
    val others = listOfNotNull(
        if (outlook != null) 'r' else null, if (withHours) 'h' else null, if (withDays) 'd' else null,
        if (withDetails) 'x' else null, if (withAir) 'a' else null,
    )
    FlipContainer(1 + others.size, seed) { face ->
        Box(Modifier.fillMaxSize()) {
            val kind = if (face == 0) 'n' else others[(face - 1) % others.size]
            if (kind == 'x') {
                WeatherDetailsFace(weather)
            } else if (kind == 'r' && outlook != null) {
                MetroText(
                    rainOutlookText(outlook), size = 20.sp, weight = FontWeight.Normal, maxLines = 4, lineHeight = 24.sp,
                    modifier = Modifier.padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN, bottom = NAME_ROOM).testTag("weather:rain-face"),
                )
            } else if (kind == 'a') {
                val aqi = weather.air?.aqi ?: 0
                Column(Modifier.padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN)) {
                    MetroText(stringResource(R.string.weather_air_title), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.tileContent.copy(alpha = 0.85f))
                    MetroText("$aqi", size = 34.sp, modifier = Modifier.testTag("weather:air-face"))
                    MetroText(stringArrayResource(R.array.aqi_levels)[OpenMeteo.aqiLevel(aqi)], size = 16.sp, weight = FontWeight.Normal, maxLines = 1)
                }
            } else if (kind == 'h') {
                HoursFace(weather, width)
            } else if (kind == 'n') {
                val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.46f else 0.38f).toSp() }
                Column(Modifier.align(Alignment.TopStart).padding(start = FACE_MARGIN, top = 2.dp)) {
                    MetroText("${weather.temperature}°", size = digits)
                    if (size != TileSize.Small) {
                        MetroText(
                            stringResource(weather.condition.label),
                            size = 16.sp, weight = FontWeight.Normal, modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            } else {
                // As many days as fit at the size of the text the user chose: a day is two lines tall and about 70 dp wide.
                val fontScale = LocalDensity.current.fontScale
                val shown = weather.days.drop(1).take(
                    if (size == TileSize.Wide) {
                        ((width - 20.dp) / (70.dp * fontScale + 18.dp)).toInt().coerceIn(1, 3)
                    } else {
                        ((height - NAME_ROOM - 8.dp) / (46.dp * fontScale)).toInt().coerceIn(1, 2)
                    },
                )
                val rows: @Composable (DayForecast) -> Unit = { day ->
                    Column(Modifier.padding(end = 18.dp)) {
                        MetroText(
                            day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.tileContent.copy(alpha = 0.85f),
                        )
                        MetroText("${day.high}° ${day.low}°", size = 20.sp)
                    }
                }
                // Above the name of the tile, in the room that is left: what does not fit gets smaller, it never runs over the name.
                if (size == TileSize.Wide) {
                    Row(Modifier.align(Alignment.TopStart).fillMaxHeight().padding(start = FACE_MARGIN, top = FACE_MARGIN, bottom = NAME_ROOM)) { shown.forEach { rows(it) } }
                } else {
                    Column(Modifier.align(Alignment.TopStart).fillMaxHeight().padding(start = FACE_MARGIN, top = FACE_MARGIN, bottom = NAME_ROOM)) { shown.forEach { rows(it) } }
                }
            }
            if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}
