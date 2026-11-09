package com.metrowin.launcher.todo

import com.metrowin.launcher.ui.anim.swivel
import com.metrowin.launcher.ui.anim.rememberRowSwivel
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.kids.CheckSquare
import com.metrowin.launcher.ui.theme.LocalStyle
import java.util.Locale
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.sectionHeader
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroDatePicker
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroTimePicker
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.DateTimeDialog
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TodoScreen() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val store = remember { TodoStore(context) }
    var items by remember { mutableStateOf<List<TodoItem>?>(null) }
    var list by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    val pager = rememberPagerState { 2 }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { TodoReminders.pull(context, store.load()).also { store.save(it) } }
    }
    val all = items.orEmpty()

    fun commit(next: List<TodoItem>) {
        items = next
        scope.launch(Dispatchers.IO) { store.save(next) }
    }

    /** Saves a change of one item; its reminder follows it. */
    fun change(item: TodoItem) {
        val before = all.firstOrNull { it.id == item.id }
        scope.launch {
            val synced = withContext(Dispatchers.IO) { TodoReminders.sync(context, item, before) }
            commit(Todos.replace(items.orEmpty(), synced))
        }
    }

    fun create(text: String, due: Long, listName: String) {
        val now = System.currentTimeMillis()
        val added = Todos.add(all, text, due, listName, now = now)
        if (added.size == all.size) return
        val item = added.last()
        scope.launch {
            val synced = withContext(Dispatchers.IO) { TodoReminders.sync(context, item, null) }
            commit(Todos.add(all, text, due, listName, now = now).let { Todos.replace(it, synced) })
        }
    }

    fun delete(item: TodoItem) {
        scope.launch(Dispatchers.IO) { TodoReminders.release(context, item) }
        commit(Todos.remove(all, item.id))
    }

    BackHandler(enabled = editing != null) { editing = null }

    MiniFrame {
        PivotHeader(
            listOf(stringResource(R.string.todo_pivot_open), stringResource(R.string.todo_pivot_done)), pager,
            Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
        )
        val names = Todos.lists(all)
        if (names.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
                item { PageChip(stringResource(R.string.todo_all), list == null, "todo:list:all") { list = null } }
                items(names) { name -> PageChip(name, list == name, "todo:list:$name") { list = name } }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page == 0) {
                OpenPage(all, list, items != null, onToggle = { change(it.copy(done = !it.done)) }, onEdit = { editing = it }, onAdd = ::create, listName = list.orEmpty(), onReorder = { ids -> commit(Todos.reorder(all, ids)) })
            } else {
                DonePage(Todos.finished(all, list), onToggle = { change(it.copy(done = !it.done)) }, onEdit = { editing = it })
            }
        }
        AppBar(
            listOf(
                AppBarItem("✕", stringResource(R.string.todo_clear_done)) {
                    all.filter { it.done }.forEach { scope.launch(Dispatchers.IO) { TodoReminders.release(context, it) } }
                    commit(Todos.clearDone(all))
                },
            ),
        )
    }
    editing?.let { item ->
        EditDialog(item, names = Todos.lists(all), onSave = { change(it); editing = null }, onDelete = { delete(item); editing = null }, onCancel = { editing = null })
    }
}

