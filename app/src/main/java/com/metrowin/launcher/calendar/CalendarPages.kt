package com.metrowin.launcher.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.components.sectionHeader
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.onAccent
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How many months the grid can be swiped each way from the month of today. */
private const val MONTHS_EACH_WAY = 240

fun monthTitle(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + " " + month.year

fun dayTitle(day: LocalDate, today: LocalDate): String =
    day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).let { if (day == today) "• $it" else it }

private val STATUS_TEXT = mapOf(2 to R.string.cal_status_declined, 4 to R.string.cal_status_maybe)

@Composable
fun EventRow(event: CalEvent, day: LocalDate?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val env = LocalCal.current
    val details = env.options[Option.RowDetails]
    val fill = eventFill(event)
    val span = CalendarLogic.days(event, env.zone)
    val time = if (event.allDay) stringResource(R.string.cal_all_day) else {
        // A multi-day event shows its time only on the days it starts and ends.
        env.time(event.start) + " – " + env.time(event.end).let { if (day != null && span.start != day && span.endInclusive != day) "" else it }
    }
    val declined = event.selfStatus == 2
    val band = details && event.allDay
    val ink = if (band) eventInk(fill) else colors.foreground
    val subInk = if (band) ink.copy(alpha = 0.85f) else colors.subtle
    val link = if (details) CalendarLogic.conferenceLink(event.location, event.description) else null
    Row(modifier.fillMaxWidth().testTag("cal:event").metroPress(onClick = onClick).padding(vertical = 3.dp).then(if (band) Modifier.background(fill) else Modifier).padding(vertical = 3.dp)) {
        if (!band) Box(Modifier.width(6.dp).fillMaxHeight().background(fill))
        Column(Modifier.weight(1f).padding(start = if (band) 10.dp else 12.dp)) {
            MetroText(
                event.title.ifBlank { stringResource(R.string.cal_untitled) }, size = 21.sp, color = if (declined) subInk else ink,
                modifier = if (declined) Modifier.semantics { } else Modifier,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.recurring && details) { CalIconView(CalIcon.Repeat, 13.dp, subInk); Box(Modifier.width(4.dp)) }
                MetroText(time, size = 14.sp, weight = FontWeight.Normal, color = subInk)
                if (event.location.isNotBlank() && details) {
                    MetroText("  ·  ", size = 14.sp, weight = FontWeight.Normal, color = subInk)
                    if (link == null) { CalIconView(CalIcon.Place, 13.dp, subInk); Box(Modifier.width(3.dp)) }
                    MetroText(event.location, size = 14.sp, weight = FontWeight.Normal, color = subInk, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                } else if (event.location.isNotBlank()) {
                    MetroText("  ·  " + event.location, size = 14.sp, weight = FontWeight.Normal, color = subInk, maxLines = 1)
                }
                if (link != null) { Box(Modifier.width(6.dp)); CalIconView(CalIcon.Video, 15.dp, subInk) }
            }
            STATUS_TEXT[event.selfStatus]?.let { MetroText(stringResource(it), size = 13.sp, weight = FontWeight.Normal, color = subInk) }
            if (details && event.calendarName.isNotBlank() && !band) MetroText(event.calendarName, size = 12.sp, weight = FontWeight.Normal, color = subInk.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

/** The agenda: the days that have events, from today, each with its events. Headers stay on top as the list scrolls. */
@Composable
fun AgendaPage(events: List<CalEvent>, selected: LocalDate, truncated: Boolean, onEdit: (CalEvent) -> Unit) {
    val env = LocalCal.current
    val today = env.today
    val accent = LocalAccent.current
    val colors = LocalMetroColors.current
    val days = remember(events, today) {
        val range = today..today.plusDays(CalendarDefaults.AGENDA_DAYS.toLong() - 1)
        CalendarLogic.byDay(events, range, env.zone).toSortedMap().map { it.key to it.value }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (truncated) PageNote(stringResource(R.string.cal_truncated), Modifier.padding(bottom = 4.dp).testTag("cal:truncated"))
        if (!env.options.onboarded) Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).border(1.dp, accent).padding(10.dp).testTag("cal:tips")) {
            MetroText(stringResource(R.string.cal_tips_title), size = 16.sp, weight = FontWeight.SemiBold, color = accent)
            PageNote(stringResource(R.string.cal_tips), Modifier.padding(top = 4.dp))
            MetroButton(stringResource(R.string.cal_tips_ok), Modifier.padding(top = 6.dp)) { env.options.finishOnboarding() }
        }
        if (days.isEmpty()) EmptyState(stringResource(R.string.cal_no_events_hint), Modifier.testTag("cal:empty"))
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().scrollIndicator(listState).testTag("cal:agenda"), state = listState) {
            days.forEach { (day, list) ->
                sectionHeader("d:$day") {
                    Column(Modifier.then(if (day == selected) Modifier.background(accent.copy(alpha = 0.18f)) else Modifier)) {
                        if (env.options[Option.RowDetails]) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.subtle.copy(alpha = 0.3f)))
                        MetroText(dayTitle(day, today), size = 16.sp, weight = FontWeight.SemiBold, color = accent, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    }
                }
                itemsIndexed(list, key = { _, it -> "e:$day:${it.id}:${it.start}" }) { index, it -> EventRow(it, day, Modifier.featherRow(index)) { onEdit(it) } }
            }
        }
    }
}

