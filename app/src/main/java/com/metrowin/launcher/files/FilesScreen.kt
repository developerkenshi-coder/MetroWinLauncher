package com.metrowin.launcher.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.metrowin.launcher.R
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Locale

/** What waits to be pasted. */
data class Clip(val files: List<File>, val move: Boolean)

/** One of the two panels: where it is, what is picked in it and what is searched. */
class PaneState(start: File) {
    var path by mutableStateOf(start)
    var selection by mutableStateOf(emptySet<String>())
    var query by mutableStateOf("")
    var deep by mutableStateOf(false)
    var searching by mutableStateOf(false)
}

@Composable
fun FilesScreen() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(FilesSupport.hasAccess(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = FilesSupport.hasAccess(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { allowed = FilesSupport.hasAccess(context) }
    if (!allowed) {
        MiniFrame {
            MetroText(stringResource(R.string.stock_files), size = 44.sp, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
            Column(Modifier.padding(horizontal = 24.dp)) {
                PageNote(stringResource(R.string.fm_need_access))
                MetroButton(stringResource(R.string.recorder_allow), Modifier.padding(top = 14.dp).testTag("fm:allow")) {
                    if (android.os.Build.VERSION.SDK_INT >= 30) FilesSupport.openAccessSettings(context) else permission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
        return
    }
    Manager()
}

private fun relative(places: List<Place>, dir: File): List<Pair<String, File>> {
    val root = places.filter { it.kind == Place.Kind.Storage && FileOps.isInside(dir, it.file) }.maxByOrNull { it.file.absolutePath.length }
    val base = root?.file ?: File("/")
    val parts = ArrayList<Pair<String, File>>()
    parts += (root?.name ?: "/") to base
    var current = base
    dir.absolutePath.removePrefix(base.absolutePath).split('/').filter { it.isNotEmpty() }.forEach { part ->
        current = File(current, part)
        parts += part to current
    }
    return parts
}

@Composable
private fun Manager() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { FilesPrefs(context) }
    val storages = remember { FilesSupport.storages(context) }
    val places = remember { storages + FilesSupport.standardFolders(context) }
    var bookmarks by remember { mutableStateOf(prefs.bookmarks) }
    val left = remember { PaneState(prefs.lastPath(0) ?: storages.first().file) }
    val right = remember { PaneState(prefs.lastPath(1) ?: FilesSupport.standardFolders(context).firstOrNull()?.file ?: storages.first().file) }
    var active by remember { mutableIntStateOf(0) }
    var clip by remember { mutableStateOf<Clip?>(null) }
    var sort by remember { mutableStateOf(prefs.sort) }
    var hidden by remember { mutableStateOf(prefs.showHidden) }
    var version by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<FilesDialog?>(null) }
    var operation by remember { mutableStateOf<Pair<String, Progress>?>(null) }
    val trash = remember { FilesSupport.trash(context).also { runCatching { it.purgeOld() } } }
    val pager = rememberPagerState { 2 }
    fun pane(i: Int) = if (i == 0) left else right
    val current = pane(active)
    val other = pane(1 - active)

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    /** Runs [work] away from the screen with a bar that shows how far it is and a button to stop it. */
    fun run(title: String, total: Long, work: (Progress) -> Unit) {
        val progress = Progress().also { it.totalBytes = total }
        operation = title to progress
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { work(progress) } }
            operation = null
            version++
            left.selection = emptySet(); right.selection = emptySet()
            if (progress.failed > 0) toast(context.getString(R.string.fm_failed, progress.failed))
        }
    }

    fun transfer(files: List<File>, target: File, move: Boolean, conflict: Conflict) =
        run(context.getString(if (move) R.string.fm_cut else R.string.fm_copy), FileOps.measure(files)) { progress ->
            if (move) FileOps.move(files, target, conflict, progress) else FileOps.copy(files, target, conflict, progress)
        }

    fun start(files: List<File>, target: File, move: Boolean) {
        val taken = FileOps.conflicts(files, target).filter { it.parentFile?.canonicalFile != target.canonicalFile }
        if (taken.isEmpty()) transfer(files, target, move, Conflict.KeepBoth)
        else dialog = FilesDialog.Conflicts(taken.size) { policy -> transfer(files, target, move, policy) }
    }

    fun selected(p: PaneState, entries: List<File>) = entries.filter { it.absolutePath in p.selection }

    fun go(p: PaneState, dir: File, index: Int) {
        p.path = dir
        p.selection = emptySet(); p.query = ""; p.searching = false
        prefs.setLastPath(index, dir)
    }

    // The list of what is in each panel: read away from the screen, again when the folder, the order or the version changes.
    val leftFiles by produceState<List<Entry>?>(null, left.path, sort, hidden, version) { value = withContext(Dispatchers.IO) { FileOps.list(left.path, hidden, sort) } }
    val rightFiles by produceState<List<Entry>?>(null, right.path, sort, hidden, version) { value = withContext(Dispatchers.IO) { FileOps.list(right.path, hidden, sort) } }
    val leftFound by produceState<List<File>?>(null, left.query, left.deep, left.path, hidden, version) {
        value = if (left.query.isBlank()) null else withContext(Dispatchers.IO) { if (left.deep) FileOps.search(left.path, left.query, hidden) else leftFiles?.filter { it.name.contains(left.query.trim(), true) }?.map { it.file } }
    }
    val rightFound by produceState<List<File>?>(null, right.query, right.deep, right.path, hidden, version) {
        value = if (right.query.isBlank()) null else withContext(Dispatchers.IO) { if (right.deep) FileOps.search(right.path, right.query, hidden) else rightFiles?.filter { it.name.contains(right.query.trim(), true) }?.map { it.file } }
    }
    fun shownOf(i: Int): List<File> = (if (i == 0) leftFound else rightFound) ?: (if (i == 0) leftFiles else rightFiles)?.map { it.file }.orEmpty()
    fun entries(i: Int) = shownOf(i)

    BackHandler(enabled = dialog != null) { dialog = null }
    BackHandler(enabled = dialog == null && (current.selection.isNotEmpty() || current.searching || relative(places, current.path).size > 1)) {
        when {
            current.selection.isNotEmpty() -> current.selection = emptySet()
            current.searching -> { current.searching = false; current.query = "" }
            else -> current.path.parentFile?.let { go(current, it, active) }
        }
    }

    val picked = selected(current, entries(active))
    val pickedAny = picked.isNotEmpty()

    fun delete(files: List<File>, forever: Boolean) = run(context.getString(R.string.fm_delete), files.size.toLong()) { progress ->
        files.forEach { f ->
            if (progress.cancelled) return@forEach
            progress.current = f.name
            val ok = if (forever) FileOps.delete(f) else trash.put(f)
            if (!ok) progress.failed++
            progress.doneBytes++
        }
    }

    MiniFrame {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > maxHeight
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    for (i in 0..1) Box(Modifier.weight(1f).fillMaxSize()) {
                        PanePage(
                            pane(i), i, i == active, places, bookmarks, entries(i), if (i == 0) leftFiles == null else rightFiles == null, sort,
                            onActivate = { active = i }, onGo = { go(pane(i), it, i) }, onOpenFile = { f -> dialog = fileDialog(f, context) },
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    PivotHeader(
                        listOf(relative(places, left.path).last().first, relative(places, right.path).last().first),
                        pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                    )
                    androidx.compose.runtime.LaunchedEffect(pager.currentPage) { active = pager.currentPage }
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { i ->
                        PanePage(
                            pane(i), i, i == active, places, bookmarks, entries(i), if (i == 0) leftFiles == null else rightFiles == null, sort,
                            onActivate = { active = i }, onGo = { go(pane(i), it, i) }, onOpenFile = { f -> dialog = fileDialog(f, context) },
                        )
                    }
                }
            }
        }
        if (pickedAny) {
            AppBar(
                listOf(
                    AppBarItem("⧉", stringResource(R.string.fm_copy)) { clip = Clip(picked, false); current.selection = emptySet() },
                    AppBarItem("✂", stringResource(R.string.fm_cut)) { clip = Clip(picked, true); current.selection = emptySet() },
                    AppBarItem("✕", stringResource(R.string.fm_delete)) { dialog = FilesDialog.Delete(picked.size) { forever -> delete(picked, forever) } },
                    AppBarItem("⇧", stringResource(R.string.fm_share)) { FilesSupport.share(context, picked) },
                    AppBarItem("⋯", stringResource(R.string.fm_more)) { dialog = FilesDialog.Actions(picked) },
                ),
            )
        } else {
            AppBar(
                buildList {
                    add(AppBarItem("＋", stringResource(R.string.fm_new)) { dialog = FilesDialog.New })
                    clip?.let { add(AppBarItem("⎘", stringResource(R.string.fm_paste)) { start(it.files, current.path, it.move); if (it.move) clip = null }) }
                    add(AppBarItem("☆", stringResource(R.string.fm_places)) { dialog = FilesDialog.Places })
                    add(AppBarItem("⇅", stringResource(R.string.fm_sort)) { dialog = FilesDialog.SortOptions })
                    add(AppBarItem("⋯", stringResource(R.string.fm_more)) { dialog = FilesDialog.Menu })
                },
            )
        }
    }

    // --- the dialogs
    operation?.let { (title, progress) -> ProgressDialog(title, progress) }
    when (val d = dialog) {
        null -> {}
        FilesDialog.New -> NewDialog(
            onFolder = { name -> if (FileOps.mkdir(current.path, name) != null) { version++; dialog = null } else toast(context.getString(R.string.fm_name_bad)) },
            onFile = { name -> if (FileOps.createFile(current.path, name) != null) { version++; dialog = null } else toast(context.getString(R.string.fm_name_bad)) },
            onCancel = { dialog = null },
        )
        FilesDialog.Places -> PlacesDialog(
            storages, FilesSupport.standardFolders(context), bookmarks,
            onGo = { go(current, it, active); dialog = null },
            onTrash = { dialog = FilesDialog.Trash },
            onCancel = { dialog = null },
        )
        FilesDialog.SortOptions -> SortDialog(sort, hidden, onSort = { sort = it; prefs.sort = it }, onHidden = { hidden = it; prefs.showHidden = it }, onClose = { dialog = null })
        FilesDialog.Menu -> MenuDialog(
            canPaste = clip != null,
            onPaste = { clip?.let { start(it.files, current.path, it.move); if (it.move) clip = null }; dialog = null },
            onSearch = { current.searching = true; dialog = null },
            onSelectAll = { current.selection = entries(active).map { it.absolutePath }.toSet(); dialog = null },
            onTrash = { dialog = FilesDialog.Trash },
            onBookmark = {
                val here = current.path
                bookmarks = if (here in bookmarks) bookmarks - here else bookmarks + here
                prefs.bookmarks = bookmarks
                dialog = null
            },
            bookmarked = current.path in bookmarks,
            onCancel = { dialog = null },
        )
        FilesDialog.Trash -> TrashDialog(trash, onChanged = { version++ }, fallback = File(storages.first().file, "Restored"), onClose = { dialog = null })
        is FilesDialog.Conflicts -> ConflictDialog(d.count, onChoose = { policy -> dialog = null; d.then(policy) }, onCancel = { dialog = null })
        is FilesDialog.Delete -> DeleteDialog(d.count, onChoose = { forever -> dialog = null; d.then(forever) }, onCancel = { dialog = null })
        is FilesDialog.Actions -> ActionsDialog(
            d.files, otherPath = other.path,
            onRename = { dialog = FilesDialog.Rename(d.files.first()) },
            onZip = { dialog = FilesDialog.ZipName(d.files) },
            onExtract = { toFolder -> extractZip(d.files.first(), current.path, toFolder, ::run, context) { dialog = null } },
            onToOther = { move -> start(d.files, other.path, move); dialog = null },
            onProperties = { dialog = FilesDialog.Properties(d.files.first()) },
            onInvert = { val all = entries(active).map { it.absolutePath }.toSet(); current.selection = all - current.selection; dialog = null },
            onSelectAll = { current.selection = entries(active).map { it.absolutePath }.toSet(); dialog = null },
            onCopyPath = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("path", d.files.joinToString("\n") { it.absolutePath }))
                toast(context.getString(R.string.fm_path_copied)); dialog = null
            },
            onBookmark = {
                val dir = d.files.first()
                bookmarks = if (dir in bookmarks) bookmarks - dir else bookmarks + dir
                prefs.bookmarks = bookmarks; dialog = null
            },
            bookmarks = bookmarks,
            onEdit = { dialog = FilesDialog.Editor(d.files.first()) },
            onCancel = { dialog = null },
        )
        is FilesDialog.Rename -> NameDialog(stringResource(R.string.fm_rename), d.file.name, onOk = { name ->
            if (FileOps.rename(d.file, name) != null) { current.selection = emptySet(); version++; dialog = null } else toast(context.getString(R.string.fm_name_bad))
        }, onCancel = { dialog = null })
        is FilesDialog.ZipName -> NameDialog(stringResource(R.string.fm_zip_name), (if (d.files.size == 1) d.files.first().nameWithoutExtension else current.path.name.ifEmpty { "archive" }) + ".zip", onOk = { name ->
            val target = FileOps.uniqueFile(current.path, if (name.endsWith(".zip", true)) name else "$name.zip")
            if (!FileOps.validName(target.name)) toast(context.getString(R.string.fm_name_bad)) else {
                dialog = null
                run(context.getString(R.string.fm_zip), FileOps.measure(d.files)) { progress -> if (!FileOps.zip(d.files, target, progress)) progress.failed++ }
            }
        }, onCancel = { dialog = null })
        is FilesDialog.Properties -> PropertiesDialog(d.file, onClose = { dialog = null })
        is FilesDialog.Editor -> TextEditor(d.file, onClose = { dialog = null; version++ })
        is FilesDialog.Zip -> ZipChoice(d.file, onHere = { extractZip(d.file, current.path, false, ::run, context) { dialog = null } }, onFolder = { extractZip(d.file, current.path, true, ::run, context) { dialog = null } }, onOpen = { FilesSupport.open(context, d.file); dialog = null }, onCancel = { dialog = null })
    }
}

