package com.metrowin.launcher.calendar

import com.metrowin.launcher.ui.components.MarkIcon
import com.metrowin.launcher.ui.components.Mark
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.todo.TodoQuick
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.mini.DateTimeDialog
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the form hands back when the user saves. [touchReminders] is false when the reminders of the event could not be read: they are then left as they are. */
class Saved(val event: CalEvent, val all: Boolean, val rule: String?, val touchReminders: Boolean)

/** The colours an event can be given, besides the one of its calendar (the Windows Phone accents). */
val EVENT_COLORS = listOf(0xFFE51400.toInt(), 0xFFFA6800.toInt(), 0xFFF0A30A.toInt(), 0xFF60A917.toInt(), 0xFF00ABA9.toInt(), 0xFF1BA1E2.toInt(), 0xFF0050EF.toInt(), 0xFFAA00FF.toInt())

@Composable
fun reminderText(minutes: Int): String = when {
    minutes < 0 -> stringResource(R.string.cal_rem_none)
    minutes == 10 -> stringResource(R.string.cal_rem_10)
    minutes == 30 -> stringResource(R.string.cal_rem_30)
    minutes == 60 -> stringResource(R.string.cal_rem_60)
    minutes == 1440 -> stringResource(R.string.cal_rem_day)
    minutes % 1440 == 0 -> stringResource(R.string.cal_rem_days, minutes / 1440)
    minutes % 60 == 0 -> stringResource(R.string.cal_rem_hours, minutes / 60)
    else -> stringResource(R.string.cal_rem_minutes, minutes)
}

/** The reminders chosen in the form (nothing chosen yet is null, which keeps those that were read), kept while the screen is recreated. */
private val RemindersSaver = Saver<List<Int>?, IntArray>(save = { it?.toIntArray() ?: intArrayOf(-1) }, restore = { if (it.contentEquals(intArrayOf(-1))) null else it.toList() })

/**
 * The page of one event (a whole page, not a small window: it is long): title, times, place, notes, calendar, colour, reminders, how it repeats
 * (a new one), and who is invited. A calendar that cannot be changed gets [EventView] instead. The reminders are read first: until they are,
 * saving waits, so an event is never saved over reminders that were not seen.
 */
