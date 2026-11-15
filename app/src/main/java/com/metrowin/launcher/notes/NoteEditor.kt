package com.metrowin.launcher.notes

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.ButtonRole
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.fieldTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** The title, the bold and the boxes already ticked, shown as such while the text is typed. The characters stay as they are, so the cursor and the selection need no mapping. */
private class LiveStyle(private val subtle: Color, private val bold: FontWeight) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = buildAnnotatedString {
            append(raw)
            var start = 0
            for (line in raw.split('\n')) {
                val end = start + line.length
                when {
                    line.startsWith("## ") -> addStyle(SpanStyle(fontSize = 23.sp, fontWeight = bold), start, end)
                    line.startsWith("# ") -> addStyle(SpanStyle(fontSize = 28.sp, fontWeight = bold), start, end)
                    line.startsWith("☑ ") -> addStyle(SpanStyle(color = subtle, textDecoration = TextDecoration.LineThrough), start, end)
                }
                start = end + 1
            }
            NoteText.boldRanges(raw).forEach { addStyle(SpanStyle(fontWeight = bold), it.first, it.last + 1) }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
internal fun NoteEditor(
    note: Note,
    noteFiles: NoteFiles,
    prefs: NotePrefs,
    folders: List<String>,
    onChange: (Note) -> Unit,
    onSketch: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalMetroColors.current
    val style = LocalStyle.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(TextFieldValue(note.text)) }
    var reading by remember(note.id) { mutableStateOf(note.text.isNotBlank()) }
    var more by remember { mutableStateOf(false) }
    var present by remember { mutableStateOf(false) }
    var viewing by remember { mutableIntStateOf(-1) }
    var confirmDelete by remember { mutableStateOf(false) }
    var folderText by remember(note.id) { mutableStateOf(note.folder) }
    val history = remember(note.id) { NoteHistory(note.text) }
    // The steps of undo and redo are not state: this changes when they do, and the two buttons are drawn again.
    var steps by remember(note.id) { mutableIntStateOf(0) }

    fun toast(id: Int) = MetroToast.show(context, context.getString(id))

    val latest by rememberUpdatedState(note)
    val latestTitle by rememberUpdatedState(title)
    val latestBody by rememberUpdatedState(body.text)
    val latestFolder by rememberUpdatedState(folderText.trim())
    fun snapshot() = latest.copy(title = latestTitle, text = latestBody, folder = latestFolder, updated = System.currentTimeMillis())
    fun dirty() = latestTitle != latest.title || latestBody != latest.text || latestFolder != latest.folder

