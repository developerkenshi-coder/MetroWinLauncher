package com.metrowin.launcher.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.components.sectionHeader
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The switches. A switch that needs the contacts asks for the permission first: [onBirthdays] says the new state and decides. */
@Composable
fun OptionsDialog(options: CalendarOptions, onBirthdays: (Boolean) -> Unit, onClose: () -> Unit) {
    DialogFrame(onCancel = onClose, title = stringResource(R.string.cal_options)) {
        Column(Modifier.height(420.dp)) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(Option.entries.toList()) { o ->
                    MetroToggle(stringResource(o.label), options[o], { if (o == Option.Birthdays) onBirthdays(it) else options.set(o, it) }, Modifier.testTag("cal:opt:${o.key}"))
                }
            }
        }
    }
}

/** The calendars of the phone: which are shown, and a colour for each (these choices live in the app, the calendars themselves are not touched). */
@Composable
fun CalendarsDialog(calendars: List<CalendarInfo>, options: CalendarOptions, onClose: () -> Unit) {
    DialogFrame(onCancel = onClose, title = stringResource(R.string.cal_calendars)) {
        if (calendars.isEmpty()) PageNote(stringResource(R.string.cal_no_calendars))
        Column(Modifier.height(380.dp)) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(calendars) { c ->
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(Color((options.colors[c.id] ?: c.color) or 0xFF000000.toInt())))
                            MetroToggle(c.name.ifBlank { c.account } + if (!c.writable) "  ·  " + stringResource(R.string.cal_read_only) else "", c.id !in options.hidden, { options.toggleHidden(c.id) }, Modifier.padding(start = 8.dp).testTag("cal:cals:${c.id}"))
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { PageChip(stringResource(R.string.cal_color_default), options.colors[c.id] == null, "cal:cals:color:${c.id}:0") { options.setColor(c.id, null) } }
                            items(EVENT_COLORS) { col -> Box(Modifier.testTag("cal:cals:color:${c.id}:$col").size(36.dp).background(Color(col)).metroPress(onClick = { options.setColor(c.id, col) })) }
                        }
                    }
                }
            }
        }
    }
}

/** Search of the events: a box and the events that have the words, the first from a year ago, in days. */
@Composable
fun SearchPage(repository: CalendarRepository, today: LocalDate, zone: ZoneId, hidden: Set<Long>, onEdit: (CalEvent) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<CalEvent>()) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        val from = today.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusYears(2).atStartOfDay(zone).toInstant().toEpochMilli()
        results = withContext(Dispatchers.IO) { CalendarLogic.visible(repository.search(query.trim(), from, to), hidden) }
    }
    val grouped = remember(results, zone) { results.groupBy { CalendarLogic.days(it, zone).start }.toSortedMap().toList() }
    val accent = LocalAccent.current
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        MetroTextField(query, { query = it.take(100) }, stringResource(R.string.cal_search_hint), Modifier.padding(bottom = 8.dp).testTag("cal:search"))
        if (query.trim().length >= 2 && results.isEmpty()) PageNote(stringResource(R.string.cal_search_none))
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().scrollIndicator(listState).testTag("cal:search:list"), state = listState) {
            grouped.forEach { (day, list) ->
                sectionHeader("s:$day") { MetroText(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), size = 16.sp, weight = FontWeight.SemiBold, color = accent, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                itemsIndexed(list, key = { _, it -> "r:$day:${it.id}:${it.start}" }) { index, it -> EventRow(it, day, Modifier.featherRow(index)) { onEdit(it) } }
            }
        }
    }
}

/** The free stretches of the day: touching one starts an event in it. */
@Composable
fun FreeTimeDialog(slots: List<Pair<Int, Int>>, day: LocalDate, zone: ZoneId, onPick: (Long, Long) -> Unit, onClose: () -> Unit) {
    val env = LocalCal.current
    DialogFrame(onCancel = onClose, title = stringResource(R.string.cal_free_time)) {
        PageNote(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
        if (slots.isEmpty()) PageNote(stringResource(R.string.cal_free_none), Modifier.padding(top = 6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            slots.forEach { (s, e) ->
                val start = CalendarLogic.timeAt(day, s, zone)
                val end = CalendarLogic.timeAt(day, e, zone)
                PageChip(env.time(start) + " – " + env.time(end) + "  (" + stringResource(R.string.cal_minutes_n, e - s) + ")", false, "cal:free:$s") { onPick(start, minOf(end, start + CalendarDefaults.DURATION_MS)) }
            }
        }
    }
}

/** The zones that can be shown beside the hours: the ones with a region and a city, narrowed by what is typed. */
@Composable
fun ZoneDialog(current: String, onChoose: (String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember { ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }.sorted() }
    val shown = remember(query) { all.filter { query.isBlank() || query.trim().replace(' ', '_').let { q -> it.contains(q, true) } }.take(40) }
    DialogFrame(onCancel = onClose, title = stringResource(R.string.cal_second_zone)) {
        MetroTextField(query, { query = it.take(40) }, stringResource(R.string.cal_zone_hint), Modifier.testTag("cal:zone:query"))
        PageChip(stringResource(R.string.cal_zone_none), current.isBlank(), "cal:zone:none", Modifier.padding(top = 8.dp)) { onChoose("") }
        Column(Modifier.height(260.dp)) {
            LazyColumn(Modifier.fillMaxSize()) { items(shown) { id -> PageChip(id.replace('_', ' '), id == current, "cal:zone:$id", Modifier.padding(top = 4.dp).fillMaxWidth()) { onChoose(id) } } }
        }
    }
}

/** What an import found: how many events, and in which calendar to write them. */
@Composable
fun ImportDialog(found: List<IcsEvent>, calendars: List<CalendarInfo>, onImport: (Long) -> Unit, onClose: () -> Unit) {
    var chosen by remember { mutableStateOf(calendars.firstOrNull()?.id ?: 0L) }
    DialogFrame(onCancel = onClose, title = stringResource(R.string.cal_import)) {
        PageNote(stringResource(R.string.cal_import_found, found.size))
        if (calendars.isEmpty()) PageNote(stringResource(R.string.cal_no_calendar))
        else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(calendars) { c -> PageChip(c.name.ifBlank { c.account }, chosen == c.id, "cal:import:cal:${c.id}") { chosen = c.id } }
            }
            if (found.isNotEmpty()) MetroButton(stringResource(R.string.cal_import_do), Modifier.fillMaxWidth().testTag("cal:import:ok")) { onImport(chosen) }
        }
    }
}

/** The tools of the "…" menu. */
@Composable
fun ToolsDialog(zone: String, onImport: () -> Unit, onExport: () -> Unit, onFree: () -> Unit, onZone: () -> Unit, onClose: () -> Unit) {
    DialogFrame(onCancel = onClose, title = stringResource(R.string.cal_tools)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroButton(stringResource(R.string.cal_free_time), Modifier.fillMaxWidth().testTag("cal:tool:free")) { onFree() }
            MetroButton(stringResource(R.string.cal_import), Modifier.fillMaxWidth().testTag("cal:tool:import")) { onImport() }
            MetroButton(stringResource(R.string.cal_export), Modifier.fillMaxWidth().testTag("cal:tool:export")) { onExport() }
            MetroButton(stringResource(R.string.cal_second_zone) + if (zone.isNotBlank()) ": " + zone.substringAfter('/').replace('_', ' ') else "", Modifier.fillMaxWidth().testTag("cal:tool:zone")) { onZone() }
        }
    }
}
