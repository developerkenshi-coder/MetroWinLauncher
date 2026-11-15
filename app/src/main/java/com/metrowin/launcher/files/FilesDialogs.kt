package com.metrowin.launcher.files

import com.metrowin.launcher.ui.actioncenter.MetroToast
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.metrowin.launcher.ui.components.MetroProgressBar
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.fieldTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Locale

/** What is open over the file manager. */
sealed interface FilesDialog {
    data object New : FilesDialog
    data object Places : FilesDialog
    data object View : FilesDialog
    data object Categories : FilesDialog
    data object Menu : FilesDialog
    data object Trash : FilesDialog
    data class Conflicts(val count: Int, val then: (Conflict) -> Unit) : FilesDialog
    data class Delete(val count: Int, val then: (Boolean) -> Unit) : FilesDialog
    data class Actions(val files: List<File>) : FilesDialog
    data class Rename(val file: File) : FilesDialog
    data class Compress(val files: List<File>) : FilesDialog
    data class Properties(val file: File) : FilesDialog
    data class Editor(val file: File) : FilesDialog
    data class Zip(val file: File) : FilesDialog
    data object Options : FilesDialog
    data object Analyzer : FilesDialog
    data class BulkRename(val files: List<File>) : FilesDialog
    data class ArchiveList(val file: File) : FilesDialog
    data class Failures(val failures: List<Failure>) : FilesDialog
    data class Viewer(val file: File) : FilesDialog
}

private fun size(bytes: Long) = StorageInfo.format(bytes, Locale.getDefault())

@Composable
fun NameDialog(title: String, initial: String, onOk: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    DialogFrame(onCancel = onCancel) {
        MetroText(title, size = 20.sp, weight = FontWeight.Normal)
        MetroTextField(name, { name = it.take(200) }, stringResource(R.string.fm_name), Modifier.testTag("fm:name"), onSearch = { onOk(name) })
        MetroButton(stringResource(R.string.todo_save), Modifier.fillMaxWidth().testTag("fm:name:ok")) { onOk(name) }
    }
}

@Composable
fun NewDialog(onFolder: (String) -> Unit, onFile: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    DialogFrame(onCancel = onCancel) {
        MetroTextField(name, { name = it.take(200) }, stringResource(R.string.fm_name), Modifier.testTag("fm:new:name"))
        MetroButton(stringResource(R.string.fm_new_folder), Modifier.fillMaxWidth().testTag("fm:new:folder")) { onFolder(name) }
        MetroButton(stringResource(R.string.fm_new_file), Modifier.fillMaxWidth().testTag("fm:new:file")) { onFile(name) }
    }
}

