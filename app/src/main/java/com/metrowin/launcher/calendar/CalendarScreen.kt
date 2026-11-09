package com.metrowin.launcher.calendar

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.theme.LocalStyle
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.sectionHeader
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.DateTimeDialog
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

private fun firstDayOfWeek(): DayOfWeek = DayOfWeek.of((Calendar.getInstance().firstDayOfWeek - 2 + 7) % 7 + 1)

@Composable
fun CalendarScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CalendarRepository(context) }
    var canRead by remember { mutableStateOf(LivePermissions.isGranted(context, Manifest.permission.READ_CALENDAR)) }
    var canWrite by remember { mutableStateOf(LivePermissions.isGranted(context, Manifest.permission.WRITE_CALENDAR)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        canRead = it[Manifest.permission.READ_CALENDAR] == true || LivePermissions.isGranted(context, Manifest.permission.READ_CALENDAR)
        canWrite = it[Manifest.permission.WRITE_CALENDAR] == true || LivePermissions.isGranted(context, Manifest.permission.WRITE_CALENDAR)
    }
    val today = remember { LocalDate.now() }
    var selected by remember { mutableStateOf(today) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var version by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<CalEvent?>(null) }
    val pager = rememberPagerState { 3 }
    val zone = ZoneId.systemDefault()

    // What the three pages need: from the month before the one shown to a few months after, and four months from today for the agenda.
    val from = month.minusMonths(1).atDay(1)
    val to = maxOf(month.plusMonths(2).atDay(1), today.plusDays(120))
    val events by produceState(emptyList<CalEvent>(), canRead, from, to, version) {
        value = if (canRead) withContext(Dispatchers.IO) {
            repository.events(from.atStartOfDay(zone).toInstant().toEpochMilli(), to.atStartOfDay(zone).toInstant().toEpochMilli())
        } else emptyList()
    }

    fun newEvent(day: LocalDate): CalEvent {
        val start = if (day == today) CalendarLogic.nextHour(System.currentTimeMillis()) else day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        return CalEvent(0L, 0L, "", start, start + 3_600_000L, false)
    }
    BackHandler(enabled = editing != null) { editing = null }

    MiniFrame {
        PivotHeader(
            listOf(stringResource(R.string.cal_pivot_agenda), stringResource(R.string.cal_pivot_month), stringResource(R.string.cal_pivot_week)),
            pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
        )
        if (!canRead) {
            Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
                PageNote(stringResource(R.string.cal_need_permission))
                MetroButton(stringResource(R.string.recorder_allow), Modifier.padding(top = 14.dp).testTag("cal:allow")) { ask.launch(PERMISSIONS) }
            }
        } else {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    0 -> AgendaPage(events, today, zone) { editing = it }
                    1 -> MonthPage(events, month, selected, today, zone, onMonth = { month = it }, onSelect = { selected = it }, onEdit = { editing = it })
                    else -> WeekPage(events, selected, today, zone, onShift = { selected = selected.plusDays(it * 7L); month = YearMonth.from(selected) }, onEdit = { editing = it })
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("＋", stringResource(R.string.cal_new)) {
                    if (!canRead) ask.launch(PERMISSIONS) else if (!canWrite) ask.launch(PERMISSIONS) else editing = newEvent(selected)
                },
                AppBarItem("⌂", stringResource(R.string.cal_today)) { selected = today; month = YearMonth.from(today) },
            ),
        )
    }
    editing?.let { event ->
        EventDialog(
            event, repository,
            onSave = { saved, all ->
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { if (saved.id == 0L) repository.insert(saved) != 0L else repository.update(saved, event, all) }
                    if (!ok) android.widget.Toast.makeText(context, R.string.cal_save_failed, android.widget.Toast.LENGTH_LONG).show()
                    version++
                    editing = null
                }
            },
            onDelete = { all -> scope.launch { withContext(Dispatchers.IO) { repository.delete(event, all) }; version++; editing = null } },
            onCancel = { editing = null },
        )
    }
}

private fun dayTitle(day: LocalDate, today: LocalDate): String =
    day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).let { if (day == today) "• $it" else it }

