package com.metrowin.launcher.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.mini.DateTimeDialog
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.data.Reminders
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** The fields of one item. It draws its parts one under the other and leaves the frame (a page or a dialog) to its caller. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditForm(item: TodoItem, names: List<String>, now: Long, onSave: (TodoItem) -> Unit, onDelete: () -> Unit) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    var due by remember(item.id) { mutableStateOf(item.due) }
    var star by remember(item.id) { mutableStateOf(item.starred) }
    var listName by remember(item.id) { mutableStateOf(item.list) }
    var note by remember(item.id) { mutableStateOf(item.note) }
    var repeat by remember(item.id) { mutableStateOf(item.repeat) }
    var subs by remember(item.id) { mutableStateOf(item.subs) }
    var newSub by remember(item.id) { mutableStateOf("") }
    val addLabel = stringResource(R.string.todo_cd_add)
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    MetroTextField(text, { text = it.take(Todos.MAX_TEXT) }, stringResource(R.string.todo_hint), Modifier.testTag("todo:edit:text"), singleLine = false)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TodoChip(if (due == 0L) stringResource(R.string.todo_no_date) else whenText(due, now), due != 0L, "todo:edit:due") { picking = true }
        if (due != 0L) TodoChip("✕", false, "todo:edit:nodue", description = stringResource(R.string.todo_cd_clear_date)) { due = 0L; repeat = Repeat.None }
        TodoChip("★", star, "todo:edit:star", description = stringResource(R.string.todo_cd_star)) { star = !star }
    }
    // The times a touch gives, which also move a date that has passed (what "later" is for a reminder that rang).
    val quick = remember { Reminders.QuickTime.entries }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(quick) { which ->
            val at = Reminders.quickTime(which, LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            TodoChip(stringResource(quickName(which)), due == at, "todo:edit:quick:${which.name}") { due = at }
        }
    }
    if (due != 0L) {
        PageNote(stringResource(R.string.todo_repeat))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Repeat.entries.forEach { r -> TodoChip(stringResource(repeatName(r)), repeat == r, "todo:edit:repeat:${r.name}") { repeat = r } }
        }
    }
    MetroTextField(listName, { listName = it.take(Todos.MAX_LIST) }, stringResource(R.string.todo_list_hint), Modifier.testTag("todo:edit:list"))
    if (names.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(names) { n -> TodoChip(n, listName == n, "todo:edit:pick:$n") { listName = n } } }
    MetroTextField(note, { note = it.take(Todos.MAX_NOTE) }, stringResource(R.string.todo_note_hint), Modifier.testTag("todo:edit:note"), singleLine = false)
    subs.forEachIndexed { index, sub ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(48.dp).testTag("todo:edit:sub:$index").metroPress(onClick = { subs = subs.toMutableList().also { it[index] = sub.copy(done = !sub.done) } }), contentAlignment = Alignment.Center) {
                MetroText(if (sub.done) "☑" else "☐", size = 24.sp, color = LocalAccent.current)
            }
            Box(Modifier.weight(1f)) { MetroText(sub.text, size = 18.sp, color = if (sub.done) LocalMetroColors.current.subtle else LocalMetroColors.current.foreground) }
            TodoChip("✕", false, "todo:edit:subdel:$index", description = stringResource(R.string.todo_delete)) { subs = subs.toMutableList().also { it.removeAt(index) } }
        }
    }
    if (subs.size < Todos.MAX_SUBS) {
        fun addSub() {
            val t = newSub.trim().take(Todos.MAX_SUB_TEXT)
            if (t.isNotEmpty()) { subs = subs + Sub(t); newSub = "" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroTextField(newSub, { newSub = it.take(Todos.MAX_SUB_TEXT) }, stringResource(R.string.todo_sub_hint), Modifier.weight(1f).testTag("todo:edit:newsub"), onSearch = ::addSub)
            MetroButton("＋", Modifier.semantics { contentDescription = addLabel }.testTag("todo:edit:addsub")) { addSub() }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetroButton(stringResource(R.string.todo_edit_save), Modifier.weight(1f).testTag("todo:edit:save")) {
            // A step typed and not added yet is added: leaving it out would lose what was written.
            val pending = newSub.trim().take(Todos.MAX_SUB_TEXT).takeIf { it.isNotEmpty() && subs.size < Todos.MAX_SUBS }
            Todos.clean(text)?.let {
                onSave(item.copy(text = it, due = due, starred = star, list = Todos.cleanList(listName), note = Todos.cleanNote(note), repeat = if (due == 0L) Repeat.None else repeat, subs = if (pending != null) subs + Sub(pending) else subs))
            }
        }
        MetroButton(stringResource(if (confirm) R.string.todo_delete_confirm else R.string.todo_delete), Modifier.weight(1f).testTag("todo:edit:delete")) {
            if (confirm) onDelete() else confirm = true
        }
    }
    if (picking) DateTimeDialog(if (due != 0L) due else now + Todos.DEFAULT_DUE_OFFSET_MS, onChoose = { due = it; picking = false }, onCancel = { picking = false })
}

fun quickName(which: Reminders.QuickTime) = when (which) {
    Reminders.QuickTime.InOneHour -> R.string.todo_in_one_hour
    Reminders.QuickTime.ThisEvening -> R.string.todo_this_evening
    Reminders.QuickTime.TomorrowMorning -> R.string.todo_tomorrow
}

private fun repeatName(r: Repeat) = when (r) {
    Repeat.None -> R.string.todo_repeat_none
    Repeat.Daily -> R.string.todo_repeat_daily
    Repeat.Weekly -> R.string.todo_repeat_weekly
    Repeat.Monthly -> R.string.todo_repeat_monthly
}

/** The edit form on a page of its own, over the list. */
@Composable
fun EditPage(item: TodoItem, names: List<String>, now: Long, onSave: (TodoItem) -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(LocalMetroColors.current.background).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp).testTag("todo:edit:page"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { EditForm(item, names, now, onSave, onDelete) }
}

/** [item] on its page, that comes in over the list; nothing while it is null. */
@Composable
fun EditOverlay(item: TodoItem?, names: List<String>, now: Long, onSave: (TodoItem) -> Unit, onDelete: (TodoItem) -> Unit) {
    AnimatedVisibility(item != null, enter = fadeIn() + slideInVertically { it / 10 }, exit = ExitTransition.None) {
        item?.let { EditPage(it, names, now, onSave, { onDelete(it) }) }
    }
}

/** The edit form in a dialog (the choice of those who like it better than a page). */
@Composable
fun EditDialog(item: TodoItem, names: List<String>, now: Long, onSave: (TodoItem) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    DialogFrame(onCancel = onCancel) { EditForm(item, names, now, onSave, onDelete) }
}

// --- the panels of the menu

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionsPanel(o: TodoOptions, onChange: (TodoOptions) -> Unit, onClose: () -> Unit) {
    DialogFrame(onCancel = onClose, title = stringResource(R.string.todo_menu_options)) {
        Column(Modifier.testTag("todo:options"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PageNote(stringResource(R.string.todo_opt_group))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Todos.GroupBy.entries.forEach { g ->
                    TodoChip(stringResource(when (g) { Todos.GroupBy.Section -> R.string.todo_group_section; Todos.GroupBy.Day -> R.string.todo_group_day; Todos.GroupBy.List -> R.string.todo_group_list }), o.group == g, "todo:opt:group:${g.name}") { onChange(o.copy(group = g)) }
                }
            }
            PageNote(stringResource(R.string.todo_opt_view))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TodoOptions.View.entries.forEach { v ->
                    TodoChip(stringResource(when (v) { TodoOptions.View.Rows -> R.string.todo_view_rows; TodoOptions.View.Compact -> R.string.todo_view_compact; TodoOptions.View.Kanban -> R.string.todo_view_kanban }), o.view == v, "todo:opt:view:${v.name}") { onChange(o.copy(view = v)) }
                }
            }
            PageNote(stringResource(R.string.todo_opt_filter))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Todos.Filter.entries.forEach { f ->
                    TodoChip(stringResource(when (f) { Todos.Filter.All -> R.string.todo_filter_all; Todos.Filter.Starred -> R.string.todo_filter_starred; Todos.Filter.Late -> R.string.todo_filter_late; Todos.Filter.Dated -> R.string.todo_filter_dated }), o.filter == f, "todo:opt:filter:${f.name}") { onChange(o.copy(filter = f)) }
                }
            }
            MetroToggle(stringResource(R.string.todo_opt_counts), o.counts, { onChange(o.copy(counts = it)) })
            MetroToggle(stringResource(R.string.todo_opt_list_colors), o.listColors, { onChange(o.copy(listColors = it)) })
            MetroToggle(stringResource(R.string.todo_opt_preview), o.preview, { onChange(o.copy(preview = it)) })
            MetroToggle(stringResource(R.string.todo_opt_burst), o.burst, { onChange(o.copy(burst = it)) })
            MetroToggle(stringResource(R.string.todo_opt_edit_page), o.editPage, { onChange(o.copy(editPage = it)) })
            MetroToggle(stringResource(R.string.todo_opt_dashboard), o.dashboard, { onChange(o.copy(dashboard = it)) })
            MetroToggle(stringResource(R.string.todo_opt_swipe), o.swipe, { onChange(o.copy(swipe = it)) })
            MetroToggle(stringResource(R.string.todo_opt_large), o.large, { onChange(o.copy(large = it)) })
            MetroToggle(stringResource(R.string.todo_opt_contrast), o.contrast, { onChange(o.copy(contrast = it)) })
            MetroToggle(stringResource(R.string.todo_opt_dictation), o.dictation, { onChange(o.copy(dictation = it)) })
            PageNote(stringResource(R.string.todo_opt_dictation_note))
        }
    }
}