/**
 * The month: a grid of weeks that is a pager of months (a swipe on it goes to the next or the one before), with the events of the chosen day
 * under it. On a wide screen the cells are tall and show the titles of the first events. Touching the chosen day again opens it.
 */
@Composable
fun MonthPage(
    events: List<CalEvent>,
    month: YearMonth,
    selected: LocalDate,
    roomy: Boolean,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onOpenDay: () -> Unit,
    onEdit: (CalEvent) -> Unit,
) {
    val env = LocalCal.current
    val today = env.today
    val zone = env.zone
    val options = env.options
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val classic = LocalStyle.current.isClassic
    val first = env.first
    val context = LocalContext.current
    // The months are pages: the month that is set from outside (the Today button, a day of another month) brings its page in.
    val origin = remember(today) { YearMonth.from(today) }
    val monthPager = rememberPagerState(initialPage = MONTHS_EACH_WAY + ChronoUnit.MONTHS.between(origin, month).toInt()) { 2 * MONTHS_EACH_WAY + 1 }
    LaunchedEffect(monthPager, origin) { snapshotFlow { monthPager.settledPage }.collect { onMonth(origin.plusMonths((it - MONTHS_EACH_WAY).toLong())) } }
    LaunchedEffect(month) {
        val target = MONTHS_EACH_WAY + ChronoUnit.MONTHS.between(origin, month).toInt()
        if (monthPager.settledPage != target) monthPager.animateScrollToPage(target)
    }
    val rowHeight = if (roomy) 78.dp else 46.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    val weekCol = 26.dp
    val showWeeks = options[Option.WeekNumbers]
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            if (showWeeks) Box(Modifier.width(weekCol))
            CalendarLogic.week(selected, first).forEach { d ->
                MetroText(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        // Tall enough for six weeks, so the list under it does not jump from a month to the next.
        HorizontalPager(monthPager, Modifier.fillMaxWidth().height(rowHeight * 6).testTag("cal:months")) { page ->
            val shownMonth = origin.plusMonths((page - MONTHS_EACH_WAY).toLong())
            val weeks = remember(shownMonth, first) { CalendarLogic.weeks(shownMonth, first) }
            // The busy days of this page, not of the month on show: a page that is coming in during a swipe has its marks too.
            val byDay = remember(events, shownMonth, zone, first) { CalendarLogic.byDay(events, weeks.first().first()..weeks.last().last(), zone) }
            Column(Modifier.fillMaxWidth().testTag("cal:month:$shownMonth")) {
                weeks.forEach { row ->
                    Row(Modifier.fillMaxWidth().height(rowHeight)) {
                        if (showWeeks) MetroText(CalendarLogic.weekNumber(row[3]).toString(), size = 11.sp, weight = FontWeight.Normal, color = colors.subtle, textAlign = TextAlign.Center, modifier = Modifier.width(weekCol).align(Alignment.CenterVertically).clearAndSetSemantics { })
                        row.forEach { day ->
                            val inMonth = YearMonth.from(day) == shownMonth
                            val isSelected = day == selected
                            val isToday = day == today
                            val list = byDay[day].orEmpty()
                            // In Windows Phone today is the cell filled with the accent colour, a chosen day is outlined, and a bar marks a day with events.
                            val filled = if (classic) isToday else isSelected
                            val heat = if (options[Option.Heatmap] && !filled) (list.sumOf { if (it.allDay) 600 else it.end.minus(it.start).div(60_000L).toInt() }.coerceAtMost(600) / 600f * 0.5f) else 0f
                            val count = list.size
                            val desc = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) + if (count > 0) ", " + context.resources.getQuantityString(R.plurals.cal_events_count, count, count) else ""
                            Column(
                                Modifier.weight(1f).fillMaxHeight().padding(1.dp).testTag("cal:day:$day")
                                    .metroPress(onClick = { if (isSelected && options[Option.OpenDay]) onOpenDay() else { onSelect(day); if (!inMonth) onMonth(YearMonth.from(day)) } })
                                    .semantics { contentDescription = desc; this.selected = isSelected }
                                    .background(if (filled) accent else if (heat > 0f) accent.copy(alpha = heat) else Color.Transparent)
                                    .then(if (isSelected && (!classic || !isToday)) Modifier.border(if (classic) 3.dp else 2.dp, accent) else Modifier)
                                    .then(if (isToday && !classic && !isSelected) Modifier.border(1.dp, accent) else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = if (roomy) Arrangement.Top else Arrangement.Center,
                            ) {
                                MetroText(
                                    day.dayOfMonth.toString(), size = 19.sp, weight = if (isToday) FontWeight.SemiBold else FontWeight.Light, modifier = Modifier.clearAndSetSemantics { },
                                    color = when { filled -> onAccent(); classic -> if (inMonth) colors.foreground else colors.subtle; isToday -> accent; inMonth -> colors.foreground; else -> colors.subtle },
                                )
                                if (roomy) list.take(2).forEach { e ->
                                    val fill = eventFill(e)
                                    MetroText(e.title.ifBlank { "·" }, size = 10.sp, weight = FontWeight.Normal, color = eventInk(fill), maxLines = 1, modifier = Modifier.fillMaxWidth().padding(top = 1.dp).background(fill).padding(horizontal = 2.dp).clearAndSetSemantics { })
                                }
                                val markColor = if (filled) onAccent() else accent
                                if (!roomy) {
                                    if (classic) Box(Modifier.padding(top = 2.dp).size(14.dp, 3.dp).background(if (count == 0) Color.Transparent else markColor))
                                    else Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (count == 0) Color.Transparent else markColor))
                                }
                            }
                        }
                    }
                }
            }
        }
        MetroText(dayTitle(selected, today), size = 16.sp, weight = FontWeight.SemiBold, color = accent, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        val list = remember(events, selected, zone) { CalendarLogic.on(events, selected, zone) }
        if (list.isEmpty()) PageNote(stringResource(R.string.cal_no_events), Modifier.testTag("cal:day:empty"))
        val listState = rememberLazyListState()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().scrollIndicator(listState).testTag("cal:day:list"), state = listState) {
            itemsIndexed(list, key = { _, it -> "${it.id}:${it.start}" }) { index, it -> EventRow(it, selected, Modifier.featherRow(index)) { onEdit(it) } }
        }
    }
}

