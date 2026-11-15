package com.metrowin.launcher.ui.start

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import com.metrowin.launcher.ui.components.shrinkToHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Birthday
import com.metrowin.launcher.data.Birthdays
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.Countdown
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.MoonPhase
import com.metrowin.launcher.data.NextAlarm
import com.metrowin.launcher.data.WorldCities
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay









/** The days left to a date in large light type, and what they are to: "62 days to Christmas". It counts up after the date. */
@Composable
internal fun CountdownFace(item: AppItem, size: TileSize, width: Dp, height: Dp) {
    var today by remember { mutableStateOf(LocalDate.now()) }
    // Once a day, just after midnight, and only while the launcher is on screen.
    LaunchedWhileStarted(Unit) {
        while (true) {
            val now = java.time.LocalDateTime.now()
            today = now.toLocalDate()
            delay(java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1_000L)
        }
    }
    val days = Countdown.daysLeft(item.targetDay, today)
    val number = kotlin.math.abs(days).coerceAtMost(99_999L).toInt()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            if (days == 0L) {
                MetroText(stringResource(R.string.cd_today), size = if (size == TileSize.Small) 16.sp else 36.sp, modifier = Modifier.testTag("countdown:days"))
            } else {
                MetroText(number.toString(), size = if (size == TileSize.Small) 26.sp else 56.sp, modifier = Modifier.testTag("countdown:days"))
                if (size != TileSize.Small) {
                    MetroText(
                        pluralStringResource(if (days > 0) R.plurals.cd_to else R.plurals.cd_since, number, number, item.label),
                        size = 15.sp, weight = FontWeight.Normal, maxLines = 2, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        if (days == 0L && size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}


/** The time in another city, with the name of the city and, when it is not the same day, which day it is there. */
@Composable
internal fun WorldClockFace(item: AppItem, size: TileSize, width: Dp, height: Dp, zone: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableStateOf(java.time.Instant.now()) }
    // Redrawn when the minute changes, and only while the launcher is on screen.
    LaunchedWhileStarted(Unit) {
        while (true) {
            now = java.time.Instant.now()
            delay(60_000L - now.toEpochMilli() % 60_000L + 50L)
        }
    }
    // A zone that cannot be read (a backup from elsewhere) is the zone of the phone: the face still tells the time.
    val zoneId = remember(zone) { runCatching { java.time.ZoneId.of(zone) }.getOrNull() ?: java.time.ZoneId.systemDefault() }
    val there = WorldCities.timeIn(zoneId, now, java.time.ZoneId.systemDefault())
    val locale = Locale.getDefault()
    val is24 = LocalTileFeatures.current.clockHours.is24(android.text.format.DateFormat.is24HourFormat(context))
    // On a wide or large tile the time sits on the left and the day and the date on the right, so that neither half is empty.
    val sideBySide = size == TileSize.Wide || size == TileSize.Large
    Box(Modifier.fillMaxSize()) {
        val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.26f else if (sideBySide) 0.38f else 0.3f).toSp() }
        val meridiem = ClockFormat.meridiem(there.time, is24, locale)
        val timeRow: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.Bottom) {
                MetroText(ClockFormat.time(there.time, is24, locale), size = digits, maxLines = 1, modifier = Modifier.weight(1f, fill = false).testTag("worldclock:time"))
                // "AM" and "PM" are small, next to the digits: the time never wraps or gets cut on a narrow tile.
                if (meridiem.isNotEmpty()) MetroText(meridiem, size = 14.sp, weight = FontWeight.Normal, modifier = Modifier.padding(start = 3.dp, bottom = 6.dp))
            }
        }
        val details: @Composable () -> Unit = {
            val ahead = remember(zoneId, now) { ((zoneId.rules.getOffset(now).totalSeconds - java.time.ZoneId.systemDefault().rules.getOffset(now).totalSeconds) / 60) }
            MetroText(
                if (ahead == 0) item.label else item.label + " · " + hoursAhead(ahead),
                size = 18.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.testTag("worldclock:city"),
            )
            when (there.dayOffset) {
                1 -> MetroText(stringResource(R.string.live_tomorrow), size = 14.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("worldclock:day"))
                -1 -> MetroText(stringResource(R.string.worldclock_yesterday), size = 14.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("worldclock:day"))
            }
        }
        if (sideBySide) {
            Row(Modifier.align(Alignment.Center).padding(horizontal = FACE_MARGIN), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                timeRow()
                Column {
                    MetroText(java.time.LocalDate.ofInstant(now, zoneId).dayOfWeek.getDisplayName(TextStyle.FULL, locale), size = 22.sp, maxLines = 1, modifier = Modifier.testTag("worldclock:weekday"))
                    details()
                }
            }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                timeRow()
                if (size != TileSize.Small) details()
            }
        }
        if (size == TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}


