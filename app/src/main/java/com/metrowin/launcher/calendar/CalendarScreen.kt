package com.metrowin.launcher.calendar

import com.metrowin.launcher.ui.components.PageSwitch
import com.metrowin.launcher.ui.components.isWide
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.data.DataChanges
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.theme.LocalHighContrast
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

private enum class Page { Agenda, Month, Week, Day, Year }

private class Loaded(val events: List<CalEvent>, val truncated: Boolean)

private fun firstDayOfWeek(): DayOfWeek = DayOfWeek.of((Calendar.getInstance().firstDayOfWeek - 2 + 7) % 7 + 1)

private val DaySaver = Saver<LocalDate, Long>(save = { it.toEpochDay() }, restore = { LocalDate.ofEpochDay(it) })
private val MonthSaver = Saver<YearMonth, String>(save = { it.toString() }, restore = { YearMonth.parse(it) })

@Composable
fun CalendarScreen(initialDay: LocalDate? = null, draft: Pair<String, String>? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CalendarRepository(context) }
    val options = remember { CalendarOptions(context) }

    // Read and write are asked for apart: the form of a new event asks for write when it is needed.
    var canRead by remember { mutableStateOf(LivePermissions.isGranted(context, Manifest.permission.READ_CALENDAR)) }
    var canWrite by remember { mutableStateOf(LivePermissions.isGranted(context, Manifest.permission.WRITE_CALENDAR)) }
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    var is24 by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var selected by rememberSaveable(stateSaver = DaySaver) { mutableStateOf(initialDay ?: LocalDate.now()) }
    var month by rememberSaveable(stateSaver = MonthSaver) { mutableStateOf(YearMonth.now()) }
    var version by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<CalEvent?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var imported by remember { mutableStateOf<List<IcsEvent>?>(null) }

    fun refreshToday() {
        val now = LocalDate.now()
        if (now != today) { if (selected == today) { selected = now; month = YearMonth.from(now) }; today = now }
    }
    // What can change while the app is away: the permissions (the user may grant them in the settings), the day, the zone, the clock.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canRead = LivePermissions.isGranted(context, Manifest.permission.READ_CALENDAR)
        canWrite = LivePermissions.isGranted(context, Manifest.permission.WRITE_CALENDAR)
        zone = ZoneId.systemDefault(); is24 = DateFormat.is24HourFormat(context)
        refreshToday(); version++
    }
    // The day changes at midnight while the app is open: the agenda, the line of "now" and the day marked follow.
    LaunchedEffect(today, zone) {
        val next = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        delay((next - System.currentTimeMillis()).coerceAtLeast(1_000L) + 500L)
        refreshToday()
    }

    val askRead = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canRead = it || LivePermissions.isGranted(context, Manifest.permission.READ_CALENDAR) }
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canWrite = it || LivePermissions.isGranted(context, Manifest.permission.WRITE_CALENDAR) }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> options.set(Option.Birthdays, granted); version++ }

    // The calendar app of the phone (or an account sync) changes events while this is open: the lists are read again, a moment after the last change.
    LaunchedEffect(canRead) { if (canRead) DataChanges.flow(context, DataChanges.Source.Calendar).collect { version++ } }

    val calendars by produceState(emptyList<CalendarInfo>(), canRead, version) { value = if (canRead) withContext(Dispatchers.IO) { repository.calendars() } else emptyList() }

    // What the pages need: from the month before the one shown to a few months after, four months from today for the agenda, and the year when it has a page.
    var from = month.minusMonths(1).atDay(1)
    var to = maxOf(month.plusMonths(2).atDay(1), today.plusDays(CalendarDefaults.AGENDA_DAYS.toLong()))
    if (options[Option.YearView]) { from = minOf(from, LocalDate.of(month.year, 1, 1)); to = maxOf(to, LocalDate.of(month.year + 1, 1, 1)) }
    val birthdayLabel = stringResource(R.string.cal_birthdays)
    val birthdaysOn = options[Option.Birthdays]
    val loaded by produceState(Loaded(emptyList(), false), canRead, from, to, version, zone, birthdaysOn) {
        value = if (canRead) withContext(Dispatchers.IO) {
            val read = repository.events(from.atStartOfDay(zone).toInstant().toEpochMilli(), to.atStartOfDay(zone).toInstant().toEpochMilli())
            val extra = if (birthdaysOn) Birthdays.read(context, from..to.minusDays(1), birthdayLabel) else emptyList()
            Loaded((read + extra).sortedBy { it.start }, read.size >= CalendarDefaults.MAX_EVENTS)
        } else Loaded(emptyList(), false)
    }
    val shown = remember(loaded, options.hidden, options[Option.HideDeclined]) { CalendarLogic.visible(loaded.events, options.hidden, !options[Option.HideDeclined]) }

    val locale = Locale.getDefault()
    val timeFormat = remember(is24, locale) { DateFormat.getTimeFormat(context) }
    val first = remember(locale) { firstDayOfWeek() }
    val env = remember(options, today, zone, first, is24, timeFormat, calendars) { CalendarEnv(options, today, zone, first, is24, timeFormat, calendars) }

    val wide = isWide() && options[Option.SplitWide]
    val pages = remember(wide, options[Option.YearView]) { buildList { add(Page.Agenda); if (!wide) add(Page.Month); add(Page.Week); add(Page.Day); if (options[Option.YearView]) add(Page.Year) } }
    val pager = rememberPagerState { pages.size }

    fun newEvent(day: LocalDate, hour: Int? = null): CalEvent {
        val start = when {
            hour != null -> CalendarLogic.timeAt(day, hour * 60, zone)
            day == today -> CalendarLogic.nextHour(System.currentTimeMillis(), zone)
            else -> CalendarLogic.timeAt(day, CalendarDefaults.START_HOUR * 60, zone)
        }
        return CalEvent(0L, 0L, "", start, start + CalendarDefaults.DURATION_MS, false)
    }
    fun startNew(event: CalEvent) {
        if (!canRead) askRead.launch(Manifest.permission.READ_CALENDAR) else if (!canWrite) askWrite.launch(Manifest.permission.WRITE_CALENDAR) else editing = event
    }
    // A new event with the words of a message: the form opens on it as soon as the calendar can be written (the permissions are asked first).
    var pendingDraft by remember { mutableStateOf(draft) }
    LaunchedEffect(canRead, canWrite) {
        pendingDraft?.let { (title, note) ->
            if (canRead && canWrite) pendingDraft = null
            startNew(newEvent(today).copy(title = title, description = note))
        }
    }
    fun open(day: LocalDate) {
        selected = day; month = YearMonth.from(day)
        scope.launch { pager.animateScrollToPage(pages.indexOf(Page.Day).coerceAtLeast(0)) }
    }
    fun failure(): String = context.getString(when (repository.failure) { Failure.Permission -> R.string.cal_fail_permission; Failure.Other -> R.string.cal_fail_other; else -> R.string.cal_save_failed })
    fun join(link: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) scope.launch {
            val list = shown.filter { !it.birthday }
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(Ics.write(list).toByteArray()) } != null }.getOrDefault(false) }
            MetroToast.show(context, if (ok) context.getString(R.string.cal_exported, list.size) else context.getString(R.string.cal_fail_other))
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { Ics.parse(Ics.readBounded(it), zone) } }.getOrNull().orEmpty() }
            if (found.isEmpty()) MetroToast.show(context, R.string.cal_import_none) else imported = found
        }
    }

    BackHandler(enabled = editing != null || searching || dialog != null || imported != null) {
        if (dialog != null) dialog = null else if (imported != null) imported = null else if (editing != null) editing = null else searching = false
    }

    // The large text of the switch makes every text of the calendar a quarter bigger, over what the phone already asks for.
    val density = LocalDensity.current
    val colors = LocalMetroColors.current
    val colorsHere = if (LocalHighContrast.current) colors.copy(subtle = colors.foreground) else colors
    CompositionLocalProvider(
        LocalCal provides env,
        LocalMetroColors provides colorsHere,
        LocalDensity provides if (options[Option.LargeText]) Density(density.density, density.fontScale * 1.25f) else density,
    ) {
        Box(Modifier.fillMaxSize()) {
            PageSwitch(
                page = editing, onBack = { editing = null },
                list = {
                    MiniFrame(panes = wide) {
                        val titles = pages.map { p ->
                            when (p) {
                                Page.Agenda -> stringResource(R.string.cal_pivot_agenda)
                                Page.Month -> (if (month == YearMonth.from(today)) "• " else "") + monthTitle(month)
                                Page.Week -> stringResource(R.string.cal_pivot_week)
                                Page.Day -> stringResource(R.string.cal_pivot_day)
                                Page.Year -> stringResource(R.string.cal_pivot_year)
                            }
                        }
                        PivotHeader(titles, pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
                        if (!canRead) {
                            Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
                                EmptyState(stringResource(R.string.cal_need_permission), locked = true) {
                                    MetroButton(stringResource(R.string.recorder_allow), Modifier.padding(top = 14.dp).testTag("cal:allow")) { askRead.launch(Manifest.permission.READ_CALENDAR) }
                                    MetroButton(stringResource(R.string.cal_open_settings), Modifier.padding(top = 8.dp).testTag("cal:settings")) {
                                        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    }
                                }
                            }
                        } else if (searching) {
                            Box(Modifier.weight(1f)) { SearchPage(repository, today, zone, options.hidden) { editing = it } }
                        } else Row(Modifier.weight(1f).fillMaxWidth()) {
                            if (wide) Box(Modifier.weight(0.45f)) {
                                MonthPage(shown, month, selected, roomy = true, onMonth = { month = it }, onSelect = { selected = it }, onOpenDay = { open(selected) }, onEdit = { editing = it })
                            }
                            Box(Modifier.weight(if (wide) 0.55f else 1f)) {
                                HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
                                    when (pages[index]) {
                                        Page.Agenda -> AgendaPage(shown, selected, loaded.truncated) { editing = it }
                                        Page.Month -> MonthPage(shown, month, selected, roomy = false, onMonth = { month = it }, onSelect = { selected = it }, onOpenDay = { open(selected) }, onEdit = { editing = it })
                                        Page.Week -> WeekPage(shown, selected, onShift = { selected = selected.plusDays(it * 7L); month = YearMonth.from(selected) }, onOpenDay = { open(it) }, onEdit = { editing = it }, onCreate = { d, h -> startNew(newEvent(d, h)) })
                                        Page.Day -> DayPage(
                                            shown, selected, onShift = { selected = selected.plusDays(it.toLong()); month = YearMonth.from(selected) }, onEdit = { editing = it }, onCreate = { d, h -> startNew(newEvent(d, h)) },
                                            onCreateRange = { d, a, b -> startNew(CalEvent(0L, 0L, "", CalendarLogic.timeAt(d, a, zone), CalendarLogic.timeAt(d, b, zone), false)) },
                                            onMove = { e, s, en -> scope.launch { val ok = withContext(Dispatchers.IO) { repository.move(e, s, en) }; if (!ok) MetroToast.show(context, failure()); version++ } },
                                        )
                                        Page.Year -> YearPage(shown, month.year, onShift = { month = month.plusYears(it.toLong()); selected = selected.plusYears(it.toLong()) }, onPick = { m -> month = m; selected = if (m == YearMonth.from(today)) today else m.atDay(1); if (!wide) scope.launch { pager.animateScrollToPage(pages.indexOf(Page.Month)) } })
                                    }
                                }
                            }
                        }
                        AppBar(
                            listOf(
                                AppBarItem("＋", stringResource(R.string.cal_new)) { startNew(newEvent(selected)) },
                                AppBarItem("⌂", stringResource(R.string.cal_today)) { selected = today; month = YearMonth.from(today); searching = false },
                            ),
                            menu = listOf(
                                AppBarItem("⌕", stringResource(R.string.cal_search)) { searching = !searching },
                                AppBarItem("▦", stringResource(R.string.cal_calendars)) { dialog = "calendars" },
                                AppBarItem("⇄", stringResource(R.string.cal_tools)) { dialog = "tools" },
                                AppBarItem("⚙", stringResource(R.string.cal_options)) { dialog = "options" },
                            ),
                        )
                    }
                },
                detail = { event ->
                    if (event.readOnly) EventView(event, repository, onCancel = { editing = null }, onJoin = ::join)
                    else EventForm(
                        event, repository,
                        onSave = { saved ->
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { if (saved.event.id == 0L) repository.insert(saved.event, saved.rule) != 0L else repository.update(saved.event, event, saved.all, saved.touchReminders) }
                                version++
                                // A save that failed leaves the form open: what was typed is not lost.
                                if (ok) editing = null else MetroToast.show(context, failure())
                            }
                        },
                        onDelete = { all -> scope.launch { val ok = withContext(Dispatchers.IO) { repository.delete(event, all) }; version++; if (ok) editing = null else MetroToast.show(context, failure()) } },
                        onCancel = { editing = null },
                        onJoin = ::join,
                    )
                },
            )
            when (dialog) {
                "options" -> OptionsDialog(options, onBirthdays = { on ->
                    if (!on) options.set(Option.Birthdays, false)
                    else if (LivePermissions.isGranted(context, Manifest.permission.READ_CONTACTS)) options.set(Option.Birthdays, true)
                    else askContacts.launch(Manifest.permission.READ_CONTACTS)
                }, onClose = { dialog = null })
                "calendars" -> CalendarsDialog(calendars, options) { dialog = null }
                "tools" -> ToolsDialog(
                    options.secondZone,
                    onImport = { dialog = null; import.launch(arrayOf("text/calendar", "text/plain", "application/octet-stream")) },
                    onExport = { dialog = null; export.launch("calendar.ics") },
                    onFree = { dialog = "free" }, onZone = { dialog = "zone" }, onClose = { dialog = null },
                )
                "free" -> FreeTimeDialog(CalendarLogic.freeSlots(shown, selected, zone), selected, zone, onPick = { s, e -> dialog = null; startNew(CalEvent(0L, 0L, "", s, e, false)) }, onClose = { dialog = null })
                "zone" -> ZoneDialog(options.secondZone, onChoose = { options.chooseSecondZone(it); dialog = null }, onClose = { dialog = null })
            }
            imported?.let { found ->
                ImportDialog(
                    found, calendars.filter { it.writable },
                    onImport = { id ->
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { repository.insertAll(found, id) }
                            imported = null; version++
                            MetroToast.show(context, if (n > 0) context.getString(R.string.cal_imported, n, found.size) else failure())
                        }
                    },
                    onClose = { imported = null },
                )
            }
        }
    }
}
