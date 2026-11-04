package com.metrowin.launcher.recorder

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.data.TimerFormat
import com.metrowin.launcher.data.VoiceRecorder
import com.metrowin.launcher.notes.Note
import com.metrowin.launcher.notes.NoteStore
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun RecorderScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { Recordings(context) }
    val recorder = remember { VoiceRecorder(context, store) }
    DisposableEffect(recorder) { onDispose { recorder.release() } }
    var allowed by remember { mutableStateOf(LivePermissions.isGranted(context, Manifest.permission.RECORD_AUDIO)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var version by remember { mutableIntStateOf(0) }
    val files = remember(version) { store.list() }
    var selected by remember { mutableStateOf<String?>(null) }
    val pager = rememberPagerState { 2 }
    var recording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = SystemClock.elapsedRealtime() - startedAt
            delay(250L)
        }
    }
    val open = files.firstOrNull { it.name == selected }
    BackHandler(enabled = open != null) { selected = null }
    if (open != null) {
        RecordingDetail(open, store, onChanged = { version++ }, onClose = { selected = null }, onSelect = { selected = it.name; version++ })
        return
    }
    MiniFrame {
        PivotHeader(listOf(stringResource(R.string.rec_pivot_record), stringResource(R.string.rec_pivot_list)), pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            if (page == 0) {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!allowed) {
                        PageNote(stringResource(R.string.recorder_need_permission))
                        MetroButton(stringResource(R.string.recorder_allow), Modifier.padding(top = 14.dp).testTag("rec:allow")) { ask.launch(Manifest.permission.RECORD_AUDIO) }
                    } else {
                        MetroText(TimerFormat.stopwatch(if (recording) elapsed else 0L, tenths = false), size = 80.sp, maxLines = 1, modifier = Modifier.testTag("rec:time"))
                        MetroButton(stringResource(if (recording) R.string.recorder_stop else R.string.recorder_start), Modifier.fillMaxWidth().padding(top = 24.dp).testTag("rec:toggle")) {
                            if (recording) {
                                recorder.stop()
                                recording = false
                                version++
                            } else if (recorder.start()) {
                                failed = false
                                startedAt = SystemClock.elapsedRealtime()
                                recording = true
                            } else {
                                failed = true
                            }
                        }
                        if (failed) PageNote(stringResource(R.string.recorder_failed), Modifier.padding(top = 8.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    if (files.isEmpty()) PageNote(stringResource(R.string.rec_empty), Modifier.testTag("rec:empty"))
                    LazyColumn(Modifier.fillMaxSize().testTag("rec:list")) {
                        items(files, key = { it.name }) { file ->
                            MetroText(
                                Recordings.title(file.name), size = 21.sp, weight = FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth().testTag("rec:row").metroPress(onClick = { if (!recording) selected = file.name }).padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun seconds(ms: Long) = TimerFormat.stopwatch(ms, tenths = false)

/** One recording: its sound as a picture with two handles to choose a piece, and what can be done with the piece and with the whole. */
@Composable
private fun RecordingDetail(file: File, store: Recordings, onChanged: () -> Unit, onClose: () -> Unit, onSelect: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val duration by produceState(0L, file) { value = withContext(Dispatchers.IO) { AudioTools.durationMs(file) } }
    val peaks by produceState(FloatArray(0), file) { value = withContext(Dispatchers.IO) { AudioTools.peaks(file) } }
    var from by remember(file.name) { mutableFloatStateOf(0f) }
    var to by remember(file.name) { mutableFloatStateOf(1f) }
    var position by remember(file.name) { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    val player = remember(file.name) { runCatching { MediaPlayer().apply { setDataSource(file.path); prepare() } }.getOrNull() }
    DisposableEffect(player) { onDispose { runCatching { player?.release() } } }
    LaunchedEffect(playing, player) {
        while (playing && player != null) {
            position = player.currentPosition.toLong()
            // Playing a piece stops at the end of it.
            if (duration > 0 && position >= (to * duration).toLong()) { runCatching { player.pause() }; playing = false }
            delay(80)
        }
    }
    var naming by remember { mutableStateOf<String?>(null) }   // "cut" or "rename"
    var name by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var transcript by remember(file.name) { mutableStateOf("") }
    var transcribing by remember { mutableStateOf(false) }
    var transcriptFailed by remember { mutableStateOf(false) }
    val transcriber = remember { Transcriber(context) }
    DisposableEffect(Unit) { onDispose { transcriber.release() } }

    fun toast(id: Int) = Toast.makeText(context, id, Toast.LENGTH_LONG).show()

    MiniFrame {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            MetroText(Recordings.title(file.name), size = 26.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            MetroText(seconds(position) + " / " + seconds(duration), size = 20.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.testTag("rec:position"))
            Canvas(
                Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp).background(colors.surface).testTag("rec:wave")
                    .pointerInput(duration) {
                        detectTapGestures { tap ->
                            // A touch on the picture moves the playing there.
                            val ms = (tap.x / size.width * duration).toLong().coerceIn(0, duration)
                            runCatching { player?.seekTo(ms.toInt()) }
                            position = ms
                        }
                    }
                    .pointerInput(duration) {
                        var handle = 0
                        detectDragGestures(
                            onDragStart = { start -> handle = if (kotlin.math.abs(start.x / size.width - from) <= kotlin.math.abs(start.x / size.width - to)) 1 else 2 },
                            onDrag = { change, _ ->
                                change.consume()
                                val f = (change.position.x / size.width).coerceIn(0f, 1f)
                                val gap = if (duration > 0) (300f / duration).coerceAtMost(0.5f) else 0.02f
                                if (handle == 1) from = f.coerceAtMost(to - gap).coerceAtLeast(0f) else to = f.coerceAtLeast(from + gap).coerceAtMost(1f)
                            },
                        )
                    },
            ) {
                val bar = size.width / maxOf(peaks.size, 1)
                peaks.forEachIndexed { i, p ->
                    val x = i * bar
                    val inside = x / size.width in from..to
                    val h = (p * size.height).coerceAtLeast(2f)
                    drawRect(if (inside) accent else colors.subtle, Offset(x, (size.height - h) / 2f), Size(maxOf(bar - 1f, 1f), h))
                }
                listOf(from, to).forEach { f -> drawRect(colors.foreground, Offset(f * size.width - 2f, 0f), Size(4f, size.height)) }
                if (duration > 0) drawRect(colors.foreground.copy(alpha = 0.6f), Offset(position.toFloat() / duration * size.width, 0f), Size(2f, size.height))
            }
            MetroText(
                stringResource(R.string.rec_selection, seconds((from * duration).toLong()), seconds((to * duration).toLong())),
                size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.testTag("rec:selection"),
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetroButton(stringResource(if (playing) R.string.rec_pause else R.string.rec_play), Modifier.weight(1f).testTag("rec:play")) {
                    val p = player ?: return@MetroButton
                    if (playing) { runCatching { p.pause() }; playing = false } else {
                        val startMs = (from * duration).toLong()
                        if (position < startMs || position >= (to * duration).toLong()) { p.seekTo(startMs.toInt()); position = startMs }
                        runCatching { p.start() }
                        playing = true
                    }
                }
                MetroButton(stringResource(R.string.rec_cut), Modifier.weight(1f).testTag("rec:cut")) { name = ""; naming = "cut" }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetroButton(stringResource(R.string.recorder_rename), Modifier.weight(1f).testTag("rec:rename")) { name = Recordings.nameOf(file.name); naming = "rename" }
                MetroButton(stringResource(R.string.recorder_share), Modifier.weight(1f).testTag("rec:share")) {
                    runCatching {
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
                        val send = Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, null))
                    }
                }
            }
            MetroButton(
                stringResource(if (confirm) R.string.notes_delete_confirm else R.string.recorder_delete), Modifier.fillMaxWidth().padding(top = 10.dp).testTag("rec:delete"),
            ) {
                if (confirm) { runCatching { player?.release() }; store.delete(file); onChanged(); onClose() } else confirm = true
            }
            MetroText(stringResource(R.string.rec_transcript), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
            if (!transcriber.isSupported) PageNote(stringResource(R.string.rec_transcribe_unsupported), Modifier.padding(bottom = 8.dp).testTag("rec:unsupported"))
            else MetroButton(stringResource(if (transcribing) R.string.rec_transcribing else R.string.rec_transcribe), Modifier.fillMaxWidth().testTag("rec:transcribe")) {
                if (transcribing) return@MetroButton
                transcribing = true
                transcriptFailed = false
                transcript = ""
                transcriber.start(file, onText = { transcript = it }, onDone = { transcribing = false }, onFailed = { transcribing = false; transcriptFailed = true })
            }
            if (transcriptFailed) PageNote(stringResource(R.string.rec_transcribe_failed), Modifier.padding(top = 6.dp).testTag("rec:failed"))
            if (transcript.isNotEmpty()) {
                MetroText(transcript, size = 19.sp, modifier = Modifier.padding(top = 10.dp).testTag("rec:text"))
                MetroButton(stringResource(R.string.rec_to_notes), Modifier.fillMaxWidth().padding(top = 10.dp).testTag("rec:tonotes")) {
                    val text = transcript
                    val title = Recordings.title(file.name)
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            val notes = NoteStore(context)
                            val now = System.currentTimeMillis()
                            runCatching { notes.save(notes.load() + Note(UUID.randomUUID().toString(), title, text, created = now, updated = now)) }.getOrDefault(false)
                        }
                        toast(if (saved) R.string.rec_sent_to_notes else R.string.notes_save_failed)
                    }
                }
            }
            if (busy) PageNote(stringResource(R.string.rec_working), Modifier.padding(top = 8.dp))
        }
        AppBar(listOf(AppBarItem("←", stringResource(R.string.rec_back)) { onClose() }))
    }
    naming?.let { mode ->
        DialogFrame(onCancel = { naming = null }) {
            MetroTextField(name, { name = Recordings.cleanName(it) }, stringResource(R.string.recorder_name_hint), Modifier.testTag("rec:name"))
            MetroButton(stringResource(R.string.recorder_rename_save), Modifier.fillMaxWidth().testTag("rec:name:ok")) {
                if (mode == "rename") {
                    if (store.rename(file, name) != null) { onChanged(); naming = null; onClose() } else toast(R.string.recorder_rename_failed)
                } else {
                    val startMs = (from * duration).toLong()
                    val endMs = (to * duration).toLong()
                    naming = null
                    busy = true
                    scope.launch {
                        val made = withContext(Dispatchers.IO) {
                            // A recording of its own, a moment later than the others; the one cut is left as it was.
                            var target = store.newFile()
                            while (target.exists()) target = store.newFile(java.time.LocalDateTime.now().plusSeconds(1))
                            if (AudioTools.cut(file, startMs, endMs, target)) (if (name.isBlank()) target else store.rename(target, name) ?: target) else null
                        }
                        busy = false
                        if (made != null) { onChanged(); toast(R.string.rec_cut_done); onSelect(made) } else toast(R.string.rec_cut_failed)
                    }
                }
            }
        }
    }
}