/** The moon as it is today: a disc with the lit part drawn, and its name and how much of it is lit. */
@Composable
internal fun MoonFace(item: AppItem, size: TileSize, width: Dp, height: Dp) {
    val today = rememberToday()
    val ink = LocalMetroColors.current.tileContent
    val illumination = MoonPhase.illumination(today)
    val waxing = MoonPhase.waxing(today)
    val name = MoonPhase.name(today)
    Box(Modifier.fillMaxSize()) {
        val disc = minOf(width, height) * if (size == TileSize.Small) 0.6f else 0.5f
        Canvas(Modifier.align(Alignment.Center).size(disc).testTag("moon:disc")) {
            val r = this.size.minDimension / 2f
            // The lit side is the right in the first half of the month and the left in the second.
            withTransform({ if (!waxing) scale(-1f, 1f, pivot = center) }) {
                drawCircle(ink.copy(alpha = 0.22f), r)
                // The lit part: the right half of the disc, less the half of an ellipse for a crescent, plus it for a gibbous moon.
                // The ellipse is the edge between light and dark, and it gets thinner towards the quarters.
                val w = r * kotlin.math.abs(1f - 2f * illumination.toFloat())
                val half = Path().apply { arcTo(Rect(center.x - r, center.y - r, center.x + r, center.y + r), -90f, 180f, true); close() }
                val edge = Path().apply { addOval(Rect(center.x - w, center.y - r, center.x + w, center.y + r)) }
                val lit = Path.combine(if (illumination < 0.5) PathOperation.Difference else PathOperation.Union, half, edge)
                drawPath(lit, ink)
            }
        }
        if (size != TileSize.Small) {
            Column(Modifier.align(Alignment.BottomStart).padding(start = FACE_MARGIN, bottom = FACE_MARGIN)) {
                MetroText(stringResource(name.label), size = 14.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("moon:name"))
                if (size == TileSize.Wide) MetroText(stringResource(R.string.moon_lit, (illumination * 100).toInt()), size = 12.sp, weight = FontWeight.Normal, color = ink.copy(alpha = 0.8f))
            }
        }
    }
}


