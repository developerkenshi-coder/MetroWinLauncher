package com.metrowin.launcher.notes

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.fieldTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Which screen of the notes is open: the list, a note, or the sketch of a note. */
private sealed interface Page {
    data object List : Page
    data class Edit(val id: String) : Page
    data class Sketch(val id: String) : Page
}

@Composable
fun NotesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { NoteStore(context) }
    val noteFiles = remember { NoteFiles(context) }
    val lock = remember { Mutex() }
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    // A file of notes that the key of the phone no longer opens is kept aside; the list says so, and says what can be done.
    var lost by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<Page>(Page.List) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { store.load().also { lost = store.hasUnreadable() } }.let { notes = it } }

    fun toast(id: Int, vararg args: Any) = Toast.makeText(context, context.getString(id, *args), Toast.LENGTH_LONG).show()

    fun commit(list: List<Note>) {
        notes = list
        scope.launch(Dispatchers.IO) {
            val saved = lock.withLock { store.save(list) }
            if (!saved) withContext(Dispatchers.Main) { toast(R.string.notes_save_failed) }
        }
    }

    fun replace(note: Note) {
        val list = notes.orEmpty()
        commit(if (list.any { it.id == note.id }) list.map { if (it.id == note.id) note else it } else list + note)
    }

    fun create(): Note = Note(UUID.randomUUID().toString(), created = System.currentTimeMillis(), updated = System.currentTimeMillis())

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val snapshot = notes.orEmpty()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { NoteBackup.write(snapshot, it, noteFiles::file) { note -> sketchPng(note.strokes) } }
                }.isSuccess
            }
            if (ok) toast(R.string.notes_backup_done, snapshot.size) else toast(R.string.notes_backup_failed)
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val incoming = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)!!.use { NoteBackup.read(it) { name, file -> noteFiles.restore(name, file) } } }.getOrNull() }
            if (incoming == null) toast(R.string.notes_restore_failed) else {
                commit(NoteBackup.merge(notes.orEmpty(), incoming))
                toast(R.string.notes_restore_done, incoming.size)
            }
        }
    }

    val current = (page as? Page.Edit)?.id ?: (page as? Page.Sketch)?.id
    val note = notes?.firstOrNull { it.id == current }
    when (val shown = page) {
        Page.List -> NoteList(
            notes = notes,
            onOpen = { page = Page.Edit(it.id) },
            onNew = { val n = create(); replace(n); page = Page.Edit(n.id) },
            onNewSketch = { val n = create(); replace(n); page = Page.Sketch(n.id) },
            onBackup = { backup.launch("metrowin-notes-" + java.time.LocalDate.now() + ".zip") },
            onRestore = { restore.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            lost = lost && notes.orEmpty().isEmpty(),
            onDiscard = { scope.launch(Dispatchers.IO) { store.discardUnreadable(); lost = false } },
        )
        is Page.Edit -> if (note == null) page = Page.List else NoteEditor(
            note = note,
            noteFiles = noteFiles,
            folders = notes.orEmpty().map { it.folder }.filter { it.isNotBlank() }.distinct(),
            onChange = ::replace,
            onSketch = { page = Page.Sketch(note.id) },
            onDelete = { note.files.forEach(noteFiles::delete); commit(notes.orEmpty().filter { it.id != note.id }); page = Page.List },
            onClose = {
                // A note with nothing in it is not kept.
                if (note.isBlank) commit(notes.orEmpty().filter { it.id != note.id })
                page = Page.List
            },
        )
        is Page.Sketch -> if (note == null) page = Page.List else SketchEditor(note.strokes) { strokes ->
            replace(note.copy(strokes = strokes, updated = System.currentTimeMillis()))
            page = Page.Edit(shown.id)
        }
    }
}

private fun noteColor(index: Int) = MetroAccents.getOrNull(index - 1)

private fun snippet(note: Note): String =
    NoteText.parse(note.text).firstOrNull { it.body.isNotBlank() }?.let { it.body.replace("**", "") }.orEmpty()