@Composable
fun PlacesDialog(storages: List<Place>, folders: List<Place>, bookmarks: List<File>, onGo: (File) -> Unit, onTrash: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    DialogFrame(onCancel = onCancel) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            (storages + folders + bookmarks.map { Place(it.name.ifEmpty { it.path }, it, Place.Kind.Bookmark) }).forEach { place ->
                Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("fm:place").metroPress(onClick = { onGo(place.file) }).padding(vertical = 8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (place.kind == Place.Kind.Bookmark) FilesGlyph(FilesIcon.Star, 18.dp, Modifier.padding(end = 6.dp), accent)
                        MetroText(place.name, size = 21.sp)
                    }
                    if (place.kind == Place.Kind.Storage) {
                        // The room is read away from the screen: a storage can be slow to answer.
                        val space by produceState<Pair<Long, Long>?>(null, place.file) {
                            value = withContext(Dispatchers.IO) { runCatching { place.file.totalSpace to place.file.usableSpace }.getOrDefault(0L to 0L) }
                        }
                        space?.takeIf { it.first > 0 }?.let { (total, free) ->
                            Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).background(colors.surface)) {
                                Box(Modifier.fillMaxWidth(((total - free).toFloat() / total).coerceIn(0f, 1f)).height(6.dp).background(accent))
                            }
                            MetroText(stringResource(R.string.ds_of_total, size(total), size(free)), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("fm:place:trash").metroPress(onClick = onTrash).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilesGlyph(FilesIcon.Trash, 20.dp, Modifier.padding(end = 8.dp))
                MetroText(stringResource(R.string.fm_trash), size = 21.sp)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ViewDialog(
    sort: Sort, hidden: Boolean, tiles: Boolean, tileSide: Int, perFolder: Boolean,
    onSort: (Sort) -> Unit, onHidden: (Boolean) -> Unit, onTiles: (Boolean) -> Unit, onTileSide: (Int) -> Unit, onClose: () -> Unit,
) {
    DialogFrame(onCancel = onClose) {
        MetroText(stringResource(R.string.fm_sort), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        if (perFolder) PageNote(stringResource(R.string.fmx_sort_folder_note))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SortKey.entries.forEach { key ->
                PageChip(
                    stringResource(when (key) { SortKey.Name -> R.string.fm_sort_name; SortKey.Date -> R.string.fm_sort_date; SortKey.Size -> R.string.fm_sort_size; SortKey.Type -> R.string.fm_sort_type; SortKey.Opened -> R.string.fm_sort_opened }),
                    sort.key == key, "fm:sort:${key.name}",
                ) { onSort(sort.copy(key = key)) }
            }
        }
        PageChip(stringResource(R.string.fm_descending), sort.descending, "fm:sort:desc") { onSort(sort.copy(descending = !sort.descending)) }
        MetroText(stringResource(R.string.fm_view), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PageChip(stringResource(R.string.fm_view_list), !tiles, "fm:view:list") { onTiles(false) }
            PageChip(stringResource(R.string.fm_view_tiles), tiles, "fm:view:tiles") { onTiles(true) }
        }
        if (tiles) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PageChip(stringResource(R.string.fmx_tile_small), tileSide == 80, "fm:tile:80") { onTileSide(80) }
            PageChip(stringResource(R.string.fmx_tile_medium), tileSide == 104, "fm:tile:104") { onTileSide(104) }
            PageChip(stringResource(R.string.fmx_tile_large), tileSide == 140, "fm:tile:140") { onTileSide(140) }
        }
        PageChip(stringResource(R.string.fm_hidden), hidden, "fm:hidden") { onHidden(!hidden) }
    }
}

/** A line of a menu: its text (with its drawn [icon] when it has one) on a row at least 52 dp high, with a tick when [checked] is true. */
@Composable
fun MenuRow(text: String, tag: String, checked: Boolean? = null, icon: FilesIcon? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).testTag(tag).metroPress(onClick = onClick)
            .then(if (checked != null) Modifier.semantics { role = Role.Checkbox; toggleableState = if (checked) ToggleableState.On else ToggleableState.Off } else Modifier),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (icon != null) FilesGlyph(icon, 20.dp, Modifier.padding(end = 12.dp))
        MetroText(text, size = 20.sp, weight = FontWeight.Normal, modifier = Modifier.weight(1f))
        if (checked == true) FilesGlyph(FilesIcon.Check, 20.dp, color = LocalAccent.current)
    }
}

