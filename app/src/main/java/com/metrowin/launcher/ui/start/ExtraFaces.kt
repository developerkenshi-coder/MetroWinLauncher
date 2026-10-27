package com.metrowin.launcher.ui.start

import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import com.metrowin.launcher.data.ClockHands
import com.metrowin.launcher.data.ParkedCar
import androidx.compose.foundation.layout.width
import com.metrowin.launcher.data.Steps
import com.metrowin.launcher.data.WeatherBlock
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import java.util.Locale
import java.time.temporal.WeekFields
import java.time.format.TextStyle
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import java.time.LocalDate
import java.time.Duration
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.notifications.MediaSessions
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.BatteryInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.components.AppArt
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** The steps of today in large light type. A small tile has the number and nothing else. */
@Composable
internal fun StepsFace(item: AppItem, size: TileSize, width: Dp, height: Dp, steps: Int?, goal: Int = 0) {
    if (steps == null) {
        StaticFace(item, size, width, height)
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            MetroText(steps.toString(), size = if (size == TileSize.Small) 22.sp else 52.sp, maxLines = 1, modifier = Modifier.testTag("steps:count"))
            if (size != TileSize.Small) MetroText(stringResource(R.string.steps_today), size = 15.sp, weight = FontWeight.Normal)
            if (goal > 0 && size != TileSize.Small) {
                val ink = LocalMetroColors.current.tileContent
                Box(Modifier.padding(top = 6.dp).width(width * 0.5f).height(4.dp).background(ink.copy(alpha = 0.3f)).testTag("steps:goal")) {
                    Box(Modifier.fillMaxWidth((steps.toFloat() / goal).coerceIn(0f, 1f)).height(4.dp).background(ink))
                }
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** The last photo taken, with the name of the tile over it. Without a photo (or the permission) it is the camera mark. */
@Composable
internal fun CameraFace(item: AppItem, size: TileSize, width: Dp, height: Dp, photo: ImageBitmap?) {
    if (photo == null) {
        StaticFace(item, size, width, height)
        return
    }
    Box(Modifier.fillMaxSize()) {
        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().testTag("camera:photo"))
        if (size != TileSize.Small) PhotoLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** The mark of a tile and, under it, a line of what it holds: the recordings, the cards, the street. */
@Composable
internal fun NoteLineFace(item: AppItem, size: TileSize, width: Dp, height: Dp, line: String?, tag: String) {
    if (line == null || size == TileSize.Small) {
        StaticFace(item, size, width, height)
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = NAME_ROOM), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppArt(item, minOf(width, height) * 0.3f)
            MetroText(
                line, size = 14.sp, weight = FontWeight.Normal, maxLines = if (size == TileSize.Wide) 2 else 3, lineHeight = 18.sp,
                color = LocalMetroColors.current.tileContent.copy(alpha = 0.9f), modifier = Modifier.testTag(tag),
            )
        }
        TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** The usual face of a person or a site: their picture when there is one, the mark otherwise. */
@Composable
internal fun PinnedBase(item: AppItem, size: TileSize, width: Dp, height: Dp) {
    if (item.photoPath != null) PhotoShortcutFace(item, size, width, height) else StaticFace(item, size, width, height)
}

/** How many recordings: "3 recordings". */
@Composable
internal fun recordingsLine(count: Int): String? = if (count == 0) null else pluralStringResource(R.plurals.recordings_count, count, count)

@Composable
internal fun cardsLine(count: Int): String? = if (count == 0) null else pluralStringResource(R.plurals.wallet_count, count, count)

/**
 * A person or a site that has news: the usual face, then (turning over) the latest message of the person or the newest headline
 * of the site. Without news it is the usual face.
 */
@Composable
internal fun LinkFace(item: AppItem, size: TileSize, width: Dp, height: Dp, news: String?, seed: Int, base: @Composable () -> Unit) {
    if (news.isNullOrBlank() || size == TileSize.Small) {
        base()
        return
    }
    FlipContainer(2, seed) { face ->
        if (face == 0) {
            base()
        } else {
            Box(Modifier.fillMaxSize()) {
                MetroText(
                    news, size = 16.sp, weight = FontWeight.Normal, maxLines = if (size == TileSize.Wide) 3 else 5, lineHeight = 20.sp,
                    modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = NAME_ROOM).testTag("link:news"),
                )
                TileLabel(item.label, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/** Which of the extras of the live tiles are on (see the settings): the buttons of the Music tile, the time left of the Battery tile, the agenda. */
@Immutable
data class TileFeatures(
    val musicControls: Boolean = true,
    val batteryEstimate: Boolean = true,
    val calendarAgenda: Boolean = true,
    /** The parts of the forecast the weather tile may turn to (codes of [WeatherBlock]). */
    val weatherBlocks: Set<String> = WeatherBlock.DEFAULT,
    /** The goal for the steps of a day, 0 for none. */
    val stepGoal: Int = Steps.GOAL,
    /** An app tile takes the colour of its icon. */
    val iconColors: Boolean = false,
)

val LocalTileFeatures = staticCompositionLocalOf { TileFeatures() }

/** A collage: the pictures side by side, cut square, as many as the size has room for. The first one changes with [seed], so tiles differ. */
@Composable
internal fun CollectionFace(item: AppItem, photos: List<ImageBitmap>, size: TileSize, seed: Int) {
    val columns = if (size == TileSize.Medium) 2 else 4
    val rows = if (size == TileSize.Large) 4 else 2
    val start = remember(seed, photos.size) { seed.mod(photos.size) }
    Box(Modifier.fillMaxSize().testTag("collection:face")) {
        Column(Modifier.fillMaxSize()) {
            repeat(rows) { r ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    repeat(columns) { c ->
                        Image(
                            photos[(start + r * columns + c) % photos.size], contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(1.dp),
                        )
                    }
                }
            }
        }
        PhotoLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** A switch of the Action Center on Start: its mark, and under it whether it is on. A small tile shows the mark dim when it is off. */
@Composable
internal fun ToggleFace(item: AppItem, size: TileSize, width: Dp, height: Dp, on: Boolean?) {
    Box(Modifier.fillMaxSize().testTag("toggle:face")) {
        Column(Modifier.align(Alignment.Center).padding(bottom = if (size == TileSize.Small) 0.dp else NAME_ROOM / 2), horizontalAlignment = Alignment.CenterHorizontally) {
            AppArt(item, minOf(width, height) * if (size == TileSize.Small) 0.5f else 0.36f, Modifier.graphicsLayer { alpha = if (on == false) 0.55f else 1f })
            if (size != TileSize.Small && on != null) {
                MetroText(stringResource(if (on) R.string.toggle_on else R.string.toggle_off), size = 16.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("toggle:state"))
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** When the sun rises and sets today, and how long the day is; the mark when the weather has no times (no city yet). */
@Composable
internal fun SunFace(item: AppItem, size: TileSize, width: Dp, height: Dp, weather: WeatherData?) {
    val rise = weather?.details?.sunrise
    val set = weather?.details?.sunset
    if (rise == null || set == null || size == TileSize.Small) {
        StaticFace(item, size, width, height)
        return
    }
    val clock = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val minutes = Duration.between(rise, set).toMinutes().toInt().coerceAtLeast(0)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = NAME_ROOM), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MetroText(stringResource(R.string.weather_sunrise), size = 13.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.tileContent.copy(alpha = 0.85f))
            MetroText(rise.format(clock), size = 26.sp, maxLines = 1, modifier = Modifier.testTag("sun:rise"))
            MetroText(stringResource(R.string.weather_sunset), size = 13.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.tileContent.copy(alpha = 0.85f))
            MetroText(set.format(clock), size = 26.sp, maxLines = 1, modifier = Modifier.testTag("sun:set"))
            if (size != TileSize.Medium) MetroText(stringResource(R.string.sun_day_length, BatteryInfo.duration(minutes)), size = 14.sp, weight = FontWeight.Normal)
        }
        TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

private enum class Transport { Previous, Play, Pause, Next }

/** Previous, play or pause, next: sent to the music that plays in any app (the same control the lock screen uses). */
@Composable
internal fun MusicControls(playing: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun controller() = MediaSessions.best(MediaSessions.active(context))
    Row(modifier.testTag("music:controls"), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        TransportButton(Transport.Previous, "music:prev") { controller()?.transportControls?.skipToPrevious() }
        TransportButton(if (playing) Transport.Pause else Transport.Play, "music:toggle") {
            controller()?.let { c -> if (playing) c.transportControls.pause() else c.transportControls.play() }
        }
        TransportButton(Transport.Next, "music:next") { controller()?.transportControls?.skipToNext() }
    }
}

@Composable
private fun TransportButton(kind: Transport, tag: String, onClick: () -> Unit) {
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.size(40.dp).testTag(tag).metroPress(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            fun triangle(left: Float, pointsRight: Boolean) = Path().apply {
                if (pointsRight) {
                    moveTo(left, 0f); lineTo(left + w * 0.5f, h / 2); lineTo(left, h)
                } else {
                    moveTo(left + w * 0.5f, 0f); lineTo(left, h / 2); lineTo(left + w * 0.5f, h)
                }
                close()
            }
            when (kind) {
                Transport.Play -> drawPath(triangle(w * 0.2f, true), ink)
                Transport.Pause -> {
                    drawRect(ink, Offset(w * 0.15f, 0f), Size(w * 0.25f, h))
                    drawRect(ink, Offset(w * 0.6f, 0f), Size(w * 0.25f, h))
                }
                Transport.Previous -> {
                    drawRect(ink, Offset(0f, 0f), Size(w * 0.12f, h))
                    drawPath(triangle(w * 0.1f, false), ink)
                    drawPath(triangle(w * 0.5f, false), ink)
                }
                Transport.Next -> {
                    drawRect(ink, Offset(w * 0.88f, 0f), Size(w * 0.12f, h))
                    drawPath(triangle(w * 0.0f, true), ink)
                    drawPath(triangle(w * 0.4f, true), ink)
                }
            }
        }
    }
}

/** The next events one under the other: when, then what. */
@Composable
internal fun AgendaList(events: List<LiveEvent>, today: LocalDate, modifier: Modifier = Modifier) {
    Column(modifier.testTag("calendar:agenda"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        events.forEach { event ->
            Column {
                MetroText(describeWhen(event, today), size = 12.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.tileContent.copy(alpha = 0.85f), maxLines = 1)
                MetroText(event.title, size = 16.sp, maxLines = 1, weight = FontWeight.Normal)
            }
        }
    }
}

/** The month as a grid, the day of today lit, and the next events under it, for the large Calendar tile. */
@Composable
internal fun LargeCalendar(item: AppItem, events: List<LiveEvent>, today: LocalDate) {
    val locale = Locale.getDefault()
    val first = WeekFields.of(locale).firstDayOfWeek
    val month = YearMonth.from(today)
    val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val weeks = (lead + month.lengthOfMonth() + 6) / 7
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.fillMaxSize().testTag("calendar:month")) {
        Column(Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = NAME_ROOM), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MetroText(month.month.getDisplayName(TextStyle.FULL, locale) + " " + month.year, size = 20.sp, maxLines = 1)
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { i ->
                    MetroText(
                        first.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale), size = 12.sp, weight = FontWeight.Normal,
                        color = ink.copy(alpha = 0.8f), modifier = Modifier.weight(1f), maxLines = 1, textAlign = TextAlign.Center,
                    )
                }
            }
            repeat(weeks) { w ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { d ->
                        val day = w * 7 + d - lead + 1
                        val shown = day in 1..month.lengthOfMonth()
                        Box(
                            Modifier.weight(1f).height(22.dp).then(if (shown && day == today.dayOfMonth) Modifier.background(ink.copy(alpha = 0.3f)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (shown) MetroText(day.toString(), size = 13.sp, weight = FontWeight.Normal, maxLines = 1)
                        }
                    }
                }
            }
            if (events.isNotEmpty()) AgendaList(events.take(if (weeks > 5) 2 else 3), today, Modifier.padding(top = 6.dp))
        }
        TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** "Via Roma 1, Torino · 2 hours ago" for the face of the parking tile. */
@Composable
internal fun parkedLine(car: ParkedCar): String {
    val ago = android.text.format.DateUtils.getRelativeTimeSpanString(car.savedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
    return listOfNotNull(car.address?.substringBefore(','), ago).joinToString(" · ")
}

/** A clock with hands, drawn in the colour of the text of the tile, set again every minute while the launcher is on screen. */
@Composable
internal fun AnalogClockFace(item: AppItem, size: TileSize, width: Dp, height: Dp) {
    var now by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedWhileStarted(Unit) {
        while (true) {
            now = java.time.LocalTime.now()
            kotlinx.coroutines.delay(60_000L - (now.second * 1000L + now.nano / 1_000_000L) + 50L)
        }
    }
    val ink = LocalMetroColors.current.tileContent
    val (hour, minute) = ClockHands.angles(now)
    Box(Modifier.fillMaxSize()) {
        val face = minOf(width, height) * if (size == TileSize.Small) 0.78f else 0.7f
        androidx.compose.foundation.Canvas(Modifier.align(Alignment.Center).size(face).testTag("analogclock:face")) {
            val r = this.size.minDimension / 2f
            drawCircle(ink, r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            for (tick in 0 until 12) {
                val a = Math.toRadians(tick * 30.0 - 90.0)
                val inner = if (tick % 3 == 0) r * 0.78f else r * 0.88f
                drawLine(
                    ink, center + androidx.compose.ui.geometry.Offset((kotlin.math.cos(a) * inner).toFloat(), (kotlin.math.sin(a) * inner).toFloat()),
                    center + androidx.compose.ui.geometry.Offset((kotlin.math.cos(a) * r * 0.95f).toFloat(), (kotlin.math.sin(a) * r * 0.95f).toFloat()),
                    strokeWidth = (if (tick % 3 == 0) 2.5f else 1.5f).dp.toPx(),
                )
            }
            fun hand(degrees: Float, length: Float, thickness: Float) {
                val a = Math.toRadians(degrees - 90.0)
                drawLine(
                    ink, center, center + androidx.compose.ui.geometry.Offset((kotlin.math.cos(a) * r * length).toFloat(), (kotlin.math.sin(a) * r * length).toFloat()),
                    strokeWidth = thickness.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            hand(hour, 0.5f, 4f)
            hand(minute, 0.8f, 3f)
            drawCircle(ink, 3.dp.toPx())
        }
        if (size != TileSize.Small && size != TileSize.Medium) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}
