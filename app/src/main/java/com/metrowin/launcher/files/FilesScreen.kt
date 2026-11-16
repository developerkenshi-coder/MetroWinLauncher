package com.metrowin.launcher.files

import com.metrowin.launcher.ui.components.PageSwitch
import com.metrowin.launcher.ui.components.isWide
import com.metrowin.launcher.ui.components.windowClass
import com.metrowin.launcher.ui.components.sectionStarts
import com.metrowin.launcher.ui.components.sectionHeader
import com.metrowin.launcher.ui.apps.JumpList
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.components.scrollIndicator
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
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.kids.CheckSquare
import com.metrowin.launcher.ui.pages.PageChip
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.metrowin.launcher.R
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroProgressBar
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.SelectableRow
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Locale

/** What waits to be pasted. */
data class Clip(val files: List<File>, val move: Boolean)

/** Views that list files by what they are, wherever they are on the storage, instead of a folder. */
enum class Special { Recent, Large, Duplicates }

/** What a copy or a move did, as the screen offers to take it back. */
class UndoOffer(val move: Boolean, val results: List<Pair<File, File>>)

/** One of the two panels: where it is, what is picked in it and what is searched. */
class PaneState(start: File) {
    var path by mutableStateOf(start)
    var selection by mutableStateOf(emptySet<String>())
    var query by mutableStateOf("")
    var deep by mutableStateOf(false)
    /** Search inside text files too (when the option is on). */
    var content by mutableStateOf(false)
    var searching by mutableStateOf(false)
    /** The group of files shown instead of the folder, and the kind of document within it; null when the folder is shown. */
    var category by mutableStateOf<Category?>(null)
    var doc by mutableStateOf<DocKind?>(null)
    /** The view (recent, biggest, duplicates) shown instead of the folder; null when the folder or a group is shown. */
    var special by mutableStateOf<Special?>(null)
    /** Where the groups are looked for: the storage the panel was in when the group was chosen. */
    var scanRoot by mutableStateOf(start)
    /** The last file touched, where a range of the selection starts. */
    var anchor by mutableStateOf<String?>(null)
}

/** What a panel shows: its folder, or what a search found in it, or the files of a group; [loading] until the first of them is there. */
private class PaneData(val entries: List<Entry>, val loading: Boolean, val scanning: Boolean) {
    val files: List<File> by lazy { entries.map { it.file } }
}

/** The hits are at most this many in a group, so a big storage does not fill the memory. */
private const val SCAN_LIMIT = 500
private const val RECENT_DAYS = 14

private fun entryOf(file: File) = Entry(file, file.isDirectory, if (file.isDirectory) 0L else file.length(), file.lastModified(), file.name.startsWith("."))

private class Scanned(val list: List<Entry>, val done: Boolean)