/** Rename, colour or delete the lists. A list that is deleted gives its items to the main list. */
@Composable
fun ListsPanel(
    names: List<String>, counts: (String) -> Int, colorOf: (String) -> Int, onRename: (String, String) -> Unit, onColor: (String, Int) -> Unit,
    onDelete: (String) -> Unit, onClose: () -> Unit,
) {
    DialogFrame(onCancel = onClose, title = stringResource(R.string.todo_menu_lists)) {
        Column(Modifier.testTag("todo:lists"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (names.isEmpty()) PageNote(stringResource(R.string.todo_lists_none))
            names.forEach { name -> ListEditor(name, counts(name), colorOf(name), onRename, onColor, onDelete) }
        }
    }
}

@Composable
private fun ListEditor(name: String, count: Int, color: Int, onRename: (String, String) -> Unit, onColor: (String, Int) -> Unit, onDelete: (String) -> Unit) {
    var text by remember(name) { mutableStateOf(name) }
    var confirm by remember(name) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetroTextField(text, { text = it.take(Todos.MAX_LIST) }, name, Modifier.testTag("todo:lists:name:$name"))
        PageNote(stringResource(R.string.todo_lists_count, count))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 1..TodoOptionStore.COLORS) {
                val tint = paletteColor(i)
                val picked = (listTint(name, color) == tint)
                Box(
                    Modifier.size(32.dp).testTag("todo:lists:color:$name:$i").metroPress(onClick = { onColor(name, i) })
                        .drawBehind { drawRect(tint); if (picked) drawRect(androidx.compose.ui.graphics.Color.White, Offset(10.dp.toPx(), 10.dp.toPx()), Size(12.dp.toPx(), 12.dp.toPx())) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(R.string.todo_rename), Modifier.weight(1f).testTag("todo:lists:rename:$name")) { if (Todos.cleanList(text).isNotEmpty() && text != name) onRename(name, text) }
            MetroButton(stringResource(if (confirm) R.string.todo_delete_confirm else R.string.todo_delete), Modifier.weight(1f).testTag("todo:lists:delete:$name")) {
                if (confirm) onDelete(name) else confirm = true
            }
        }
    }
}