/** What is left of the bar when nothing is selected: paste, the places, the search, the hidden files, the trash, the bookmark and the options. */
@Composable
fun MenuDialog(
    canPaste: Boolean, hidden: Boolean, bookmarked: Boolean, analyzer: Boolean, onPaste: () -> Unit, onPlaces: () -> Unit, onSearch: () -> Unit, onHidden: () -> Unit,
    onTrash: () -> Unit, onBookmark: () -> Unit, onAnalyzer: () -> Unit, onOptions: () -> Unit, onCancel: () -> Unit,
) {
    DialogFrame(onCancel = onCancel) {
        if (canPaste) MenuRow(stringResource(R.string.fm_paste), "fm:menu:paste", icon = FilesIcon.Paste, onClick = onPaste)
        MenuRow(stringResource(R.string.fm_places), "fm:menu:places", icon = FilesIcon.Folder, onClick = onPlaces)
        MenuRow(stringResource(R.string.fm_search_hint), "fm:menu:search", icon = FilesIcon.Search, onClick = onSearch)
        MenuRow(stringResource(R.string.fm_hidden), "fm:menu:hidden", checked = hidden, onClick = onHidden)
        MenuRow(stringResource(if (bookmarked) R.string.fm_unbookmark else R.string.fm_bookmark), "fm:menu:bookmark", icon = FilesIcon.Star, onClick = onBookmark)
        if (analyzer) MenuRow(stringResource(R.string.fmx_analyzer), "fm:menu:analyzer", icon = FilesIcon.Info, onClick = onAnalyzer)
        MenuRow(stringResource(R.string.fm_trash), "fm:menu:trash", icon = FilesIcon.Trash, onClick = onTrash)
        MenuRow(stringResource(R.string.fmx_options), "fm:menu:options", onClick = onOptions)
    }
}

/** The groups of files: tapping one lists them under the storage of the panel. */
@Composable
fun CategoriesDialog(recent: Boolean, duplicates: Boolean, onPick: (Category) -> Unit, onSpecial: (Special) -> Unit, onCancel: () -> Unit) {
    DialogFrame(onCancel = onCancel) {
        if (recent) MenuRow(stringResource(R.string.fmx_recent), "fm:cat:recent") { onSpecial(Special.Recent) }
        Category.entries.forEach { c -> MenuRow(stringResource(categoryName(c)), "fm:cat:${c.name}") { onPick(c) } }
        MenuRow(stringResource(R.string.fmx_largest), "fm:cat:large") { onSpecial(Special.Large) }
        if (duplicates) MenuRow(stringResource(R.string.fmx_duplicates), "fm:cat:duplicates") { onSpecial(Special.Duplicates) }
    }
}

fun categoryName(c: Category): Int = when (c) {
    Category.Documents -> R.string.fm_cat_documents; Category.Photos -> R.string.fm_cat_photos; Category.Videos -> R.string.fm_cat_videos
    Category.Audio -> R.string.fm_cat_audio; Category.Apk -> R.string.fm_cat_apk; Category.Other -> R.string.fm_cat_other
}

fun docName(d: DocKind?): Int = when (d) {
    null -> R.string.fm_doc_all; DocKind.Pdf -> R.string.fm_doc_pdf; DocKind.Word -> R.string.fm_doc_word; DocKind.Excel -> R.string.fm_doc_excel
    DocKind.PowerPoint -> R.string.fm_doc_powerpoint; DocKind.Text -> R.string.fm_doc_text; DocKind.Ebook -> R.string.fm_doc_ebook; DocKind.Other -> R.string.fm_doc_other
}

/** The name and the format of a new archive. RAR is not offered: it is a closed format that nobody can make for free. */
@Composable
fun CompressDialog(initial: String, onOk: (String, ArchiveFormat) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var format by remember { mutableStateOf(ArchiveFormat.Zip) }
    DialogFrame(onCancel = onCancel) {
        MetroText(stringResource(R.string.fm_zip_name), size = 20.sp, weight = FontWeight.Normal)
        MetroTextField(name, { name = it.take(200) }, stringResource(R.string.fm_name), Modifier.testTag("fm:name"), onSearch = { onOk(name, format) })
        MetroText(stringResource(R.string.fm_format), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArchiveFormat.entries.forEach { f -> PageChip(".${f.extension}", format == f, "fm:format:${f.extension}") { format = f } }
        }
        PageNote(stringResource(R.string.fm_rar_note))
        MetroButton(stringResource(R.string.fm_compress), Modifier.fillMaxWidth().testTag("fm:name:ok")) { onOk(name, format) }
    }
}