@Composable
private fun OpenPage(
    all: List<TodoItem>,
    list: String?,
    loaded: Boolean,
    onToggle: (TodoItem) -> Unit,
    onEdit: (TodoItem) -> Unit,
    onAdd: (String, Long, String) -> Unit,
    listName: String,
    onReorder: (List<Long>) -> Unit,
) {
    val now = System.currentTimeMillis()
    val sections = Todos.open(all, list, now)
    var text by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(0L) }
    var picking by remember { mutableStateOf(false) }
    val quick = remember { Reminders.QuickTime.entries }
    val listState = rememberLazyListState()
    // A long press picks a row up; while it is held the rows of its section are in the order it would leave them in.
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf<Pair<Todos.Section, List<TodoItem>>?>(null) }
    var swappedAt by remember { mutableLongStateOf(0L) }
    val swivel = rememberRowSwivel()

    // shortcut: the list does not scroll by itself when a row is dragged to its edge; add it if long lists need it.
    fun swapUnderDrag() {
        val w = working ?: return
        // The list needs a frame to place the rows after a swap; looking again before that would swap twice.
        if (System.nanoTime() - swappedAt < SWAP_PAUSE_NS) return
        val visible = listState.layoutInfo.visibleItemsInfo
        val me = visible.firstOrNull { it.key == dragId } ?: return
        val centre = me.offset + me.size / 2f + dragOffset
        val target = visible.firstOrNull { it.key != dragId && it.key is Long && centre >= it.offset && centre <= it.offset + it.size && w.second.any { r -> r.id == it.key } } ?: return
        val from = w.second.indexOfFirst { it.id == dragId }
        val to = w.second.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        working = w.first to w.second.toMutableList().apply { add(to, removeAt(from)) }
        // The row sits at the place of the one it passed; the finger has not moved, so the offset takes the difference.
        dragOffset -= (if (to > from) target.size else target.offset - me.offset).toFloat()
        swappedAt = System.nanoTime()
    }

    fun drop() {
        working?.let { onReorder(it.second.map { r -> r.id }) }
        dragId = null; dragOffset = 0f; working = null
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (loaded && sections.isEmpty()) PageNote(stringResource(R.string.todo_empty), Modifier.padding(bottom = 8.dp).testTag("todo:empty"))
        val classic = LocalStyle.current.isClassic
        LazyColumn(Modifier.weight(1f).scrollIndicator(listState).testTag("todo:list"), state = listState) {
            sections.forEach { (section, shownRows) ->
                val rows = working?.takeIf { it.first == section }?.second ?: shownRows
                sectionHeader("h:$section") {
                    val name = stringResource(
                        when (section) {
                            Todos.Section.Late -> R.string.todo_late
                            Todos.Section.Today -> R.string.todo_today
                            Todos.Section.Later -> R.string.todo_later
                            Todos.Section.NoDate -> R.string.todo_no_date
                        },
                    )
                    // A group of Windows Phone is named in the accent colour, lowercase and large.
                    if (classic) MetroText(name.lowercase(Locale.getDefault()), size = 20.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    else MetroText(
                        name, size = 16.sp, weight = FontWeight.SemiBold, color = if (section == Todos.Section.Late) LocalAccent.current else LocalMetroColors.current.subtle,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                itemsIndexed(rows, key = { _, it -> it.id }) { index, item ->
                    val dragging = dragId == item.id
                    TodoRow(
                        item, now, onToggle, onEdit = { swivel.open(item.id) { onEdit(item) } },
                        modifier = (if (dragging) Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset; scaleX = 1.03f; scaleY = 1.03f } else Modifier.animateItem()).featherRow(index)
                            .swivel(swivel, item.id)
                            .pointerInput(item.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragId = item.id; dragOffset = 0f; working = section to rows },
                                    onDrag = { change, amount -> change.consume(); dragOffset += amount.y; swapUnderDrag() },
                                    onDragEnd = { drop() },
                                    onDragCancel = { dragId = null; dragOffset = 0f; working = null },
                                )
                            },
                    )
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            item { PageChip(stringResource(R.string.todo_no_date), due == 0L, "todo:due:none") { due = 0L } }
            items(quick) { which ->
                val at = Reminders.quickTime(which, LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                PageChip(stringResource(quickName(which)), due == at, "todo:due:${which.name}") { due = at }
            }
            item { PageChip(if (due != 0L && quick.none { w -> Reminders.quickTime(w, LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() == due }) whenText(due) else stringResource(R.string.todo_pick), false, "todo:due:pick") { picking = true } }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroTextField(text, { text = it.take(Todos.MAX_TEXT) }, stringResource(R.string.todo_hint), Modifier.weight(1f).testTag("todo:input"), onSearch = {
                if (text.isNotBlank()) { onAdd(text, due, listName); text = ""; due = 0L }
            })
            MetroButton("＋", Modifier.testTag("todo:add")) { if (text.isNotBlank()) { onAdd(text, due, listName); text = ""; due = 0L } }
        }
    }
    if (picking) DateTimeDialog(if (due != 0L) due else System.currentTimeMillis() + 3_600_000L, onChoose = { due = it; picking = false }, onCancel = { picking = false })
}

private const val SWAP_PAUSE_NS = 90_000_000L

private fun quickName(which: Reminders.QuickTime) = when (which) {
    Reminders.QuickTime.InOneHour -> R.string.todo_in_one_hour
    Reminders.QuickTime.ThisEvening -> R.string.todo_this_evening
    Reminders.QuickTime.TomorrowMorning -> R.string.todo_tomorrow
}

@Composable
private fun DonePage(rows: List<TodoItem>, onToggle: (TodoItem) -> Unit, onEdit: (TodoItem) -> Unit) {
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (rows.isEmpty()) PageNote(stringResource(R.string.todo_nothing_done), Modifier.testTag("todo:done:empty"))
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().scrollIndicator(listState).testTag("todo:done"), state = listState) {
            itemsIndexed(rows, key = { _, it -> it.id }) { index, item -> TodoRow(item, now, onToggle, onEdit, Modifier.featherRow(index)) }
        }
    }
}

@Composable
private fun TodoRow(item: TodoItem, now: Long, onToggle: (TodoItem) -> Unit, onEdit: (TodoItem) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val late = !item.done && item.due != 0L && item.due < now
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        if (LocalStyle.current.isClassic) {
            Box(Modifier.width(44.dp).testTag("todo:box:${item.id}").metroPress(onClick = { onToggle(item) }).padding(top = 2.dp)) { CheckSquare(item.done) }
        } else {
            MetroText(
                if (item.done) "☑" else "☐", size = 28.sp, color = LocalAccent.current,
                modifier = Modifier.width(44.dp).testTag("todo:box:${item.id}").metroPress(onClick = { onToggle(item) }),
            )
        }
        Column(Modifier.weight(1f).testTag("todo:item").metroPress(onClick = { onEdit(item) })) {
            androidx.compose.foundation.text.BasicText(
                text = (if (item.starred) "★ " else "") + item.text,
                style = androidx.compose.ui.text.TextStyle(
                    color = if (item.done) colors.subtle else colors.foreground, fontSize = 21.sp,
                    fontWeight = FontWeight.Light, fontFamily = com.metrowin.launcher.ui.theme.LocalStyle.current.font,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                ),
            )
            val detail = listOfNotNull(item.due.takeIf { it != 0L }?.let { whenText(it) }, item.list.takeIf { it.isNotEmpty() }).joinToString("  ·  ")
            if (detail.isNotEmpty()) MetroText(detail, size = 13.sp, weight = FontWeight.Normal, color = if (late) LocalAccent.current else colors.subtle)
        }
    }
}

