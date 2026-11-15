package com.metrowin.launcher.ui.start

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.PhotoCard
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.ThirdPartyTile
import com.metrowin.launcher.data.TileFeed
import com.metrowin.launcher.data.WeatherData
import com.metrowin.launcher.data.WorldCities
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TimerState
import com.metrowin.launcher.model.TimerStatus
import com.metrowin.launcher.notes.NoteStore
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.anim.LocalTapFeedback
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.shrinkToHeight
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** What the faces of an info tile ask the launcher to do: switch something on or off, open the forecast, read aloud. Set where the launcher is built. */
class InfoActions(
    val toggle: (QuickAction) -> Unit = {},
    val isOn: (QuickAction) -> Boolean? = { null },
    val openWeather: () -> Unit = {},
    val speak: (String) -> Unit = {},
)

val LocalInfoActions = staticCompositionLocalOf { InfoActions() }

/** The timers of Start (the tiles that are pinned), for the face that says which is running. */
val LocalTimers = staticCompositionLocalOf<List<TimerState>> { emptyList() }

/** The four switches a large info tile can hold. */
internal val TOGGLES = listOf(QuickAction.Flashlight, QuickAction.Wifi, QuickAction.QuietHours, QuickAction.Rotation)

private val Muted = 0.85f

/** A face of plain words: a small title, a big line, then lines under it, as many as the tile has room for; the name of the tile in the corner. */
@Composable
internal fun WordsFace(item: AppItem, size: TileSize, title: String?, big: String?, lines: List<String>, tag: String, corner: String = item.label) {
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(bottom = if (size == TileSize.Small) 0.dp else NAME_ROOM)) {
            Column(Modifier.shrinkToHeight(0.7f).padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN)) {
                if (title != null && size != TileSize.Small) MetroText(title, size = 14.sp, weight = FontWeight.Normal, color = ink.copy(alpha = Muted), maxLines = 1)
                if (big != null) MetroText(big, size = if (size == TileSize.Small) 22.sp else 30.sp, maxLines = 2, lineHeight = 34.sp, modifier = Modifier.testTag(tag))
                lines.take(if (size == TileSize.Small) 1 else if (size == TileSize.Large) 6 else 3).forEach {
                    MetroText(it, size = 16.sp, weight = FontWeight.Normal, maxLines = 2, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (size != TileSize.Small) TileLabel(corner, Modifier.align(Alignment.BottomStart))
    }
}

/** What waits: the missed calls and the mail not read. */
@Composable
internal fun WaitingFace(item: AppItem, size: TileSize, live: LiveTileData) {
    val lines = listOfNotNull(
        live.missedCalls.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.infot_missed, it, it) },
        live.unreadMail.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.infot_unread, it, it) },
    )
    WordsFace(item, size, stringResource(R.string.infot_waiting), lines.firstOrNull(), lines.drop(1), "info:waiting")
}

/** A timer that runs (or the one that rang), with what is left. */
@Composable
internal fun TimerStepFace(item: AppItem, size: TileSize, timers: List<TimerState>) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedWhileStarted(timers) {
        while (true) {
            now = System.currentTimeMillis()
            val left = timers.filter { it.status(now) == TimerStatus.Running }.minOfOrNull { it.remaining(now) } ?: return@LaunchedWhileStarted
            delay(com.metrowin.launcher.data.TimerFormat.untilNextCountdownTick(left))
        }
    }
    val live = timers.filter { it.status(now) == TimerStatus.Running || it.status(now) == TimerStatus.Paused }.sortedBy { it.remaining(now) }
    val lines = live.map { com.metrowin.launcher.data.TimerFormat.countdown(it.remaining(now)) }
    WordsFace(item, size, stringResource(R.string.infot_timer), lines.firstOrNull(), lines.drop(1), "info:timer")
}

/** Whether a timer is going (or paused) now: the timer face has something to say only then. */
internal fun timerGoing(timers: List<TimerState>, now: Long) = timers.any { it.status(now) == TimerStatus.Running || it.status(now) == TimerStatus.Paused }

