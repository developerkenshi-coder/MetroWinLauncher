package com.metrowin.launcher.notes

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.rememberRowSwivel
import com.metrowin.launcher.ui.anim.swivel
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.ChoiceRow
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.start.whenText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.MetroAccents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun noteColor(index: Int) = MetroAccents.getOrNull(index - 1)

/** A colour for [key] that stays the same for it: a note keeps the colour of its tile when the others are moved, a folder has one. */
private fun tint(key: String) = MetroAccents[(key.hashCode() and Int.MAX_VALUE) % MetroAccents.size]

internal fun snippet(note: Note): String =
    if (note.locked) "" else NoteText.parse(note.text).firstOrNull { it.body.isNotBlank() }?.let { it.body.replace("**", "") }.orEmpty()

@Composable
private fun noteDate(millis: Long, relative: Boolean): String = when {
    millis <= 0L -> ""
    relative -> DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    else -> whenText(millis)
}

/** The things the list holds: a note, or the heading of the folder that the notes after it are in. */
private sealed interface Entry {
    data class Head(val name: String) : Entry
    data class Item(val note: Note, val index: Int) : Entry
}

private fun entries(shown: List<Note>, sections: Boolean): List<Entry> {
    if (!sections) return shown.mapIndexed { i, n -> Entry.Item(n, i) }
    val (plain, filed) = shown.partition { it.folder.isEmpty() }
    return buildList {
        plain.forEach { add(Entry.Item(it, size)) }
        filed.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (folder, list) ->
            add(Entry.Head(folder))
            list.forEach { add(Entry.Item(it, size)) }
        }
    }
}

@Composable
internal fun NoteList(
    notes: List<Note>?,
    prefs: NotePrefs,
    noteFiles: NoteFiles,
    saveFailed: Boolean,
    onOpen: (Note) -> Unit,
    onNew: () -> Unit,
    onNewSketch: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    lost: Boolean,
    onDiscard: () -> Unit,
    onBin: (Set<String>, Boolean) -> Unit,
    onForever: (Set<String>) -> Unit,
    onPin: (Set<String>, Boolean) -> Unit,
) {
    val colors = LocalMetroColors.current
    var confirmDiscard by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val sort = prefs[NotePrefs.SORT]
    val ranked = prefs.on(NotePrefs.RANKED)
    val live = remember(notes) { notes.orEmpty().filter { it.deleted == 0L } }
    val binned = remember(notes) { notes.orEmpty().filter { it.deleted > 0L }.sortedByDescending { it.deleted } }
    val all = remember(live, sort) { NoteStore.sorted(live, sort) }
    val folders = remember(live) { live.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted() }
    val shown = remember(all, query, folder, ranked) { NoteStore.search(all.filter { folder.isEmpty() || it.folder == folder }, query, ranked) }
    val hasBin = prefs.on(NotePrefs.BIN) || binned.isNotEmpty()
    val pager = rememberPagerState { if (hasBin) 2 else 1 }
    val titles = listOf(stringResource(R.string.notes_title)) + if (hasBin) listOf(stringResource(R.string.notes_bin_title)) else emptyList()
    BackHandler(enabled = picked.isNotEmpty()) { picked = emptySet() }

    MiniFrame {
        PivotHeader(titles, pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            MetroTextField(query, { query = it.take(80) }, stringResource(R.string.notes_search), Modifier.testTag("notes:search"))
            if (folders.isNotEmpty() && pager.currentPage == 0) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    item { PageChip(stringResource(R.string.notes_all), folder.isEmpty(), "notes:folder:all") { folder = "" } }
                    items(folders) { name -> PageChip(name, folder == name, "notes:folder:$name") { folder = name } }
                }
            }
            if (lost) {
                PageNote(stringResource(R.string.notes_lost), Modifier.padding(top = 14.dp).testTag("notes:lost"))
                MetroButton(stringResource(if (confirmDiscard) R.string.notes_delete_confirm else R.string.notes_lost_discard), Modifier.fillMaxWidth().padding(top = 8.dp).testTag("notes:discard")) {
                    if (confirmDiscard) onDiscard() else confirmDiscard = true
                }
            }
            // The last save did not reach the phone: what is on the screen is not yet kept.
            if (saveFailed) PageNote(stringResource(R.string.notes_unsaved), Modifier.padding(top = 10.dp).testTag("notes:unsaved"))
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = hasBin) { page ->
                if (page == 0) {
                    NotesPage(notes, shown, query, folder, prefs, noteFiles, picked, onPicked = { picked = it }, onOpen = onOpen)
                } else {
                    BinPage(binned, onRestore = { onBin(setOf(it.id), false) }, onForever = { onForever(setOf(it.id)) }, onEmpty = { onForever(binned.map { it.id }.toSet()) })
                }
            }
        }
        if (picked.isNotEmpty()) {
            val anyUnpinned = shown.any { it.id in picked && !it.pinned }
            AppBar(
                listOf(
                    AppBarItem("✕", stringResource(R.string.notes_delete_selected)) {
                        if (prefs.on(NotePrefs.BIN)) onBin(picked, true) else onForever(picked)
                        picked = emptySet()
                    },
                    AppBarItem(if (anyUnpinned) "★" else "☆", stringResource(if (anyUnpinned) R.string.notes_pin else R.string.notes_unpin)) { onPin(picked, anyUnpinned); picked = emptySet() },
                    AppBarItem("↩", stringResource(R.string.notes_select_cancel)) { picked = emptySet() },
                ),
            )
        } else {
            AppBar(
                listOf(
                    AppBarItem("＋", stringResource(R.string.notes_new), onNew),
                    AppBarItem("✎", stringResource(R.string.notes_sketch), onNewSketch),
                ),
                menu = listOf(
                    AppBarItem("⇪", stringResource(R.string.notes_backup), onBackup),
                    AppBarItem("⇩", stringResource(R.string.notes_restore), onRestore),
                    AppBarItem("⋯", stringResource(R.string.notes_options)) { options = true },
                ),
            )
        }
    }
    if (options) Options(prefs) { options = false }
}