@Composable
private fun EditDialog(item: TodoItem, names: List<String>, onSave: (TodoItem) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    var due by remember(item.id) { mutableStateOf(item.due) }
    var star by remember(item.id) { mutableStateOf(item.starred) }
    var listName by remember(item.id) { mutableStateOf(item.list) }
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    DialogFrame(onCancel = onCancel) {
        MetroTextField(text, { text = it.take(Todos.MAX_TEXT) }, stringResource(R.string.todo_hint), Modifier.testTag("todo:edit:text"), singleLine = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PageChip(if (due == 0L) stringResource(R.string.todo_no_date) else whenText(due), due != 0L, "todo:edit:due") { picking = true }
            if (due != 0L) PageChip("✕", false, "todo:edit:nodue") { due = 0L }
            PageChip("★", star, "todo:edit:star") { star = !star }
        }
        MetroTextField(listName, { listName = it.take(Todos.MAX_LIST) }, stringResource(R.string.todo_list_hint), Modifier.testTag("todo:edit:list"))
        if (names.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(names) { n -> PageChip(n, listName == n, "todo:edit:pick:$n") { listName = n } } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(R.string.todo_save), Modifier.weight(1f).testTag("todo:edit:save")) {
                Todos.clean(text)?.let { onSave(item.copy(text = it, due = due, starred = star, list = Todos.cleanList(listName))) }
            }
            MetroButton(stringResource(if (confirm) R.string.notes_delete_confirm else R.string.todo_delete), Modifier.weight(1f).testTag("todo:edit:delete")) {
                if (confirm) onDelete() else confirm = true
            }
        }
    }
    if (picking) DateTimeDialog(if (due != 0L) due else System.currentTimeMillis() + 3_600_000L, onChoose = { due = it; picking = false }, onCancel = { picking = false })
}