@Composable
fun TrashDialog(trash: FileTrash, onChanged: () -> Unit, fallback: File, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    val items by produceState<List<Trashed>?>(null, version) { value = withContext(Dispatchers.IO) { trash.list() } }
    val colors = LocalMetroColors.current
    val dateFormat = remember { android.text.format.DateFormat.getMediumDateFormat(context) }
    // Deleting for good is asked twice: the first touch marks what would go ([sure] is "" for all of it), the second does it.
    var sure by remember { mutableStateOf<String?>(null) }
    DialogFrame(onCancel = onClose) {
        MetroText(stringResource(R.string.fm_trash), size = 24.sp)
        if (items?.isEmpty() == true) PageNote(stringResource(R.string.fm_trash_empty), Modifier.testTag("fm:trash:empty"))
        Column(Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
            items.orEmpty().forEach { item ->
                val purgeLabel = stringResource(R.string.fm_delete_forever)
                Row(Modifier.fillMaxWidth().testTag("fm:trash:item").padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MetroText(item.name, size = 18.sp, maxLines = 1)
                        MetroText(
                            size(item.size) + "  ·  " + stringResource(R.string.fmx_deleted_on, dateFormat.format(Date(item.deleted))) + "  ·  " + File(item.original).parent,
                            size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2, minScale = 1f,
                        )
                    }
                    Box(Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).testTag("fm:trash:restore").metroPress(onClick = {
                        scope.launch { withContext(Dispatchers.IO) { trash.restore(item, fallback) }; version++; onChanged() }
                    }), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        MetroText(stringResource(R.string.fm_restore), size = 15.sp, color = LocalAccent.current, modifier = Modifier.padding(8.dp))
                    }
                    Box(
                        Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = purgeLabel; role = Role.Button }.metroPress(onClick = {
                            if (sure == item.id) { sure = null; scope.launch { withContext(Dispatchers.IO) { trash.purge(item) }; version++ } } else sure = item.id
                        }),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        if (sure == item.id) MetroText(stringResource(R.string.fmx_sure), size = 15.sp, color = LocalAccent.current, modifier = Modifier.padding(8.dp))
                        else FilesGlyph(FilesIcon.Delete, 20.dp, color = colors.subtle)
                    }
                }
            }
        }
        if (items?.isNotEmpty() == true) MetroButton(stringResource(if (sure == "") R.string.fmx_empty_confirm else R.string.fm_empty_trash), Modifier.fillMaxWidth().testTag("fm:trash:clear")) {
            if (sure == "") { sure = null; scope.launch { withContext(Dispatchers.IO) { trash.empty() }; version++ } } else sure = ""
        }
    }
}

@Composable
fun ConflictDialog(count: Int, onChoose: (Conflict) -> Unit, onCancel: () -> Unit) {
    DialogFrame(onCancel = onCancel) {
        MetroText(pluralStringResource(R.plurals.fm_conflict_title, count, count), size = 20.sp, weight = FontWeight.Normal)
        MetroButton(stringResource(R.string.fm_replace), Modifier.fillMaxWidth().testTag("fm:conflict:replace")) { onChoose(Conflict.Replace) }
        MetroButton(stringResource(R.string.fm_keep_both), Modifier.fillMaxWidth().testTag("fm:conflict:both")) { onChoose(Conflict.KeepBoth) }
        MetroButton(stringResource(R.string.fm_skip), Modifier.fillMaxWidth().testTag("fm:conflict:skip")) { onChoose(Conflict.Skip) }
    }
}

@Composable
fun DeleteDialog(count: Int, onChoose: (Boolean) -> Unit, onCancel: () -> Unit) {
    DialogFrame(onCancel = onCancel) {
        MetroText(pluralStringResource(R.plurals.fm_delete_ask, count, count), size = 20.sp, weight = FontWeight.Normal)
        MetroButton(stringResource(R.string.fm_to_trash), Modifier.fillMaxWidth().testTag("fm:delete:trash")) { onChoose(false) }
        MetroButton(stringResource(R.string.fm_delete_forever), Modifier.fillMaxWidth().testTag("fm:delete:forever")) { onChoose(true) }
    }
}