@Composable
private fun paneData(pane: PaneState, sort: Sort, hidden: Boolean, version: Int, opened: Map<String, Long>): PaneData {
    // The list is read away from the screen, again when the folder, the order or the version changes.
    val list by produceState<List<Entry>?>(null, pane.path, sort, hidden, version, if (sort.key == SortKey.Opened) opened else null) {
        value = withContext(Dispatchers.IO) { FileOps.list(pane.path, hidden, sort, opened) }
    }
    // A deep search waits a moment after the last key and stops when the next comes, so typing does not walk the storage for each letter.
    val found by produceState<List<Entry>?>(null, pane.query, pane.deep, pane.content, pane.path, hidden, version, if (pane.deep || pane.content) null else list) {
        value = if (pane.query.isBlank()) null else if (pane.deep || pane.content) {
            delay(300)
            withContext(Dispatchers.IO) { FileOps.search(pane.path, pane.query, hidden, content = pane.content, active = { isActive }).map(::entryOf) }
        } else list?.filter { it.name.contains(pane.query.trim(), true) }
    }
    // A group is looked for in the background, the hits shown as they come; changing the group or leaving it cancels the walk.
    val scan by produceState<Scanned?>(null, pane.category, pane.doc, pane.special, pane.scanRoot, hidden, version, if (pane.special != null) null else sort) {
        value = null
        val category = pane.category
        val special = pane.special
        if (category == null && special == null) return@produceState
        val cmp = FileOps.comparator(sort)
        val active = { isActive }
        val result = withContext(Dispatchers.IO) {
            when (special) {
                Special.Recent -> FileViews.recent(pane.scanRoot, hidden, System.currentTimeMillis() - RECENT_DAYS * 86_400_000L, SCAN_LIMIT, active)
                Special.Large -> FileViews.largest(pane.scanRoot, hidden, 200, active)
                Special.Duplicates -> FileViews.duplicates(pane.scanRoot, hidden, active = active)
                null -> FileKinds.scan(
                    pane.scanRoot, category!!, pane.doc, hidden, SCAN_LIMIT, active, comparator = cmp,
                    onPartial = { part -> value = Scanned(part.sortedWith(cmp).take(SCAN_LIMIT), false) },
                )
            }
        }
        value = Scanned(result, true)
    }
    val scanning = pane.category != null || pane.special != null
    val entries = when {
        scanning -> scan?.list.orEmpty()
        else -> found ?: list.orEmpty()
    }
    return PaneData(entries, if (scanning) scan == null else list == null, scanning && scan?.done != true)
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
                GlyphIcon(Glyph.Folder, 64.dp, Modifier.padding(bottom = 12.dp), LocalMetroColors.current.subtle)
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
    var tileSide by remember { mutableIntStateOf(prefs.tileSide) }
    var opened by remember { mutableStateOf(prefs.opened) }
    var folderSorts by remember { mutableStateOf(prefs.folderSorts) }
    var tags by remember { mutableStateOf(prefs.tags) }
    val opts = remember { mutableStateMapOf<Opt, Boolean>().also { m -> Opt.entries.forEach { m[it] = prefs.flag(it) } } }
    fun on(o: Opt) = opts[o] ?: o.default
    var version by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<FilesDialog?>(null) }
    var undo by remember { mutableStateOf<UndoOffer?>(null) }
    val trash = remember { FilesSupport.trash(context) }
    // The old things of the trash go away from the screen, not while the folder is being drawn.
    LaunchedEffect(trash) { withContext(Dispatchers.IO) { runCatching { trash.purgeOld() } } }
    val pager = rememberPagerState { 2 }
    fun pane(i: Int) = if (i == 0) left else right
    val current = pane(active)
    val other = pane(1 - active)
    val job = FilesJobs.current
    val finished = FilesJobs.result

    fun toast(text: String) = MetroToast.show(context, text)

    /** The order a panel lists by: the one of its folder when each folder has its own, else the common one. */
    fun sortFor(p: PaneState): Sort = (if (on(Opt.SortPerFolder)) folderSorts[p.path.absolutePath] ?: sort else sort).copy(natural = on(Opt.Natural))

    /** Runs [work] away from the screen (and from the life of the screen) with a bar that shows how far it is and a button to stop it. */
    fun startJob(title: String, total: Long, kind: JobKind = JobKind.Other, conflict: Conflict? = null, byCount: Boolean = false, work: (Progress) -> Unit): Boolean {
        val started = FilesJobs.start(title, total, byCount, kind, conflict, work)
        if (!started) toast(context.getString(R.string.fmx_busy))
        return started
    }

    // The result of a work that ended, also one that ended while the screen was away.
    LaunchedEffect(finished?.id) {
        val r = finished ?: return@LaunchedEffect
        FilesJobs.taken(r)
        version++
        left.selection = emptySet(); right.selection = emptySet()
        val p = r.job.progress
        when {
            p.reason == FileOps.ENCRYPTED -> toast(context.getString(R.string.fm_extract_password))
            p.reason == FileOps.DAMAGED -> toast(context.getString(R.string.fm_extract_bad))
            p.failed > 0 -> dialog = FilesDialog.Failures(p.failures.toList())
            on(Opt.Undo) && !p.cancelled && p.results.isNotEmpty() && r.job.conflict != Conflict.Replace && (r.job.kind == JobKind.Copy || r.job.kind == JobKind.Move) ->
                undo = UndoOffer(r.job.kind == JobKind.Move, p.results.toList())
        }
    }
    LaunchedEffect(undo) { if (undo != null) { delay(10_000); undo = null } }

    fun undoNow(u: UndoOffer) {
        undo = null
        startJob(context.getString(R.string.fmx_undo), u.results.size.toLong(), byCount = true) { progress ->
            u.results.forEach { (source, destination) ->
                if (progress.cancelled) return@forEach
                progress.current = destination.name
                if (u.move) {
                    val back = source.parentFile?.let { FileOps.move(listOf(destination), it, Conflict.KeepBoth, Progress()).firstOrNull() }
                    // Put back under the name it had, when it came out of the move under another.
                    if (back != null && back.name != source.name && !source.exists()) back.renameTo(source)
                    if (back == null) progress.fail(destination.name)
                } else FileOps.delete(destination, progress)
                progress.doneBytes++
            }
        }
    }

    fun transfer(files: List<File>, target: File, move: Boolean, conflict: Conflict) =
        startJob(context.getString(if (move) R.string.fm_cut else R.string.fm_copy), FileOps.measure(files), if (move) JobKind.Move else JobKind.Copy, conflict) { progress ->
            if (move) FileOps.move(files, target, conflict, progress) else FileOps.copy(files, target, conflict, progress)
        }

    fun start(files: List<File>, target: File, move: Boolean) {
        val taken = FileOps.conflicts(files, target).filter { it.parentFile?.canonicalFile != target.canonicalFile }
        if (taken.isEmpty()) transfer(files, target, move, Conflict.KeepBoth)
        else dialog = FilesDialog.Conflicts(taken.size) { policy -> transfer(files, target, move, policy) }
    }

    fun go(p: PaneState, dir: File, index: Int) {
        p.path = dir
        p.selection = emptySet(); p.query = ""; p.searching = false; p.category = null; p.doc = null; p.special = null; p.anchor = null
        prefs.setLastPath(index, dir)
    }

    val leftData = paneData(left, sortFor(left), hidden, version, opened)
    val rightData = paneData(right, sortFor(right), hidden, version, opened)
    fun data(i: Int) = if (i == 0) leftData else rightData
    fun entries(i: Int) = data(i).files

    BackHandler(enabled = dialog != null) { dialog = null }
    BackHandler(enabled = dialog == null && (current.selection.isNotEmpty() || current.category != null || current.special != null || current.searching || relative(places, current.path).size > 1)) {
        when {
            current.selection.isNotEmpty() -> current.selection = emptySet()
            current.category != null -> { current.category = null; current.doc = null }
            current.special != null -> current.special = null
            current.searching -> { current.searching = false; current.query = "" }
            else -> current.path.parentFile?.let { go(current, it, active) }
        }
    }

    val picked = entries(active).filter { it.absolutePath in current.selection }
    val pickedAny = picked.isNotEmpty()

    fun delete(files: List<File>, forever: Boolean) = startJob(context.getString(R.string.fm_delete), files.size.toLong(), JobKind.Delete, byCount = true) { progress ->
        files.forEach { f ->
            if (progress.cancelled) return@forEach
            progress.current = f.name
            // A delete for good counts what stayed itself; a throw into the trash counts here.
            if (forever) FileOps.delete(f, progress) else if (!trash.put(f, Progress())) progress.fail(f.name, FileOps.whyOf(java.io.IOException()))
            progress.doneBytes++
        }
    }

    fun openFile(f: File) {
        opened = prefs.markOpened(f)
        dialog = fileDialog(f, context, on(Opt.ImageViewer))
    }

    fun setTag(files: List<File>, n: Int) {
        val map = LinkedHashMap(tags)
        files.forEach { f -> map.remove(f.absolutePath); if (n > 0) map[f.absolutePath] = n }
        tags = map
        prefs.tags = map
    }

    // The editor and the picture viewer are pages: they come in over the folder, the other dialogs stay dialogs.
    PageSwitch(
        page = dialog.takeIf { it is FilesDialog.Editor || it is FilesDialog.Viewer },
        onBack = { dialog = null },
        list = {
            MiniFrame(panes = isWide()) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Two panels side by side when the screen is as wide as a tablet, not whenever it is turned.
                    val wide = maxWidth.windowClass.isWide
                    val look = RowLook(on(Opt.CategoryColors), on(Opt.ListThumbs), on(Opt.RichThumbs), on(Opt.RecentMark), on(Opt.LargeText))
                    if (wide) {
                        Row(Modifier.fillMaxSize()) {
                            for (i in 0..1) Box(Modifier.weight(1f).fillMaxSize()) {
                                PanePage(pane(i), i, places, data(i), tiles, tileSide, look, on(Opt.ContentSearch), if (on(Opt.Tags)) tags else emptyMap(), onActivate = { active = i }, onGo = { go(pane(i), it, i) }, onOpenFile = ::openFile, lettered = sortFor(pane(i)).key == SortKey.Name, range = on(Opt.Range))
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            PivotHeader(
                                listOf(relative(places, left.path).last().first, relative(places, right.path).last().first),
                                pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                            )
                            LaunchedEffect(pager.currentPage) { active = pager.currentPage }
                            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { i ->
                                PanePage(pane(i), i, places, data(i), tiles, tileSide, look, on(Opt.ContentSearch), if (on(Opt.Tags)) tags else emptyMap(), onActivate = { active = i }, onGo = { go(pane(i), it, i) }, onOpenFile = ::openFile, lettered = sortFor(pane(i)).key == SortKey.Name, range = on(Opt.Range))
                            }
                        }
                    }
                }
                job?.takeIf { it.hidden }?.let { j -> ProgressStrip(j) { j.hidden = false } }
                undo?.let { u ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).defaultMinSize(minHeight = 48.dp).testTag("fm:undo"), verticalAlignment = Alignment.CenterVertically) {
                        MetroText(stringResource(if (u.move) R.string.fmx_moved else R.string.fmx_copied), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(1f))
                        MetroText(
                            stringResource(R.string.fmx_undo), size = 17.sp, color = LocalAccent.current,
                            modifier = Modifier.semantics { role = Role.Button }.metroPress(onClick = { undoNow(u) }).defaultMinSize(minHeight = 48.dp, minWidth = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
                if (pickedAny) {
                    FilesBar(
                        buildList {
                            add(FilesBarItem(FilesIcon.Copy, stringResource(R.string.fm_copy)) { clip = Clip(picked, false); current.selection = emptySet() })
                            add(FilesBarItem(FilesIcon.Cut, stringResource(R.string.fm_cut)) { clip = Clip(picked, true); current.selection = emptySet() })
                            add(FilesBarItem(FilesIcon.Delete, stringResource(R.string.fm_delete)) { dialog = FilesDialog.Delete(picked.size) { forever -> delete(picked, forever) } })
                            add(FilesBarItem(FilesIcon.Share, stringResource(R.string.fm_share)) { FilesSupport.share(context, picked) })
                            add(FilesBarItem(FilesIcon.Compress, stringResource(R.string.fm_compress)) { dialog = FilesDialog.Compress(picked) })
                            if (picked.size == 1) add(FilesBarItem(FilesIcon.Rename, stringResource(R.string.fm_rename_short)) { dialog = FilesDialog.Rename(picked.first()) })
                            add(FilesBarItem(FilesIcon.More, stringResource(R.string.fm_more)) { dialog = FilesDialog.Actions(picked) })
                        },
                    )
                } else {
                    FilesBar(
                        buildList {
                            add(FilesBarItem(FilesIcon.New, stringResource(R.string.fm_new)) { dialog = FilesDialog.New })
                            clip?.let { add(FilesBarItem(FilesIcon.Paste, stringResource(R.string.fm_paste)) { start(it.files, current.path, it.move); if (it.move) clip = null }) }
                            add(FilesBarItem(FilesIcon.Select, stringResource(R.string.fm_select)) { current.selection = entries(active).map { it.absolutePath }.toSet() })
                            add(FilesBarItem(FilesIcon.View, stringResource(R.string.fm_view)) { dialog = FilesDialog.View })
                            add(FilesBarItem(FilesIcon.Categories, stringResource(R.string.fm_categories)) { dialog = FilesDialog.Categories })
                            add(FilesBarItem(FilesIcon.More, stringResource(R.string.fm_more)) { dialog = FilesDialog.Menu })
                        },
                    )
                }
            }
        },
        detail = { page ->
            when (page) {
                is FilesDialog.Editor -> TextEditor(page.file, onClose = { dialog = null; version++ })
                is FilesDialog.Viewer -> ImageViewer(page.file, onClose = { dialog = null })
                else -> Unit
            }
        },
    )

    // --- the dialogs
    job?.takeIf { !it.hidden }?.let { j -> ProgressDialog(j) { j.hidden = true } }
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
            sortFor(current), hidden, tiles, tileSide, on(Opt.SortPerFolder),
            onSort = {
                if (on(Opt.SortPerFolder)) { folderSorts = folderSorts + (current.path.absolutePath to it); prefs.folderSorts = folderSorts }
                else { sort = it; prefs.sort = it }
            },
            onHidden = { hidden = it; prefs.showHidden = it }, onTiles = { tiles = it; prefs.tiles = it }, onTileSide = { tileSide = it; prefs.tileSide = it },
            onClose = { dialog = null },
        )
        FilesDialog.Categories -> CategoriesDialog(
            recent = on(Opt.RecentView), duplicates = on(Opt.Duplicates),
            onPick = { c ->
                current.selection = emptySet(); current.query = ""; current.searching = false
                current.scanRoot = relative(places, current.path).first().second
                current.doc = null; current.special = null; current.category = c
                dialog = null
            },
            onSpecial = { sp ->
                current.selection = emptySet(); current.query = ""; current.searching = false
                current.scanRoot = relative(places, current.path).first().second
                current.doc = null; current.category = null; current.special = sp
                dialog = null
            },
            onCancel = { dialog = null },
        )
        FilesDialog.Menu -> MenuDialog(
            canPaste = clip != null, hidden = hidden, bookmarked = current.path in bookmarks, analyzer = on(Opt.Analyzer),
            onPaste = { clip?.let { start(it.files, current.path, it.move); if (it.move) clip = null }; dialog = null },
            onPlaces = { dialog = FilesDialog.Places },
            onSearch = { current.category = null; current.special = null; current.searching = true; dialog = null },
            onHidden = { hidden = !hidden; prefs.showHidden = hidden; dialog = null },
            onTrash = { dialog = FilesDialog.Trash },
            onBookmark = {
                val here = current.path
                bookmarks = if (here in bookmarks) bookmarks - here else bookmarks + here
                prefs.bookmarks = bookmarks
                dialog = null
            },
            onAnalyzer = { dialog = FilesDialog.Analyzer },
            onOptions = { dialog = FilesDialog.Options },
            onCancel = { dialog = null },
        )
        FilesDialog.Options -> OptionsDialog(
            isOn = ::on,
            onToggle = { opt -> val v = !on(opt); opts[opt] = v; prefs.setFlag(opt, v) },
            onClose = { dialog = null },
        )
        FilesDialog.Analyzer -> AnalyzerDialog(current.path, hidden, onGo = { go(current, it, active); dialog = null }, onClose = { dialog = null })
        FilesDialog.Trash -> TrashDialog(trash, onChanged = { version++ }, fallback = File(storages.first().file, context.getString(R.string.fmx_restored_folder)), onClose = { dialog = null })
        is FilesDialog.Conflicts -> ConflictDialog(d.count, onChoose = { policy -> dialog = null; d.then(policy) }, onCancel = { dialog = null })
        is FilesDialog.Delete -> DeleteDialog(d.count, onChoose = { forever -> dialog = null; d.then(forever) }, onCancel = { dialog = null })
        is FilesDialog.Failures -> FailuresDialog(d.failures, onClose = { dialog = null })
        is FilesDialog.Actions -> ActionsDialog(
            d.files, otherPath = other.path, bookmarks = bookmarks, opts = ::on,
            tag = d.files.firstOrNull()?.let { tags[it.absolutePath] } ?: 0,
            onTag = { n -> setTag(d.files, n); dialog = null },
            onExtract = { toFolder -> extractArchive(d.files.first(), current.path, toFolder, ::startJob, context) { dialog = null } },
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
            onEdit = { dialog = FilesDialog.Editor(d.files.first()) },
            onBulkRename = { dialog = FilesDialog.BulkRename(d.files) },
            onArchiveList = { dialog = FilesDialog.ArchiveList(d.files.first()) },
            onCancel = { dialog = null },
        )
        is FilesDialog.Rename -> NameDialog(stringResource(R.string.fm_rename), d.file.name, onOk = { name ->
            if (FileOps.rename(d.file, name) != null) { current.selection = emptySet(); version++; dialog = null } else toast(context.getString(R.string.fm_name_bad))
        }, onCancel = { dialog = null })
        is FilesDialog.BulkRename -> BulkRenameDialog(d.files, onApply = { plans ->
            dialog = null
            startJob(context.getString(R.string.fmx_bulk_rename), plans.size.toLong(), byCount = true) { progress ->
                val wanted = plans.count { it.ok && it.newName != it.file.name }
                if (FileViews.applyRename(plans) < wanted) progress.fail(plans.first().file.name)
                progress.doneBytes = plans.size.toLong()
            }
        }, onCancel = { dialog = null })
        is FilesDialog.Compress -> CompressDialog(
            (if (d.files.size == 1) d.files.first().nameWithoutExtension else current.path.name.ifEmpty { context.getString(R.string.fmx_archive_default) }),
            onOk = { name, format ->
                val clean = name.trim().removeSuffix("." + format.extension).removeSuffix("." + format.extension.uppercase(Locale.ROOT))
                val folder = d.files.first().parentFile ?: current.path
                val target = FileOps.uniqueFile(folder, "$clean.${format.extension}")
                if (!FileOps.validName(target.name)) toast(context.getString(R.string.fm_name_bad)) else {
                    dialog = null
                    startJob(context.getString(R.string.fm_zip), FileOps.measure(d.files)) { progress ->
                        val ok = if (format == ArchiveFormat.SevenZ) FileOps.sevenZip(d.files, target, progress) else FileOps.zip(d.files, target, progress)
                        if (!ok && !progress.cancelled) progress.fail(target.name)
                    }
                }
            },
            onCancel = { dialog = null },
        )
        is FilesDialog.Properties -> PropertiesDialog(d.file, onClose = { dialog = null })
        is FilesDialog.Editor, is FilesDialog.Viewer -> Unit
        is FilesDialog.ArchiveList -> ArchiveListDialog(d.file, onExtractOne = { name ->
            dialog = null
            val folder = d.file.parentFile ?: current.path
            startJob(context.getString(R.string.fm_extract_here), 0L, byCount = true) { progress -> if (FileOps.extractOne(d.file, name, folder, progress) == null) progress.fail(name.substringAfterLast('/')) }
        }, onClose = { dialog = null })
        is FilesDialog.Zip -> ZipChoice(
            d.file, list = on(Opt.ArchiveList),
            onHere = { extractArchive(d.file, current.path, false, ::startJob, context) { dialog = null } },
            onFolder = { extractArchive(d.file, current.path, true, ::startJob, context) { dialog = null } },
            onList = { dialog = FilesDialog.ArchiveList(d.file) },
            onOpen = { FilesSupport.open(context, d.file); dialog = null },
            onCancel = { dialog = null },
        )
    }
}

