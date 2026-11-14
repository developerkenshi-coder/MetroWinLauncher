package com.metrowin.launcher.todo

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.data.readAtMost
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.rememberRowSwivel
import com.metrowin.launcher.ui.anim.swivel
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.components.sectionHeader
import com.metrowin.launcher.ui.mini.DateTimeDialog
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.model.Glyph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private enum class Panel { Options, Lists, Stats, Data, Focus }

/** How long a thing just ticked stays on the page, drawn done, before it leaves (the strike and the burst are seen). */
private const val COMPLETE_MS = 350L

private const val UNDO_MS = 8_000L

/** "Now", renewed at the start of every minute: what is late, what is for today and the quick times follow the clock, also across midnight. */
@Composable
private fun rememberMinute(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        val t = System.currentTimeMillis()
        value = t
        delay(60_000L - t % 60_000L + 50L)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { TodoStore(context) }
    val optionStore = remember { TodoOptionStore(context) }
    var items by remember { mutableStateOf<List<TodoItem>?>(null) }
    var options by remember { mutableStateOf(optionStore.load()) }
    var list by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var undo by remember { mutableStateOf<List<TodoItem>?>(null) }
    var completing by remember { mutableStateOf(emptySet<Long>()) }
    var health by remember { mutableStateOf<TodoReminders.Health?>(null) }
    var colorTick by remember { mutableIntStateOf(0) }
    var spoken by remember { mutableStateOf<String?>(null) }
    var tipSeen by remember { mutableStateOf(optionStore.dragTipSeen) }
    val now by rememberMinute()
    val pager = rememberPagerState { 2 }
    val syncLock = remember { Mutex() }
    val all = items.orEmpty()

    fun toast(res: Int, vararg args: Any) = Toast.makeText(context, context.getString(res, *args), Toast.LENGTH_SHORT).show()

    fun setOptions(next: TodoOptions) {
        options = next
        optionStore.save(next)
    }

    /** What is on the page is what is saved: the save goes to a thread that keeps the order of the saves. */
    fun commit(next: List<TodoItem>) {
        items = next
        store.saveLater(next)
    }

    /** Makes the reminder of one item match it. One at a time, and on the item as it is when its turn comes, so quick changes never leave a reminder behind. */
    fun syncItem(id: Long, before: TodoItem?) {
        scope.launch {
            syncLock.withLock {
                val current = items.orEmpty().firstOrNull { it.id == id } ?: return@withLock
                val synced = withContext(Dispatchers.IO + NonCancellable) { TodoReminders.sync(context, current, before) }
                if (synced.reminderId != current.reminderId) {
                    items.orEmpty().firstOrNull { it.id == id }?.let { commit(Todos.replace(items.orEmpty(), it.copy(reminderId = synced.reminderId))) }
                }
                if (!synced.done && synced.due > System.currentTimeMillis()) {
                    if (synced.reminderId == 0L) toast(R.string.todo_alarm_failed)
                    else if (!TodoReminders.health(context).exactAlarms) toast(R.string.todo_alarm_inexact)
                }
            }
        }
    }

    /** [next] with a new open copy for each item of it that was just done and repeats. */
    fun withRepeats(before: List<TodoItem>, next: List<TodoItem>): List<TodoItem> {
        val nowMs = System.currentTimeMillis()
        val doneNow = next.filter { n -> n.done && before.any { it.id == n.id && !it.done } }
        var out = next
        doneNow.forEach { d ->
            val copy = Todos.spawn(d, store.reserve(out), nowMs)
            if (copy != null && !Todos.isFull(out)) {
                out = out + copy
                syncItem(copy.id, null)
            }
        }
        return out
    }

    fun toggle(item: TodoItem) {
        val cur = items.orEmpty()
        val live = cur.firstOrNull { it.id == item.id } ?: return
        val ticked = Todos.toggled(live, System.currentTimeMillis())
        commit(withRepeats(cur, Todos.replace(cur, ticked)))
        syncItem(ticked.id, live)
    }

    /** A tick on an open item: it is drawn done for a moment, then it goes. */
    fun tick(item: TodoItem) {
        if (item.id in completing) return
        if (item.done) { toggle(item); return }
        completing = completing + item.id
        scope.launch {
            delay(COMPLETE_MS)
            toggle(item)
            completing = completing - item.id
        }
    }

    /** Saves a change of one item; its reminder follows it. */
    fun change(item: TodoItem) {
        val cur = items.orEmpty()
        val before = cur.firstOrNull { it.id == item.id } ?: return
        commit(Todos.replace(cur, item.copy(reminderId = before.reminderId, done = before.done, completedAt = before.completedAt, order = before.order)))
        syncItem(item.id, before)
    }

    fun create(text: String, due: Long, listName: String): Boolean {
        val cur = items.orEmpty()
        if (Todos.isFull(cur)) { toast(R.string.todo_full); return false }
        val added = Todos.add(cur, text, due, listName, now = System.currentTimeMillis(), id = store.reserve(cur))
        if (added.size == cur.size) return false
        commit(added)
        syncItem(added.last().id, null)
        return true
    }

    fun offerUndo(removed: List<TodoItem>) { undo = removed }

    fun delete(item: TodoItem) {
        val live = items.orEmpty().firstOrNull { it.id == item.id } ?: return
        commit(Todos.remove(items.orEmpty(), live.id))
        scope.launch(Dispatchers.IO) { TodoReminders.release(context, live) }
        offerUndo(listOf(live))
    }

    fun clearDone() {
        val done = items.orEmpty().filter { it.done }
        if (done.isEmpty()) return
        commit(Todos.clearDone(items.orEmpty()))
        scope.launch(Dispatchers.IO) { TodoReminders.releaseAll(context, done) }
        offerUndo(done)
    }

    fun restore(removed: List<TodoItem>) {
        // Their reminders went with them: the ones with a date still to come get a new one.
        commit(Todos.restore(items.orEmpty(), removed.map { it.copy(reminderId = 0L) }))
        removed.forEach { syncItem(it.id, null) }
        undo = null
    }

    /** What was done from the notification of a reminder is done here too. */
    fun pullNow() {
        scope.launch {
            val reminders = withContext(Dispatchers.IO) { TodoReminders.reminders(context) }
            val cur = items ?: return@launch
            val next = TodoReminders.applyReminders(cur, reminders, System.currentTimeMillis())
            if (next != cur) commit(withRepeats(cur, next))
        }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { store.load() }
        items = loaded
        pullNow()
        withContext(Dispatchers.IO) { TodoReminders.verify(context, loaded) }
        health = TodoReminders.health(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (items != null) { pullNow(); health = TodoReminders.health(context) }
    }
    // The "done" button of a notification works while this page is open: the reminders change and the page follows.
    DisposableEffect(Unit) {
        val prefs = Prefs.main(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == REMINDERS_KEY && items != null) pullNow() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(undo) { if (undo != null) { delay(UNDO_MS); undo = null } }

    // --- files, share, speech

    fun export(format: TodoFormats.Format, uri: Uri?) {
        if (uri == null) return
        val text = TodoFormats.export(items.orEmpty(), format)
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }.isSuccess }
            toast(if (ok) R.string.todo_exported else R.string.todo_export_failed)
        }
    }
    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TodoFormats.Format.Markdown.mime)) { export(TodoFormats.Format.Markdown, it) }
    val exportTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TodoFormats.Format.TodoTxt.mime)) { export(TodoFormats.Format.TodoTxt, it) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TodoFormats.Format.Csv.mime)) { export(TodoFormats.Format.Csv, it) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readAtMost(IMPORT_LIMIT) }?.toString(Charsets.UTF_8) }.getOrNull() }
            if (text == null) { toast(R.string.todo_import_failed); return@launch }
            val drafts = TodoFormats.parse(text, System.currentTimeMillis())
            val cur = items.orEmpty()
            val merged = TodoFormats.merge(cur, drafts, store.reserve(cur, drafts.size))
            commit(merged)
            merged.drop(cur.size).forEach { syncItem(it.id, null) }
            toast(R.string.todo_imported, merged.size - cur.size)
        }
    }
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken = it }
    }

    BackHandler(enabled = editing != null || panel != null || searching) {
        when {
            panel != null -> panel = null
            editing != null -> editing = null
            else -> { searching = false; query = "" }
        }
    }

    // --- what the page shows

    val names = Todos.lists(all)
    val colorOf: (String) -> Color? = remember(colorTick, options.listColors) { { name -> if (options.listColors) listTint(name, optionStore.listColor(name)) else null } }
    val minute = now / 60_000L
    val kanban = options.view == TodoOptions.View.Kanban
    val groups = remember(all, list, options.group, options.filter, query, minute, kanban) {
        Todos.groups(all, list, if (kanban) Todos.GroupBy.List else options.group, now, options.filter, query)
    }
    val finished = remember(all, list, query) { Todos.finished(all, list, query) }
    val env = RowEnv(now, options, colorOf, completing, onToggle = ::tick, onEdit = { editing = it })
    val wide = isWide()
    val editPage = editing != null && options.editPage

    MiniFrame {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                val titles = listOf(stringResource(R.string.todo_pivot_open), stringResource(R.string.todo_pivot_done)).let { t ->
                    if (options.counts && all.isNotEmpty()) listOf(t[0] + " " + Todos.openCount(all, list), t[1]) else t
                }
                PivotHeader(titles, pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp))
                if (searching) {
                    MetroTextField(query, { query = it.take(Todos.MAX_TEXT) }, stringResource(R.string.todo_search_hint), Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp).testTag("todo:search"))
                }
                val problem = health?.takeIf { !it.ok && all.any { i -> !i.done && i.due != 0L } }
                if (problem != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).testTag("todo:health"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PageNote(stringResource(if (!problem.exactAlarms) R.string.todo_health_exact else R.string.todo_health_notif), Modifier.weight(1f))
                        MetroButton(stringResource(R.string.todo_fix)) {
                            val intent = if (!problem.exactAlarms && android.os.Build.VERSION.SDK_INT >= 31) {
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                            } else {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    }
                }
                if (names.isNotEmpty() && !wide) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp), modifier = Modifier.padding(bottom = 8.dp).fadeEdges()) {
                        item { TodoChip(chipName(stringResource(R.string.todo_all), if (options.counts) Todos.openCount(all) else null), list == null, "todo:list:all") { list = null } }
                        items(names) { name -> TodoChip(chipName(name, if (options.counts) Todos.openCount(all, name) else null), list == name, "todo:list:$name", tint = colorOf(name)) { list = name } }
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (wide) {
                        Column(Modifier.width(200.dp).verticalScroll(rememberScrollState()).padding(start = 24.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TodoChip(chipName(stringResource(R.string.todo_all), if (options.counts) Todos.openCount(all) else null), list == null, "todo:list:all") { list = null }
                            names.forEach { name -> TodoChip(chipName(name, if (options.counts) Todos.openCount(all, name) else null), list == name, "todo:list:$name", tint = colorOf(name)) { list = name } }
                        }
                    }
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxSize()) { page ->
                        if (page == 0) {
                            OpenPage(
                                groups, items != null, all.any { it.done }, query.isNotBlank() || options.filter != Todos.Filter.All, env, list.orEmpty(), kanban,
                                if (options.dashboard) Todos.today(all, now) else null,
                                spoken, { spoken = null }, if (options.dictation) {
                                    { runCatching { dictate.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }.onFailure { toast(R.string.todo_dictation_missing) } }
                                } else null,
                                onAdd = ::create, onReorder = { ids -> commit(Todos.reorder(items.orEmpty(), ids)) }, onDelete = ::delete,
                                tipSeen = tipSeen, onTipSeen = { tipSeen = true; optionStore.dragTipSeen = true },
                            )
                        } else {
                            DonePage(finished, env)
                        }
                    }
                }
            }
            EditOverlay(if (editPage) editing else null, names, now, onSave = { change(it); editing = null }, onDelete = { delete(it); editing = null })
            if (panel == Panel.Focus) {
                FocusPage(
                    Todos.groups(all, list, Todos.GroupBy.Section, now, options.filter).flatMap { it.items },
                    onDone = { toggle(it) }, onClose = { panel = null },
                )
            }
        }
        undo?.let { removed ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp).testTag("todo:undo"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PageNote(if (removed.size == 1) stringResource(R.string.todo_deleted) else stringResource(R.string.todo_cleared, removed.size), Modifier.weight(1f))
                MetroButton(stringResource(R.string.todo_undo), Modifier.testTag("todo:undo:button")) { restore(removed) }
            }
        }
        if (!editPage && panel != Panel.Focus) {
            AppBar(
                listOf(
                    AppBarItem("✕", stringResource(R.string.todo_clear_done)) { clearDone() },
                    AppBarItem("⌕", stringResource(R.string.todo_search)) { searching = !searching; if (!searching) query = "" }.marked(searching),
                    AppBarItem("▶", stringResource(R.string.todo_focus)) { panel = Panel.Focus }.icon(Mark.Play),
                ),
                menu = listOf(
                    AppBarItem("☰", stringResource(R.string.todo_menu_lists)) { panel = Panel.Lists },
                    AppBarItem("▣", stringResource(R.string.todo_menu_stats)) { panel = Panel.Stats },
                    AppBarItem("⇪", stringResource(R.string.todo_menu_share)) { panel = Panel.Data },
                    AppBarItem("⚙", stringResource(R.string.todo_menu_options)) { panel = Panel.Options },
                ),
            )
        }
    }
    if (editing != null && !options.editPage) {
        val item = editing!!
        EditDialog(item, names, now, onSave = { change(it); editing = null }, onDelete = { delete(item); editing = null }, onCancel = { editing = null })
    }
    when (panel) {
        Panel.Options -> OptionsPanel(options, ::setOptions) { panel = null }
        Panel.Lists -> ListsPanel(
            names, { Todos.openCount(all, it) }, { optionStore.listColor(it) },
            onRename = { from, to ->
                commit(Todos.renameList(items.orEmpty(), from, to))
                optionStore.renameListColor(from, Todos.cleanList(to))
                if (list == from) list = Todos.cleanList(to)
                colorTick++
            },
            onColor = { name, c -> optionStore.setListColor(name, c); colorTick++ },
            onDelete = { name -> commit(Todos.deleteList(items.orEmpty(), name)); optionStore.setListColor(name, 0); if (list == name) list = null; colorTick++ },
            onClose = { panel = null },
        )
        Panel.Stats -> StatsPanel(Todos.stats(all, now), now) { panel = null }
        Panel.Data -> DataPanel(
            onShare = {
                val text = TodoFormats.export(all.filter { !it.done && (list == null || it.list == list) }, TodoFormats.Format.Markdown)
                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)) }
                panel = null
            },
            onExport = { f ->
                when (f) {
                    TodoFormats.Format.Markdown -> exportMd.launch("todo.md")
                    TodoFormats.Format.TodoTxt -> exportTxt.launch("todo.txt")
                    TodoFormats.Format.Csv -> exportCsv.launch("todo.csv")
                }
                panel = null
            },
            onImport = { importer.launch(arrayOf("text/*", "application/octet-stream")); panel = null },
            onClose = { panel = null },
        )
        else -> {}
    }
}