@Composable
fun EventForm(event: CalEvent, repository: CalendarRepository, onSave: (Saved) -> Unit, onDelete: (Boolean) -> Unit, onCancel: () -> Unit, onJoin: (String) -> Unit) {
    val env = LocalCal.current
    val zone = env.zone
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val writable = remember(env.calendars) { env.calendars.filter { it.writable } }
    // (done, minutes): not done yet while they are being read; done with null minutes when they could not be read.
    val reminders by produceState(if (event.id == 0L) true to listOf(CalendarDefaults.REMINDERS.first()) else false to null, event.id) {
        if (event.id != 0L) value = true to withContext(Dispatchers.IO) { repository.remindersOf(event.id) }
    }
    val loadedReminders = reminders.second
    val readFailed = reminders.first && loadedReminders == null
    var title by rememberSaveable(event.id, event.start) { mutableStateOf(event.title) }
    var location by rememberSaveable(event.id, event.start) { mutableStateOf(event.location) }
    var notes by rememberSaveable(event.id, event.start) { mutableStateOf(event.description) }
    var allDay by rememberSaveable(event.id, event.start) { mutableStateOf(event.allDay) }
    val span = CalendarLogic.days(event, zone)
    var startMs by rememberSaveable(event.id, event.start) { mutableStateOf(if (event.allDay) CalendarLogic.timeAt(span.start, CalendarDefaults.START_HOUR * 60, zone) else event.start) }
    var endMs by rememberSaveable(event.id, event.start) { mutableStateOf(if (event.allDay) startMs + CalendarDefaults.DURATION_MS else event.end) }
    var firstDay by rememberSaveable(event.id, event.start, stateSaver = DaySaver) { mutableStateOf(span.start) }
    var lastDay by rememberSaveable(event.id, event.start, stateSaver = DaySaver) { mutableStateOf(span.endInclusive) }
    var calendarId by rememberSaveable(event.id) { mutableStateOf(event.calendarId) }
    var color by rememberSaveable(event.id) { mutableIntStateOf(event.ownColor) }
    var reminder by rememberSaveable(event.id, stateSaver = RemindersSaver) { mutableStateOf<List<Int>?>(null) }
    var repeat by rememberSaveable(event.id) { mutableStateOf(Repeat.None) }
    // Of a repeating event only the time that was opened is changed, unless the user says all of them.
    var all by rememberSaveable(event.id) { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val chosenCalendar = calendarId.takeIf { it != 0L } ?: writable.firstOrNull()?.id ?: 0L
    val chosenReminders = reminder ?: loadedReminders ?: emptyList()
    val remindersKnown = loadedReminders != null
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val link = CalendarLogic.conferenceLink(location, notes)
    val colors = LocalMetroColors.current
    val account = env.calendars.firstOrNull { it.id == event.calendarId }?.account.orEmpty()

    fun save() {
        if (chosenCalendar == 0L) { MetroToast.show(context, R.string.cal_no_calendar); return }
        if (!remindersKnown && !readFailed) return
        val (s, e) = if (allDay) CalendarLogic.allDayRange(firstDay, lastDay) else startMs to maxOf(endMs, startMs + CalendarDefaults.MIN_EVENT_MS)
        onSave(Saved(event.copy(title = title.trim(), location = location.trim(), description = notes.trim(), allDay = allDay, start = s, end = e, calendarId = chosenCalendar, reminders = chosenReminders, ownColor = color, timeZone = event.timeZone), all, repeat.rule.takeIf { event.id == 0L }, remindersKnown))
    }

    MiniFrame {
        MetroText(stringResource(if (event.id == 0L) R.string.cal_new_event else R.string.cal_event), size = 34.sp, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp).testTag("cal:form:title"))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetroTextField(title, { title = it.take(200) }, stringResource(R.string.cal_title_hint), Modifier.testTag("cal:edit:title"))
            MetroToggle(stringResource(R.string.cal_all_day_toggle), allDay, {
                if (it) { firstDay = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate(); lastDay = Instant.ofEpochMilli(maxOf(endMs - 1, startMs)).atZone(zone).toLocalDate().let { d -> if (d < firstDay) firstDay else d } }
                allDay = it
            }, Modifier.testTag("cal:edit:allday"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText(stringResource(R.string.cal_starts), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.width(64.dp))
                PageChip(if (allDay) firstDay.format(dateFormat) else whenText(startMs), true, "cal:edit:start") { picking = "start" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetroText(stringResource(R.string.cal_ends), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.width(64.dp))
                PageChip(if (allDay) lastDay.format(dateFormat) else whenText(endMs), true, "cal:edit:end") { picking = "end" }
            }
            // The event was written in another zone than the one of the phone: the times above are those of the phone.
            if (!allDay && event.timeZone.isNotBlank() && event.timeZone != zone.id) PageNote(stringResource(R.string.cal_zone_note, event.timeZone))
            MetroTextField(location, { location = it.take(200) }, stringResource(R.string.cal_location_hint), Modifier.testTag("cal:edit:location"))
            if (link != null) MetroButton(stringResource(R.string.cal_join), Modifier.fillMaxWidth().testTag("cal:edit:join")) { onJoin(link) }
            MetroTextField(notes, { notes = it.take(2000) }, stringResource(R.string.cal_notes_hint), Modifier.testTag("cal:edit:notes"), singleLine = false)
            if (writable.size > 1) {
                Label(R.string.cal_calendar)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(writable) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(Color(c.color or 0xFF000000.toInt())))
                            PageChip(c.name.ifBlank { c.account }, chosenCalendar == c.id, "cal:edit:cal:${c.id}", Modifier.padding(start = 2.dp)) { calendarId = c.id }
                        }
                    }
                }
            }
            Label(R.string.cal_color)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { PageChip(stringResource(R.string.cal_color_default), color == 0, "cal:edit:color:0") { color = 0 } }
                itemsIndexed(EVENT_COLORS) { i, c ->
                    val name = stringResource(R.string.cal_color_n, i + 1)
                    Box(Modifier.testTag("cal:edit:color:$c").size(44.dp).background(Color(c)).metroPress(onClick = { color = c }).semantics { contentDescription = name; selected = color == c }, contentAlignment = Alignment.Center) {
                        if (color == c) MarkIcon(Mark.Check, 20.dp, color = com.metrowin.launcher.ui.theme.ColorMath.inkOn(Color(c)), modifier = Modifier.clearAndSetSemantics { })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { CalIconView(CalIcon.Bell, 16.dp); Label(R.string.cal_reminders, Modifier.padding(start = 6.dp)) }
            if (!remindersKnown && !readFailed) PageNote(stringResource(R.string.cal_loading))
            if (readFailed) PageNote(stringResource(R.string.cal_reminders_unread))
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val shown = (listOf(-1) + CalendarDefaults.REMINDERS + chosenReminders.filter { it !in CalendarDefaults.REMINDERS }.sorted()).distinct()
                items(shown) { minutes ->
                    PageChip(reminderText(minutes), if (minutes < 0) chosenReminders.isEmpty() else minutes in chosenReminders, "cal:edit:rem:$minutes") { if (remindersKnown) reminder = CalendarLogic.toggleReminder(chosenReminders, minutes) }
                }
                item { PageChip("＋", false, "cal:edit:rem:custom") { if (remindersKnown) custom = true } }
            }
            if (event.id == 0L && env.options[Option.RepeatNew]) {
                Row(verticalAlignment = Alignment.CenterVertically) { CalIconView(CalIcon.Repeat, 16.dp); Label(R.string.cal_repeat, Modifier.padding(start = 6.dp)) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Repeat.entries) { r -> PageChip(stringResource(repeatName(r)), repeat == r, "cal:edit:repeat:${r.name}") { repeat = r } }
                }
            }
            if (event.recurring) {
                PageNote(stringResource(R.string.cal_repeats_note))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PageChip(stringResource(R.string.cal_only_this), !all, "cal:edit:only") { all = false }
                    PageChip(stringResource(R.string.cal_all_events), all, "cal:edit:all") { all = true }
                }
            }
            if (event.id != 0L && env.options[Option.Attendees]) Attendees(event, repository, account)
            Box(Modifier.padding(bottom = 8.dp))
        }
        AppBar(
            listOf(
                AppBarItem("✓", stringResource(R.string.todo_save)) { save() },
                AppBarItem("✕", stringResource(R.string.cal_cancel)) { onCancel() },
            ),
            menu = if (event.id != 0L) listOf(
                AppBarItem("☑", stringResource(R.string.cal_to_todo)) { toTodo(context, event) },
                AppBarItem("−", stringResource(if (confirm) R.string.notes_delete_confirm else R.string.todo_delete)) { if (confirm) onDelete(all) else confirm = true },
            ) else emptyList(),
        )
    }
    if (custom) CustomReminder(onChoose = { m -> reminder = (chosenReminders + m).distinct().sorted(); custom = false }, onCancel = { custom = false })
    when (picking) {
        "start" -> DateTimeDialog(
            if (allDay) firstDay.atStartOfDay(zone).toInstant().toEpochMilli() else startMs, dateOnly = allDay,
            onChoose = { ms ->
                if (allDay) { firstDay = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(); if (lastDay.isBefore(firstDay)) lastDay = firstDay }
                else { val length = endMs - startMs; startMs = ms; endMs = ms + length.coerceAtLeast(CalendarDefaults.MIN_EVENT_MS) }
                picking = null
            },
            onCancel = { picking = null },
        )
        "end" -> DateTimeDialog(
            if (allDay) lastDay.atStartOfDay(zone).toInstant().toEpochMilli() else endMs, dateOnly = allDay,
            onChoose = { ms ->
                if (allDay) lastDay = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().let { if (it.isBefore(firstDay)) firstDay else it }
                else endMs = maxOf(ms, startMs + CalendarDefaults.MIN_EVENT_MS)
                picking = null
            },
            onCancel = { picking = null },
        )
    }
}