@Composable
private fun EventRow(event: CalEvent, day: LocalDate?, zone: ZoneId, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val context = LocalContext.current
    val time = if (event.allDay) stringResource(R.string.cal_all_day) else {
        val format = DateFormat.getTimeFormat(context)
        val first = CalendarLogic.days(event, zone).start
        // A multi-day event shows its time only on the days it starts and ends.
        format.format(Date(event.start)) + " – " + format.format(Date(event.end)).let { if (day != null && first != day && CalendarLogic.days(event, zone).endInclusive != day) "" else it }
    }
    Row(modifier.fillMaxWidth().testTag("cal:event").metroPress(onClick = onClick).padding(vertical = 6.dp)) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(if (event.color != 0) Color(event.color) else LocalAccent.current))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            MetroText(event.title.ifBlank { stringResource(R.string.cal_untitled) }, size = 21.sp)
            MetroText(
                listOf(time, event.location).filter { it.isNotBlank() }.joinToString("  ·  "), size = 14.sp, weight = FontWeight.Normal,
                color = colors.subtle,
            )
        }
    }
}

@Composable
private fun AgendaPage(events: List<CalEvent>, today: LocalDate, zone: ZoneId, onEdit: (CalEvent) -> Unit) {
    val days = remember(events, today) {
        generateSequence(today) { it.plusDays(1) }.take(120).map { it to CalendarLogic.on(events, it, zone) }.filter { it.second.isNotEmpty() }.toList()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (days.isEmpty()) PageNote(stringResource(R.string.cal_no_events), Modifier.testTag("cal:empty"))
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().scrollIndicator(listState).testTag("cal:agenda"), state = listState) {
            days.forEach { (day, list) ->
                sectionHeader("d:$day") { MetroText(dayTitle(day, today), size = 16.sp, weight = FontWeight.SemiBold, color = LocalAccent.current, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) }
                itemsIndexed(list, key = { _, it -> "e:$day:${it.id}:${it.start}" }) { index, it -> EventRow(it, day, zone, Modifier.featherRow(index)) { onEdit(it) } }
            }
        }
    }
}