    // What is typed is kept half a second after the last key, when the note is left, and when the app goes to the background (it may not come back).
    LaunchedEffect(title, body.text, folderText) {
        delay(500)
        if (dirty()) onChange(snapshot())
    }
    LaunchedEffect(body.text) {
        delay(600)
        history.push(NoteHistory.State(body.text, body.selection.start))
        steps++
    }
    DisposableEffect(note.id) { onDispose { if (dirty()) onChange(snapshot()) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, note.id) {
        val watcher = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && dirty()) onChange(snapshot()) }
        lifecycle.addObserver(watcher)
        onDispose { lifecycle.removeObserver(watcher) }
    }
    BackHandler(enabled = viewing >= 0) { viewing = -1 }
    BackHandler(enabled = more) { more = false }
    BackHandler(enabled = present) { present = false }
    BackHandler(enabled = !more && !present && viewing < 0) { if (dirty()) onChange(snapshot()); onClose() }

    val fileName = NoteShare.safeName(note.title.ifBlank { stringResource(R.string.notes_file_default) }).take(40)
    val withPhotos = prefs.on(NotePrefs.PDF_PHOTOS)

    fun pdfTo(out: java.io.OutputStream, shot: Note) {
        val photos = if (withPhotos) shot.files.filter(NoteFiles::isPicture).mapNotNull { noteFiles.picture(it, 1400) } else emptyList()
        try {
            NotePdf.write(shot, if (shot.strokes.isEmpty()) null else sketchPng(shot.strokes, paper = shot.paper), out, photos)
        } finally {
            photos.forEach { it.recycle() }
        }
    }
    /** Writes what [write] makes to the file the user picked, and says whether it went. */
    fun export(uri: Uri?, write: (java.io.OutputStream) -> Unit) {
        uri ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use(write) }.isSuccess }
            toast(if (ok) R.string.notes_export_done else R.string.notes_export_failed)
        }
    }
    val md = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val text = NoteBackup.plain(snapshot())
        export(uri) { it.write(text.toByteArray()) }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val shot = snapshot()
        export(uri) { pdfTo(it, shot) }
    }
    val png = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val shot = snapshot()
        export(uri) { it.write(sketchPng(shot.strokes, paper = shot.paper)) }
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
            if (name == null) toast(R.string.notes_attach_failed) else onChange(snapshot().let { it.copy(files = it.files + name) })
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { attach(it) }
    val audio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { attach(it) }

    var playing by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableIntStateOf(0) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player.value?.release() } }
    LaunchedEffect(playing) {
        while (playing != null) {
            position = runCatching { player.value?.currentPosition }.getOrNull() ?: 0
            delay(250)
        }
    }
    fun play(name: String) {
        player.value?.release()
        player.value = null
        if (playing == name) { playing = null; return }
        playing = name
        position = 0
        // Prepared away from the screen: a long file did not hold the page still while it was read.
        player.value = runCatching {
            MediaPlayer().apply {
                setDataSource(noteFiles.file(name)!!.path)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { playing = null }
                setOnErrorListener { _, _, _ -> playing = null; true }
                prepareAsync()
            }
        }.getOrElse { playing = null; null }
    }

    fun mark(marker: String) {
        history.push(NoteHistory.State(body.text, body.selection.start))
        val (text, cursor) = NoteText.mark(body.text, body.selection.start, marker)
        body = TextFieldValue(text, TextRange(cursor))
    }
    fun typed(value: TextFieldValue) {
        if (value.text.length > NoteCodec.MAX_TEXT) return
        val next = if (prefs.on(NotePrefs.AUTO_LIST) && value.selection.collapsed) NoteText.continueList(body.text, value.text, value.selection.start) else null
        body = if (next != null) TextFieldValue(next.first, TextRange(next.second)) else value
    }
    fun go(state: NoteHistory.State?) {
        state ?: return
        body = TextFieldValue(state.text, TextRange(state.cursor.coerceIn(0, state.text.length)))
        steps++
    }

    if (present) {
        val view = LocalView.current
        DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
        MiniFrame {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).pointerInput(Unit) { detectTapGestures { present = false } }.testTag("notes:present")) {
                if (title.isNotBlank()) MetroText(title, size = 40.sp, modifier = Modifier.padding(bottom = 16.dp))
                NoteBody(body.text, onToggle = {}, scale = 1.5f)
            }
        }
        return
    }

    val subtle = colors.subtle
    val bold = style.weight(FontWeight.SemiBold)
    val live = remember(subtle, bold) { LiveStyle(subtle, bold) }
    val pictures = note.files.filter(NoteFiles::isPicture)
    val sketchCd = stringResource(R.string.notes_sketch_cd)

    MiniFrame {
        Column(Modifier.weight(1f).imePadding().padding(horizontal = 24.dp)) {
            BasicTextField(
                value = title, onValueChange = { if (it.length > NoteCodec.MAX_TITLE) toast(R.string.notes_limit_title); title = it.take(NoteCodec.MAX_TITLE) }, singleLine = true,
                textStyle = fieldTextStyle(colors.foreground, 30.sp), cursorBrush = SolidColor(LocalAccent.current),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).testTag("notes:title"),
                decorationBox = { inner -> Box { if (title.isEmpty()) MetroText(stringResource(R.string.notes_title_hint), size = 30.sp, color = colors.subtle); inner() } },
            )
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // On a wide screen turned sideways the text is on one side and the sketch and the files on the other.
                val sideBySide = maxWidth >= 600.dp && maxWidth > maxHeight
                val text: @Composable () -> Unit = {
                    if (reading) {
                        NoteBody(body.text, onToggle = { index -> history.push(NoteHistory.State(body.text, body.selection.start)); body = TextFieldValue(NoteText.toggle(body.text, index)) })
                    } else {
                        BasicTextField(
                            value = body, onValueChange = ::typed,
                            textStyle = fieldTextStyle(colors.foreground, 19.sp).copy(lineHeight = 27.sp), cursorBrush = SolidColor(LocalAccent.current),
                            visualTransformation = if (prefs.on(NotePrefs.LIVE)) live else VisualTransformation.None,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("notes:body"),
                            decorationBox = { inner -> Box { if (body.text.isEmpty()) MetroText(stringResource(R.string.notes_body_hint), size = 19.sp, color = colors.subtle); inner() } },
                        )
                    }
                }
                val extras: @Composable () -> Unit = {
                    if (note.strokes.isNotEmpty()) SketchPreview(note.strokes, note.paper, sketchCd, onSketch)
                    note.files.forEach { name ->
                        Attachment(
                            name, noteFiles, playing == name, position,
                            onPlay = { play(name) }, onSeek = { fraction, total -> runCatching { player.value?.seekTo((fraction * total).toInt()) } },
                            onOpen = { viewing = pictures.indexOf(name) },
                        ) {
                            if (playing == name) play(name)
                            noteFiles.delete(name)
                            onChange(snapshot().let { it.copy(files = it.files - name) })
                        }
                    }
                }
                if (sideBySide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) { Column(Modifier.widthIn(max = TEXT_WIDTH), content = { text() }) }
                        Column(Modifier.weight(0.8f).fillMaxSize().verticalScroll(rememberScrollState())) { extras() }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Column(Modifier.widthIn(max = TEXT_WIDTH), content = { text() })
                        extras()
                    }
                }
            }
            if (!reading) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (prefs.on(NotePrefs.UNDO)) {
                        // Reading `steps` here is what draws them again when a step is taken or undone.
                        val back = steps >= 0 && (history.canUndo || body.text != history.text)
                        PageChip("↶", false, "notes:fmt:undo", Modifier.alpha(if (back) 1f else 0.4f).semantics { contentDescription = context.getString(R.string.sketch_undo) }) { if (back) go(history.undo(NoteHistory.State(body.text, body.selection.start))) }
                        PageChip("↷", false, "notes:fmt:redo", Modifier.alpha(if (history.canRedo) 1f else 0.4f).semantics { contentDescription = context.getString(R.string.sketch_redo) }) { go(history.redo()) }
                    }
                    PageChip(stringResource(R.string.notes_fmt_h1), false, "notes:fmt:h1", Modifier.semantics { contentDescription = context.getString(R.string.notes_fmt_h1_cd) }) { mark("# ") }
                    PageChip(stringResource(R.string.notes_fmt_h2), false, "notes:fmt:h2", Modifier.semantics { contentDescription = context.getString(R.string.notes_fmt_h2_cd) }) { mark("## ") }
                    PageChip(stringResource(R.string.notes_fmt_bold), false, "notes:fmt:bold", Modifier.semantics { contentDescription = context.getString(R.string.notes_fmt_bold_cd) }) {
                        history.push(NoteHistory.State(body.text, body.selection.start))
                        val (text, range) = NoteText.bold(body.text, body.selection.start, body.selection.end)
                        body = TextFieldValue(text, TextRange(range.first, range.last + 1))
                    }
                    PageChip("•", false, "notes:fmt:bullet", Modifier.semantics { contentDescription = context.getString(R.string.notes_fmt_bullet_cd) }) { mark("- ") }
                    PageChip("☐", false, "notes:fmt:box", Modifier.semantics { contentDescription = context.getString(R.string.notes_fmt_box_cd) }) { mark("☐ ") }
                    PageChip("✎", false, "notes:fmt:sketch", Modifier.semantics { contentDescription = context.getString(R.string.notes_sketch) }, onClick = onSketch)
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
    if (viewing in pictures.indices) PhotoViewer(pictures, viewing, noteFiles) { viewing = -1 }
    if (more) {
        DialogFrame(onCancel = { more = false; confirmDelete = false }) {
            MetroText(stringResource(R.string.notes_color), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..NoteCodec.MAX_COLOR).forEach { index ->
                    val name = if (index == 0) stringResource(R.string.notes_color_none) else "${stringResource(R.string.notes_color)} $index"
                    Box(
                        Modifier.size(32.dp).testTag("notes:color:$index").semantics { contentDescription = name; role = Role.RadioButton }.metroPress(onClick = { onChange(snapshot().copy(color = index)) })
                            .background(noteColor(index) ?: colors.surface).border(if (note.color == index) 3.dp else 1.dp, if (note.color == index) colors.foreground else colors.subtle),
                    )
                }
            }
            MetroText(stringResource(R.string.notes_folder), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
            MetroTextField(folderText, { if (it.length > NoteCodec.MAX_FOLDER) toast(R.string.notes_limit_folder); folderText = it.take(NoteCodec.MAX_FOLDER) }, stringResource(R.string.notes_folder_hint), Modifier.testTag("notes:folder"))
            if (folders.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(folders) { name -> PageChip(name, folderText == name, "notes:pick:$name") { folderText = name } } }
            }
            if (prefs.on(NotePrefs.LOCK) || note.locked) {
                MetroToggle(stringResource(R.string.notes_lock), note.locked, { on -> onChange(snapshot().copy(locked = on)) }, Modifier.testTag("notes:lock"))
            }
            MetroButton(stringResource(R.string.notes_share), Modifier.fillMaxWidth()) {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, NoteBackup.plain(snapshot()))
                context.startActivity(android.content.Intent.createChooser(send, null))
            }
            if (note.strokes.isNotEmpty()) MetroButton(stringResource(R.string.notes_share_sketch), Modifier.fillMaxWidth()) {
                val shot = snapshot()
                scope.launch { NoteShare.sendBytes(context, "$fileName.png", "image/png", withContext(Dispatchers.IO) { sketchPng(shot.strokes, paper = shot.paper) }) }
            }
            if (pictures.isNotEmpty()) MetroButton(stringResource(R.string.notes_share_photos), Modifier.fillMaxWidth()) {
                NoteShare.sendFiles(context, pictures.mapNotNull { n -> noteFiles.file(n)?.takeIf { it.isFile }?.let { n to it } }, "image/*")
            }
            MetroButton(stringResource(R.string.notes_share_pdf), Modifier.fillMaxWidth()) {
                val shot = snapshot()
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) { runCatching { ByteArrayOutputStream().also { pdfTo(it, shot) }.toByteArray() }.getOrNull() }
                    if (bytes == null) toast(R.string.notes_export_failed) else NoteShare.sendBytes(context, "$fileName.pdf", "application/pdf", bytes)
                }
            }
            MetroButton(stringResource(R.string.notes_attach_photo), Modifier.fillMaxWidth().testTag("notes:photo")) { more = false; photo.launch("image/*") }
            MetroButton(stringResource(R.string.notes_attach_audio), Modifier.fillMaxWidth().testTag("notes:audio")) { more = false; audio.launch("audio/*") }
            MetroButton(stringResource(R.string.notes_export_text), Modifier.fillMaxWidth()) { md.launch("$fileName.md") }
            MetroButton(stringResource(R.string.notes_export_pdf), Modifier.fillMaxWidth().testTag("notes:pdf")) { pdf.launch("$fileName.pdf") }
            if (note.strokes.isNotEmpty()) MetroButton(stringResource(R.string.notes_export_sketch), Modifier.fillMaxWidth()) { png.launch("$fileName.png") }
            if (note.strokes.isNotEmpty()) MetroButton(stringResource(R.string.notes_sketch_remove), Modifier.fillMaxWidth()) { onChange(snapshot().copy(strokes = emptyList())); more = false }
            if (body.text.isNotBlank()) MetroButton(stringResource(R.string.notes_present), Modifier.fillMaxWidth().testTag("notes:presentation")) { more = false; present = true }
            MetroButton(stringResource(if (confirmDelete) R.string.notes_delete_confirm else if (prefs.on(NotePrefs.BIN)) R.string.notes_to_bin else R.string.notes_delete), Modifier.fillMaxWidth().testTag("notes:delete"), role = if (confirmDelete) ButtonRole.Danger else ButtonRole.Normal) {
                if (confirmDelete) onDelete() else confirmDelete = true
            }
        }
    }
}

