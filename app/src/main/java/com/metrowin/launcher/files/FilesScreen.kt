package com.metrowin.launcher.files

import com.metrowin.launcher.ui.actioncenter.MetroToast
import androidx.compose.ui.res.pluralStringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateFormat
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier as M
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.pages.PageChip
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
import kotlinx.coroutines.isActive
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
    /** The group of files shown instead of the folder, and the kind of document within it; null when the folder is shown. */
    var category by mutableStateOf<Category?>(null)
    var doc by mutableStateOf<DocKind?>(null)
    /** Where the groups are looked for: the storage the panel was in when the group was chosen. */
    var scanRoot by mutableStateOf(start)
}

/** What a panel shows: its folder, or what a search found in it, or the files of a group. */
private class PaneData(val list: List<Entry>?, val found: List<File>?, val scan: List<Entry>?, val scanning: Boolean) {
    val loading get() = if (scanning) scan == null else list == null
    val files: List<File> get() = if (scanning) scan?.map { it.file }.orEmpty() else found ?: list?.map { it.file }.orEmpty()
}

/** The hits are at most this many in a group, so a big storage does not fill the memory. */
private const val SCAN_LIMIT = 500

@Composable
private fun paneData(pane: PaneState, sort: Sort, hidden: Boolean, version: Int, opened: Map<String, Long>): PaneData {
    // The list is read away from the screen, again when the folder, the order or the version changes.
    val list by produceState<List<Entry>?>(null, pane.path, sort, hidden, version, if (sort.key == SortKey.Opened) opened else null) {
        value = withContext(Dispatchers.IO) { FileOps.list(pane.path, hidden, sort, opened) }
    }
    val found by produceState<List<File>?>(null, pane.query, pane.deep, pane.path, hidden, version, list) {
        value = if (pane.query.isBlank()) null else withContext(Dispatchers.IO) {
            if (pane.deep) FileOps.search(pane.path, pane.query, hidden) else list?.filter { it.name.contains(pane.query.trim(), true) }?.map { it.file }
        }
    }
    // A group is looked for in the background; changing the group or leaving it cancels the walk.
    val scan by produceState<List<Entry>?>(null, pane.category, pane.doc, pane.scanRoot, hidden, version, sort) {
        value = null
        value = pane.category?.let { c ->
            withContext(Dispatchers.IO) { FileKinds.scan(pane.scanRoot, c, pane.doc, hidden, SCAN_LIMIT, active = { isActive }).sortedWith(FileOps.comparator(sort)) }
        }
    }
    return PaneData(list, found, scan, pane.category != null)
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
    var tiles by remember { mutableStateOf(prefs.tiles) }
    var opened by remember { mutableStateOf(prefs.opened) }
    var version by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<FilesDialog?>(null) }
    var operation by remember { mutableStateOf<Pair<String, Progress>?>(null) }
    val trash = remember { FilesSupport.trash(context).also { runCatching { it.purgeOld() } } }
    val pager = rememberPagerState { 2 }
    fun pane(i: Int) = if (i == 0) left else right
    val current = pane(active)
    val other = pane(1 - active)

    fun toast(text: String) = MetroToast.show(context, text)

    /** Runs [work] away from the screen with a bar that shows how far it is and a button to stop it. */
    fun run(title: String, total: Long, work: (Progress) -> Unit) {
        val progress = Progress().also { it.totalBytes = total }
        operation = title to progress
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { work(progress) } }
            operation = null
            version++
            left.selection = emptySet(); right.selection = emptySet()
            when {
                progress.reason == FileOps.ENCRYPTED -> toast(context.getString(R.string.fm_extract_password))
                progress.reason == FileOps.DAMAGED -> toast(context.getString(R.string.fm_extract_bad))
                progress.failed > 0 -> toast(context.resources.getQuantityString(R.plurals.fm_failed, progress.failed, progress.failed))
            }
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

    fun go(p: PaneState, dir: File, index: Int) {
        p.path = dir
        p.selection = emptySet(); p.query = ""; p.searching = false; p.category = null; p.doc = null
        prefs.setLastPath(index, dir)
    }

    val leftData = paneData(left, sort, hidden, version, opened)
    val rightData = paneData(right, sort, hidden, version, opened)
    fun data(i: Int) = if (i == 0) leftData else rightData
    fun entries(i: Int) = data(i).files

    BackHandler(enabled = dialog != null) { dialog = null }
    BackHandler(enabled = dialog == null && (current.selection.isNotEmpty() || current.category != null || current.searching || relative(places, current.path).size > 1)) {
        when {
            current.selection.isNotEmpty() -> current.selection = emptySet()
            current.category != null -> { current.category = null; current.doc = null }
            current.searching -> { current.searching = false; current.query = "" }
            else -> current.path.parentFile?.let { go(current, it, active) }
        }
    }

    val picked = entries(active).filter { it.absolutePath in current.selection }
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

    fun openFile(f: File) {
        opened = prefs.markOpened(f)
        dialog = fileDialog(f, context)
    }

    MiniFrame {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth > maxHeight
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    for (i in 0..1) Box(Modifier.weight(1f).fillMaxSize()) {
                        PanePage(pane(i), i, places, bookmarks, data(i), tiles, onActivate = { active = i }, onGo = { go(pane(i), it, i) }, onOpenFile = ::openFile)
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
                        PanePage(pane(i), i, places, bookmarks, data(i), tiles, onActivate = { active = i }, onGo = { go(pane(i), it, i) }, onOpenFile = ::openFile)
                    }
                }
            }
        }
        if (pickedAny) {
            FilesBar(
                buildList {
                    add(AppBarItem("⧉", stringResource(R.string.fm_copy)) { clip = Clip(picked, false); current.selection = emptySet() })
                    add(AppBarItem("✂", stringResource(R.string.fm_cut)) { clip = Clip(picked, true); current.selection = emptySet() })
                    add(AppBarItem("✕", stringResource(R.string.fm_delete)) { dialog = FilesDialog.Delete(picked.size) { forever -> delete(picked, forever) } })
                    add(AppBarItem("⇧", stringResource(R.string.fm_share)) { FilesSupport.share(context, picked) })
                    add(AppBarItem("▣", stringResource(R.string.fm_compress)) { dialog = FilesDialog.Compress(picked) })
                    if (picked.size == 1) add(AppBarItem("✎", stringResource(R.string.fm_rename_short)) { dialog = FilesDialog.Rename(picked.first()) })
                    add(AppBarItem("⋯", stringResource(R.string.fm_more)) { dialog = FilesDialog.Actions(picked) })
                },
            )
        } else {
            FilesBar(
                buildList {
                    add(AppBarItem("＋", stringResource(R.string.fm_new)) { dialog = FilesDialog.New })
                    clip?.let { add(AppBarItem("⎘", stringResource(R.string.fm_paste)) { start(it.files, current.path, it.move); if (it.move) clip = null }) }
                    add(AppBarItem("☑", stringResource(R.string.fm_select)) { current.selection = entries(active).map { it.absolutePath }.toSet() })
                    add(AppBarItem("⇅", stringResource(R.string.fm_view)) { dialog = FilesDialog.View })
                    add(AppBarItem("☰", stringResource(R.string.fm_categories)) { dialog = FilesDialog.Categories })
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
        FilesDialog.View -> ViewDialog(
            sort, hidden, tiles,
            onSort = { sort = it; prefs.sort = it }, onHidden = { hidden = it; prefs.showHidden = it }, onTiles = { tiles = it; prefs.tiles = it },
            onClose = { dialog = null },
        )
        FilesDialog.Categories -> CategoriesDialog(
            onPick = { c ->
                current.selection = emptySet(); current.query = ""; current.searching = false
                current.scanRoot = relative(places, current.path).first().second
                current.doc = null; current.category = c
                dialog = null
            },
            onCancel = { dialog = null },
        )
        FilesDialog.Menu -> MenuDialog(
            canPaste = clip != null, hidden = hidden, bookmarked = current.path in bookmarks,
            onPaste = { clip?.let { start(it.files, current.path, it.move); if (it.move) clip = null }; dialog = null },
            onPlaces = { dialog = FilesDialog.Places },
            onSearch = { current.category = null; current.searching = true; dialog = null },
            onHidden = { hidden = !hidden; prefs.showHidden = hidden; dialog = null },
            onTrash = { dialog = FilesDialog.Trash },
            onBookmark = {
                val here = current.path
                bookmarks = if (here in bookmarks) bookmarks - here else bookmarks + here
                prefs.bookmarks = bookmarks
                dialog = null
            },
            onCancel = { dialog = null },
        )
        FilesDialog.Trash -> TrashDialog(trash, onChanged = { version++ }, fallback = File(storages.first().file, "Restored"), onClose = { dialog = null })
        is FilesDialog.Conflicts -> ConflictDialog(d.count, onChoose = { policy -> dialog = null; d.then(policy) }, onCancel = { dialog = null })
        is FilesDialog.Delete -> DeleteDialog(d.count, onChoose = { forever -> dialog = null; d.then(forever) }, onCancel = { dialog = null })
        is FilesDialog.Actions -> ActionsDialog(
            d.files, otherPath = other.path,
            onExtract = { toFolder -> extractArchive(d.files.first(), current.path, toFolder, ::run, context) { dialog = null } },
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
        is FilesDialog.Compress -> CompressDialog(
            (if (d.files.size == 1) d.files.first().nameWithoutExtension else current.path.name.ifEmpty { "archive" }),
            onOk = { name, format ->
                val clean = name.trim().removeSuffix("." + format.extension).removeSuffix("." + format.extension.uppercase(Locale.ROOT))
                val folder = d.files.first().parentFile ?: current.path
                val target = FileOps.uniqueFile(folder, "$clean.${format.extension}")
                if (!FileOps.validName(target.name)) toast(context.getString(R.string.fm_name_bad)) else {
                    dialog = null
                    run(context.getString(R.string.fm_zip), FileOps.measure(d.files)) { progress ->
                        val ok = if (format == ArchiveFormat.SevenZ) FileOps.sevenZip(d.files, target, progress) else FileOps.zip(d.files, target, progress)
                        if (!ok) progress.failed++
                    }
                }
            },
            onCancel = { dialog = null },
        )
        is FilesDialog.Properties -> PropertiesDialog(d.file, onClose = { dialog = null })
        is FilesDialog.Editor -> TextEditor(d.file, onClose = { dialog = null; version++ })
        is FilesDialog.Zip -> ZipChoice(d.file, onHere = { extractArchive(d.file, current.path, false, ::run, context) { dialog = null } }, onFolder = { extractArchive(d.file, current.path, true, ::run, context) { dialog = null } }, onOpen = { FilesSupport.open(context, d.file); dialog = null }, onCancel = { dialog = null })
    }
}

private fun fileDialog(file: File, context: Context): FilesDialog? {
    when {
        FileOps.isArchive(file) -> return FilesDialog.Zip(file)
        FileOps.isText(file) -> return FilesDialog.Editor(file)
    }
    FilesSupport.open(context, file)
    return null
}

private fun extractArchive(archive: File, here: File, toFolder: Boolean, run: (String, Long, (Progress) -> Unit) -> Unit, context: Context, done: () -> Unit) {
    done()
    run(context.getString(R.string.fm_extract_here), archive.length()) { progress ->
        val target = if (toFolder) FileOps.uniqueFile(here, archive.nameWithoutExtension).also { it.mkdirs() } else here
        if (FileOps.extract(archive, target, progress) < 0 && toFolder) target.delete()
    }
}

@Composable
private fun PanePage(
    pane: PaneState,
    index: Int,
    places: List<Place>,
    bookmarks: List<File>,
    data: PaneData,
    tiles: Boolean,
    onActivate: () -> Unit,
    onGo: (File) -> Unit,
    onOpenFile: (File) -> Unit,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val context = LocalContext.current
    val category = pane.category
    val crumbs = if (category == null) relative(places, pane.path).map { it.first to it.second }
    else listOf(relative(places, pane.scanRoot).first().let { it.first to it.second })
    val files = data.files
    // The items come in one after the other each time the folder, the group or the way of showing them changes.
    val feather = Triple(pane.path, pane.category to pane.doc, tiles)

    fun click(file: File) {
        onActivate()
        val chosen = file.absolutePath in pane.selection
        when {
            pane.selection.isNotEmpty() -> pane.selection = if (chosen) pane.selection - file.absolutePath else pane.selection + file.absolutePath
            file.isDirectory -> onGo(file)
            else -> onOpenFile(file)
        }
    }
    fun longClick(file: File) {
        onActivate()
        pane.selection = if (file.absolutePath in pane.selection) pane.selection - file.absolutePath else pane.selection + file.absolutePath
    }

    Column(Modifier.fillMaxSize().testTag("fm:pane:$index").padding(horizontal = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 6.dp).testTag("fm:crumbs:$index")) {
            items(crumbs.size + if (category != null) 1 else 0) { i ->
                val last = i == (if (category != null) crumbs.size else crumbs.lastIndex)
                MetroText(
                    (if (i > 0) "› " else "") + (if (i < crumbs.size) crumbs[i].first else stringResource(categoryName(category!!))), size = 17.sp, weight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (last) accent else colors.subtle, maxLines = 1, minScale = 1f,
                    modifier = Modifier.metroPress(onClick = { onActivate(); if (i < crumbs.size) onGo(crumbs[i].second) }).padding(vertical = 6.dp, horizontal = 3.dp),
                )
            }
        }
        if (category == Category.Documents) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                items(listOf<DocKind?>(null) + DocKind.entries) { kind ->
                    PageChip(stringResource(docName(kind)), pane.doc == kind, "fm:doc:${kind?.name ?: "all"}") { onActivate(); pane.doc = kind }
                }
            }
        }
        if (pane.searching) {
            MetroTextField(pane.query, { pane.query = it.take(60) }, stringResource(R.string.fm_search_hint), Modifier.testTag("fm:search:$index"))
            MetroText(
                (if (pane.deep) "☑ " else "☐ ") + stringResource(R.string.fm_search_deep), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle,
                modifier = Modifier.metroPress(onClick = { pane.deep = !pane.deep }).padding(vertical = 8.dp),
            )
        }
        when {
            data.loading && category != null -> PageNote(stringResource(R.string.fm_cat_scanning), Modifier.testTag("fm:scanning:$index"))
            !data.loading && files.isEmpty() -> PageNote(
                stringResource(if (category != null) R.string.fm_cat_none else if (pane.path.canRead()) R.string.fm_folder_empty else R.string.fm_unreadable),
                Modifier.testTag("fm:empty:$index"),
            )
            category != null && files.size >= SCAN_LIMIT -> PageNote(pluralStringResource(R.plurals.fm_cat_limit, SCAN_LIMIT, SCAN_LIMIT))
        }
        if (tiles) {
            LazyVerticalGrid(
                GridCells.Adaptive(104.dp), Modifier.fillMaxSize().testTag("fm:list:$index"),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                gridItemsIndexed(files, key = { _, f -> f.absolutePath }) { i, file ->
                    FileTile(file, file.absolutePath in pane.selection, if (i < 18) Modifier.featherIn(i, feather, stepMs = 30) else Modifier, onClick = { click(file) }, onLongClick = { longClick(file) })
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().testTag("fm:list:$index")) {
                itemsIndexed(files, key = { _, f -> f.absolutePath }) { i, file ->
                    val chosen = file.absolutePath in pane.selection
                    val dir = file.isDirectory
                    Row(
                        Modifier.fillMaxWidth().testTag("fm:row").then(if (i < 14) Modifier.featherIn(i, feather) else Modifier)
                            .background(if (chosen) accent.copy(alpha = 0.35f) else Color.Transparent)
                            .metroPress(onClick = { click(file) }, onLongClick = { longClick(file) }).padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The tiles are in the accent colour, as the icons of the app list of the launcher are.
                        Box(Modifier.size(44.dp).background(accent), contentAlignment = Alignment.Center) {
                            when {
                                chosen -> MetroText("✓", size = 22.sp, color = onAccent())
                                dir -> GlyphIcon(Glyph.Folder, 28.dp, color = onAccent())
                                else -> MetroText(file.extension.take(4).uppercase(Locale.ROOT).ifEmpty { "·" }, size = 12.sp, weight = FontWeight.SemiBold, minScale = 0.5f, maxLines = 1, color = onAccent())
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            MetroText(file.name, size = 19.sp)
                            val when_ = DateFormat.getMediumDateFormat(context).format(Date(file.lastModified()))
                            MetroText(
                                (if (dir) "" else StorageInfo.format(file.length(), Locale.getDefault()) + "  ·  ") + when_ + (if ((pane.query.isNotBlank() && pane.deep) || category != null) "  ·  " + (file.parentFile?.name ?: "") else ""),
                                size = 13.sp, weight = FontWeight.Normal, color = colors.subtle,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A tile of the tile view: a folder in the accent colour, a picture as its small copy, any other file as its extension. */
@Composable
private fun FileTile(file: File, chosen: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val dir = file.isDirectory
    val thumb = if (!dir && FilesThumbs.isPicture(file)) FilesThumbs.load(file) else null
    Box(
        modifier.fillMaxWidth().aspectRatio(1f).testTag("fm:row").metroPress(onClick = onClick, onLongClick = onLongClick)
            .background(if (dir) accent else colors.surface).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            thumb != null -> Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            dir -> GlyphIcon(Glyph.Folder, 44.dp, color = onAccent())
            else -> MetroText(file.extension.take(5).uppercase(Locale.ROOT).ifEmpty { "·" }, size = 24.sp, weight = FontWeight.SemiBold, minScale = 0.5f, maxLines = 1)
        }
        MetroText(
            file.name, size = 12.sp, weight = FontWeight.Normal, maxLines = 2, minScale = 1f, color = if (dir) onAccent() else colors.foreground,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().then(if (thumb != null) Modifier.background(Color.Black.copy(alpha = 0.55f)) else Modifier).padding(6.dp),
        )
        if (chosen) Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) { MetroText("✓", size = 36.sp, color = onAccent()) }
    }
}