@Composable
private fun Label(text: Int, modifier: Modifier = Modifier) =
    MetroText(stringResource(text), size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = modifier)

private fun repeatName(r: Repeat) = when (r) {
    Repeat.None -> R.string.cal_repeat_none
    Repeat.Daily -> R.string.cal_repeat_daily
    Repeat.Weekly -> R.string.cal_repeat_weekly
    Repeat.Monthly -> R.string.cal_repeat_monthly
    Repeat.Yearly -> R.string.cal_repeat_yearly
}

/** The guests, and for the user the buttons that answer the invitation. */
@Composable
private fun Attendees(event: CalEvent, repository: CalendarRepository, account: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val guests by produceState(emptyList<Attendee>(), event.id, version) { value = withContext(Dispatchers.IO) { repository.attendeesOf(event.id) } }
    if (guests.isEmpty()) return
    val me = guests.firstOrNull { it.email.equals(account, true) }
    Row(verticalAlignment = Alignment.CenterVertically) { CalIconView(CalIcon.People, 16.dp); Label(R.string.cal_guests, Modifier.padding(start = 6.dp)) }
    guests.take(30).forEach { g ->
        val mark = when (g.status) { 1 -> "✓"; 2 -> "✕"; 4 -> "?"; else -> "…" }
        MetroText("$mark  " + g.name.ifBlank { g.email }, size = 15.sp, weight = FontWeight.Normal, maxLines = 1)
    }
    if (guests.size > 30) PageNote("+${guests.size - 30}")
    if (me != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((status, label) in listOf(1 to R.string.cal_rsvp_yes, 4 to R.string.cal_rsvp_maybe, 2 to R.string.cal_rsvp_no)) {
            PageChip(stringResource(label), me.status == status, "cal:edit:rsvp:$status") {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { repository.answer(event.id, me.email, status) }
                    if (!ok) MetroToast.show(context, R.string.cal_save_failed) else version++
                }
            }
        }
    }
}