private fun fileDialog(file: File, context: Context, viewer: Boolean): FilesDialog? {
    when {
        FileOps.isArchive(file) -> return FilesDialog.Zip(file)
        FileOps.isText(file) -> return FilesDialog.Editor(file)
        viewer && FilesThumbs.isPicture(file) -> return FilesDialog.Viewer(file)
    }
    FilesSupport.open(context, file)
    return null
}

private fun extractArchive(
    archive: File, here: File, toFolder: Boolean,
    start: (String, Long, JobKind, Conflict?, Boolean, (Progress) -> Unit) -> Boolean, context: Context, done: () -> Unit,
) {
    done()
    start(context.getString(R.string.fm_extract_here), archive.length(), JobKind.Other, null, false) { progress ->
        val target = if (toFolder) FileOps.uniqueFile(here, archive.nameWithoutExtension).also { it.mkdirs() } else here
        if (FileOps.extract(archive, target, progress) < 0 && toFolder) target.delete()
    }
}

/** How the rows of the list are drawn: each of these is an option of the file manager. */
private class RowLook(val colors: Boolean, val thumbs: Boolean, val rich: Boolean, val recentMark: Boolean, val largeText: Boolean)

/** The colour of a kind of file; the folders have the accent colour. Each is dark enough for white letters or light enough for black ones, see [onTint]. */
private fun categoryColor(category: Category?, accent: Color): Color = when (category) {
    null -> accent
    Category.Documents -> Color(0xFF0050EF)
    Category.Photos -> Color(0xFF60A917)
    Category.Videos -> Color(0xFFAA00FF)
    Category.Audio -> Color(0xFFFA6800)
    Category.Apk -> Color(0xFF008A00)
    Category.Other -> Color(0xFF647687)
}