/** What the bar of a selection has no room for. */
@Composable
fun ActionsDialog(
    files: List<File>,
    otherPath: File,
    bookmarks: List<File>,
    opts: (Opt) -> Boolean,
    tag: Int,
    onTag: (Int) -> Unit,
    onExtract: (Boolean) -> Unit,
    onToOther: (Boolean) -> Unit,
    onProperties: () -> Unit,
    onInvert: () -> Unit,
    onSelectAll: () -> Unit,
    onCopyPath: () -> Unit,
    onBookmark: () -> Unit,
    onEdit: () -> Unit,
    onBulkRename: () -> Unit,
    onArchiveList: () -> Unit,
    onCancel: () -> Unit,
) {
    val single = files.singleOrNull()
    DialogFrame(onCancel = onCancel) {
        MetroText(pluralStringResource(R.plurals.fm_delete_ask, files.size, files.size), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        if (single != null && FileOps.isArchive(single)) {
            MenuRow(stringResource(R.string.fm_extract_here), "fm:act:extract") { onExtract(false) }
            MenuRow(stringResource(R.string.fm_extract_to), "fm:act:extractto") { onExtract(true) }
            if (opts(Opt.ArchiveList)) MenuRow(stringResource(R.string.fmx_archive_content), "fm:act:archive", onClick = onArchiveList)
        }
        MenuRow(stringResource(R.string.fm_copy_to_other) + " (" + (otherPath.name.ifEmpty { "/" }) + ")", "fm:act:copyother", icon = FilesIcon.Copy) { onToOther(false) }
        MenuRow(stringResource(R.string.fm_move_to_other), "fm:act:moveother", icon = FilesIcon.Cut) { onToOther(true) }
        if (single != null && FileOps.isText(single)) MenuRow(stringResource(R.string.fm_edit_text), "fm:act:edit", icon = FilesIcon.Rename, onClick = onEdit)
        if (opts(Opt.BulkRename) && files.size > 1) MenuRow(stringResource(R.string.fmx_bulk_rename), "fm:act:bulk", icon = FilesIcon.Rename, onClick = onBulkRename)
        if (single != null) MenuRow(stringResource(R.string.fm_properties), "fm:act:properties", icon = FilesIcon.Info, onClick = onProperties)
        if (single != null && single.isDirectory) MenuRow(stringResource(if (single in bookmarks) R.string.fm_unbookmark else R.string.fm_bookmark), "fm:act:bookmark", icon = FilesIcon.Star, onClick = onBookmark)
        MenuRow(stringResource(R.string.fm_copy_path), "fm:act:path", onClick = onCopyPath)
        MenuRow(stringResource(R.string.fm_select_all), "fm:act:all", icon = FilesIcon.Select, onClick = onSelectAll)
        MenuRow(stringResource(R.string.fm_invert), "fm:act:invert", onClick = onInvert)
        if (opts(Opt.Tags)) {
            MetroText(stringResource(R.string.fmx_tag), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..TAG_COLORS.size).forEach { n ->
                    val name = stringResource(if (n == 0) R.string.fmx_tag_none else R.string.fmx_tag_n, n)
                    Box(
                        Modifier.size(48.dp).testTag("fm:tag:$n").semantics { contentDescription = name; role = Role.Button; selected = tag == n }
                            .metroPress(onClick = { onTag(n) }).background(if (n == 0) LocalMetroColors.current.surface else TAG_COLORS[n - 1]),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { if (tag == n) FilesGlyph(FilesIcon.Check, 22.dp, color = if (n == 0) LocalMetroColors.current.foreground else Color.White) else if (n == 0) FilesGlyph(FilesIcon.Delete, 18.dp, color = LocalMetroColors.current.subtle) }
                }
            }
        }
    }
}

/** The five colours a file can be tagged with. */
val TAG_COLORS = listOf(Color(0xFFE51400), Color(0xFFFA6800), Color(0xFFF0A30A), Color(0xFF60A917), Color(0xFF0050EF))

@Composable
fun ZipChoice(file: File, list: Boolean, onHere: () -> Unit, onFolder: () -> Unit, onList: () -> Unit, onOpen: () -> Unit, onCancel: () -> Unit) {
    DialogFrame(onCancel = onCancel) {
        MetroText(file.name, size = 20.sp, weight = FontWeight.Normal)
        if (list) MetroButton(stringResource(R.string.fmx_archive_content), Modifier.fillMaxWidth().testTag("fm:zip:list"), onList)
        MetroButton(stringResource(R.string.fm_extract_here), Modifier.fillMaxWidth().testTag("fm:zip:here"), onHere)
        MetroButton(stringResource(R.string.fm_extract_to), Modifier.fillMaxWidth().testTag("fm:zip:folder"), onFolder)
        MetroButton(stringResource(R.string.fm_open_with), Modifier.fillMaxWidth(), onOpen)
    }
}

@Composable
fun PropertiesDialog(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val total by produceState<Pair<Long, Int>?>(null, file) { value = withContext(Dispatchers.IO) { FileOps.size(file) to FileOps.countFiles(file) } }
    val sums = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var working by remember { mutableStateOf(false) }
    // One reading of the file gives the three sums; closing the window stops it.
    val sumProgress = remember { Progress().also { it.totalBytes = file.length() } }
    DisposableEffect(Unit) { onDispose { sumProgress.cancel() } }
    var percent by remember { mutableStateOf(0) }
    LaunchedEffect(working) { while (working) { percent = if (sumProgress.totalBytes > 0) (sumProgress.doneBytes * 100 / sumProgress.totalBytes).toInt().coerceIn(0, 100) else 0; delay(200) } }
    val details by produceState<Triple<String, String, String?>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val rights = (if (file.canRead()) "r" else "-") + (if (file.canWrite()) "w" else "-") + (if (file.canExecute()) "x" else "-")
            val link = runCatching { java.nio.file.Files.readSymbolicLink(file.toPath()).toString() }.getOrNull()
            Triple(rights, FileOps.mime(file), link)
        }
    }
    DialogFrame(onCancel = onClose) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetroText(file.name, size = 22.sp, maxLines = 3)
            FilePreview(file)
            Row2(stringResource(R.string.fm_path), file.absolutePath)
            Row2(stringResource(R.string.fm_type), if (file.isDirectory) stringResource(R.string.stock_files) else details?.second ?: FileOps.mime(file))
            Row2(stringResource(R.string.fm_size), total?.let { size(it.first) } ?: "…")
            if (file.isDirectory) Row2(stringResource(R.string.fm_contains), total?.let { pluralStringResource(R.plurals.fm_items, it.second - 1, it.second - 1) } ?: "…")
            Row2(stringResource(R.string.fm_modified), android.text.format.DateFormat.getMediumDateFormat(context).format(Date(file.lastModified())) + " " + android.text.format.DateFormat.getTimeFormat(context).format(Date(file.lastModified())))
            details?.let { Row2(stringResource(R.string.fmx_permissions), it.first) }
            details?.third?.let { Row2(stringResource(R.string.fmx_link_to), it) }
            if (file.isFile) {
                listOf("MD5", "SHA-1", "SHA-256").forEach { algorithm ->
                    sums[algorithm]?.let { Row2(algorithm, it, small = true) }
                }
                MetroButton(if (working) stringResource(R.string.fm_working) + " $percent%" else stringResource(R.string.fm_calculate), Modifier.fillMaxWidth().testTag("fm:checksums")) {
                    if (working) return@MetroButton
                    working = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { FileOps.checksums(file, progress = sumProgress) }
                        listOf("MD5", "SHA-1", "SHA-256").forEach { a -> sums[a] = result?.get(a) ?: "?" }
                        working = false
                    }
                }
            }
        }
    }
}