/** The seven days from today, each with a dot when it has something on. */
@Composable
internal fun WeekFace(item: AppItem, size: TileSize, days: Set<Long>) {
    val today = rememberToday()
    val ink = LocalMetroColors.current.tileContent
    val locale = Locale.getDefault()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN, bottom = if (size == TileSize.Small) FACE_MARGIN else NAME_ROOM)) {
            if (size != TileSize.Small) MetroText(stringResource(R.string.infot_week), size = 14.sp, weight = FontWeight.Normal, color = ink.copy(alpha = Muted), maxLines = 1)
            Row(Modifier.fillMaxWidth().weight(1f).testTag("info:week")) {
                val count = if (size == TileSize.Small) 3 else 7
                repeat(count) { i ->
                    val day = today.plusDays(i.toLong())
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        MetroText(day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), size = 12.sp, weight = FontWeight.Normal, color = ink.copy(alpha = Muted), maxLines = 1)
                        MetroText(day.dayOfMonth.toString(), size = if (i == 0) 22.sp else 18.sp, maxLines = 1)
                        Box(Modifier.padding(top = 3.dp).size(6.dp).background(if (day.toEpochDay() in days) ink else Color.Transparent, CircleShape))
                    }
                }
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** The day as a line from 6 to 24 o'clock with a block for each thing on, and where it is now. */
@Composable
internal fun DayLineFace(item: AppItem, size: TileSize, events: List<LiveEvent>) {
    val now = rememberNowMinute()
    val ink = LocalMetroColors.current.tileContent
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val from = start + DAY_FROM_HOUR * HOUR_MS
    val span = (24 - DAY_FROM_HOUR) * HOUR_MS
    val next = events.firstOrNull { !it.isOver(now) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN, bottom = if (size == TileSize.Small) FACE_MARGIN else NAME_ROOM)) {
            if (size != TileSize.Small) MetroText(stringResource(R.string.infot_day), size = 14.sp, weight = FontWeight.Normal, color = ink.copy(alpha = Muted), maxLines = 1)
            Canvas(Modifier.fillMaxWidth().weight(1f).testTag("info:day")) {
                val barHeight = 18.dp.toPx()
                val top = (this.size.height - barHeight) / 2f
                drawRect(ink.copy(alpha = 0.25f), Offset(0f, top), Size(this.size.width, barHeight))
                events.forEach { event ->
                    val end = if (event.endMillis > 0L) event.endMillis else event.startMillis + HOUR_MS
                    val a = ((event.startMillis - from).toFloat() / span).coerceIn(0f, 1f)
                    val b = ((end - from).toFloat() / span).coerceIn(0f, 1f)
                    if (b > a) drawRect(ink.copy(alpha = 0.9f), Offset(a * this.size.width, top), Size((b - a) * this.size.width, barHeight))
                }
                val x = ((now - from).toFloat() / span).coerceIn(0f, 1f) * this.size.width
                drawLine(ink, Offset(x, top - 6.dp.toPx()), Offset(x, top + barHeight + 6.dp.toPx()), strokeWidth = 2.dp.toPx())
            }
            if (size != TileSize.Small) {
                MetroText(next?.let { eventWhen(it, rememberToday(), now) + " · " + it.title } ?: stringResource(R.string.infot_day_free), size = 14.sp, weight = FontWeight.Normal, maxLines = 1)
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

private const val DAY_FROM_HOUR = 6
private const val HOUR_MS = 60 * 60_000L

/** The temperature of the next hours as a line, with the chance of rain under it as bars. */
@Composable
internal fun WeatherChartFace(item: AppItem, size: TileSize, weather: WeatherData) {
    val ink = LocalMetroColors.current.tileContent
    val hours = weather.hours.take(24)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN, bottom = if (size == TileSize.Small) FACE_MARGIN else NAME_ROOM)) {
            if (size != TileSize.Small) {
                MetroText(
                    stringResource(R.string.infot_chart) + " · " + (hours.minOfOrNull { it.temperature } ?: weather.temperature) + "° / " + (hours.maxOfOrNull { it.temperature } ?: weather.temperature) + "°",
                    size = 14.sp, weight = FontWeight.Normal, color = ink.copy(alpha = Muted), maxLines = 1,
                )
            }
            Canvas(Modifier.fillMaxWidth().weight(1f).testTag("info:chart")) {
                if (hours.size < 2) return@Canvas
                val lo = hours.minOf { it.temperature }.toFloat()
                val hi = hours.maxOf { it.temperature }.toFloat().coerceAtLeast(lo + 1f)
                val step = this.size.width / (hours.size - 1)
                val area = this.size.height * 0.72f
                val line = Path()
                hours.forEachIndexed { i, h ->
                    val y = area - (h.temperature - lo) / (hi - lo) * area * 0.9f - area * 0.05f
                    if (i == 0) line.moveTo(0f, y) else line.lineTo(i * step, y)
                }
                drawPath(line, ink, style = Stroke(2.5.dp.toPx()))
                hours.forEachIndexed { i, h ->
                    val chance = (h.rainChance ?: 0) / 100f
                    if (chance > 0f) drawRect(ink.copy(alpha = 0.6f), Offset(i * step - step * 0.3f, this.size.height - chance * this.size.height * 0.25f), Size(step * 0.6f, chance * this.size.height * 0.25f))
                }
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** The advice the weather gives, as the string that says it, or null when it has none now. Pure but for the string ids. */
internal fun adviceOf(weather: WeatherData?): Int? {
    weather ?: return null
    if (com.metrowin.launcher.data.InfoPriority.rainSoon(weather)) return R.string.infot_advice_umbrella
    val celsius = if (weather.fahrenheit) (weather.temperature - 32) * 5 / 9 else weather.temperature
    val wind = weather.details.wind?.let { if (weather.fahrenheit) it * 16 / 10 else it }
    return when {
        celsius <= 5 -> R.string.infot_advice_cold
        celsius >= 29 -> R.string.infot_advice_hot
        wind != null && wind >= 40 -> R.string.infot_advice_wind
        else -> null
    }
}

@Composable
internal fun AdviceFace(item: AppItem, size: TileSize, weather: WeatherData?) {
    val advice = adviceOf(weather) ?: return StaticFace(item, size, 0.dp, 0.dp)
    WordsFace(item, size, stringResource(R.string.infot_advice), stringResource(advice), emptyList(), "info:advice")
}

/** A person from the contacts: the photo over the whole tile, the name on it. */
@Composable
internal fun PersonFace(item: AppItem, size: TileSize, person: com.metrowin.launcher.data.LivePerson) {
    Box(Modifier.fillMaxSize()) {
        Image(person.photo, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        PhotoLabel(person.name, Modifier.align(Alignment.BottomStart))
    }
}

/** A photo over the whole tile; a memory says how many years ago it was. */
@Composable
internal fun PhotoCardFace(item: AppItem, size: TileSize, card: PhotoCard, hidden: Boolean, share: Boolean = false) {
    val said = if (card.year > 0) {
        val years = (LocalDate.now().year - card.year).coerceAtLeast(1)
        pluralStringResource(R.plurals.infot_years_ago, years, years)
    } else {
        null
    }
    val describe = said ?: stringResource(R.string.infot_photo)
    Box(Modifier.fillMaxSize()) {
        Image(
            card.bitmap, contentDescription = describe, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (hidden) Modifier.background(Color.Black) else Modifier).testTag("info:photo"),
            alpha = if (hidden) 0f else 1f,
        )
        if (size != TileSize.Small) {
            PhotoLabel(said ?: item.label, Modifier.align(Alignment.BottomStart))
        } else if (said != null) {
            PhotoLabel(said, Modifier.align(Alignment.BottomStart))
        }
        // A memory can be sent with what it says: the chooser of Android asks who gets it, nothing leaves before that.
        if (share && said != null && !hidden && card.uri.isNotEmpty() && size != TileSize.Small) {
            val context = LocalContext.current
            Box(Modifier.align(Alignment.BottomEnd)) {
                EventChip(stringResource(R.string.infot_share), "info:share") { sharePhoto(context, card.uri, said) }
            }
        }
    }
}

internal fun sharePhoto(context: Context, uri: String, text: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, Uri.parse(uri)).putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** The last message that came in a chat, from the notifications that are up. Blank with "hide what is written" on. */
@Composable
internal fun ChatFace(item: AppItem, size: TileSize, hideText: Boolean) {
    val apps by NotificationHub.apps.collectAsState()
    val last = apps.filter { !it.ongoing && !it.groupSummary && (it.messages.isNotEmpty() || it.category == "msg") }.maxByOrNull { it.postTime }
    val sender = last?.messages?.lastOrNull()?.sender?.takeIf { it.isNotBlank() } ?: last?.conversationTitle ?: last?.title.orEmpty()
    val text = if (hideText) "" else last?.messages?.lastOrNull()?.text ?: last?.text.orEmpty()
    WordsFace(item, size, stringResource(R.string.infot_chat), sender, listOf(text).filter { it.isNotBlank() }, "info:chat")
}

/** Whether a message is up that the chat face can say. */
internal fun chatUp(): Boolean = NotificationHub.apps.value.any { !it.ongoing && !it.groupSummary && (it.messages.isNotEmpty() || it.category == "msg") }

/** What an app published to its tile (see TilePublishProvider), as the user chose which app. */
@Composable
internal fun PublishedFace(item: AppItem, size: TileSize, packageName: String) {
    val tiles by TileFeed.tiles.collectAsState()
    val tile: ThirdPartyTile? = tiles[packageName]
    WordsFace(item, size, null, tile?.title?.takeIf { it.isNotBlank() } ?: tile?.count?.takeIf { it > 0 }?.toString(), listOfNotNull(tile?.text?.takeIf { it.isNotBlank() }), "info:published")
}

internal fun publishedUp(packageName: String): Boolean = TileFeed.tiles.value[packageName]?.let { it.title.isNotBlank() || it.text.isNotBlank() || it.count > 0 } == true

/** The first lines of a note of the user. A note with the lock of the phone is never shown. */
@Composable
internal fun NoteTextFace(item: AppItem, size: TileSize, noteId: String) {
    val context = LocalContext.current
    val note by produceState<com.metrowin.launcher.notes.Note?>(null, noteId) {
        value = withContext(Dispatchers.IO) { runCatching { NoteStore(context).load().firstOrNull { it.id == noteId && !it.locked && it.deleted == 0L } }.getOrNull() }
    }
    val text = note?.text.orEmpty()
    WordsFace(item, size, note?.title?.takeIf { it.isNotBlank() }, text.lineSequence().firstOrNull { it.isNotBlank() }, text.lineSequence().filter { it.isNotBlank() }.drop(1).take(4).toList(), "info:note")
}

/** Four switches to touch: each has its own touch, so that the tile does not open an app. */
@Composable
internal fun TogglesFace(item: AppItem, size: TileSize) {
    val actions = LocalInfoActions.current
    val ink = LocalMetroColors.current.tileContent
    val feedback by rememberUpdatedState(LocalTapFeedback.current)
    var bump by remember { mutableLongStateOf(0L) }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.align(Alignment.Center).padding(bottom = NAME_ROOM / 2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TOGGLES.forEach { action ->
                val on = remember(bump, action) { actions.isOn(action) }
                val label = stringResource(action.label)
                Box(
                    Modifier
                        .size(52.dp)
                        .testTag("info:toggle:${action.id}")
                        .semantics { contentDescription = label }
                        .then(if (on == true) Modifier.background(ink) else Modifier.background(ink.copy(alpha = 0.25f)))
                        .pointerInput(action) {
                            detectTapGestures {
                                feedback()
                                actions.toggle(action)
                                bump = System.nanoTime()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(action.glyph, 30.dp, color = if (on == true) Color.Black else ink) }
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** Two or three time zones, a line each: the city and the time there. */
@Composable
internal fun ZonesFace(item: AppItem, size: TileSize, zones: List<String>, names: List<String>) {
    val context = LocalContext.current
    val minute = rememberNowMinute()
    val locale = Locale.getDefault()
    val is24 = LocalTileFeatures.current.clockHours.is24(android.text.format.DateFormat.is24HourFormat(context))
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN, bottom = if (size == TileSize.Small) FACE_MARGIN else NAME_ROOM), verticalArrangement = Arrangement.Center) {
            zones.take(if (size == TileSize.Small) 2 else 3).forEachIndexed { i, zone ->
                val id = runCatching { ZoneId.of(zone) }.getOrNull() ?: ZoneId.systemDefault()
                val there = WorldCities.timeIn(id, Instant.ofEpochMilli(minute), ZoneId.systemDefault())
                Row(Modifier.fillMaxWidth().testTag("info:zone:$i"), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    MetroText(names.getOrNull(i)?.takeIf { it.isNotBlank() } ?: zone.substringAfter('/').replace('_', ' '), size = 16.sp, weight = FontWeight.Normal, maxLines = 1, color = ink.copy(alpha = Muted), modifier = Modifier.weight(1f))
                    MetroText(ClockFormat.time(there.time, is24, locale) + ClockFormat.meridiem(there.time, is24, locale).let { if (it.isEmpty()) "" else " $it" }, size = if (size == TileSize.Small) 20.sp else 28.sp, maxLines = 1)
                }
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** The time here, big, and the time at home under it while away from home. */
@Composable
internal fun HomeAwayFace(item: AppItem, size: TileSize, home: String) {
    val context = LocalContext.current
    val minute = rememberNowMinute()
    val locale = Locale.getDefault()
    val is24 = LocalTileFeatures.current.clockHours.is24(android.text.format.DateFormat.is24HourFormat(context))
    val homeId = runCatching { ZoneId.of(home) }.getOrNull() ?: ZoneId.systemDefault()
    val here = WorldCities.timeIn(ZoneId.systemDefault(), Instant.ofEpochMilli(minute), ZoneId.systemDefault())
    val there = WorldCities.timeIn(homeId, Instant.ofEpochMilli(minute), ZoneId.systemDefault())
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            MetroText(ClockFormat.time(here.time, is24, locale), size = if (size == TileSize.Small) 30.sp else 48.sp, maxLines = 1, modifier = Modifier.testTag("info:here"))
            MetroText(
                stringResource(R.string.infot_zone_home, ClockFormat.time(there.time, is24, locale)),
                size = 16.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.testTag("info:home"),
            )
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** Buttons for a timer of 5, 10 and 15 minutes (the clock app starts it at once). */
@Composable
internal fun QuickTimers(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(modifier.padding(end = FACE_MARGIN, bottom = FACE_MARGIN), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(5, 10, 15).forEach { minutes ->
            EventChip(stringResource(R.string.infot_minutes_short, minutes), "info:timer:$minutes") { startTimer(context, minutes) }
        }
    }
}

internal fun startTimer(context: Context, minutes: Int) {
    runCatching {
        context.startActivity(
            Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60).putExtra(AlarmClock.EXTRA_SKIP_UI, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Dots that say how many faces there are and which is on; a touch on them turns to the next. */
@Composable
internal fun InfoDots(count: Int, current: Int, modifier: Modifier = Modifier, onNext: () -> Unit) {
    if (count < 2) return
    val ink = LocalMetroColors.current.tileContent
    val label = stringResource(R.string.infot_a11y_dot, current + 1, count)
    Row(
        modifier
            .height(36.dp)
            .testTag("info:dots")
            .semantics { contentDescription = label }
            .pointerInput(onNext) { detectTapGestures { onNext() } }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count.coerceAtMost(8)) { i ->
            Box(Modifier.size(if (i == current) 7.dp else 5.dp).clip(CircleShape).background(ink.copy(alpha = if (i == current) 1f else 0.45f)))
        }
    }
}

/** A tile laid out as a board: the time on one side, what the tile has to say on the other, none of it turning. */
@Composable
internal fun DashboardFace(item: AppItem, size: TileSize, width: Dp, height: Dp, config: InfoConfig, live: LiveTileData, events: List<LiveEvent>, parts: List<com.metrowin.launcher.model.InfoPart>) {
    val context = LocalContext.current
    val now = rememberNowMinute()
    val today = rememberToday()
    val locale = Locale.getDefault()
    val is24 = LocalTileFeatures.current.clockHours.is24(android.text.format.DateFormat.is24HourFormat(context))
    val time = remember(now) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime() }
    val ink = LocalMetroColors.current.tileContent
    Box(Modifier.fillMaxSize().testTag("info:dashboard")) {
        Row(Modifier.fillMaxSize().padding(FACE_MARGIN).padding(bottom = NAME_ROOM - FACE_MARGIN), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                MetroText(ClockFormat.time(time, is24, locale) + ClockFormat.meridiem(time, is24, locale).let { if (it.isEmpty()) "" else " $it" }, size = if (size == TileSize.Large) 44.sp else 36.sp, maxLines = 1)
                MetroText(ClockFormat.date(today, locale), size = 15.sp, weight = FontWeight.Normal, maxLines = 2, color = ink.copy(alpha = Muted))
            }
            Column(Modifier.weight(0.58f).fillMaxHeight().shrinkToHeight(0.6f), verticalArrangement = Arrangement.Center) {
                parts.forEach { part ->
                    when (part) {
                        com.metrowin.launcher.model.InfoPart.Calendar -> events.firstOrNull { !it.isOver(now) }?.let {
                            MetroText(eventWhen(it, today, now), size = 13.sp, weight = FontWeight.Normal, color = ink.copy(alpha = Muted), maxLines = 1)
                            MetroText(if (config.hideTitles) stringResource(R.string.infot_event_hidden) else it.title, size = 18.sp, maxLines = 2, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        com.metrowin.launcher.model.InfoPart.Weather -> (live.cityWeather[config.weatherCity] ?: live.weather)?.let {
                            MetroText("${it.temperature}° " + stringResource(it.condition.label), size = 16.sp, weight = FontWeight.Normal, maxLines = 1)
                        }
                        com.metrowin.launcher.model.InfoPart.Battery -> live.battery?.let { MetroText("${it.percent}%", size = 16.sp, weight = FontWeight.Normal, maxLines = 1) }
                        com.metrowin.launcher.model.InfoPart.Alarm -> if (live.nextAlarm > 0L) {
                            val alarm = Instant.ofEpochMilli(live.nextAlarm).atZone(ZoneId.systemDefault()).toLocalTime()
                            MetroText(ClockFormat.time(alarm, is24, locale), size = 16.sp, weight = FontWeight.Normal, maxLines = 1)
                        }
                        com.metrowin.launcher.model.InfoPart.Todo -> live.todo?.takeIf { it.open > 0 }?.let {
                            MetroText("✓ ${it.open}", size = 16.sp, weight = FontWeight.Normal, maxLines = 1)
                        }
                        com.metrowin.launcher.model.InfoPart.Waiting -> {
                            if (live.missedCalls > 0) MetroText(pluralStringResource(R.plurals.infot_missed, live.missedCalls, live.missedCalls), size = 16.sp, weight = FontWeight.Normal, maxLines = 1)
                            if (live.unreadMail > 0) MetroText(pluralStringResource(R.plurals.infot_unread, live.unreadMail, live.unreadMail), size = 16.sp, weight = FontWeight.Normal, maxLines = 1)
                        }
                        else -> Unit
                    }
                }
            }
        }
        TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}

/** A summary of the tile, in words, for the screen reader: the same whichever face is on. */
@Composable
internal fun infoSummary(config: InfoConfig, live: LiveTileData, events: List<LiveEvent>, weather: WeatherData?): String {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val is24 = LocalTileFeatures.current.clockHours.is24(android.text.format.DateFormat.is24HourFormat(context))
    val now = rememberNowMinute()
    val today = rememberToday()
    val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime()
    val parts = ArrayList<String>()
    parts += ClockFormat.time(time, is24, locale) + ClockFormat.meridiem(time, is24, locale).let { if (it.isEmpty()) "" else " $it" } + ", " + ClockFormat.date(today, locale)
    if (config.has(com.metrowin.launcher.model.InfoPart.Calendar)) {
        events.firstOrNull { !it.isOver(now) }?.let { parts += stringResource(R.string.infot_sum_event, if (config.hideTitles) stringResource(R.string.infot_event_hidden) else it.title, eventWhen(it, today, now)) }
    }
    if (config.has(com.metrowin.launcher.model.InfoPart.Alarm) && live.nextAlarm > 0L) {
        parts += stringResource(R.string.infot_sum_alarm, ClockFormat.time(Instant.ofEpochMilli(live.nextAlarm).atZone(ZoneId.systemDefault()).toLocalTime(), is24, locale))
    }
    if (config.has(com.metrowin.launcher.model.InfoPart.Weather) && weather != null) parts += stringResource(R.string.infot_sum_weather, weather.temperature, stringResource(weather.condition.label))
    if (config.has(com.metrowin.launcher.model.InfoPart.Battery)) live.battery?.let { parts += stringResource(R.string.infot_sum_battery, it.percent) }
    if (config.has(com.metrowin.launcher.model.InfoPart.Todo)) live.todo?.takeIf { it.open > 0 }?.let { parts += pluralStringResource(R.plurals.infot_sum_todo, it.open, it.open) }
    if (config.has(com.metrowin.launcher.model.InfoPart.Waiting)) {
        if (live.missedCalls > 0) parts += pluralStringResource(R.plurals.infot_missed, live.missedCalls, live.missedCalls)
        if (live.unreadMail > 0) parts += pluralStringResource(R.plurals.infot_unread, live.unreadMail, live.unreadMail)
    }
    return parts.joinToString(". ")
}