/** The week: seven columns with the hours down the page (or, with the switch off, seven days one under the other). */
@Composable
fun WeekPage(events: List<CalEvent>, selected: LocalDate, onShift: (Int) -> Unit, onOpenDay: (LocalDate) -> Unit, onEdit: (CalEvent) -> Unit, onCreate: (LocalDate, Int) -> Unit) {
    val env = LocalCal.current
    val today = env.today
    val zone = env.zone
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val days = remember(selected, env.first) { CalendarLogic.week(selected, env.first) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Column(Modifier.fillMaxSize().padding(horizontal = if (env.options[Option.WeekGrid]) 12.dp else 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            NavArrow("‹", stringResource(R.string.cal_prev_week), "cal:week:prev") { onShift(-1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                MetroText(days.first().format(formatter) + " – " + days.last().format(formatter), size = 18.sp, weight = FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.testTag("cal:week"))
                if (env.options[Option.WeekNumbers]) MetroText(stringResource(R.string.cal_week_n, CalendarLogic.weekNumber(days.first().plusDays(3))), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
            }
            NavArrow("›", stringResource(R.string.cal_next_week), "cal:week:next") { onShift(1) }
        }
        if (env.options[Option.WeekGrid]) WeekGrid(events, days, onOpenDay, onEdit, onCreate) else {
            val listState = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().scrollIndicator(listState).testTag("cal:weeklist"), state = listState) {
                days.forEach { day ->
                    item(key = "w:$day") { MetroText(dayTitle(day, today), size = 16.sp, weight = FontWeight.SemiBold, color = if (day == today) accent else colors.subtle, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                    itemsIndexed(CalendarLogic.on(events, day, zone), key = { _, it -> "w:$day:${it.id}:${it.start}" }) { index, it -> EventRow(it, day, Modifier.featherRow(index)) { onEdit(it) } }
                }
            }
        }
    }
}

@Composable
private fun WeekGrid(events: List<CalEvent>, days: List<LocalDate>, onOpenDay: (LocalDate) -> Unit, onEdit: (CalEvent) -> Unit, onCreate: (LocalDate, Int) -> Unit) {
    val env = LocalCal.current
    val zone = env.zone
    val today = env.today
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val gutter = 30.dp
    val hour = hourHeight(1f)
    val hourPx = with(LocalDensity.current) { hour.toPx() }
    val blocks = remember(events, days, zone) { days.map { DayLayout.blocks(events, it, zone) } }
    val allDay = remember(events, days, zone) { days.map { DayLayout.allDay(events, it, zone) } }
    val scroll = rememberScrollState()
    val hourLabel = remember(env.is24) { DateTimeFormatter.ofPattern(if (env.is24) "H" else "ha") }
    LaunchedEffect(days) { scroll.scrollTo((7 * hourPx).toInt()) }
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(gutter))
        days.forEach { d ->
            Column(
                Modifier.weight(1f).metroPress(onClick = { onOpenDay(d) }).semantics { contentDescription = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MetroText(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3), size = 11.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
                MetroText(d.dayOfMonth.toString(), size = 17.sp, weight = if (d == today) FontWeight.SemiBold else FontWeight.Light, color = if (d == today) onAccent() else colors.foreground, modifier = Modifier.then(if (d == today) Modifier.background(accent).padding(horizontal = 5.dp) else Modifier).clearAndSetSemantics { })
            }
        }
    }
    if (allDay.any { it.isNotEmpty() }) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 2.dp)) {
        Box(Modifier.width(gutter))
        allDay.forEach { list ->
            Column(Modifier.weight(1f).padding(horizontal = 1.dp)) {
                list.take(2).forEach { e ->
                    val fill = eventFill(e)
                    Box(Modifier.fillMaxWidth().padding(bottom = 1.dp).background(fill).metroPress(onClick = { onEdit(e) }).padding(horizontal = 2.dp)) { MetroText(e.title.ifBlank { "·" }, size = 10.sp, weight = FontWeight.Normal, color = eventInk(fill), maxLines = 1) }
                }
                if (list.size > 2) MetroText("+${list.size - 2}", size = 10.sp, weight = FontWeight.Normal, color = colors.subtle)
            }
        }
    }
    Box(Modifier.fillMaxSize().verticalScroll(scroll).testTag("cal:week:grid")) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(hour * 24).pointerInput(days, hourPx) {
                detectTapGestures { o ->
                    val col = ((o.x - gutter.toPx()) / ((size.width - gutter.toPx()) / 7f)).toInt()
                    if (col in 0..6 && env.options[Option.TapToCreate]) onCreate(days[col], (o.y / hourPx).toInt().coerceIn(0, 23))
                }
            },
        ) {
            val col = (maxWidth - gutter) / 7
            for (h in 0 until 24) {
                Box(Modifier.offset(y = hour * h).fillMaxWidth().height(hour)) {
                    MetroText(hourLabel.format(java.time.LocalTime.of(h, 0)).lowercase(), size = 10.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1, modifier = Modifier.width(gutter).padding(top = 2.dp))
                    Box(Modifier.padding(start = gutter).fillMaxWidth().height(1.dp).background(colors.subtle.copy(alpha = 0.3f)))
                }
            }
            days.forEachIndexed { i, d ->
                if (d == today) Box(Modifier.offset(x = gutter + col * i).width(col).fillMaxHeight().background(accent.copy(alpha = 0.08f)))
                blocks[i].forEach { b ->
                    val w = col / b.columns
                    val fill = eventFill(b.event)
                    Box(
                        Modifier.offset(x = gutter + col * i + w * b.column, y = hour * (b.startMin / 60f)).width(w - 1.dp).height(maxOf(hour * ((b.endMin - b.startMin) / 60f), 18.dp) - 1.dp)
                            .background(fill).metroPress(onClick = { onEdit(b.event) }).padding(1.dp).testTag("cal:week:block"),
                    ) { MetroText(b.event.title.ifBlank { "·" }, size = 10.sp, weight = FontWeight.Normal, color = eventInk(fill), maxLines = 3, lineHeight = 11.sp) }
                }
            }
            val nowMin = CalendarLogic.wallMinutes(System.currentTimeMillis(), today, zone)
            val todayIndex = days.indexOf(today)
            if (todayIndex >= 0) Box(Modifier.offset(x = gutter + col * todayIndex, y = hour * (nowMin / 60f)).width(col).height(2.dp).background(accent))
        }
    }
}

