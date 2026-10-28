package com.metrowin.launcher.ui.start

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BootCount
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.Finance
import com.metrowin.launcher.data.FinanceData
import com.metrowin.launcher.data.FinanceItems
import com.metrowin.launcher.data.LivePerson
import com.metrowin.launcher.data.NewsAge
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.data.TimerFormat
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.StopwatchState
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TimerState
import com.metrowin.launcher.model.TimerStatus
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay

/** How many headlines the News tile turns through. */
private const val NEWS_FACES = 5

/** "Today 18:00", "Tomorrow 9:30 AM", "Mon 26 Oct 18:00": a moment as a tile or a page says it, in the language and the clock of the phone. */
@Composable
fun whenText(millis: Long, now: Long = System.currentTimeMillis()): String {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val is24 = DateFormat.is24HourFormat(context)
    val meridiem = ClockFormat.meridiem(at.toLocalTime(), is24, locale)
    val time = ClockFormat.time(at.toLocalTime(), is24, locale) + if (meridiem.isEmpty()) "" else " $meridiem"
    val day = when (at.toLocalDate()) {
        today -> stringResource(R.string.live_today)
        today.plusDays(1) -> stringResource(R.string.live_tomorrow)
        else -> ClockFormat.date(at.toLocalDate(), locale)
    }
    return "$day $time"
}

/** "12 min", "3 h", "2 d" or "now": how long ago a headline came out. */
@Composable
fun ageText(publishedAt: Long, now: Long = System.currentTimeMillis()): String? {
    val age = NewsAge.of(publishedAt, now) ?: return null
    return when (age.unit) {
        NewsAge.Unit.Now -> stringResource(R.string.news_age_now)
        NewsAge.Unit.Minutes -> stringResource(R.string.news_age_minutes, age.value)
        NewsAge.Unit.Hours -> stringResource(R.string.news_age_hours, age.value)
        NewsAge.Unit.Days -> stringResource(R.string.news_age_days, age.value)
    }
}

/** A place: the pin, the address under it and the name in the corner. A touch starts the navigation there. */
@Composable
internal fun PlaceFace(item: AppItem, size: TileSize, width: Dp, height: Dp, address: String) {
    if (size == TileSize.Small) {
        StaticFace(item, size, width, height)
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = NAME_ROOM), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GlyphIcon(item.glyph ?: Glyph.Maps, minOf(width, height) * 0.3f)
            MetroText(
                address, size = 14.sp, weight = FontWeight.Normal, maxLines = if (size == TileSize.Wide) 2 else 3, lineHeight = 18.sp,
                color = LocalMetroColors.current.tileContent.copy(alpha = 0.9f), modifier = Modifier.testTag("place:address"),
            )
        }
        TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** The photos of an album, turning over one after the other, with the name of the album over them. */