private const val REMINDERS_KEY = "reminders"
private const val IMPORT_LIMIT = 1_000_000

private fun chipName(name: String, count: Int?) = if (count != null && count > 0) "$name $count" else name

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenPage(
    groups: List<Todos.Group>,
    loaded: Boolean,
    hasDone: Boolean,
    filtering: Boolean,
    env: RowEnv,
    listName: String,
    kanban: Boolean,
    day: Todos.Day?,
    spoken: String?,
    onSpokenUsed: () -> Unit,
    onDictate: (() -> Unit)?,
    onAdd: (String, Long, String) -> Boolean,
    onReorder: (List<Long>) -> Unit,
    onDelete: (TodoItem) -> Unit,
    tipSeen: Boolean,
    onTipSeen: () -> Unit,
) {
    val colors = LocalMetroColors.current
    val now = env.now
    var text by remember { mutableStateOf("") }
    var due by remember { mutableLongStateOf(0L) }
    var picking by remember { mutableStateOf(false) }
    val quick = remember { Reminders.QuickTime.entries }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    // A long press picks a row up; while it is held the rows of its group are in the order it would leave them in.
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf<Pair<String, List<TodoItem>>?>(null) }
    var swappedAt by remember { mutableLongStateOf(0L) }
    val swivel = rememberRowSwivel()
    val up = stringResource(R.string.todo_move_up)
    val down = stringResource(R.string.todo_move_down)
    val remove = stringResource(R.string.todo_delete)
    val chars = stringResource(R.string.todo_chars, text.length, Todos.MAX_TEXT)
    val edgePx = with(LocalDensity.current) { EDGE_DP.dp.toPx() }
    val landscapeKeyboard = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && WindowInsets.isImeVisible
    val rowEnv = RowEnv(env.now, env.options, env.colorOf, env.completing, env.onToggle, onEdit = { swivel.open(it.id) { env.onEdit(it) } })

    LaunchedEffect(spoken) {
        val said = spoken ?: return@LaunchedEffect
        val parsed = SpokenDate.parse(said, System.currentTimeMillis())
        text = parsed.text
        if (parsed.due != 0L) due = parsed.due
        onSpokenUsed()
    }

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

    // A row held near the top or the bottom edge makes the list scroll, faster the closer it is to the edge.
    LaunchedEffect(dragId) {
        while (dragId != null && isActive) {
            val info = listState.layoutInfo
            val me = info.visibleItemsInfo.firstOrNull { it.key == dragId }
            if (me != null) {
                val centre = me.offset + me.size / 2f + dragOffset
                val top = info.viewportStartOffset + edgePx
                val bottom = info.viewportEndOffset - edgePx
                val speed = when {
                    centre < top -> -(top - centre) / edgePx * MAX_SCROLL
                    centre > bottom -> (centre - bottom) / edgePx * MAX_SCROLL
                    else -> 0f
                }
                if (speed != 0f) {
                    val used = listState.scrollBy(speed)
                    dragOffset += used
                    swapUnderDrag()
                }
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }

    fun drop() {
        working?.let { onReorder(it.second.map { r -> r.id }) }
        dragId = null; dragOffset = 0f; working = null
    }

    fun submit() {
        if (text.isNotBlank() && onAdd(text, due, listName)) { text = ""; due = 0L }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (day != null) DashboardCard(day)
        if (loaded && groups.isEmpty()) {
            val state = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(state, enter = scaleIn(initialScale = 0.6f) + fadeIn()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("todo:empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EmptyArt(done = hasDone && !filtering)
                    PageNote(stringResource(if (filtering) R.string.todo_no_match else if (hasDone) R.string.todo_all_done else R.string.todo_empty), Modifier.fillMaxWidth(), )
                }
            }
        }
        if (!tipSeen && !kanban && groups.any { it.items.size > 1 }) {
            PageNote(stringResource(R.string.todo_tip_drag), Modifier.padding(bottom = 6.dp).testTag("todo:tip").metroPress(onClick = onTipSeen))
        }
        if (kanban) {
            KanbanBoard(groups, rowEnv, countOf = { if (env.options.counts) it.items.size else null }, modifier = Modifier.weight(1f))
        } else LazyColumn(Modifier.weight(1f).scrollIndicator(listState).testTag("todo:list"), state = listState) {
            groups.forEach { group ->
                val rows = working?.takeIf { it.first == group.key }?.second ?: group.items
                sectionHeader("h:${group.key}") {
                    GroupTitle(group, now, if (env.options.counts) group.items.size else null, if (env.options.listColors) group.list?.let { env.colorOf(it) } else null)
                }
                itemsIndexed(rows, key = { _, it -> it.id }) { index, item ->
                    val dragging = dragId == item.id
                    Box(
                        (if (dragging) Modifier.zIndex(1f) else Modifier.animateItem()).featherRow(index).swivel(swivel, item.id)
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(up) { onReorder(Todos.moved(rows.map { it.id }, item.id, -1)); true },
                                    CustomAccessibilityAction(down) { onReorder(Todos.moved(rows.map { it.id }, item.id, 1)); true },
                                    CustomAccessibilityAction(remove) { onDelete(item); true },
                                )
                            },
                    ) {
                        // The place the row will leave, kept visible while it is held.
                        if (dragging) Box(Modifier.matchParentSize().background(colors.subtle.copy(alpha = 0.18f)))
                        TodoRow(
                            item, rowEnv,
                            modifier = (if (dragging) Modifier.graphicsLayer { translationY = dragOffset; scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 12.dp.toPx(); shape = RectangleShape } .background(colors.background) else Modifier)
                                .swipeRow(env.options.swipe && !dragging, onRight = { env.onToggle(item) }, onLeft = { onDelete(item) })
                                .pointerInput(item.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!tipSeen) onTipSeen()
                                            dragId = item.id; dragOffset = 0f; working = group.key to rows
                                        },
                                        onDrag = { change, amount -> change.consume(); dragOffset += amount.y; swapUnderDrag() },
                                        onDragEnd = { drop() },
                                        onDragCancel = { dragId = null; dragOffset = 0f; working = null },
                                    )
                                },
                        )
                    }
                }
            }
        }
        if (!landscapeKeyboard) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp).fadeEdges()) {
                item { TodoChip(stringResource(R.string.todo_no_date), due == 0L, "todo:due:none") { due = 0L } }
                items(quick) { which ->
                    val at = Reminders.quickTime(which, LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    TodoChip(stringResource(quickName(which)), due == at, "todo:due:${which.name}") { due = at }
                }
                item {
                    val custom = due != 0L && quick.none { w -> Reminders.quickTime(w, LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() == due }
                    TodoChip(if (custom) whenText(due, now) else stringResource(R.string.todo_pick), custom, "todo:due:pick") { picking = true }
                }
            }
        }
        if (text.length >= Todos.MAX_TEXT * 4 / 5) PageNote(chars, Modifier.fillMaxWidth().testTag("todo:chars"))
        val addLabel = stringResource(R.string.todo_cd_add)
        val micLabel = stringResource(R.string.todo_cd_mic)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroTextField(text, { text = it.take(Todos.MAX_TEXT) }, stringResource(R.string.todo_hint), Modifier.weight(1f).testTag("todo:input"), onSearch = { submit() })
            if (onDictate != null) {
                Box(Modifier.size(48.dp).testTag("todo:mic").semantics { contentDescription = micLabel }.metroPress(onClick = onDictate), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Mic, 26.dp) }
            }
            MetroButton("＋", Modifier.semantics { contentDescription = addLabel }.testTag("todo:add")) { submit() }
        }
    }
    if (picking) DateTimeDialog(if (due != 0L) due else now + Todos.DEFAULT_DUE_OFFSET_MS, onChoose = { due = it; picking = false }, onCancel = { picking = false })
}

private const val SWAP_PAUSE_NS = 90_000_000L
private const val EDGE_DP = 72
private const val MAX_SCROLL = 22f

@Composable
private fun DonePage(rows: List<TodoItem>, env: RowEnv) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (rows.isEmpty()) PageNote(stringResource(R.string.todo_nothing_done), Modifier.testTag("todo:done:empty"))
        val listState = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().scrollIndicator(listState).testTag("todo:done"), state = listState) {
            itemsIndexed(rows, key = { _, it -> it.id }) { index, item -> TodoRow(item, env, Modifier.featherRow(index)) }
        }
    }
}