@Composable
private fun MonthPage(
    events: List<CalEvent>,
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onEdit: (CalEvent) -> Unit,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val classic = LocalStyle.current.isClassic
    val first = remember { firstDayOfWeek() }
    val weeks = remember(month) { CalendarLogic.weeks(month, first) }
    val busy = remember(events, month) { CalendarLogic.busyDays(events, weeks.first().first()..weeks.last().last(), zone) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroText("‹", size = 34.sp, modifier = Modifier.testTag("cal:prev").metroPress(onClick = { onMonth(month.minusMonths(1)) }).padding(horizontal = 14.dp))
            MetroText(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + " " + month.year, size = 24.sp, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag("cal:month"),
            )
            MetroText("›", size = 34.sp, modifier = Modifier.testTag("cal:next").metroPress(onClick = { onMonth(month.plusMonths(1)) }).padding(horizontal = 14.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            weeks.first().forEach { d ->
                MetroText(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        weeks.forEach { row ->
            Row(Modifier.fillMaxWidth().height(46.dp)) {
                row.forEach { day ->
                    val inMonth = YearMonth.from(day) == month
                    val isSelected = day == selected
                    val isToday = day == today
                    // In Windows Phone today is the cell filled with the accent colour, a chosen day is outlined, and a bar marks a day with events.
                    val filled = if (classic) isToday else isSelected
                    Column(
                        Modifier.weight(1f).fillMaxHeight().padding(1.dp).testTag("cal:day:$day").metroPress(onClick = { onSelect(day); if (!inMonth) onMonth(YearMonth.from(day)) })
                            .background(if (filled) accent else Color.Transparent)
                            .then(if (classic && isSelected && !isToday) Modifier.border(2.dp, accent) else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        MetroText(
                            day.dayOfMonth.toString(), size = 19.sp, weight = if (day == today) FontWeight.SemiBold else FontWeight.Light,
                            color = when { filled -> onAccent(); classic -> if (inMonth) colors.foreground else colors.subtle; day == today -> accent; inMonth -> colors.foreground; else -> colors.subtle },
                        )
                        if (classic) Box(Modifier.padding(top = 2.dp).size(14.dp, 3.dp).background(if (day !in busy) Color.Transparent else if (filled) onAccent() else accent))
                        else MetroText(if (day in busy) "●" else " ", size = 8.sp, color = if (isSelected) onAccent() else accent)
                    }
                }
            }
        }
        MetroText(dayTitle(selected, today), size = 16.sp, weight = FontWeight.SemiBold, color = accent, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        val list = CalendarLogic.on(events, selected, zone)
        if (list.isEmpty()) PageNote(stringResource(R.string.cal_no_events), Modifier.testTag("cal:day:empty"))
        val listState = rememberLazyListState()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().scrollIndicator(listState).testTag("cal:day:list"), state = listState) {
            itemsIndexed(list, key = { _, it -> "${it.id}:${it.start}" }) { index, it -> EventRow(it, selected, zone, Modifier.featherRow(index)) { onEdit(it) } }
        }
    }
}

@Composable
private fun WeekPage(events: List<CalEvent>, selected: LocalDate, today: LocalDate, zone: ZoneId, onShift: (Int) -> Unit, onEdit: (CalEvent) -> Unit) {
    val days = remember(selected) { CalendarLogic.week(selected, firstDayOfWeek()) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroText("‹", size = 34.sp, modifier = Modifier.testTag("cal:week:prev").metroPress(onClick = { onShift(-1) }).padding(end = 14.dp))
            MetroText(days.first().format(formatter) + " – " + days.last().format(formatter), size = 18.sp, weight = FontWeight.Normal, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).testTag("cal:week"))
            MetroText("›", size = 34.sp, modifier = Modifier.testTag("cal:week:next").metroPress(onClick = { onShift(1) }).padding(start = 14.dp))
        }
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().scrollIndicator(listState).testTag("cal:weeklist"), state = listState) {
            days.forEach { day ->
                item(key = "w:$day") { MetroText(dayTitle(day, today), size = 16.sp, weight = FontWeight.SemiBold, color = if (day == today) LocalAccent.current else LocalMetroColors.current.subtle, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                itemsIndexed(CalendarLogic.on(events, day, zone), key = { _, it -> "w:$day:${it.id}:${it.start}" }) { index, it -> EventRow(it, day, zone, Modifier.featherRow(index)) { onEdit(it) } }
            }
        }
    }
}

@Composable
private fun EventDialog(event: CalEvent, repository: CalendarRepository, onSave: (CalEvent, Boolean) -> Unit, onDelete: (Boolean) -> Unit, onCancel: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val calendars by produceState(emptyList<CalendarInfo>()) { value = withContext(Dispatchers.IO) { repository.writableCalendars() } }
    val reminder0 by produceState(if (event.id == 0L) listOf(10) else emptyList<Int>(), event.id) { if (event.id != 0L) value = withContext(Dispatchers.IO) { repository.remindersOf(event.id) } }
    var title by remember(event.id, event.start) { mutableStateOf(event.title) }
    var location by remember(event.id, event.start) { mutableStateOf(event.location) }
    var notes by remember(event.id, event.start) { mutableStateOf(event.description) }
    var allDay by remember(event.id, event.start) { mutableStateOf(event.allDay) }
    val span = CalendarLogic.days(event, zone)
    var startMs by remember(event.id, event.start) { mutableStateOf(if (event.allDay) span.start.atTime(9, 0).atZone(zone).toInstant().toEpochMilli() else event.start) }
    var endMs by remember(event.id, event.start) { mutableStateOf(if (event.allDay) startMs + 3_600_000L else event.end) }
    var firstDay by remember(event.id, event.start) { mutableStateOf(span.start) }
    var lastDay by remember(event.id, event.start) { mutableStateOf(span.endInclusive) }
    var calendarId by remember(event.id) { mutableStateOf(event.calendarId) }
    var reminder by remember(event.id) { mutableStateOf<List<Int>?>(null) }
    // Of a repeating event only the time that was opened is changed, unless the user says all of them.
    var all by remember(event.id) { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val chosenCalendar = calendarId.takeIf { it != 0L } ?: calendars.firstOrNull()?.id ?: 0L
    val chosenReminders = reminder ?: reminder0
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    DialogFrame(onCancel = onCancel) {
        MetroTextField(title, { title = it.take(200) }, stringResource(R.string.cal_title_hint), Modifier.testTag("cal:edit:title"))
        MetroToggle(stringResource(R.string.cal_all_day_toggle), allDay, { allDay = it }, Modifier.testTag("cal:edit:allday"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroText(stringResource(R.string.cal_starts), size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.width(64.dp))
            PageChip(if (allDay) firstDay.format(dateFormat) else whenText(startMs), true, "cal:edit:start") { picking = "start" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroText(stringResource(R.string.cal_ends), size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.width(64.dp))
            PageChip(if (allDay) lastDay.format(dateFormat) else whenText(endMs), true, "cal:edit:end") { picking = "end" }
        }
        MetroTextField(location, { location = it.take(200) }, stringResource(R.string.cal_location_hint), Modifier.testTag("cal:edit:location"))
        MetroTextField(notes, { notes = it.take(2000) }, stringResource(R.string.cal_notes_hint), Modifier.testTag("cal:edit:notes"), singleLine = false)
        if (calendars.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(calendars) { c -> PageChip(c.name.ifBlank { c.account }, chosenCalendar == c.id, "cal:edit:cal:${c.id}") { calendarId = c.id } } }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(-1, 10, 30, 60, 1440)) { minutes ->
                PageChip(stringResource(reminderName(minutes)), if (minutes < 0) chosenReminders.isEmpty() else minutes in chosenReminders, "cal:edit:rem:$minutes") { reminder = CalendarLogic.toggleReminder(chosenReminders, minutes) }
            }
        }
        if (event.recurring) {
            PageNote(stringResource(R.string.cal_repeats_note))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PageChip(stringResource(R.string.cal_only_this), !all, "cal:edit:only") { all = false }
                PageChip(stringResource(R.string.cal_all_events), all, "cal:edit:all") { all = true }
            }
        }
        MetroButton(stringResource(R.string.todo_save), Modifier.fillMaxWidth().testTag("cal:edit:save")) {
            if (chosenCalendar == 0L) return@MetroButton
            val (s, e) = if (allDay) CalendarLogic.allDayRange(firstDay, lastDay) else startMs to maxOf(endMs, startMs + 60_000L)
            onSave(event.copy(title = title.trim(), location = location.trim(), description = notes.trim(), allDay = allDay, start = s, end = e, calendarId = chosenCalendar, reminders = chosenReminders), all)
        }
        if (event.id != 0L) MetroButton(stringResource(if (confirm) R.string.notes_delete_confirm else R.string.todo_delete), Modifier.fillMaxWidth().testTag("cal:edit:delete")) {
            if (confirm) onDelete(all) else confirm = true
        }
    }
    when (picking) {
        "start" -> DateTimeDialog(
            if (allDay) firstDay.atStartOfDay(zone).toInstant().toEpochMilli() else startMs, dateOnly = allDay,
            onChoose = { ms ->
                if (allDay) { firstDay = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(); if (lastDay.isBefore(firstDay)) lastDay = firstDay }
                else { val length = endMs - startMs; startMs = ms; endMs = ms + length.coerceAtLeast(60_000L) }
                picking = null
            },
            onCancel = { picking = null },
        )
        "end" -> DateTimeDialog(
            if (allDay) lastDay.atStartOfDay(zone).toInstant().toEpochMilli() else endMs, dateOnly = allDay,
            onChoose = { ms ->
                if (allDay) lastDay = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().let { if (it.isBefore(firstDay)) firstDay else it }
                else endMs = maxOf(ms, startMs + 60_000L)
                picking = null
            },
            onCancel = { picking = null },
        )
    }
}

private fun reminderName(minutes: Int) = when (minutes) {
    -1 -> R.string.cal_rem_none
    10 -> R.string.cal_rem_10
    30 -> R.string.cal_rem_30
    60 -> R.string.cal_rem_60
    else -> R.string.cal_rem_day
}