/** The next birthdays of the contacts: the nearest in large type, and on the other face the next ones. */
@Composable
internal fun BirthdaysFace(item: AppItem, size: TileSize, width: Dp, height: Dp, birthdays: List<Birthday>, seed: Int) {
    val today = rememberToday()
    val upcoming = remember(birthdays, today) { Birthdays.upcoming(birthdays, today, 3) }
    if (upcoming.isEmpty()) {
        StaticFace(item, size, width, height)
        return
    }
    val first = upcoming.first()
    FlipContainer(if (upcoming.size > 1 && size != TileSize.Small) 2 else 1, seed, slides = true) { face ->
        Box(Modifier.fillMaxSize()) {
            if (face == 0) {
                Column(Modifier.padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN)) {
                    MetroText(first.name, size = if (size == TileSize.Small) 14.sp else 22.sp, maxLines = 2, lineHeight = 26.sp, modifier = Modifier.testTag("birthday:name"))
                    if (size != TileSize.Small) {
                        MetroText(birthdayWhen(first), size = 16.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("birthday:when"))
                        first.turning?.let { MetroText(stringResource(R.string.birthdays_turning, it), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.tileContent.copy(alpha = 0.85f)) }
                    }
                }
            } else {
                Column(Modifier.padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    upcoming.forEach { MetroText(it.name + " · " + birthdayWhen(it, short = true), size = 15.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("birthday:next")) }
                }
            }
            if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}


@Composable
internal fun birthdayWhen(next: Birthdays.Next, short: Boolean = false): String = when {
    next.daysAway == 0 -> stringResource(R.string.birthdays_today)
    else -> pluralStringResource(R.plurals.birthdays_in, next.daysAway, next.daysAway)
}


/** The Alarms tile: when the next alarm rings, as a time in large type and the day under it. No alarm: just the mark. */
@Composable
internal fun AlarmFace(item: AppItem, size: TileSize, width: Dp, height: Dp, triggerMillis: Long, seed: Int = 0) {
    val next = remember(triggerMillis) { NextAlarm.describe(triggerMillis, System.currentTimeMillis()) }
    if (next == null) {
        StaticFace(item, size, width, height)
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val locale = Locale.getDefault()
    // The hours are written as the clock of Start says (the same as on the time face).
    val is24 = LocalTileFeatures.current.clockHours.is24(android.text.format.DateFormat.is24HourFormat(context))
    Box(Modifier.fillMaxSize()) {
        val digits = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.26f else 0.3f).toSp() }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                MetroText(ClockFormat.time(next.time, is24, locale), size = digits, maxLines = 1, modifier = Modifier.weight(1f, fill = false).testTag("alarm:time"))
                val meridiem = ClockFormat.meridiem(next.time, is24, locale)
                if (meridiem.isNotEmpty() && size != TileSize.Small) {
                    MetroText(meridiem, size = 14.sp, weight = FontWeight.Normal, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                }
            }
            if (size != TileSize.Small) {
                MetroText(
                    when (val day = next.day) {
                        NextAlarm.Day.Today -> stringResource(R.string.live_today)
                        NextAlarm.Day.Tomorrow -> stringResource(R.string.live_tomorrow)
                        is NextAlarm.Day.Weekday -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                    },
                    size = 15.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("alarm:day"),
                )
            }
        }
        if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
    }
}