/** How it went: the numbers and the last seven days as bars. */
@Composable
fun StatsPanel(s: Todos.Stats, now: Long, onClose: () -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val zone = ZoneId.systemDefault()
    DialogFrame(onCancel = onClose, title = stringResource(R.string.todo_menu_stats)) {
        Column(Modifier.testTag("todo:stats"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetroText(stringResource(R.string.todo_stats_open, s.open), size = 20.sp)
            MetroText(stringResource(R.string.todo_stats_late, s.late), size = 20.sp, color = if (s.late > 0) accent else colors.foreground)
            MetroText(stringResource(R.string.todo_stats_done, s.done), size = 20.sp)
            MetroText(stringResource(R.string.todo_stats_week, s.doneWeek), size = 20.sp)
            val top = (s.perDay.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(110.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                s.perDay.forEachIndexed { i, n ->
                    val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusDays((s.perDay.size - 1 - i).toLong())
                    Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        MetroText(n.toString(), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                        Box(Modifier.fillMaxWidth().height((70f * n / top).dp.coerceAtLeast(2.dp)).background(if (n == 0) colors.subtle.copy(alpha = 0.3f) else accent))
                        MetroText(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                    }
                }
            }
        }
    }
}

/** What leaves the app as text, and what comes in: nothing is sent anywhere by the app itself, the file or the share sheet is the user's choice. */
@Composable
fun DataPanel(onShare: () -> Unit, onExport: (TodoFormats.Format) -> Unit, onImport: () -> Unit, onClose: () -> Unit) {
    DialogFrame(onCancel = onClose, title = stringResource(R.string.todo_menu_share)) {
        Column(Modifier.testTag("todo:data"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetroButton(stringResource(R.string.todo_share_text), Modifier.fillMaxWidth().testTag("todo:data:share")) { onShare() }
            MetroButton(stringResource(R.string.todo_export_md), Modifier.fillMaxWidth().testTag("todo:data:md")) { onExport(TodoFormats.Format.Markdown) }
            MetroButton(stringResource(R.string.todo_export_txt), Modifier.fillMaxWidth().testTag("todo:data:txt")) { onExport(TodoFormats.Format.TodoTxt) }
            MetroButton(stringResource(R.string.todo_export_csv), Modifier.fillMaxWidth().testTag("todo:data:csv")) { onExport(TodoFormats.Format.Csv) }
            MetroButton(stringResource(R.string.todo_import), Modifier.fillMaxWidth().testTag("todo:data:import")) { onImport() }
        }
    }
}

/** One thing at a time, large: the next one open, with "done" and "next". */
@Composable
fun FocusPage(items: List<TodoItem>, onDone: (TodoItem) -> Unit, onClose: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    val colors = LocalMetroColors.current
    Column(
        Modifier.fillMaxSize().background(colors.background).padding(24.dp).testTag("todo:focus"),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (items.isEmpty()) {
            EmptyArt(done = true)
            MetroText(stringResource(R.string.todo_all_done), size = 26.sp, textAlign = TextAlign.Center)
        } else {
            val item = items[index.coerceIn(0, items.lastIndex)]
            PageNote(stringResource(R.string.todo_focus_counter, index.coerceIn(0, items.lastIndex) + 1, items.size))
            MetroText(item.text, size = 36.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (item.due != 0L) PageNote(whenText(item.due))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetroButton(stringResource(R.string.todo_focus_done), Modifier.weight(1f).testTag("todo:focus:done")) { onDone(item); index = index.coerceAtMost((items.size - 2).coerceAtLeast(0)) }
                if (items.size > 1) MetroButton(stringResource(R.string.todo_focus_next), Modifier.weight(1f).testTag("todo:focus:next")) { index = (index + 1) % items.size }
            }
        }
        MetroButton(stringResource(R.string.todo_focus_close), Modifier.fillMaxWidth().testTag("todo:focus:close")) { onClose() }
    }
}

/** What the day holds, at the top of the page: how many are for today and late, and how much of it is done. */
@Composable
fun DashboardCard(day: Todos.Day, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    Column(modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("todo:dash"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MetroText(stringResource(R.string.todo_dash_today, day.open + day.late), size = 28.sp)
        MetroText(
            listOf(stringResource(R.string.todo_dash_late, day.late), stringResource(R.string.todo_dash_done, day.done)).joinToString("  ·  "),
            size = 14.sp, weight = FontWeight.Normal, color = colors.subtle,
        )
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            drawRect(colors.subtle.copy(alpha = 0.3f))
            drawRect(accent, Offset.Zero, Size(size.width * day.progress, size.height))
        }
    }
}

/** The lists as columns of cards, side by side. A card opens and ticks like a row; to move it to another list, edit it. */
@Composable
fun KanbanBoard(groups: List<Todos.Group>, env: RowEnv, countOf: (Todos.Group) -> Int?, modifier: Modifier = Modifier) {
    LazyRow(modifier.testTag("todo:kanban"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(groups, key = { it.key }) { group ->
            Column(Modifier.width(if (isWide()) 300.dp else 260.dp).fillMaxSize()) {
                GroupTitle(group, env.now, countOf(group), if (env.options.listColors) group.list?.let { env.colorOf(it) } else null)
                LazyColumn(Modifier.weight(1f)) {
                    items(group.items, key = { it.id }) { item -> TodoRow(item, env) }
                }
            }
        }
    }
}