@Composable
private fun NoteList(
    notes: List<Note>?,
    onOpen: (Note) -> Unit,
    onNew: () -> Unit,
    onNewSketch: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    lost: Boolean,
    onDiscard: () -> Unit,
) {
    val colors = LocalMetroColors.current
    var confirmDiscard by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    val all = NoteStore.sorted(notes.orEmpty())
    val folders = all.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
    val shown = all.filter { n ->
        (folder.isEmpty() || n.folder == folder) &&
            (query.isBlank() || n.title.contains(query, true) || n.text.contains(query, true) || n.folder.contains(query, true))
    }
    MiniFrame {
        MetroText(stringResource(R.string.notes_title), size = 44.sp, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            MetroTextField(query, { query = it.take(80) }, stringResource(R.string.notes_search), Modifier.testTag("notes:search"))
            if (folders.isNotEmpty()) {
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
            if (notes != null && shown.isEmpty()) PageNote(stringResource(R.string.notes_empty), Modifier.padding(top = 14.dp).testTag("notes:empty"))
            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp).testTag("notes:list")) {
                items(shown, key = { it.id }) { note ->
                    Row(Modifier.fillMaxWidth().testTag("notes:item").metroPress(onClick = { onOpen(note) }).padding(vertical = 7.dp)) {
                        Box(Modifier.width(6.dp).fillMaxHeight().background(noteColor(note.color) ?: colors.subtle.copy(alpha = 0.25f)))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            MetroText(
                                (if (note.pinned) "★ " else "") + note.title.ifBlank { snippet(note).ifBlank { stringResource(R.string.notes_untitled) } },
                                size = 22.sp,
                            )
                            val line = if (note.title.isBlank()) "" else snippet(note)
                            if (line.isNotEmpty()) MetroText(line, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                            if (note.folder.isNotEmpty() || note.strokes.isNotEmpty()) {
                                MetroText(
                                    listOfNotNull(note.folder.takeIf { it.isNotEmpty() }, "✎".takeIf { note.strokes.isNotEmpty() }).joinToString("  ·  "),
                                    size = 12.sp, weight = FontWeight.Normal, color = LocalAccent.current, maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("＋", stringResource(R.string.notes_new), onNew),
                AppBarItem("✎", stringResource(R.string.notes_sketch), onNewSketch),
                AppBarItem("⇪", stringResource(R.string.notes_backup), onBackup),
                AppBarItem("⇩", stringResource(R.string.notes_restore), onRestore),
            ),
        )
    }
}

@Composable
private fun NoteEditor(
    note: Note,
    noteFiles: NoteFiles,
    folders: List<String>,
    onChange: (Note) -> Unit,
    onSketch: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalMetroColors.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(TextFieldValue(note.text)) }
    var reading by remember(note.id) { mutableStateOf(note.text.isNotBlank()) }
    var more by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var folderText by remember(note.id) { mutableStateOf(note.folder) }

    val latest by rememberUpdatedState(note)
    val latestTitle by rememberUpdatedState(title)
    val latestBody by rememberUpdatedState(body.text)
    val latestFolder by rememberUpdatedState(folderText.trim())
    fun snapshot() = latest.copy(title = latestTitle, text = latestBody, folder = latestFolder, updated = System.currentTimeMillis())
    fun dirty() = latestTitle != latest.title || latestBody != latest.text || latestFolder != latest.folder

    // What is typed is kept half a second after the last key, and when the note is left.
    LaunchedEffect(title, body.text, folderText) {
        delay(500)
        if (dirty()) onChange(snapshot())
    }
    DisposableEffect(note.id) { onDispose { if (dirty()) onChange(snapshot()) } }
    BackHandler(enabled = more) { more = false }
    BackHandler(enabled = !more) { if (dirty()) onChange(snapshot()); onClose() }

    val md = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri?.let { scope.launch(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(it, "wt")!!.use { out -> out.write(NoteBackup.plain(snapshot()).toByteArray()) } } } }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val shot = snapshot()
        uri?.let { scope.launch(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(it, "wt")!!.use { out -> NotePdf.write(shot, if (shot.strokes.isEmpty()) null else sketchPng(shot.strokes), out) } } } }
    }
    fun attach(uri: Uri?) {
        uri ?: return
        scope.launch {
            val name = if (latest.files.size >= NoteCodec.MAX_FILES) null else withContext(Dispatchers.IO) {
                runCatching {
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri)).orEmpty()
                    context.contentResolver.openInputStream(uri)!!.use { noteFiles.add(it, ext) }
                }.getOrNull()
            }
            if (name == null) Toast.makeText(context, R.string.notes_attach_failed, Toast.LENGTH_LONG).show() else onChange(snapshot().let { it.copy(files = it.files + name) })
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { attach(it) }
    val audio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { attach(it) }
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player.value?.release() } }
    fun play(name: String) {
        player.value?.release()
        player.value = null
        if (playing == name) { playing = null; return }
        playing = name
        player.value = runCatching {
            MediaPlayer().apply { setDataSource(noteFiles.file(name)!!.path); setOnCompletionListener { playing = null }; prepare(); start() }
        }.getOrElse { playing = null; null }
    }
    val png = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val strokes = note.strokes
        uri?.let { scope.launch(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(it, "wt")!!.use { out -> out.write(sketchPng(strokes)) } } } }
    }
    val fileName = note.title.ifBlank { "note" }.map { if (it.isLetterOrDigit() || it == ' ' || it == '-') it else '_' }.joinToString("").trim().take(40).ifEmpty { "note" }

    MiniFrame {
        Column(Modifier.weight(1f).imePadding().padding(horizontal = 24.dp)) {
            BasicTextField(
                value = title, onValueChange = { title = it.take(120) }, singleLine = true,
                textStyle = fieldTextStyle(colors.foreground, 30.sp), cursorBrush = SolidColor(LocalAccent.current),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).testTag("notes:title"),
                decorationBox = { inner -> Box { if (title.isEmpty()) MetroText(stringResource(R.string.notes_title_hint), size = 30.sp, color = colors.subtle); inner() } },
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (reading) {
                    NoteBody(body.text, onToggle = { index -> body = TextFieldValue(NoteText.toggle(body.text, index)) })
                } else {
                    BasicTextField(
                        value = body, onValueChange = { if (it.text.length <= NoteCodec.MAX_TEXT) body = it },
                        textStyle = fieldTextStyle(colors.foreground, 19.sp), cursorBrush = SolidColor(LocalAccent.current),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("notes:body"),
                        decorationBox = { inner -> Box { if (body.text.isEmpty()) MetroText(stringResource(R.string.notes_body_hint), size = 19.sp, color = colors.subtle); inner() } },
                    )
                }
                if (note.strokes.isNotEmpty()) {
                    SketchPreview(note.strokes, onSketch)
                }
                note.files.forEach { name ->
                    Attachment(name, noteFiles, playing == name, onPlay = { play(name) }) {
                        if (playing == name) play(name)
                        noteFiles.delete(name)
                        onChange(snapshot().let { it.copy(files = it.files - name) })
                    }
                }
            }
            if (!reading) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fun mark(marker: String) {
                        val (text, cursor) = NoteText.mark(body.text, body.selection.start, marker)
                        body = TextFieldValue(text, TextRange(cursor))
                    }
                    PageChip("H1", false, "notes:fmt:h1") { mark("# ") }
                    PageChip("H2", false, "notes:fmt:h2") { mark("## ") }
                    PageChip("B", false, "notes:fmt:bold") {
                        val (text, range) = NoteText.bold(body.text, body.selection.start, body.selection.end)
                        body = TextFieldValue(text, TextRange(range.first, range.last + 1))
                    }
                    PageChip("•", false, "notes:fmt:bullet") { mark("- ") }
                    PageChip("☐", false, "notes:fmt:box") { mark("☐ ") }
                    PageChip("✎", false, "notes:fmt:sketch", onClick = onSketch)
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem(if (reading) "✎" else "◉", stringResource(if (reading) R.string.notes_edit else R.string.notes_read)) { reading = !reading },
                AppBarItem(if (note.pinned) "★" else "☆", stringResource(if (note.pinned) R.string.notes_unpin else R.string.notes_pin)) { onChange(snapshot().copy(pinned = !latest.pinned)) },
                AppBarItem("⋯", stringResource(R.string.notes_more)) { more = true },
            ),
        )
    }
    if (more) {
        DialogFrame(onCancel = { more = false; confirmDelete = false }) {
            MetroText(stringResource(R.string.notes_color), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..7).forEach { index ->
                    Box(
                        Modifier.size(32.dp).testTag("notes:color:$index").metroPress(onClick = { onChange(snapshot().copy(color = index)) })
                            .background(noteColor(index) ?: colors.surface).border(if (note.color == index) 3.dp else 1.dp, if (note.color == index) colors.foreground else colors.subtle),
                    )
                }
            }
            MetroText(stringResource(R.string.notes_folder), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
            MetroTextField(folderText, { folderText = it.take(60) }, stringResource(R.string.notes_folder_hint), Modifier.testTag("notes:folder"))
            if (folders.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(folders) { name -> PageChip(name, folderText == name, "notes:pick:$name") { folderText = name } } }
            }
            MetroButton(stringResource(R.string.notes_share), Modifier.fillMaxWidth()) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, NoteBackup.plain(snapshot()))
                context.startActivity(Intent.createChooser(send, null))
            }
            MetroButton(stringResource(R.string.notes_attach_photo), Modifier.fillMaxWidth().testTag("notes:photo")) { more = false; photo.launch("image/*") }
            MetroButton(stringResource(R.string.notes_attach_audio), Modifier.fillMaxWidth().testTag("notes:audio")) { more = false; audio.launch("audio/*") }
            MetroButton(stringResource(R.string.notes_export_text), Modifier.fillMaxWidth()) { md.launch("$fileName.md") }
            MetroButton(stringResource(R.string.notes_export_pdf), Modifier.fillMaxWidth().testTag("notes:pdf")) { pdf.launch("$fileName.pdf") }
            if (note.strokes.isNotEmpty()) MetroButton(stringResource(R.string.notes_export_sketch), Modifier.fillMaxWidth()) { png.launch("$fileName.png") }
            if (note.strokes.isNotEmpty()) MetroButton(stringResource(R.string.notes_sketch_remove), Modifier.fillMaxWidth()) { onChange(snapshot().copy(strokes = emptyList())); more = false }
            MetroButton(stringResource(if (confirmDelete) R.string.notes_delete_confirm else R.string.notes_delete), Modifier.fillMaxWidth().testTag("notes:delete")) {
                if (confirmDelete) onDelete() else confirmDelete = true
            }
        }
    }
}