private fun fileDialog(file: File, context: Context): FilesDialog? {
    when {
        FileOps.isZip(file) && !file.name.endsWith(".apk", true) -> return FilesDialog.Zip(file)
        FileOps.isText(file) -> return FilesDialog.Editor(file)
    }
    FilesSupport.open(context, file)
    return null
}

private fun extractZip(zip: File, here: File, toFolder: Boolean, run: (String, Long, (Progress) -> Unit) -> Unit, context: Context, done: () -> Unit) {
    done()
    run(context.getString(R.string.fm_extract_here), zip.length()) { progress ->
        val target = if (toFolder) FileOps.uniqueFile(here, zip.nameWithoutExtension).also { it.mkdirs() } else here
        if (FileOps.unzip(zip, target, progress) < 0) progress.failed++
    }
}

@Composable
private fun PanePage(
    pane: PaneState,
    index: Int,
    isActive: Boolean,
    places: List<Place>,
    bookmarks: List<File>,
    files: List<File>,
    loading: Boolean,
    sort: Sort,
    onActivate: () -> Unit,
    onGo: (File) -> Unit,
    onOpenFile: (File) -> Unit,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val context = LocalContext.current
    val crumbs = relative(places, pane.path)
    val state = rememberLazyListState()
    Column(Modifier.fillMaxSize().testTag("fm:pane:$index").padding(horizontal = 16.dp).then(if (isActive) Modifier else Modifier)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 6.dp).testTag("fm:crumbs:$index")) {
            items(crumbs.size) { i ->
                MetroText(
                    (if (i > 0) "› " else "") + crumbs[i].first, size = 17.sp, weight = if (i == crumbs.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (i == crumbs.lastIndex) accent else colors.subtle, maxLines = 1, minScale = 1f,
                    modifier = Modifier.metroPress(onClick = { onActivate(); onGo(crumbs[i].second) }).padding(vertical = 6.dp, horizontal = 3.dp),
                )
            }
        }
        if (pane.searching) {
            MetroTextField(pane.query, { pane.query = it.take(60) }, stringResource(R.string.fm_search_hint), Modifier.testTag("fm:search:$index"))
            MetroText(
                (if (pane.deep) "☑ " else "☐ ") + stringResource(R.string.fm_search_deep), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle,
                modifier = Modifier.metroPress(onClick = { pane.deep = !pane.deep }).padding(vertical = 8.dp),
            )
        }
        if (!loading && files.isEmpty()) PageNote(stringResource(if (pane.path.canRead()) R.string.fm_folder_empty else R.string.fm_unreadable), Modifier.testTag("fm:empty:$index"))
        LazyColumn(Modifier.fillMaxSize().testTag("fm:list:$index"), state = state) {
            items(files, key = { it.absolutePath }) { file ->
                val chosen = file.absolutePath in pane.selection
                val dir = file.isDirectory
                Row(
                    Modifier.fillMaxWidth().testTag("fm:row").background(if (chosen) accent.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent)
                        .metroPress(
                            onClick = {
                                onActivate()
                                when {
                                    pane.selection.isNotEmpty() -> pane.selection = if (chosen) pane.selection - file.absolutePath else pane.selection + file.absolutePath
                                    dir -> onGo(file)
                                    else -> onOpenFile(file)
                                }
                            },
                            onLongClick = { onActivate(); pane.selection = if (chosen) pane.selection - file.absolutePath else pane.selection + file.absolutePath },
                        ).padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).background(if (chosen) accent else colors.surface), contentAlignment = Alignment.Center) {
                        when {
                            chosen -> MetroText("✓", size = 22.sp, color = onAccent())
                            dir -> GlyphIcon(Glyph.Folder, 28.dp)
                            else -> MetroText(file.extension.take(4).uppercase(Locale.ROOT).ifEmpty { "·" }, size = 12.sp, weight = FontWeight.SemiBold, minScale = 0.5f, maxLines = 1)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        MetroText(file.name, size = 19.sp)
                        val when_ = DateFormat.getMediumDateFormat(context).format(Date(file.lastModified()))
                        MetroText(
                            (if (dir) "" else StorageInfo.format(file.length(), Locale.getDefault()) + "  ·  ") + when_ + (if (pane.query.isNotBlank() && pane.deep) "  ·  " + (file.parentFile?.name ?: "") else ""),
                            size = 13.sp, weight = FontWeight.Normal, color = colors.subtle,
                        )
                    }
                }
            }
        }
    }
}
