package com.metrowin.launcher.calendar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private val HOUR = 60.dp
private val GUTTER = 52.dp
private const val SNAP_MIN = 15

/** How tall an hour is: it grows with the size of the text (the labels would be cramped otherwise) and with the pinch of the user. */
@Composable
fun hourHeight(zoom: Float) = HOUR * LocalDensity.current.fontScale.coerceIn(1f, 2f) * zoom

/** The day view's first row to show: the first event, or for today the hour of now when nothing is still to come, or eight o'clock. */
fun focusMinute(blocks: List<DayBlock>, nowMin: Int): Int {
    val first = blocks.minOfOrNull { it.startMin }
    val coming = blocks.filter { it.endMin > nowMin }.minOfOrNull { it.startMin }
    return when {
        nowMin >= 0 && (first == null || coming == null) -> nowMin
        else -> first ?: 8 * 60
    }
}

/**
 * The day view: the hours of the day down the page, the events as blocks in the hours they take (side by side when they share time), the
 * all-day ones in a band on top, and for today a line at "now". The grid opens on the hour before the first event (or before now, or 8 o'clock).
 * Pinching the grid zooms the hours. Arrows (and, if switched on, a swipe) go to the day before and after; a touch on an empty hour makes
 * an event there; with the switch on an event can be held and dragged to another time, or its lower edge dragged to change its end.
 */