@Composable
internal fun CalendarFace(
    item: AppItem, size: TileSize, width: Dp, height: Dp, event: LiveEvent?, events: List<LiveEvent>, seed: Int,
    /** Inside an info tile: which event this turn shows (no turn of its own, no day number: the time face has the date). */
    sub: Int? = null,
    hideTitle: Boolean = false,
    actions: Boolean = false,
) {
    val today = rememberToday()
    val features = LocalTileFeatures.current
    if (sub != null) {
        val shown = events.getOrNull(sub) ?: event
        if (shown == null) StaticFace(item, size, width, height) else EventFace(item, size, shown, today, hideTitle, actions)
        return
    }
    if (features.calendarAgenda && size == TileSize.Large) {
        LargeCalendar(item, events, today)
        return
    }
    val agenda = features.calendarAgenda && size == TileSize.Wide && events.size > 1
    val faces = if (event != null && size != TileSize.Small) 2 else 1
    FlipContainer(faces, seed) { face ->
        Box(Modifier.fillMaxSize()) {
            if (face == 0 || event == null) {
                val numberSize = with(LocalDensity.current) { (height * if (size == TileSize.Small) 0.46f else 0.4f).toSp() }
                Column(Modifier.align(Alignment.TopStart).padding(start = FACE_MARGIN, top = 2.dp)) {
                    MetroText(today.dayOfMonth.toString(), size = numberSize)
                    if (size != TileSize.Small) {
                        MetroText(
                            today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                            size = 15.sp,
                            weight = FontWeight.Normal,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
                if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
            } else if (agenda) {
                AgendaList(events.take(3), today, Modifier.align(Alignment.TopStart).padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN, bottom = NAME_ROOM))
                TileLabel(item.label, Modifier.align(Alignment.BottomStart))
            } else {
                EventFace(item, size, event, today, hideTitle = false, actions = false)
            }
        }
    }
}

/** The minute that is now, kept right while the launcher is on screen. */
@Composable
internal fun rememberNowMinute(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedWhileStarted(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
        }
    }
    return now
}

/** When an event is, in the words that are quickest to read: "in 25 min" within the hour, "now, until 15:30" while it goes on, else the day and the hour. */
@Composable
internal fun eventWhen(event: LiveEvent, today: LocalDate, now: Long): String {
    if (event.isOn(now) && event.endMillis > 0L) {
        val end = Instant.ofEpochMilli(event.endMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        return stringResource(R.string.infot_event_now, end)
    }
    val minutes = ((event.startMillis - now + 59_999L) / 60_000L).toInt()
    if (!event.allDay && minutes in 1..59) return pluralStringResource(R.plurals.infot_in_minutes, minutes, minutes)
    return describeWhen(event, today)
}

/** The event a tile says: when it is, its title (as much as the tile holds), and, if it has them, the buttons to join the meeting and to go there. */
@Composable
internal fun EventFace(item: AppItem, size: TileSize, event: LiveEvent, today: LocalDate, hideTitle: Boolean, actions: Boolean) {
    val now = rememberNowMinute()
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize()) {
        // The text shrinks to the room that is left over the name of the tile: a long title is made smaller, it never runs over the name.
        Column(Modifier.fillMaxSize().padding(bottom = if (size == TileSize.Small) 0.dp else NAME_ROOM)) {
            Column(Modifier.shrinkToHeight(0.7f).padding(start = FACE_MARGIN, top = FACE_MARGIN, end = FACE_MARGIN)) {
                MetroText(eventWhen(event, today, now), size = 14.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("event:when"))
                MetroText(
                    if (hideTitle) stringResource(R.string.infot_event_hidden) else event.title,
                    size = 22.sp, maxLines = 3, lineHeight = 26.sp, modifier = Modifier.padding(top = 4.dp).testTag("event:title"),
                )
            }
        }
        if (size != TileSize.Small) {
            TileLabel(item.label, Modifier.align(Alignment.BottomStart))
            val link = event.link
            val place = event.location.takeIf { it.isNotBlank() && !hideTitle }
            if (actions && (link != null || place != null)) {
                Row(Modifier.align(Alignment.BottomEnd).padding(end = FACE_MARGIN, bottom = FACE_MARGIN), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (link != null) EventChip(stringResource(R.string.infot_join), "event:join") { openAddress(context, link) }
                    if (place != null) EventChip(stringResource(R.string.infot_go), "event:go") { openAddress(context, "geo:0,0?q=" + android.net.Uri.encode(place)) }
                }
            }
        }
    }
}

/** A small button on a face: its own touch, so that it does not open the app. */
@Composable
internal fun EventChip(text: String, tag: String, onClick: () -> Unit) {
    val ink = LocalMetroColors.current.tileContent
    val feedback by rememberUpdatedState(com.metrowin.launcher.ui.anim.LocalTapFeedback.current)
    Box(
        Modifier
            .heightIn(min = 32.dp)
            .testTag(tag)
            .border(2.dp, ink.copy(alpha = 0.8f))
            .pointerInput(onClick) { detectTapGestures { feedback(); onClick() } }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) { MetroText(text, size = 14.sp, weight = FontWeight.Normal, maxLines = 1) }
}

/** Opens an address (a web page or a place) in the app that takes it; nothing happens when there is none. */
internal fun openAddress(context: android.content.Context, address: String) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(address)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}


/** The current date, kept right across midnight. */
@Composable
internal fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedWhileStarted(Unit) {
        while (true) {
            // Coming back after a night off screen, the date is right at once.
            LocalDate.now().let { if (it != today) today = it }
            delay(60_000)
        }
    }
    return today
}


@Composable
internal fun describeWhen(event: LiveEvent, today: LocalDate): String {
    val start = Instant.ofEpochMilli(event.startMillis).atZone(ZoneId.systemDefault())
    val day = when (start.toLocalDate()) {
        today -> stringResource(R.string.live_today)
        today.plusDays(1) -> stringResource(R.string.live_tomorrow)
        else -> start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    if (event.allDay) return day
    return "$day ${start.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}"
}

/** "+6 h" or "-5:30 h": how far a place is from the phone, in hours. */
internal fun hoursAhead(minutes: Int): String {
    val sign = if (minutes < 0) "-" else "+"
    val abs = kotlin.math.abs(minutes)
    return sign + (abs / 60) + (if (abs % 60 != 0) ":" + "%02d".format(abs % 60) else "") + " h"
}