@Composable
private fun Row2(key: String, value: String, small: Boolean = false) {
    Column {
        MetroText(key, size = 13.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        MetroText(value, size = if (small) 12.sp else 17.sp, weight = FontWeight.Normal, minScale = 1f, maxLines = Int.MAX_VALUE)
    }
}

/** The time left as the clock shows it: 3:05, or 1:02:09. */
private fun clock(seconds: Long): String = android.text.format.DateUtils.formatElapsedTime(seconds.coerceAtLeast(0L))

@Composable
fun ProgressDialog(job: FileJob, onHide: () -> Unit) {
    val progress = job.progress
    var done by remember { mutableLongStateOf(0L) }
    var name by remember { mutableStateOf("") }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(progress) {
        while (true) { done = progress.doneBytes; name = progress.current; elapsed = System.currentTimeMillis() - job.startedAt; delay(200) }
    }
    val share = if (progress.totalBytes <= 0) 0f else (done.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
    val accent = LocalAccent.current
    val speed = if (elapsed > 1500 && !progress.byCount) done * 1000 / elapsed else 0L
    DialogFrame(onCancel = onHide) {
        MetroText(job.title, size = 22.sp)
        MetroText(name, size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 1)
        if (progress.totalBytes <= 0) MetroProgressBar(Modifier.padding(vertical = 3.dp))
        else Box(Modifier.fillMaxWidth().height(10.dp).background(LocalMetroColors.current.surface).testTag("fm:progress").semantics { progressBarRangeInfo = ProgressBarRangeInfo(share, 0f..1f) }) {
            Box(Modifier.fillMaxWidth(share).height(10.dp).background(accent))
        }
        MetroText(
            if (progress.byCount) stringResource(R.string.fmx_count_of, done, progress.totalBytes)
            else size(done) + " / " + size(progress.totalBytes) + if (speed > 0) "  ·  " + stringResource(R.string.fmx_speed, size(speed), clock((progress.totalBytes - done) / speed)) else "",
            size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 2,
        )
        MetroButton(stringResource(R.string.fmx_background), Modifier.fillMaxWidth().testTag("fm:hide"), onHide)
        MetroButton(stringResource(R.string.fm_cancel), Modifier.fillMaxWidth().testTag("fm:cancel")) { progress.cancel() }
    }
}

/** The line that stays above the bar while a work that was put in the background goes on; a touch brings its window back. */
@Composable
fun ProgressStrip(job: FileJob, onShow: () -> Unit) {
    val progress = job.progress
    var done by remember { mutableLongStateOf(0L) }
    LaunchedEffect(progress) { while (true) { done = progress.doneBytes; delay(300) } }
    val share = if (progress.totalBytes <= 0) 0f else (done.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
    val label = job.title + if (progress.totalBytes > 0) " · " + (share * 100).toInt() + "%" else ""
    Column(Modifier.fillMaxWidth().testTag("fm:strip").semantics { contentDescription = label; role = Role.Button }.metroPress(onClick = onShow).padding(horizontal = 24.dp, vertical = 6.dp)) {
        MetroText(label, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 1)
        Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).background(LocalMetroColors.current.surface)) { Box(Modifier.fillMaxWidth(share).height(4.dp).background(LocalAccent.current)) }
    }
}

/** What did not work, and why, one line each. */
@Composable
fun FailuresDialog(failures: List<Failure>, onClose: () -> Unit) {
    DialogFrame(onCancel = onClose) {
        MetroText(pluralStringResource(R.plurals.fm_failed, failures.size, failures.size), size = 20.sp, weight = FontWeight.Normal)
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).testTag("fm:failures"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            failures.forEach { f ->
                Column {
                    MetroText(f.name, size = 17.sp, maxLines = 2)
                    MetroText(
                        stringResource(when (f.why) { Why.Denied -> R.string.fmx_why_denied; Why.Missing -> R.string.fmx_why_missing; Why.NoSpace -> R.string.fmx_why_space; Why.Outside -> R.string.fmx_why_outside; Why.Other -> R.string.fmx_why_other }),
                        size = 13.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
                    )
                }
            }
        }
        MetroButton(stringResource(R.string.fmx_close), Modifier.fillMaxWidth(), onClose)
    }
}