private fun onTint(tint: Color): Color = if (tint.luminance() > 0.5f) Color.Black else Color.White

@Composable
private fun CheckRow(text: String, checked: Boolean, tag: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag(tag)
            .semantics(mergeDescendants = true) { role = Role.Checkbox; toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
            .metroPress(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSquare(checked, Modifier.padding(end = 12.dp))
        MetroText(text, size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
    }
}

@Composable
private fun EmptyNote(text: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        GlyphIcon(Glyph.Folder, 56.dp, Modifier.padding(bottom = 8.dp), LocalMetroColors.current.subtle.copy(alpha = 0.6f))
        PageNote(text)
    }
}

@Composable
private fun PanePage(
    pane: PaneState,
    index: Int,
    places: List<Place>,
    data: PaneData,
    tiles: Boolean,
    tileSide: Int,
    look: RowLook,
    contentSearch: Boolean,
    tags: Map<String, Int>,
    onActivate: () -> Unit,
    onGo: (File) -> Unit,
    onOpenFile: (File) -> Unit,
    /** The list is by name: each letter has a heading, and a touch on one opens the grid of letters. */
    lettered: Boolean = false,
    range: Boolean = true,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val context = LocalContext.current
    val category = pane.category
    val special = pane.special
    // The name of where you are is the heading of the list: in Windows Phone a group is named large, in the accent colour.
    val classic = LocalStyle.current.isClassic
    val away = category != null || special != null
    val crumbs = if (!away) relative(places, pane.path).map { it.first to it.second }
    else listOf(relative(places, pane.scanRoot).first().let { it.first to it.second })
    val entries = data.entries
    val files = data.files
    // The items come in one after the other each time the folder, the group or the way of showing them changes.
    val feather = Triple(pane.path, Triple(pane.category, pane.doc, pane.special), tiles)
    // Read once for all the rows, not once for each.
    val dateFormat = remember { DateFormat.getMediumDateFormat(context) }
    val now = remember(entries) { System.currentTimeMillis() }

    fun click(file: File) {
        onActivate()
        val chosen = file.absolutePath in pane.selection
        pane.anchor = file.absolutePath
        when {
            pane.selection.isNotEmpty() -> pane.selection = if (chosen) pane.selection - file.absolutePath else pane.selection + file.absolutePath
            file.isDirectory -> onGo(file)
            else -> onOpenFile(file)
        }
    }
    fun longClick(file: File) {
        onActivate()
        val from = pane.anchor?.let { a -> files.indexOfFirst { it.absolutePath == a } } ?: -1
        val to = files.indexOf(file)
        // A long touch while something is picked picks everything from the last touched file to this one.
        if (range && pane.selection.isNotEmpty() && from >= 0 && to >= 0 && file.absolutePath !in pane.selection) {
            pane.selection = pane.selection + files.subList(minOf(from, to), maxOf(from, to) + 1).map { it.absolutePath }
        } else pane.selection = if (file.absolutePath in pane.selection) pane.selection - file.absolutePath else pane.selection + file.absolutePath
        pane.anchor = file.absolutePath
    }

    Column(Modifier.fillMaxSize().testTag("fm:pane:$index").padding(horizontal = 24.dp)) {
        val crumbState = rememberLazyListState()
        val crumbCount = crumbs.size + if (away) 1 else 0
        // The end of a long path is the part that matters: the row of names is scrolled to it.
        LaunchedEffect(crumbCount, pane.path) { crumbState.scrollToItem((crumbCount - 1).coerceAtLeast(0)) }
        LazyRow(state = crumbState, horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.padding(bottom = 2.dp).testTag("fm:crumbs:$index")) {
            items(crumbCount) { i ->
                val last = i == crumbCount - 1
                val label = if (i < crumbs.size) crumbs[i].first else stringResource(if (special != null) specialName(special) else categoryName(category!!))
                Row(
                    Modifier.defaultMinSize(minHeight = 48.dp).semantics { role = Role.Button }.metroPress(onClick = { onActivate(); if (i < crumbs.size) onGo(crumbs[i].second) }).padding(horizontal = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (i > 0) FilesGlyph(FilesIcon.Chevron, 14.dp, Modifier.padding(end = 4.dp), colors.subtle)
                    MetroText(
                        label, size = if (last && classic) 20.sp else 17.sp, weight = if (last && !classic) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (last) accent else colors.subtle, maxLines = 1, minScale = 1f,
                    )
                }
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
            CheckRow(stringResource(R.string.fm_search_deep), pane.deep, "fm:search:deep:$index") { pane.deep = !pane.deep }
            if (contentSearch) CheckRow(stringResource(R.string.fmx_search_content), pane.content, "fm:search:content:$index") { pane.content = !pane.content }
        }
        when {
            data.loading && away -> {
                MetroProgressBar(Modifier.padding(vertical = 6.dp))
                PageNote(stringResource(R.string.fm_cat_scanning), Modifier.testTag("fm:scanning:$index"))
            }
            data.loading -> MetroProgressBar(Modifier.padding(vertical = 6.dp))
            !data.loading && entries.isEmpty() && !data.scanning -> EmptyNote(
                stringResource(if (away) R.string.fm_cat_none else if (pane.path.canRead()) R.string.fm_folder_empty else R.string.fm_unreadable),
                "fm:empty:$index",
            )
            away && data.scanning -> {
                MetroProgressBar(Modifier.padding(vertical = 4.dp))
                PageNote(stringResource(R.string.fm_cat_scanning), Modifier.testTag("fm:scanning:$index"))
            }
            category != null && entries.size >= SCAN_LIMIT -> PageNote(pluralStringResource(R.plurals.fm_cat_limit, SCAN_LIMIT, SCAN_LIMIT))
        }
        if (tiles) {
            LazyVerticalGrid(
                GridCells.Adaptive(tileSide.dp), Modifier.fillMaxSize().testTag("fm:list:$index"),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                gridItemsIndexed(entries, key = { _, e -> e.file.absolutePath }) { i, entry ->
                    FileTile(entry, entry.file.absolutePath in pane.selection, look.rich, tags[entry.file.absolutePath], if (i < 18) Modifier.featherIn(i, feather, stepMs = 30) else Modifier, onClick = { click(entry.file) }, onLongClick = { longClick(entry.file) })
                }
            }
        } else {
            val runs = if (lettered && !away) letterRuns(entries) { it.name } else listOf("" to entries)
            var jumping by remember { mutableStateOf(false) }
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            // Where each letter first starts in the list, for the grid of letters.
            val startOf = remember(runs) {
                val starts = sectionStarts(runs.map { it.second.size })
                LinkedHashMap<String, Int>().also { m -> runs.forEachIndexed { r, run -> m.putIfAbsent(run.first, starts[r]) } }
            }
            Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().scrollIndicator(listState).testTag("fm:list:$index"), state = listState) {
                var base = 0
                runs.forEachIndexed { r, (letter, group) ->
                    if (lettered && !away) sectionHeader("h$r", onClick = { jumping = true }) {
                        MetroText(letter, size = 24.sp, color = accent, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp))
                    }
                    val first = base
                    itemsIndexed(group, key = { _, e -> e.file.absolutePath }) { n, entry ->
                        val i = first + n
                        FileRow(
                            entry, entry.file.absolutePath in pane.selection, pane.selection.isNotEmpty(), look, tags[entry.file.absolutePath], now, dateFormat,
                            where = (pane.query.isNotBlank() && pane.deep) || away,
                            modifier = Modifier.then(if (i < 14) Modifier.featherIn(i, feather) else Modifier),
                            onClick = { click(entry.file) }, onLongClick = { longClick(entry.file) },
                        )
                    }
                    base += group.size
                }
            }
            if (jumping) {
                JumpList(
                    keys = ('A'..'Z').map { it.toString() } + startOf.keys.filter { it.single() !in 'A'..'Z' },
                    available = startOf.keys,
                    onPick = { key -> jumping = false; startOf[key]?.let { start -> scope.launch { listState.scrollToItem(start) } } },
                    onDismiss = { jumping = false },
                    showRecent = false,
                    modifier = Modifier.testTag("fm:jump:$index"),
                )
            }
            }
        }
    }
}