@Composable
internal fun AlbumFace(item: AppItem, size: TileSize, width: Dp, height: Dp, photos: List<ImageBitmap>, seed: Int) {
    FlipContainer(if (size == TileSize.Small || photos.isEmpty()) 1 else 1 + photos.size, seed) { face ->
        if (face == 0 || photos.isEmpty()) {
            StaticFace(item, size, width, height)
        } else {
            Box(Modifier.fillMaxSize()) {
                Image(photos[(face - 1) % photos.size], contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
                PhotoLabel(item.label, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/** The faces of the people of a group of contacts, one after the other, with the name of the person over each. */
@Composable
internal fun GroupFace(item: AppItem, size: TileSize, width: Dp, height: Dp, people: List<LivePerson>, seed: Int) {
    FlipContainer(if (size == TileSize.Small || people.isEmpty()) 1 else 1 + people.size, seed) { face ->
        if (face == 0 || people.isEmpty()) {
            StaticFace(item, size, width, height)
        } else {
            val person = people[(face - 1) % people.size]
            Box(Modifier.fillMaxSize()) {
                Image(person.photo, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
                PhotoLabel(person.name, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/**
 * A timer: what is left in large type, counting down while the launcher is on screen. Nothing runs when it is not: the end is a
 * moment kept in the tile, and the number is worked out from the clock whenever somebody looks; the alarm that rings is one of
 * Android's. With the timer stopped the tile is still.
 */
@Composable
internal fun TimerFace(item: AppItem, size: TileSize, width: Dp, height: Dp, state: TimerState) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedWhileStarted(state) {
        now = System.currentTimeMillis()
        while (state.status(now) == TimerStatus.Running) {
            delay(TimerFormat.untilNextCountdownTick(state.remaining(now)))
            now = System.currentTimeMillis()
        }
    }
    val status = state.status(now)
    if (status == TimerStatus.Idle && size == TileSize.Small) {
        StaticFace(item, size, width, height)
        return
    }
    val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.24f else 0.26f).toSp() }
    Box(Modifier.fillMaxSize()) {
        // Centred in what the name at the bottom leaves free.
        Column(
            Modifier.align(Alignment.Center).padding(bottom = if (size == TileSize.Small) 0.dp else NAME_ROOM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MetroText(
                TimerFormat.countdown(if (status == TimerStatus.Idle) state.durationMs else state.remaining(now)),
                size = digits, maxLines = 1, modifier = Modifier.testTag("timer:time"),
            )
            if (size != TileSize.Small) {
                val line = when (status) {
                    TimerStatus.Idle -> stringResource(R.string.timer_tap_start)
                    TimerStatus.Paused -> stringResource(R.string.timer_paused)
                    TimerStatus.Finished -> stringResource(R.string.timer_done)
                    TimerStatus.Running -> null
                }
                if (line != null) MetroText(line, size = 14.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp).testTag("timer:status"))
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** A stopwatch: the time in large type, changing every second while it runs and the launcher is on screen. */
@Composable
internal fun StopwatchFace(item: AppItem, size: TileSize, width: Dp, height: Dp, state: StopwatchState) {
    val context = LocalContext.current
    val boot = remember { BootCount.read(context) }
    var elapsed by remember { mutableLongStateOf(state.elapsed(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot)) }
    LaunchedWhileStarted(state) {
        while (true) {
            elapsed = state.elapsed(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot)
            if (!state.running) break
            delay(TimerFormat.untilNextStopwatchTick(elapsed, 1_000L))
        }
    }
    if (!state.running && elapsed == 0L && size == TileSize.Small) {
        StaticFace(item, size, width, height)
        return
    }
    val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.24f else 0.26f).toSp() }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center).padding(bottom = if (size == TileSize.Small) 0.dp else NAME_ROOM),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MetroText(TimerFormat.stopwatch(elapsed), size = digits, maxLines = 1, modifier = Modifier.testTag("stopwatch:time"))
            if (size != TileSize.Small && state.laps.isNotEmpty()) {
                MetroText(
                    pluralStringResource(R.plurals.stopwatch_laps, state.laps.size, state.laps.size), size = 14.sp, weight = FontWeight.Normal,
                    maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp).testTag("stopwatch:laps"),
                )
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** The top headlines of the news source, one on each face: the headline, and under it who published it and how long ago. */
@Composable
internal fun NewsFace(item: AppItem, size: TileSize, width: Dp, height: Dp, news: NewsData?, seed: Int) {
    val headlines = news?.items.orEmpty().take(NEWS_FACES)
    if (headlines.isEmpty() || size == TileSize.Small) {
        StaticFace(item, size, width, height)
        return
    }
    val ink = LocalMetroColors.current.tileContent
    FlipContainer(headlines.size, seed) { face ->
        val headline = headlines[face % headlines.size]
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = NAME_ROOM)) {
                MetroText(
                    headline.title, size = 16.sp, weight = FontWeight.Normal, maxLines = if (size == TileSize.Wide) 3 else 5, lineHeight = 20.sp,
                    modifier = Modifier.testTag("news:headline"),
                )
                Spacer(Modifier.weight(1f))
                val age = ageText(headline.publishedAt)
                val line = listOfNotNull(headline.source.takeIf { it.isNotBlank() }, age).joinToString(" · ")
                if (line.isNotEmpty()) MetroText(line, size = 12.sp, weight = FontWeight.Normal, maxLines = 1, color = ink.copy(alpha = 0.8f))
            }
            TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** The currencies and coins that are followed: what each is worth, and an arrow for the way it moved since yesterday. */
@Composable
internal fun FinanceFace(item: AppItem, size: TileSize, width: Dp, height: Dp, finance: FinanceData?) {
    val quotes = finance?.quotes.orEmpty()
    if (quotes.isEmpty()) {
        StaticFace(item, size, width, height)
        return
    }
    val locale = Locale.getDefault()
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.fillMaxSize()) {
        if (size == TileSize.Small) {
            val first = quotes.first()
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                MetroText(FinanceItems.symbol(first.item), size = 14.sp, weight = FontWeight.Normal)
                MetroText(Finance.format(first.value, locale), size = 18.sp, maxLines = 1, modifier = Modifier.testTag("finance:value"))
            }
        } else {
            Column(
                Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = NAME_ROOM).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                quotes.take(if (size == TileSize.Wide) 4 else 3).forEach { quote ->
                    Row(Modifier.testTag("finance:row"), verticalAlignment = Alignment.CenterVertically) {
                        MetroText(FinanceItems.symbol(quote.item), size = 15.sp, weight = FontWeight.Normal, modifier = Modifier.weight(1f))
                        MetroText(Finance.format(quote.value, locale), size = 17.sp, maxLines = 1)
                        val trend = Finance.trend(quote.changePercent)
                        val change = if (size == TileSize.Wide) Finance.formatChange(quote.changePercent, locale).orEmpty() else ""
                        MetroText(
                            arrow(trend) + change, size = 12.sp, weight = FontWeight.Normal, maxLines = 1, color = ink.copy(alpha = 0.85f),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** A mark for the way a value moved: up, down, or still. */
internal fun arrow(trend: Int): String = when {
    trend > 0 -> "▲"
    trend < 0 -> "▼"
    else -> "–"
}

/** The reminders that wait: how many, and the next one with its time. With none the tile is just the bell. */
@Composable
internal fun RemindersFace(item: AppItem, size: TileSize, width: Dp, height: Dp, reminders: List<Reminder>) {
    val now = remember(reminders) { System.currentTimeMillis() }
    val upcoming = remember(reminders) { Reminders.upcoming(reminders, now) }
    val next = upcoming.firstOrNull()
    if (next == null) {
        StaticFace(item, size, width, height)
        return
    }
    val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.34f else 0.22f).toSp() }
    Box(Modifier.fillMaxSize()) {
        if (size == TileSize.Small) {
            MetroText(upcoming.size.toString(), size = digits, modifier = Modifier.align(Alignment.Center).testTag("reminders:count"))
        } else {
            Column(Modifier.padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = NAME_ROOM)) {
                MetroText(upcoming.size.toString(), size = digits, modifier = Modifier.testTag("reminders:count"))
                MetroText(
                    next.text, size = 15.sp, weight = FontWeight.Normal, maxLines = if (size == TileSize.Wide) 2 else 1, lineHeight = 19.sp,
                    modifier = Modifier.testTag("reminders:next"),
                )
                MetroText(
                    whenText(next.at, now), size = 13.sp, weight = FontWeight.Normal, maxLines = 1,
                    color = LocalMetroColors.current.tileContent.copy(alpha = 0.85f),
                )
            }
            TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}