private val TEXT_WIDTH: Dp = 640.dp

/** A photo of a note (a touch opens it big), or a sound with a button to hear it, its length and, while it plays, where it is; a cross takes it off the note. */
@Composable
private fun Attachment(
    name: String, noteFiles: NoteFiles, playing: Boolean, position: Int,
    onPlay: () -> Unit, onSeek: (Float, Int) -> Unit, onOpen: () -> Unit, onRemove: () -> Unit,
) {
    val colors = LocalMetroColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("notes:file"), verticalAlignment = Alignment.CenterVertically) {
        if (NoteFiles.isPicture(name)) {
            val bitmap by produceState<Bitmap?>(null, name) { value = withContext(Dispatchers.IO) { noteFiles.picture(name, 800) } }
            val description = stringResource(R.string.notes_photo_cd)
            bitmap?.let { Image(it.asImageBitmap(), description, Modifier.weight(1f).testTag("notes:photo:open").metroPress(onClick = onOpen)) } ?: Box(Modifier.weight(1f))
        } else {
            val length by produceState(0, name) {
                value = withContext(Dispatchers.IO) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(noteFiles.file(name)!!.path)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
                    } catch (e: Exception) { 0 } finally { runCatching { retriever.release() } }
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().metroPress(onClick = onPlay).padding(vertical = 8.dp)) {
                    MetroText(if (playing) "■" else "▶", size = 22.sp, color = LocalAccent.current, modifier = Modifier.width(34.dp))
                    MetroText(name.substringAfterLast('.').uppercase(), size = 19.sp, modifier = Modifier.weight(1f))
                    if (length > 0) MetroText((if (playing) clock(position) + " / " else "") + clock(length), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
                if (playing && length > 0) {
                    // A bar of the file: where it is, and a touch on it goes there.
                    val accent = LocalAccent.current
                    Canvas(Modifier.fillMaxWidth().height(24.dp).pointerInput(length) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f), length) } }.testTag("notes:seek")) {
                        drawRect(colors.subtle.copy(alpha = 0.35f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height / 2 - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx()))
                        drawRect(accent, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height / 2 - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width * (position.toFloat() / length).coerceIn(0f, 1f), 4.dp.toPx()))
                    }
                }
            }
        }
        val remove = stringResource(R.string.notes_remove_file)
        Box(Modifier.size(48.dp).semantics { contentDescription = remove }.metroPress(onClick = onRemove), contentAlignment = Alignment.Center) {
            MetroText("✕", size = 22.sp, color = colors.subtle)
        }
    }
}