/** The year: twelve small months; a touch on one goes to it. Days with events are marked. */
@Composable
fun YearPage(events: List<CalEvent>, year: Int, onShift: (Int) -> Unit, onPick: (YearMonth) -> Unit) {
    val env = LocalCal.current
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val busy = remember(events, year) { CalendarLogic.busyDays(events, LocalDate.of(year, 1, 1)..LocalDate.of(year, 12, 31), env.zone) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NavArrow("‹", stringResource(R.string.cal_prev_year), "cal:year:prev") { onShift(-1) }
            MetroText(year.toString(), size = 22.sp, weight = FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).testTag("cal:year"))
            NavArrow("›", stringResource(R.string.cal_next_year), "cal:year:next") { onShift(1) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            for (row in 0 until 4) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until 3) {
                    val m = YearMonth.of(year, row * 3 + c + 1)
                    Column(Modifier.weight(1f).metroPress(onClick = { onPick(m) }).semantics { contentDescription = monthTitle(m) }.padding(2.dp)) {
                        MetroText(m.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()), size = 14.sp, weight = FontWeight.SemiBold, color = if (m == YearMonth.from(env.today)) accent else colors.foreground, modifier = Modifier.clearAndSetSemantics { })
                        Column(Modifier.clearAndSetSemantics { }) {
                            CalendarLogic.weeks(m, env.first).forEach { week ->
                                Row(Modifier.fillMaxWidth()) {
                                    week.forEach { d ->
                                        val inMonth = YearMonth.from(d) == m
                                        MetroText(
                                            if (inMonth) d.dayOfMonth.toString() else "", size = 9.sp, weight = if (d in busy && inMonth) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1,
                                            color = if (d == env.today) onAccent() else if (d in busy) accent else colors.subtle,
                                            modifier = Modifier.weight(1f).then(if (d == env.today && inMonth) Modifier.background(accent) else Modifier),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