/** A photo of a note, shown in full width, or a sound with a button to hear it; a cross takes it off the note. */
@Composable
private fun Attachment(name: String, noteFiles: NoteFiles, playing: Boolean, onPlay: () -> Unit, onRemove: () -> Unit) {
    val colors = LocalMetroColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("notes:file"), verticalAlignment = Alignment.CenterVertically) {
        if (NoteFiles.isPicture(name)) {
            val bitmap by produceState<android.graphics.Bitmap?>(null, name) { value = withContext(Dispatchers.IO) { noteFiles.picture(name, 800) } }
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.weight(1f)) } ?: Box(Modifier.weight(1f))
        } else {
            MetroText((if (playing) "■  " else "▶  ") + name.substringAfterLast('.').uppercase(), size = 19.sp, modifier = Modifier.weight(1f).metroPress(onClick = onPlay).padding(vertical = 8.dp))
        }
        MetroText("✕", size = 22.sp, color = colors.subtle, modifier = Modifier.metroPress(onClick = onRemove).padding(start = 12.dp, top = 8.dp, bottom = 8.dp))
    }
}

@Composable
private fun SketchPreview(strokes: List<Stroke>, onClick: () -> Unit) {
    Canvas(
        Modifier.fillMaxWidth(0.7f).aspectRatio(SketchPage.WIDTH / SketchPage.HEIGHT).padding(vertical = 8.dp)
            .testTag("notes:sketch").background(SketchPaper).metroPress(onClick = onClick),
    ) { drawStrokes(strokes) }
}