@Composable
private fun CustomReminder(onChoose: (Int) -> Unit, onCancel: () -> Unit) {
    var number by remember { mutableStateOf("") }
    var unit by remember { mutableIntStateOf(1) }
    DialogFrame(onCancel = onCancel, title = stringResource(R.string.cal_rem_custom)) {
        MetroTextField(number, { number = it.filter(Char::isDigit).take(5) }, stringResource(R.string.cal_rem_number), Modifier.testTag("cal:rem:number"), keyboardType = KeyboardType.Number)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            PageChip(stringResource(R.string.cal_unit_minutes), unit == 1, "cal:rem:min") { unit = 1 }
            PageChip(stringResource(R.string.cal_unit_hours), unit == 60, "cal:rem:hour") { unit = 60 }
            PageChip(stringResource(R.string.cal_unit_days), unit == 1440, "cal:rem:day") { unit = 1440 }
        }
        MetroButton(stringResource(R.string.todo_save), Modifier.fillMaxWidth().testTag("cal:rem:ok")) {
            val minutes = (number.toLongOrNull() ?: 0L) * unit
            if (minutes in 1..CalendarDefaults.MAX_REMINDER_MIN) onChoose(minutes.toInt())
        }
    }
}

/** The page of an event that cannot be changed (a subscribed calendar, a birthday): what it says, and the way to answer an invitation or join a call. */
@Composable
fun EventView(event: CalEvent, repository: CalendarRepository, onCancel: () -> Unit, onJoin: (String) -> Unit) {
    val context = LocalContext.current
    val env = LocalCal.current
    val colors = LocalMetroColors.current
    val link = CalendarLogic.conferenceLink(event.location, event.description)
    val span = CalendarLogic.days(event, env.zone)
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    val account = env.calendars.firstOrNull { it.id == event.calendarId }?.account.orEmpty()
    MiniFrame {
        MetroText(event.title.ifBlank { stringResource(R.string.cal_untitled) }, size = 34.sp, lineHeight = 40.sp, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp).testTag("cal:view:title"))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageNote(stringResource(if (event.birthday) R.string.cal_birthday_note else R.string.cal_readonly_note))
            MetroText(
                if (event.allDay) span.start.format(dateFormat) + if (span.endInclusive != span.start) " – " + span.endInclusive.format(dateFormat) else ""
                else whenText(event.start) + " – " + env.time(event.end), size = 18.sp, weight = FontWeight.Normal,
            )
            if (event.location.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) { CalIconView(CalIcon.Place, 16.dp); MetroText(event.location, size = 17.sp, weight = FontWeight.Normal, modifier = Modifier.padding(start = 6.dp)) }
            if (link != null) MetroButton(stringResource(R.string.cal_join), Modifier.fillMaxWidth()) { onJoin(link) }
            if (event.description.isNotBlank()) MetroText(event.description, size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
            if (event.calendarName.isNotBlank()) PageNote(event.calendarName)
            if (!event.birthday && env.options[Option.Attendees]) Attendees(event, repository, account)
        }
        AppBar(listOf(AppBarItem("✕", stringResource(R.string.cal_close)) { onCancel() }), menu = listOf(AppBarItem("☑", stringResource(R.string.cal_to_todo)) { toTodo(context, event) }))
    }
}

/** A thing to do for [event]: its title, on the day it starts, with its place and notes. */
private fun toTodo(context: android.content.Context, event: CalEvent) {
    val note = listOf(event.location, event.description).filter { it.isNotBlank() }.joinToString("\n")
    val ok = TodoQuick.add(context, event.title.ifBlank { context.getString(R.string.cal_untitled) }, event.start, note)
    MetroToast.show(context, if (ok) R.string.cal_todo_done else R.string.todo_full)
}