/** The switches of the app, and the order of the list. */
@Composable
private fun Options(prefs: NotePrefs, onClose: () -> Unit) {
    val colors = LocalMetroColors.current
    DialogFrame(onCancel = onClose) {
        MetroText(stringResource(R.string.notes_options), size = 22.sp)
        MetroText(stringResource(R.string.notes_sort), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
        ChoiceRow(
            listOf(stringResource(R.string.notes_sort_updated), stringResource(R.string.notes_sort_title), stringResource(R.string.notes_sort_created)),
            prefs[NotePrefs.SORT], { prefs[NotePrefs.SORT] = it }, Modifier.testTag("notes:sort"),
        )
        listOf(
            NotePrefs.RELATIVE to R.string.notes_opt_relative,
            NotePrefs.COMPACT to R.string.notes_opt_compact,
            NotePrefs.SECTIONS to R.string.notes_opt_sections,
            NotePrefs.TILE_PHOTO to R.string.notes_opt_tile_photo,
            NotePrefs.RANKED to R.string.notes_opt_ranked,
            NotePrefs.PICK to R.string.notes_opt_pick,
            NotePrefs.BIN to R.string.notes_opt_bin,
            NotePrefs.LIVE to R.string.notes_opt_live,
            NotePrefs.AUTO_LIST to R.string.notes_opt_auto_list,
            NotePrefs.UNDO to R.string.notes_opt_undo,
            NotePrefs.PDF_PHOTOS to R.string.notes_opt_pdf_photos,
            NotePrefs.PAPER to R.string.notes_opt_paper,
            NotePrefs.MOVE to R.string.notes_opt_move,
            NotePrefs.STYLUS_ONLY to R.string.notes_opt_stylus,
            NotePrefs.LOCK to R.string.notes_opt_lock,
            NotePrefs.SEAL to R.string.notes_opt_seal,
        ).forEach { (key, label) -> MetroToggle(stringResource(label), prefs.on(key), { prefs.toggle(key, it) }, Modifier.testTag("notes:opt:$key")) }
    }
}

/** The notes, as tiles in the classic look, as rows otherwise, or as short rows when that is asked for. */
@Composable
private fun NotesPage(
    notes: List<Note>?,
    shown: List<Note>,
    query: String,
    folder: String,
    prefs: NotePrefs,
    noteFiles: NoteFiles,
    picked: Set<String>,
    onPicked: (Set<String>) -> Unit,
    onOpen: (Note) -> Unit,
) {
    val colors = LocalMetroColors.current
    val relative = prefs.on(NotePrefs.RELATIVE)
    val compact = prefs.on(NotePrefs.COMPACT)
    val canPick = prefs.on(NotePrefs.PICK)
    val rows = remember(shown, query, prefs[NotePrefs.SECTIONS]) { entries(shown, prefs.on(NotePrefs.SECTIONS) && query.isBlank()) }
    val reduced = LocalReduceMotion.current
    val swivel = rememberRowSwivel()
    val listState = rememberLazyListState()
    fun tap(note: Note) {
        if (picked.isNotEmpty()) onPicked(if (note.id in picked) picked - note.id else picked + note.id) else swivel.open(note.id) { onOpen(note) }
    }
    fun hold(note: Note) { if (canPick) onPicked(picked + note.id) }
    Column(Modifier.fillMaxSize()) {
        when {
            notes == null -> PageNote(stringResource(R.string.notes_loading), Modifier.padding(top = 14.dp).testTag("notes:loading"))
            shown.isEmpty() -> Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (query.isBlank() && folder.isEmpty()) EmptyArt()
                PageNote(stringResource(if (query.isBlank() && folder.isEmpty()) R.string.notes_empty else R.string.notes_none_found), Modifier.padding(top = 10.dp).testTag("notes:empty"))
            }
        }
        if (LocalStyle.current.isClassic && !compact) {
            // The notes as tiles, like Start: a colour each (the one picked, or one of its own), the first lines, and the date.
            LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(top = 8.dp).testTag("notes:list"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                gridItems(rows, key = { if (it is Entry.Item) it.note.id else "h:" + (it as Entry.Head).name }, span = { if (it is Entry.Head) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { entry ->
                    val fade = if (reduced) Modifier else Modifier.animateItem()
                    when (entry) {
                        is Entry.Head -> FolderHead(entry.name, fade)
                        is Entry.Item -> NoteTile(
                            entry.note, query, relative, prefs.on(NotePrefs.TILE_PHOTO), noteFiles, entry.note.id in picked,
                            fade.featherRow(entry.index).swivel(swivel, entry.note.id), { tap(entry.note) }, { hold(entry.note) },
                        )
                    }
                }
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp).scrollIndicator(listState).testTag("notes:list"), state = listState) {
            items(rows, key = { if (it is Entry.Item) it.note.id else "h:" + (it as Entry.Head).name }) { entry ->
                val fade = if (reduced) Modifier else Modifier.animateItem()
                when (entry) {
                    is Entry.Head -> FolderHead(entry.name, fade)
                    is Entry.Item -> NoteRow(
                        entry.note, query, relative, compact, entry.note.id in picked,
                        fade.fillMaxWidth().featherRow(entry.index).testTag("notes:item").swivel(swivel, entry.note.id), { tap(entry.note) }, { hold(entry.note) },
                    )
                }
            }
        }
    }
}

/** The heading of a folder, when the list is in sections. */
@Composable
private fun FolderHead(name: String, modifier: Modifier) {
    MetroText(name, size = 16.sp, weight = FontWeight.SemiBold, color = tint(name), modifier = modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp).testTag("notes:section"), maxLines = 1)
}

/** A note of the list: the colour it has, its title with the words that were searched for lit, the beginning of its text, its folder and its date. */
@Composable
private fun NoteRow(note: Note, query: String, relative: Boolean, compact: Boolean, picked: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val back by animateColorAsState(if (picked) accent.copy(alpha = 0.28f) else Color.Transparent, label = "notePicked")
    val title = note.title.ifBlank { snippet(note).ifBlank { stringResource(R.string.notes_untitled) } }
    Row(modifier.background(back).metroPress(onClick = onClick, onLongClick = onLongClick, longClickLabel = stringResource(R.string.notes_select)).padding(vertical = if (compact) 4.dp else 7.dp)) {
        Box(Modifier.width(if (compact) 4.dp else 6.dp).fillMaxHeight().background(noteColor(note.color) ?: colors.subtle.copy(alpha = 0.25f)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (picked) MetroText("✓ ", size = if (compact) 18.sp else 22.sp, color = accent)
                if (note.pinned) MetroText("★ ", size = if (compact) 18.sp else 22.sp, color = accent)
                MetroText(title, size = if (compact) 18.sp else 22.sp, highlight = query, maxLines = if (compact) 1 else 2, modifier = Modifier.weight(1f))
                if (compact) MetroText(noteDate(note.updated, relative), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
            }
            if (!compact) {
                val line = if (note.title.isBlank()) "" else snippet(note)
                if (note.locked) MetroText(stringResource(R.string.notes_locked), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                else if (line.isNotEmpty()) MetroText(line, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, highlight = query, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.folder.isNotEmpty()) MetroText(note.folder, size = 12.sp, weight = FontWeight.Normal, color = tint(note.folder), maxLines = 1)
                    if (note.strokes.isNotEmpty()) MetroText((if (note.folder.isNotEmpty()) "  ·  " else "") + "✎", size = 12.sp, weight = FontWeight.Normal, color = accent)
                    MetroText((if (note.folder.isNotEmpty() || note.strokes.isNotEmpty()) "  ·  " else "") + noteDate(note.updated, relative), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
                }
            }
        }
    }
}

/** A note as a tile: coloured, with its title, the beginning of its text and when it was changed; over its first photo when there is one and it is asked for. */
@Composable
private fun NoteTile(
    note: Note, query: String, relative: Boolean, withPhoto: Boolean, noteFiles: NoteFiles, picked: Boolean,
    modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val fill = noteColor(note.color) ?: tint(note.id)
    val photo by produceState<android.graphics.Bitmap?>(null, note.files, withPhoto, note.locked) {
        val first = note.files.firstOrNull { NoteFiles.isPicture(it) }
        value = if (!withPhoto || note.locked || first == null) null else withContext(Dispatchers.IO) { noteFiles.picture(first, 360) }
    }
    val ink = if (photo != null || fill.luminance() <= 0.5f) Color.White else Color.Black
    val frame = if (picked) Modifier.border(4.dp, LocalMetroColors.current.foreground) else Modifier
    Box(modifier.fillMaxWidth().aspectRatio(1f).testTag("notes:item").metroPress(onClick = onClick, onLongClick = onLongClick, longClickLabel = stringResource(R.string.notes_select)).background(fill).then(frame)) {
        photo?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
        }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            val title = note.title.ifBlank { snippet(note).ifBlank { stringResource(R.string.notes_untitled) } }
            Row {
                if (picked) MetroText("✓ ", size = 19.sp, color = ink)
                if (note.pinned) MetroText("★ ", size = 19.sp, color = ink)
                MetroText(title, size = 19.sp, weight = FontWeight.SemiBold, color = ink, maxLines = 2, modifier = Modifier.weight(1f))
            }
            val line = if (note.title.isBlank()) "" else snippet(note)
            MetroText(if (note.locked) stringResource(R.string.notes_locked) else line, size = 14.sp, weight = FontWeight.Normal, color = ink.copy(alpha = 0.85f), maxLines = 4, modifier = Modifier.weight(1f).padding(top = 4.dp))
            MetroText(noteDate(note.updated, relative), size = 12.sp, weight = FontWeight.Normal, color = ink.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

/** The notes that were deleted: each with the days it has left, and what can be done with it. */
@Composable
private fun BinPage(binned: List<Note>, onRestore: (Note) -> Unit, onForever: (Note) -> Unit, onEmpty: () -> Unit) {
    val colors = LocalMetroColors.current
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (binned.isEmpty()) PageNote(stringResource(R.string.notes_bin_empty), Modifier.padding(top = 14.dp).testTag("notes:bin:empty"))
        else {
            PageNote(stringResource(R.string.notes_bin_hint, NoteStore.BIN_DAYS), Modifier.padding(top = 10.dp))
            LazyColumn(Modifier.weight(1f).padding(top = 6.dp).testTag("notes:bin")) {
                items(binned, key = { it.id }) { note ->
                    val left = (NoteStore.BIN_DAYS - (System.currentTimeMillis() - note.deleted) / 86_400_000L).toInt().coerceIn(0, NoteStore.BIN_DAYS)
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        MetroText(note.title.ifBlank { snippet(note).ifBlank { stringResource(R.string.notes_untitled) } }, size = 20.sp, maxLines = 1)
                        MetroText(pluralStringResource(R.plurals.notes_bin_days, left, left), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle)
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 4.dp)) {
                            MetroText(stringResource(R.string.notes_bin_restore), size = 16.sp, color = LocalAccent.current, modifier = Modifier.testTag("notes:bin:restore").metroPress(onClick = { onRestore(note) }).padding(vertical = 8.dp))
                            MetroText(stringResource(R.string.notes_bin_forever), size = 16.sp, color = colors.subtle, modifier = Modifier.testTag("notes:bin:forever").metroPress(onClick = { onForever(note) }).padding(vertical = 8.dp))
                        }
                    }
                }
            }
            MetroButton(stringResource(if (confirm) R.string.notes_delete_confirm else R.string.notes_bin_clear), Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("notes:bin:clear")) {
                if (confirm) { onEmpty(); confirm = false } else confirm = true
            }
        }
    }
}

/** A sheet of paper with lines and a pencil, for a list with nothing in it. */
@Composable
private fun EmptyArt() {
    val accent = LocalAccent.current
    val line = LocalMetroColors.current.subtle
    Canvas(Modifier.size(width = 120.dp, height = 90.dp)) {
        val edge = 2.dp.toPx()
        drawRect(line, Offset(10.dp.toPx(), 6.dp.toPx()), Size(66.dp.toPx(), 78.dp.toPx()), style = DrawStroke(edge))
        for (i in 0..3) drawLine(line, Offset(20.dp.toPx(), (24 + i * 14).dp.toPx()), Offset(66.dp.toPx(), (24 + i * 14).dp.toPx()), edge)
        drawLine(accent, Offset(70.dp.toPx(), 76.dp.toPx()), Offset(110.dp.toPx(), 22.dp.toPx()), 9.dp.toPx(), StrokeCap.Round)
        drawLine(line, Offset(66.dp.toPx(), 84.dp.toPx()), Offset(72.dp.toPx(), 74.dp.toPx()), edge, StrokeCap.Round)
    }
}