fun specialName(s: Special): Int = when (s) { Special.Recent -> R.string.fmx_recent; Special.Large -> R.string.fmx_largest; Special.Duplicates -> R.string.fmx_duplicates }

/** A line of the list: a tile with the colour of the kind of file (or its small picture), the name, and the size, the date and the folder. */
@Composable
private fun FileRow(
    entry: Entry, chosen: Boolean, selecting: Boolean, look: RowLook, tag: Int?, now: Long, dateFormat: java.text.DateFormat, where: Boolean,
    modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val classic = LocalStyle.current.isClassic
    val file = entry.file
    val dir = entry.isDir
    val tint = if (dir || !look.colors) accent else categoryColor(remember(entry.name) { FileKinds.byName(entry.name)?.category ?: Category.Other }, accent)
    val ink = if (dir || !look.colors) onAccent() else onTint(tint)
    val thumb = if (look.thumbs && !dir && (FilesThumbs.isPicture(file) || (look.rich && (FilesThumbs.isVideo(file) || FilesThumbs.isApk(file))))) FilesThumbs.load(file, look.rich) else null
    val recent = look.recentMark && !dir && now - entry.modified in 0..86_400_000L
    SelectableRow(
        selecting = selecting, checked = chosen, onClick = onClick, onLongClick = onLongClick,
        modifier = Modifier.testTag("fm:row").then(modifier).padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        // The tiles are in the accent colour, as the icons of the app list of the launcher are; with colours on, in the colour of the kind.
        Box(Modifier.size(44.dp).background(tint), contentAlignment = Alignment.Center) {
            when {
                chosen && !classic -> FilesGlyph(FilesIcon.Check, 24.dp, color = ink)
                thumb != null -> Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                dir -> GlyphIcon(Glyph.Folder, 28.dp, color = ink)
                else -> MetroText(file.extension.take(4).uppercase(Locale.ROOT).ifEmpty { "·" }, size = 12.sp, weight = FontWeight.SemiBold, minScale = 0.5f, maxLines = 1, color = ink)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (recent) Box(Modifier.padding(end = 6.dp).size(8.dp).clip(CircleShape).background(accent))
                // Two lines at most, so a long name does not make the row grow by chance.
                MetroText(entry.name, size = if (look.largeText) 24.sp else 19.sp, maxLines = 2, minScale = 1f)
            }
            MetroText(
                (if (dir) "" else StorageInfo.format(entry.size, Locale.getDefault()) + "  ·  ") + dateFormat.format(Date(entry.modified)) + (if (where) "  ·  " + (file.parentFile?.name ?: "") else ""),
                size = if (look.largeText) 16.sp else 13.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1,
            )
        }
        if (tag != null && tag in 1..TAG_COLORS.size) Box(Modifier.padding(start = 8.dp).size(width = 8.dp, height = 36.dp).background(TAG_COLORS[tag - 1]))
    }
}

/** A tile of the tile view: a folder in the accent colour, a picture as its small copy, any other file as its extension. */
@Composable
private fun FileTile(entry: Entry, chosen: Boolean, rich: Boolean, tag: Int?, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val file = entry.file
    val dir = entry.isDir
    val thumb = if (!dir && (FilesThumbs.isPicture(file) || (rich && (FilesThumbs.isVideo(file) || FilesThumbs.isApk(file))))) FilesThumbs.load(file, rich) else null
    Box(
        modifier.fillMaxWidth().aspectRatio(1f).testTag("fm:row")
            .semantics { contentDescription = file.name; role = Role.Button; selected = chosen }
            .metroPress(onClick = onClick, onLongClick = onLongClick)
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
        if (tag != null && tag in 1..TAG_COLORS.size) Box(Modifier.align(Alignment.TopEnd).size(14.dp).background(TAG_COLORS[tag - 1]))
        if (chosen) Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) { FilesGlyph(FilesIcon.Check, 36.dp, color = onAccent()) }
    }
}