@Composable
fun DayPage(
    events: List<CalEvent>,
    day: LocalDate,
    onShift: (Int) -> Unit,
    onEdit: (CalEvent) -> Unit,
    onCreate: (LocalDate, Int) -> Unit,
    onMove: (CalEvent, Long, Long) -> Unit,
) {
    val env = LocalCal.current
    val zone = env.zone
    val today = env.today
    val options = env.options
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val blocks = remember(events, day, zone, options[Option.ByCalendar]) {
        DayLayout.blocks(events, day, zone).let { if (options[Option.ByCalendar]) DayLayout.byCalendar(it) else it }
    }
    val allDay = remember(events, day, zone) { DayLayout.allDay(events, day, zone) }
    // "Now" moves on once a minute while the day is today.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(day == today) { while (day == today) { now = System.currentTimeMillis(); delay(60_000) } }
    val nowMin = if (day == today) CalendarLogic.wallMinutes(now, day, zone) else -1
    var zoom by remember { mutableFloatStateOf(1f) }
    val hour = hourHeight(zoom)
    val gutter = GUTTER * LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val scroll = rememberScrollState()
    val hourPx = with(LocalDensity.current) { hour.toPx() }
    LaunchedEffect(day, today) {
        scroll.scrollTo(((focusMinute(blocks, nowMin) / 60 - 1).coerceAtLeast(0)) * hourPx.toInt())
    }
    val timeOf = { min: Int -> env.time(CalendarLogic.timeAt(day, min, zone)) }
    val second = remember(options.secondZone, env.is24) {
        options.secondZone.takeIf { it.isNotBlank() }?.let { id -> runCatching { ZoneId.of(id) }.getOrNull() }?.let { z -> z to DateTimeFormatter.ofPattern(if (env.is24) "HH:mm" else "h a").withZone(z) }
    }
    val density = LocalDensity.current
    val classic = LocalStyle.current.isClassic
    val shape = if (classic) androidx.compose.ui.graphics.RectangleShape else androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    val pulse by rememberInfiniteTransition(label = "now").animateFloat(0.45f, 1f, infiniteRepeatable(tween(1_100), RepeatMode.Reverse), label = "now")
    val dotAlpha = if (LocalReduceMotion.current || !options[Option.NowLabel]) 1f else pulse
    val swipe = if (options[Option.SwipeDays]) Modifier.pointerInput(day) {
        var total = 0f
        detectHorizontalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total > 140f) onShift(-1) else if (total < -140f) onShift(1) }) { _, d -> total += d }
    } else Modifier

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).then(swipe)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            NavArrow("‹", stringResource(R.string.cal_prev_day), "cal:day:prev") { onShift(-1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MetroText(
                    day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), size = 18.sp, weight = FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 2,
                    color = if (day == today) accent else colors.foreground, modifier = Modifier.testTag("cal:day:title"),
                )
                if (options[Option.WeekNumbers]) MetroText(stringResource(R.string.cal_week_n, CalendarLogic.weekNumber(day)), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
            }
            NavArrow("›", stringResource(R.string.cal_next_day), "cal:day:next") { onShift(1) }
        }
        if (allDay.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            MetroText(stringResource(R.string.cal_all_day), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.width(gutter).padding(top = 4.dp))
            Column(Modifier.weight(1f)) {
                allDay.forEach { e ->
                    val fill = eventFill(e)
                    Box(Modifier.fillMaxWidth().padding(bottom = 2.dp).clip(shape).background(fill).metroPress(onClick = { onEdit(e) }).padding(horizontal = 8.dp, vertical = 4.dp).testTag("cal:day:allday")) {
                        MetroText(e.title.ifBlank { stringResource(R.string.cal_untitled) }, size = 15.sp, color = eventInk(fill), maxLines = 1)
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).testTag("cal:day:grid")
                // Two fingers zoom the hours; one finger is left to the scroll.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                val z = event.calculateZoom()
                                if (z != 1f) { zoom = (zoom * z).coerceIn(0.6f, 2.5f); event.changes.forEach { it.consume() } }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            Box(
                Modifier.fillMaxWidth().height(hour * 24).then(
                    if (options[Option.TapToCreate]) Modifier.pointerInput(day, hourPx) { detectTapGestures { o -> onCreate(day, (o.y / hourPx).toInt().coerceIn(0, 23)) } } else Modifier,
                ),
            ) {
                // The hours: a label and a thin line each, and the label of the second zone when there is one.
                for (h in 0 until 24) {
                    Box(Modifier.offset(y = hour * h).fillMaxWidth().height(hour)) {
                        MetroText(timeOf(h * 60), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1, modifier = Modifier.width(gutter).padding(top = 2.dp))
                        second?.let { (_, f) ->
                            MetroText(f.format(java.time.Instant.ofEpochMilli(CalendarLogic.timeAt(day, h * 60, zone))), size = 10.sp, weight = FontWeight.Normal, color = colors.subtle.copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.width(gutter).padding(top = 18.dp))
                        }
                        Box(Modifier.padding(start = gutter).fillMaxWidth().height(1.dp).background(colors.subtle.copy(alpha = 0.3f)))
                    }
                }
                BoxWithConstraints(Modifier.padding(start = gutter).fillMaxSize()) {
                    val full = maxWidth
                    blocks.forEach { b ->
                        val width = full / b.columns
                        val fill = eventFill(b.event)
                        val ink = eventInk(fill)
                        val movable = options[Option.DragEvents] && !b.event.readOnly && !b.event.recurring && !b.event.birthday && CalendarLogic.days(b.event, zone).let { it.start == it.endInclusive }
                        var dragY by remember(b.event.id, b.event.start) { mutableFloatStateOf(0f) }
                        var growY by remember(b.event.id, b.event.start) { mutableFloatStateOf(0f) }
                        val tall = (b.endMin - b.startMin) / 60f
                        val link = CalendarLogic.conferenceLink(b.event.location, b.event.description)
                        Box(
                            Modifier.offset(x = width * b.column, y = hour * (b.startMin / 60f)).offset { IntOffset(0, dragY.roundToInt()) }
                                .width(width - 2.dp).height((maxOf(hour * tall, 22.dp) - 1.dp + with(density) { growY.toDp() }).coerceAtLeast(14.dp))
                                .clip(shape).background(fill.copy(alpha = if (dragY != 0f || growY != 0f) 0.7f else 1f))
                                .metroPress(onClick = { onEdit(b.event) }).padding(horizontal = 6.dp, vertical = 2.dp).testTag("cal:day:block")
                                .then(
                                    if (movable) Modifier.pointerInput(b.event.id, b.event.start, hourPx) {
                                        detectDragGesturesAfterLongPress(
                                            onDragEnd = {
                                                val delta = (dragY / hourPx * 60f / SNAP_MIN).roundToInt() * SNAP_MIN
                                                dragY = 0f
                                                if (delta != 0) {
                                                    val s = CalendarLogic.timeAt(day, (b.startMin + delta).coerceIn(0, DayLayout.MINUTES_IN_DAY - SNAP_MIN), zone)
                                                    onMove(b.event, s, s + (b.event.end - b.event.start))
                                                }
                                            },
                                            onDragCancel = { dragY = 0f },
                                        ) { c, d -> c.consume(); dragY += d.y }
                                    } else Modifier,
                                ),
                        ) {
                            Column {
                                MetroText(b.event.title.ifBlank { stringResource(R.string.cal_untitled) }, size = 15.sp, color = ink, maxLines = 2)
                                if (b.endMin - b.startMin >= 45) MetroText(timeOf(b.startMin) + " – " + timeOf(b.endMin.coerceAtMost(DayLayout.MINUTES_IN_DAY - 1)), size = 11.sp, weight = FontWeight.Normal, color = ink.copy(alpha = 0.85f), maxLines = 1)
                                if (options[Option.RowDetails] && tall >= 1.2f) Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (b.event.location.isNotBlank() && link == null) { CalIconView(CalIcon.Place, 12.dp, ink.copy(alpha = 0.85f)); MetroText(b.event.location, size = 11.sp, weight = FontWeight.Normal, color = ink.copy(alpha = 0.85f), maxLines = 1, modifier = Modifier.padding(start = 3.dp)) }
                                    if (link != null) CalIconView(CalIcon.Video, 14.dp, ink.copy(alpha = 0.9f))
                                }
                            }
                            if (movable) Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp)
                                    .pointerInput(b.event.id, b.event.start, hourPx) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                val delta = (growY / hourPx * 60f / SNAP_MIN).roundToInt() * SNAP_MIN
                                                growY = 0f
                                                if (delta != 0) {
                                                    val end = maxOf(b.event.start + SNAP_MIN * 60_000L, b.event.end + delta * 60_000L)
                                                    onMove(b.event, b.event.start, end)
                                                }
                                            },
                                            onDragCancel = { growY = 0f },
                                        ) { c, d -> c.consume(); growY += d.y }
                                    },
                            ) { Box(Modifier.align(Alignment.Center).width(24.dp).height(3.dp).background(ink.copy(alpha = 0.7f))) }
                        }
                    }
                }
                if (nowMin in 0 until DayLayout.MINUTES_IN_DAY) {
                    Box(Modifier.offset(y = hour * (nowMin / 60f)).padding(start = gutter - 5.dp).fillMaxWidth().testTag("cal:day:now")) {
                        Box(Modifier.size(10.dp).offset(y = (-4).dp).alpha(dotAlpha).clip(CircleShape).background(accent))
                        Box(Modifier.padding(start = 5.dp).fillMaxWidth().height(2.dp).background(accent))
                    }
                    if (options[Option.NowLabel]) MetroText(
                        timeOf(nowMin), size = 11.sp, weight = FontWeight.SemiBold, color = onAccent(), maxLines = 1,
                        modifier = Modifier.offset(y = hour * (nowMin / 60f) - 8.dp).background(accent).padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}