private fun clock(ms: Int): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

/** The photos of a note one at a time, the whole screen: swipe for the next, touch to close. */
@Composable
private fun PhotoViewer(names: List<String>, start: Int, noteFiles: NoteFiles, onClose: () -> Unit) {
    val pager = rememberPagerState(start) { names.size }
    val description = stringResource(R.string.notes_photo_cd)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).pointerInput(Unit) { detectTapGestures { onClose() } }.testTag("notes:viewer")) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val bitmap by produceState<Bitmap?>(null, names[page]) { value = withContext(Dispatchers.IO) { noteFiles.picture(names[page], 1600) } }
            bitmap?.let { Image(it.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        if (names.size > 1) MetroText("${pager.currentPage + 1} / ${names.size}", size = 16.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
    }
}

@Composable
private fun SketchPreview(strokes: List<Stroke>, paper: Int, description: String, onClick: () -> Unit) {
    Canvas(
        Modifier.fillMaxWidth(0.7f).aspectRatio(SketchPage.WIDTH / SketchPage.HEIGHT).padding(vertical = 8.dp)
            .testTag("notes:sketch").semantics { contentDescription = description }.background(SketchPaper).metroPress(onClick = onClick),
    ) { drawPaper(paper); drawStrokes(strokes) }
}

/** The text of a note as it is read: titles, lists, bold, and boxes that can be ticked. [scale] makes it all bigger (the presentation). */
@Composable
private fun NoteBody(text: String, onToggle: (Int) -> Unit, scale: Float = 1f) {
    val colors = LocalMetroColors.current
    val style = LocalStyle.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("notes:read")) {
        NoteText.parse(text).forEach { line ->
            val size = when (line.kind) {
                NoteText.Kind.Title -> 28.sp
                NoteText.Kind.Subtitle -> 23.sp
                else -> 19.sp
            } * scale
            val done = line.kind == NoteText.Kind.Checked
            // A box that is ticked fades to the quiet colour instead of changing at once.
            val ink by animateColorAsState(if (done) colors.subtle else colors.foreground, label = "noteDone")
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                when (line.kind) {
                    NoteText.Kind.Bullet -> MetroText("•", size = size, modifier = Modifier.width(24.dp * scale))
                    NoteText.Kind.Numbered -> MetroText("${line.number}.", size = size, modifier = Modifier.width(34.dp * scale))
                    NoteText.Kind.Unchecked, NoteText.Kind.Checked -> MetroText(
                        if (done) "☑" else "☐", size = size, color = LocalAccent.current,
                        modifier = Modifier.width(34.dp * scale).heightIn(min = 36.dp).testTag("notes:box:${line.index}")
                            .semantics { role = Role.Checkbox; toggleableState = if (done) ToggleableState.On else ToggleableState.Off; contentDescription = line.body }
                            .metroPress(onClick = { onToggle(line.index) }),
                    )
                    else -> {}
                }
                BasicText(
                    text = buildAnnotatedString {
                        NoteText.spans(line.body).forEach { (piece, bold) ->
                            // One place decides the weight of each piece, the same as everywhere in the launcher (MetroText does it too).
                            pushStyle(SpanStyle(fontWeight = style.weight(if (bold || line.kind == NoteText.Kind.Title || line.kind == NoteText.Kind.Subtitle) FontWeight.SemiBold else FontWeight.Light)))
                            append(piece)
                            pop()
                        }
                    },
                    style = TextStyle(color = ink, fontSize = size, textDecoration = if (done) TextDecoration.LineThrough else null, fontFamily = style.font),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
