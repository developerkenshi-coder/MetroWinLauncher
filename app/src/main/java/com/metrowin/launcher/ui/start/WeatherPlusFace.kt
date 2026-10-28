package com.metrowin.launcher.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.metrowin.launcher.data.OpenMeteo
import com.metrowin.launcher.data.WeatherBlock
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.weather.rainOutlookText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The full weather tile, medium and wide only: the temperature and the sky, today's high and low, and then as many lines of
 * detail as the tile has room for (what is felt, wind, humidity, rain, UV, sun, air), and on the wide one the next days too.
 * The parts the user switched off on the weather page stay off here.
 */
@Composable
internal fun WeatherPlusFace(item: AppItem, size: TileSize, width: Dp, height: Dp, weather: WeatherData?) {
    if (weather == null || size == TileSize.Small) {
        StaticFace(item, size, width, height)
        return
    }
    val blocks = LocalTileFeatures.current.weatherBlocks
    val density = LocalDensity.current
    val unit = stringResource(if (weather.fahrenheit) R.string.unit_mph else R.string.unit_kmh)
    val clock = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val details = weather.details
    val today = weather.days.firstOrNull()
    val levels = stringArrayResource(R.array.aqi_levels)

    val outlook = if (WeatherBlock.Rain.code in blocks) OpenMeteo.rainOutlook(weather.condition, weather.hours)?.takeIf { it.raining || it.hours != null } else null
    val air = weather.air?.aqi?.takeIf { WeatherBlock.Air.code in blocks }
    // In the order they matter; the tile takes from the top as many as fit.
    val lines = buildList {
        if (outlook != null) add(rainOutlookText(outlook))
        if (WeatherBlock.Details.code in blocks) {
            details.feelsLike?.let { add(stringResource(R.string.weather_feels_like, it)) }
            details.wind?.let { add(stringResource(R.string.weather_wind_line, it, unit)) }
            details.humidity?.let { add(stringResource(R.string.weather_humidity_line, it)) }
            details.rainChance?.let { add(stringResource(R.string.weather_rain_line, it)) }
            details.uvIndex?.let { add(stringResource(R.string.weatherplus_uv, it)) }
            val rise = details.sunrise
            val set = details.sunset
            if (rise != null && set != null) add(stringResource(R.string.weatherplus_sun, rise.format(clock), set.format(clock)))
        }
        if (air != null) add(stringResource(R.string.weatherplus_air, air, levels[OpenMeteo.aqiLevel(air)]))
    }

    val room = height - NAME_ROOM - 10.dp
    val lineHeight = 19.dp * density.fontScale
    val ink = LocalMetroColors.current.tileContent
    val range = today?.let { stringResource(R.string.weatherplus_range, it.high, it.low) }

    @Composable
    fun Headline(modifier: Modifier = Modifier) {
        val digits = with(density) { (height * if (size == TileSize.Wide) 0.3f else 0.24f).toSp() }
        Column(modifier) {
            MetroText("${weather.temperature}°", size = digits, maxLines = 1, modifier = Modifier.testTag("weatherplus:temp"))
            MetroText(stringResource(weather.condition.label), size = 15.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.testTag("weatherplus:condition"))
            if (range != null) MetroText(range, size = 15.sp, weight = FontWeight.Normal, color = ink.copy(alpha = 0.85f), maxLines = 1, modifier = Modifier.testTag("weatherplus:range"))
        }
    }

    @Composable
    fun Details(count: Int, modifier: Modifier = Modifier) {
        Column(modifier) {
            lines.take(count).forEach {
                MetroText(it, size = 14.sp, weight = FontWeight.Normal, maxLines = 1, minScale = 0.7f, modifier = Modifier.testTag("weatherplus:line"))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (size == TileSize.Wide) {
            val days = weather.days.drop(1).take(3)
            Row(Modifier.fillMaxSize().padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = NAME_ROOM), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Headline(Modifier.weight(1f))
                Details((room / lineHeight).toInt().coerceIn(1, lines.size.coerceAtLeast(1)), Modifier.weight(1.5f))
                if (days.isNotEmpty()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        days.take((room / (lineHeight * 2)).toInt().coerceIn(1, 3)).forEach { day ->
                            MetroText(
                                "${day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${day.high}° ${day.low}°",
                                size = 14.sp, weight = FontWeight.Normal, maxLines = 1, minScale = 0.7f, modifier = Modifier.testTag("weatherplus:day"),
                            )
                        }
                    }
                }
            }
        } else {
            val headline = height * 0.24f * 1.15f + lineHeight * 2
            Column(Modifier.align(Alignment.TopStart).fillMaxSize().padding(start = 10.dp, top = 6.dp, end = 8.dp, bottom = NAME_ROOM)) {
                Headline()
                Details(((room - headline) / lineHeight).toInt().coerceIn(0, lines.size))
            }
        }
        TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}
