package com.metrowin.launcher.files

import android.widget.Toast
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
import com.metrowin.launcher.ui.start.DialogFrame
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
    DialogFrame(onCancel = onCancel) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            (storages + folders + bookmarks.map { Place(it.name.ifEmpty { it.path }, it, Place.Kind.Bookmark) }).forEach { place ->
                Column(Modifier.fillMaxWidth().testTag("fm:place").metroPress(onClick = { onGo(place.file) }).padding(vertical = 8.dp)) {
                    MetroText((if (place.kind == Place.Kind.Bookmark) "★ " else "") + place.name, size = 21.sp)
                    if (place.kind == Place.Kind.Storage) {
                        val total = runCatching { place.file.totalSpace }.getOrDefault(0L)
                        val free = runCatching { place.file.usableSpace }.getOrDefault(0L)
                        if (total > 0) MetroText(stringResource(R.string.ds_of_total, size(total), size(free)), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                    }
                }
            }
            MetroText("🗑 " + stringResource(R.string.fm_trash), size = 21.sp, modifier = Modifier.fillMaxWidth().testTag("fm:place:trash").metroPress(onClick = onTrash).padding(vertical = 8.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ViewDialog(sort: Sort, hidden: Boolean, tiles: Boolean, onSort: (Sort) -> Unit, onHidden: (Boolean) -> Unit, onTiles: (Boolean) -> Unit, onClose: () -> Unit) {
    DialogFrame(onCancel = onClose) {
        MetroText(stringResource(R.string.fm_sort), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
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
        PageChip(stringResource(R.string.fm_hidden), hidden, "fm:hidden") { onHidden(!hidden) }
    }
}

/** A line of a menu: its text on a row at least 52 dp high, with a tick when [checked] is true. */
@Composable
fun MenuRow(text: String, tag: String, checked: Boolean? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).testTag(tag).metroPress(onClick = onClick),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        MetroText(text, size = 20.sp, weight = FontWeight.Normal, modifier = Modifier.weight(1f))
        if (checked == true) MetroText("✓", size = 20.sp, color = LocalAccent.current)
    }
}

/** What is left of the bar when nothing is selected: paste, the places, the search, the hidden files, the trash and the bookmark. */
@Composable
fun MenuDialog(
    canPaste: Boolean, hidden: Boolean, bookmarked: Boolean, onPaste: () -> Unit, onPlaces: () -> Unit, onSearch: () -> Unit, onHidden: () -> Unit,
    onTrash: () -> Unit, onBookmark: () -> Unit, onCancel: () -> Unit,
) {
    DialogFrame(onCancel = onCancel) {
        if (canPaste) MenuRow(stringResource(R.string.fm_paste), "fm:menu:paste", onClick = onPaste)
        MenuRow(stringResource(R.string.fm_places), "fm:menu:places", onClick = onPlaces)
        MenuRow(stringResource(R.string.fm_search_hint), "fm:menu:search", onClick = onSearch)
        MenuRow(stringResource(R.string.fm_hidden), "fm:menu:hidden", checked = hidden, onClick = onHidden)
        MenuRow(stringResource(if (bookmarked) R.string.fm_unbookmark else R.string.fm_bookmark), "fm:menu:bookmark", onClick = onBookmark)
        MenuRow(stringResource(R.string.fm_trash), "fm:menu:trash", onClick = onTrash)
    }
}

/** The groups of files: tapping one lists them under the storage of the panel. */
@Composable
fun CategoriesDialog(onPick: (Category) -> Unit, onCancel: () -> Unit) {
    DialogFrame(onCancel = onCancel) {
        Category.entries.forEach { c -> MenuRow(stringResource(categoryName(c)), "fm:cat:${c.name}") { onPick(c) } }
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
    var version by remember { mutableStateOf(0) }
    val items by produceState<List<Trashed>?>(null, version) { value = withContext(Dispatchers.IO) { trash.list() } }
    val colors = LocalMetroColors.current
    DialogFrame(onCancel = onClose) {
        MetroText(stringResource(R.string.fm_trash), size = 24.sp)
        if (items?.isEmpty() == true) PageNote(stringResource(R.string.fm_trash_empty), Modifier.testTag("fm:trash:empty"))
        Column(Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
            items.orEmpty().forEach { item ->
                Row(Modifier.fillMaxWidth().testTag("fm:trash:item").padding(vertical = 5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MetroText(item.name, size = 18.sp)
                        MetroText(size(item.size) + "  ·  " + File(item.original).parent, size = 12.sp, weight = FontWeight.Normal, color = colors.subtle)
                    }
                    MetroText(stringResource(R.string.fm_restore), size = 15.sp, color = LocalAccent.current, modifier = Modifier.testTag("fm:trash:restore").metroPress(onClick = {
                        scope.launch { withContext(Dispatchers.IO) { trash.restore(item, fallback) }; version++; onChanged() }
                    }).padding(8.dp))
                    MetroText("✕", size = 18.sp, color = colors.subtle, modifier = Modifier.metroPress(onClick = { scope.launch { withContext(Dispatchers.IO) { trash.purge(item) }; version++ } }).padding(8.dp))
                }
            }
        }
        if (items?.isNotEmpty() == true) MetroButton(stringResource(R.string.fm_empty_trash), Modifier.fillMaxWidth().testTag("fm:trash:clear")) {
            scope.launch { withContext(Dispatchers.IO) { trash.empty() }; version++ }
        }
    }
}

@Composable
fun ConflictDialog(count: Int, onChoose: (Conflict) -> Unit, onCancel: () -> Unit) {
    DialogFrame(onCancel = onCancel) {
        MetroText(stringResource(R.string.fm_conflict_title, count), size = 20.sp, weight = FontWeight.Normal)
        MetroButton(stringResource(R.string.fm_replace), Modifier.fillMaxWidth().testTag("fm:conflict:replace")) { onChoose(Conflict.Replace) }
        MetroButton(stringResource(R.string.fm_keep_both), Modifier.fillMaxWidth().testTag("fm:conflict:both")) { onChoose(Conflict.KeepBoth) }
        MetroButton(stringResource(R.string.fm_skip), Modifier.fillMaxWidth().testTag("fm:conflict:skip")) { onChoose(Conflict.Skip) }
    }
}

@Composable
fun DeleteDialog(count: Int, onChoose: (Boolean) -> Unit, onCancel: () -> Unit) {
    DialogFrame(onCancel = onCancel) {
        MetroText(stringResource(R.string.fm_delete_ask, count), size = 20.sp, weight = FontWeight.Normal)
        MetroButton(stringResource(R.string.fm_to_trash), Modifier.fillMaxWidth().testTag("fm:delete:trash")) { onChoose(false) }
        MetroButton(stringResource(R.string.fm_delete_forever), Modifier.fillMaxWidth().testTag("fm:delete:forever")) { onChoose(true) }
    }
}

/** What the bar of a selection has no room for. */
@Composable
fun ActionsDialog(
    files: List<File>,
    otherPath: File,
    onExtract: (Boolean) -> Unit,
    onToOther: (Boolean) -> Unit,
    onProperties: () -> Unit,
    onInvert: () -> Unit,
    onSelectAll: () -> Unit,
    onCopyPath: () -> Unit,
    onBookmark: () -> Unit,
    bookmarks: List<File>,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    val single = files.singleOrNull()
    DialogFrame(onCancel = onCancel) {
        MetroText(stringResource(R.string.fm_delete_ask, files.size), size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        if (single != null && FileOps.isArchive(single)) {
            MenuRow(stringResource(R.string.fm_extract_here), "fm:act:extract") { onExtract(false) }
            MenuRow(stringResource(R.string.fm_extract_to), "fm:act:extractto") { onExtract(true) }
        }
        MenuRow(stringResource(R.string.fm_copy_to_other) + " (" + (otherPath.name.ifEmpty { "/" }) + ")", "fm:act:copyother") { onToOther(false) }
        MenuRow(stringResource(R.string.fm_move_to_other), "fm:act:moveother") { onToOther(true) }
        if (single != null && FileOps.isText(single)) MenuRow(stringResource(R.string.fm_edit_text), "fm:act:edit", onClick = onEdit)
        if (single != null) MenuRow(stringResource(R.string.fm_properties), "fm:act:properties", onClick = onProperties)
        if (single != null && single.isDirectory) MenuRow(stringResource(if (single in bookmarks) R.string.fm_unbookmark else R.string.fm_bookmark), "fm:act:bookmark", onClick = onBookmark)
        MenuRow(stringResource(R.string.fm_copy_path), "fm:act:path", onClick = onCopyPath)
        MenuRow(stringResource(R.string.fm_select_all), "fm:act:all", onClick = onSelectAll)
        MenuRow(stringResource(R.string.fm_invert), "fm:act:invert", onClick = onInvert)
    }
}

@Composable
fun ZipChoice(file: File, onHere: () -> Unit, onFolder: () -> Unit, onOpen: () -> Unit, onCancel: () -> Unit) {
    DialogFrame(onCancel = onCancel) {
        MetroText(file.name, size = 20.sp, weight = FontWeight.Normal)
        MetroButton(stringResource(R.string.fm_extract_here), Modifier.fillMaxWidth().testTag("fm:zip:here"), onHere)
        MetroButton(stringResource(R.string.fm_extract_to), Modifier.fillMaxWidth().testTag("fm:zip:folder"), onFolder)
        MetroButton(stringResource(R.string.fm_open_with), Modifier.fillMaxWidth(), onOpen)
    }
}

@Composable
fun PropertiesDialog(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalMetroColors.current
    val total by produceState<Pair<Long, Int>?>(null, file) { value = withContext(Dispatchers.IO) { FileOps.size(file) to FileOps.countFiles(file) } }
    val sums = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var working by remember { mutableStateOf(false) }
    DialogFrame(onCancel = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetroText(file.name, size = 22.sp)
            Row2(stringResource(R.string.fm_path), file.absolutePath)
            Row2(stringResource(R.string.fm_type), if (file.isDirectory) stringResource(R.string.stock_files) else FileOps.mime(file))
            Row2(stringResource(R.string.fm_size), total?.let { size(it.first) } ?: "…")
            if (file.isDirectory) Row2(stringResource(R.string.fm_contains), total?.let { stringResource(R.string.fm_items, it.second - 1) } ?: "…")
            Row2(stringResource(R.string.fm_modified), android.text.format.DateFormat.getMediumDateFormat(context).format(Date(file.lastModified())) + " " + android.text.format.DateFormat.getTimeFormat(context).format(Date(file.lastModified())))
            if (file.isFile) {
                listOf("MD5", "SHA-1", "SHA-256").forEach { algorithm ->
                    sums[algorithm]?.let { Row2(algorithm, it, small = true) }
                }
                MetroButton(stringResource(if (working) R.string.fm_working else R.string.fm_calculate), Modifier.fillMaxWidth().testTag("fm:checksums")) {
                    if (working) return@MetroButton
                    working = true
                    scope.launch {
                        listOf("MD5", "SHA-1", "SHA-256").forEach { a -> sums[a] = withContext(Dispatchers.IO) { FileOps.checksum(file, a) } ?: "?" }
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
        MetroText(value, size = if (small) 12.sp else 17.sp, weight = FontWeight.Normal, minScale = 1f)
    }
}

@Composable
fun ProgressDialog(title: String, progress: Progress) {
    var done by remember { mutableLongStateOf(0L) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(progress) {
        while (true) { done = progress.doneBytes; name = progress.current; delay(100) }
    }
    val share = if (progress.totalBytes <= 0) 0f else (done.toFloat() / progress.totalBytes).coerceIn(0f, 1f)
    val accent = LocalAccent.current
    DialogFrame(onCancel = {}) {
        MetroText(title, size = 22.sp)
        MetroText(name, size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        Box(Modifier.fillMaxWidth().height(10.dp).background(LocalMetroColors.current.surface).testTag("fm:progress")) {
            Box(Modifier.fillMaxWidth(share).height(10.dp).background(accent))
        }
        MetroText(size(done) + " / " + size(progress.totalBytes), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        MetroButton(stringResource(R.string.fm_cancel), Modifier.fillMaxWidth().testTag("fm:cancel")) { progress.cancel() }
    }
}

/** A text file opened to be read and changed. Over a megabyte it is not opened (a text field would not hold it). */
@Composable
fun TextEditor(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val tooBig = file.length() > 1_000_000L
    var text by remember(file) { mutableStateOf(if (tooBig) "" else runCatching { file.readText() }.getOrDefault("")) }
    var original by remember(file) { mutableStateOf(text) }
    androidx.activity.compose.BackHandler { onClose() }
    MiniFrame {
        MetroText(file.name, size = 24.sp, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp))
        if (tooBig) PageNote(stringResource(R.string.fm_too_big), Modifier.padding(horizontal = 24.dp))
        else Box(Modifier.weight(1f).fillMaxWidth().imePadding().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = fieldTextStyle(colors.foreground, 16.sp).copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                cursorBrush = SolidColor(LocalAccent.current), modifier = Modifier.fillMaxWidth().testTag("fm:editor"),
            )
        }
        AppBar(
            listOf(
                AppBarItem("←", stringResource(R.string.fm_back)) { onClose() },
                AppBarItem("✓", stringResource(R.string.fm_save)) {
                    if (!tooBig) {
                        val ok = runCatching { file.writeText(text) }.isSuccess
                        if (ok) original = text
                        Toast.makeText(context, if (ok) R.string.fm_saved else R.string.fm_save_failed, Toast.LENGTH_SHORT).show()
                    }
                },
            ),
        )
    }
}