/** The text of a note as it is read: titles, lists, bold, and boxes that can be ticked. */
@Composable
private fun NoteBody(text: String, onToggle: (Int) -> Unit) {
    val colors = LocalMetroColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("notes:read")) {
        NoteText.parse(text).forEach { line ->
            val size = when (line.kind) {
                NoteText.Kind.Title -> 28.sp
                NoteText.Kind.Subtitle -> 23.sp
                else -> 19.sp
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                when (line.kind) {
                    NoteText.Kind.Bullet -> MetroText("•", size = size, modifier = Modifier.width(24.dp))
                    NoteText.Kind.Unchecked, NoteText.Kind.Checked -> MetroText(
                        if (line.kind == NoteText.Kind.Checked) "☑" else "☐", size = size,
                        color = LocalAccent.current, modifier = Modifier.width(34.dp).testTag("notes:box:${line.index}").metroPress(onClick = { onToggle(line.index) }),
                    )
                    else -> {}
                }
                val done = line.kind == NoteText.Kind.Checked
                androidx.compose.foundation.text.BasicText(
                    text = androidx.compose.ui.text.buildAnnotatedString {
                        NoteText.spans(line.body).forEach { (piece, bold) ->
                            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = if (bold || line.kind == NoteText.Kind.Title || line.kind == NoteText.Kind.Subtitle) FontWeight.SemiBold else FontWeight.Light))
                            append(piece)
                            pop()
                        }
                    },
                    style = androidx.compose.ui.text.TextStyle(
                        color = if (done) colors.subtle else colors.foreground, fontSize = size,
                        textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                        fontFamily = com.metrowin.launcher.ui.theme.LocalStyle.current.font,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
