package com.metrowin.launcher.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.data.TimerFormat
import com.metrowin.launcher.notes.Note
import com.metrowin.launcher.notes.NoteStore
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroProgressBar
import com.metrowin.launcher.ui.components.MetroSlider
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** What the pages of a recording share: where the recordings are, the switches, and the one player. */
internal class RecEnv(val store: Recordings, val metas: RecMetaStore, val opts: RecorderOptions, val player: RecorderPlayer)

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val ZOOMS = listOf(1, 2, 4, 8)

/** The three parts of a recording, one at a time: to listen to it, to change it, and what was said in it. */
internal enum class Part { Listen, Edit, Text }

/**
 * One recording: its sound as a picture with two handles to choose a piece, and what can be done with the piece and with the whole,
 * in three parts so none of them is a long scroll. [onClose] goes back; [onDelete] hands the file to the list to be put in the trash.
 */
@Composable
internal fun RecordingDetail(
    rec: Rec,
    env: RecEnv,
    onChanged: () -> Unit,
    onClose: () -> Unit,
    onSelect: (File) -> Unit,
    onDelete: (List<File>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = rec.file
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val opts = env.opts
    val player = env.player
    val stamp = rec.stamp

    var part by remember(file.name) { mutableStateOf(Part.Listen) }
    var meta by remember(file.name) { mutableStateOf(rec.meta) }
    fun saveMeta(next: RecMeta) {
        meta = next
        scope.launch { withContext(Dispatchers.IO) { env.metas.save(stamp, next) }; onChanged() }
    }

    val peaks by produceState(FloatArray(0), file.path, file.length()) { value = withContext(Dispatchers.IO) { AudioTools.peaksCached(context.cacheDir, file) } }
    var spectro by remember(file.name) { mutableStateOf(false) }
    val spectrum by produceState<ImageBitmap?>(null, file.path, spectro) {
        value = null
        if (spectro) value = withContext(Dispatchers.IO) { AudioEdit.spectrogram(file)?.let { spectrumImage(it, 320, 72, accent) } }
    }
    var from by remember(file.name) { mutableFloatStateOf(0f) }
    var to by remember(file.name) { mutableFloatStateOf(1f) }
    var zoom by remember(file.name) { mutableIntStateOf(1) }

    LaunchedEffect(file.path) { player.load(file) }
    val here = player.file?.path == file.path
    val duration = rec.durationMs.takeIf { it > 0 } ?: if (here) player.duration else 0L
    val position = if (here) player.position else 0L
    val startMs = (from * duration).toLong()
    val endMs = (to * duration).toLong()
    val cuttable = remember(file.name) { AudioTools.canCut(file) }
    val cutAny = !cuttable && opts.on(Opt.CUT_ANY) && duration > 0
    val canCut = cuttable || cutAny

    var naming by remember { mutableStateOf<String?>(null) }   // "cut", "rename" or "label"
    var name by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var cancelEdit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var transcript by remember(file.name) { mutableStateOf(if (opts.on(Opt.KEEP_TEXT)) rec.meta.transcript else "") }
    var transcribing by remember { mutableStateOf(false) }
    var transcriptFailed by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }
    var unknownLanguage by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    val transcriber = remember { Transcriber(context) }
    // What the phone offers does not change while the page is open: asked once, not at every redraw.
    val mode = remember { transcriber.mode }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { transcriber.release() } }

    fun toast(id: Int) = MetroToast.show(context, id)

    fun nameFor(label: Int): String = (Recordings.nameOf(file.name) + " (" + context.getString(label).lowercase() + ")").trim()

    /** Writes a new recording from this one with [plan] and opens it; [kind] is what it is marked as. */
    fun edit(kind: String, label: Int, plan: AudioEdit.Plan, tail: String = "") {
        if (busy) return
        busy = true
        val stop = AtomicBoolean(false)
        cancelEdit = { stop.set(true) }
        scope.launch {
            val made = withContext(Dispatchers.IO) {
                val target = RecorderData.nextFile(env.store)
                val ok = AudioEdit.render(listOf(file), target, plan) { stop.get() }
                if (!ok) null else RecorderData.named(env.store, target, nameFor(label) + tail).also { env.metas.save(RecorderFormat.stampOf(it.name), RecMeta(made = kind)) }
            }
            busy = false
            cancelEdit = null
            if (made != null) { onChanged(); toast(R.string.rec_edit_done); onSelect(made) } else if (!stop.get()) toast(R.string.rec_edit_failed)
        }
    }

    fun export(extension: String) {
        if (busy) return
        busy = true
        val stop = AtomicBoolean(false)
        cancelEdit = { stop.set(true) }
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                RecorderShare.clearExports(context)
                val target = File(RecorderShare.exportDir(context), file.nameWithoutExtension + "." + extension)
                val ok = if (extension == "wav") AudioEdit.toWav(file, target) { stop.get() }
                else AudioEdit.render(listOf(file), target, AudioEdit.Plan(bitRate = 32_000, mime = android.media.MediaFormat.MIMETYPE_AUDIO_OPUS, container = android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, rate = 48_000)) { stop.get() }
                target.takeIf { ok }
            }
            busy = false
            cancelEdit = null
            if (out != null) runCatching { context.startActivity(RecorderShare.exported(context, out, if (extension == "wav") "audio/x-wav" else "audio/ogg")) }
            else if (!stop.get()) toast(R.string.rec_edit_failed)
        }
    }

    fun nudge(which: Int, ms: Long) {
        if (duration <= 0) return
        val gap = (300f / duration).coerceAtMost(0.5f)
        val d = ms.toFloat() / duration
        if (which == 1) from = (from + d).coerceIn(0f, to - gap) else to = (to + d).coerceIn(from + gap, 1f)
    }

    fun startTranscribing() {
        transcribing = true
        transcriptFailed = false
        transcript = ""
        missing = false
        var latest = ""
        transcriber.start(
            file,
            onText = { transcript = it; latest = it },
            onDone = {
                transcribing = false
                if (opts.on(Opt.KEEP_TEXT) && latest.isNotBlank()) saveMeta(meta.copy(transcript = latest, transcribedAt = System.currentTimeMillis()))
            },
            onFailed = { transcribing = false; transcriptFailed = true; missing = it == Transcriber.ERROR_LANGUAGE_MISSING; unknownLanguage = it == Transcriber.ERROR_LANGUAGE_UNKNOWN },
        )
    }

    Column(modifier) {
        // The title, which is also the way to rename it, and what it is.
        MetroText(
            RecorderFormat.title(file.name, opts.on(Opt.PRETTY)), size = 26.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 2.dp).metroPress(onClick = { name = Recordings.nameOf(file.name); naming = "rename" })
                .semantics { contentDescription = RecorderFormat.title(file.name, opts.on(Opt.PRETTY)) }.testTag("rec:title"),
        )
        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroText(TimerFormat.stopwatch(duration) + "  ·  " + RecorderFormat.size(rec.size), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            Chip(file.extension.uppercase())
            if (meta.made.isNotEmpty()) Chip(madeLabel(meta.made))
            if (opts.on(Opt.LABELS) && meta.label.isNotEmpty()) Chip(meta.label, filled = true)
        }
        Segments(
            listOf(stringResource(R.string.rec_part_listen), stringResource(R.string.rec_part_edit), stringResource(R.string.rec_part_text)),
            part.ordinal, { part = Part.entries[it] }, Modifier.padding(bottom = 6.dp).testTag("rec:parts"),
        )
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).scrollBar(scroll).verticalScroll(scroll)) {
            // The picture is in the first two parts: what is listened to is what is cut.
            if (part != Part.Text) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    MetroText(TimerFormat.stopwatch(position) + " / " + TimerFormat.stopwatch(duration), size = 20.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.testTag("rec:position"))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (opts.on(Opt.SPECTRO)) MetroText(stringResource(if (spectro) R.string.rec_view_wave else R.string.rec_view_spectrum), size = 15.sp, weight = FontWeight.Normal, color = accent, modifier = Modifier.metroPress(onClick = { spectro = !spectro }).padding(vertical = 6.dp).testTag("rec:spectro"))
                        MetroText("×" + zoom, size = 15.sp, weight = FontWeight.Normal, color = accent, modifier = Modifier.metroPress(onClick = { zoom = ZOOMS[(ZOOMS.indexOf(zoom) + 1) % ZOOMS.size] }).padding(vertical = 6.dp).testTag("rec:zoom"))
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    val viewport = maxWidth
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        EditWave(
                            peaks, spectrum, from, to, position, duration, if (opts.on(Opt.MARKS)) meta.marks else emptyList(), zoomed = zoom > 1, contrast = opts.on(Opt.CONTRAST), themed = opts.on(Opt.WAVE_THEME),
                            description = stringResource(R.string.rec_wave_description, TimerFormat.stopwatch(startMs), TimerFormat.stopwatch(endMs)),
                            actions = listOf(
                                stringResource(R.string.rec_start_plus) to { nudge(1, 1000) }, stringResource(R.string.rec_start_minus) to { nudge(1, -1000) },
                                stringResource(R.string.rec_end_plus) to { nudge(2, 1000) }, stringResource(R.string.rec_end_minus) to { nudge(2, -1000) },
                            ),
                            onFrom = { from = it }, onTo = { to = it }, onSeek = { player.seek((it * duration).toLong()) },
                            modifier = Modifier.width(viewport * zoom).height(120.dp).background(colors.surface).testTag("rec:wave"),
                        )
                    }
                }
                MetroText(
                    stringResource(R.string.rec_selection_len, TimerFormat.stopwatch(startMs), TimerFormat.stopwatch(endMs), TimerFormat.stopwatch(endMs - startMs)),
                    size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.testTag("rec:selection"),
                )
            }
            when (part) {
                Part.Listen -> {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (opts.on(Opt.SEEK)) MetroButton("−10 s", Modifier.weight(1f).testTag("rec:back10")) { player.skip(-10_000) }
                        MetroButton(stringResource(if (player.playing && here) R.string.rec_pause else R.string.rec_play), Modifier.weight(if (opts.on(Opt.SEEK)) 1.4f else 1f).testTag("rec:play")) { player.toggle(startMs, endMs) }
                        if (opts.on(Opt.SEEK)) MetroButton("+10 s", Modifier.weight(1f).testTag("rec:fwd10")) { player.skip(10_000) }
                    }
                    if (opts.on(Opt.SEEK) && duration > 0) MetroSlider(
                        (position.toFloat() / duration).coerceIn(0f, 1f), { player.seek((it * duration).toLong()) },
                        stringResource(R.string.rec_scrubber, TimerFormat.stopwatch(position)), Modifier.padding(top = 10.dp).testTag("rec:scrub"),
                    )
                    if (opts.on(Opt.SPEED)) MetroButton(
                        stringResource(R.string.rec_speed, speedText(player.speed)), Modifier.fillMaxWidth().padding(top = 10.dp).testTag("rec:speed"),
                    ) { player.changeSpeed(SPEEDS[(SPEEDS.indexOf(player.speed).coerceAtLeast(1) + 1) % SPEEDS.size]) }
                    if (player.failed) PageNote(stringResource(R.string.rec_play_failed), Modifier.padding(top = 8.dp).testTag("rec:noplay"))
                    if (opts.on(Opt.MARKS)) {
                        MetroButton(stringResource(R.string.rec_mark_here), Modifier.fillMaxWidth().padding(top = 10.dp).testTag("rec:addmark")) {
                            saveMeta(meta.copy(marks = (meta.marks + position).distinct().sorted()))
                        }
                        if (meta.marks.isNotEmpty()) {
                            PageNote(stringResource(R.string.rec_marks_hint), Modifier.padding(top = 8.dp))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                meta.marks.forEach { m ->
                                    Chip(TimerFormat.stopwatch(m), Modifier.metroPress(onClick = { player.seek(m) }, onLongClick = { saveMeta(meta.copy(marks = meta.marks - m)) }))
                                }
                            }
                        }
                    }
                }
                Part.Edit -> {
                    if (!opts.hintShown) {
                        PageNote(stringResource(R.string.rec_hint_cut), Modifier.padding(top = 10.dp).testTag("rec:hint"))
                        MetroButton(stringResource(R.string.rec_hint_ok), Modifier.padding(top = 8.dp)) { opts.hintShown = true }
                    }
                    if (opts.on(Opt.NUDGE)) Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetroButton(stringResource(R.string.rec_start_minus), Modifier.weight(1f).testTag("rec:s-")) { nudge(1, -1000) }
                            MetroButton(stringResource(R.string.rec_start_plus), Modifier.weight(1f).testTag("rec:s+")) { nudge(1, 1000) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetroButton(stringResource(R.string.rec_end_minus), Modifier.weight(1f).testTag("rec:e-")) { nudge(2, -1000) }
                            MetroButton(stringResource(R.string.rec_end_plus), Modifier.weight(1f).testTag("rec:e+")) { nudge(2, 1000) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetroButton(stringResource(R.string.rec_hear_start), Modifier.weight(1f).testTag("rec:hear-start")) { player.preview(startMs, minOf(startMs + 2000, endMs)) }
                        MetroButton(stringResource(R.string.rec_hear_end), Modifier.weight(1f).testTag("rec:hear-end")) { player.preview(maxOf(endMs - 2000, startMs), endMs) }
                    }
                    MetroButton(stringResource(R.string.rec_cut), Modifier.fillMaxWidth().padding(top = 10.dp).testTag("rec:cut")) { if (canCut) { name = ""; naming = "cut" } else toast(R.string.rec_cut_unsupported) }
                    if (!canCut) PageNote(stringResource(R.string.rec_cut_unsupported), Modifier.padding(top = 8.dp).testTag("rec:nocut"))
                    else if (cutAny) PageNote(stringResource(R.string.rec_cut_reencode), Modifier.padding(top = 8.dp).testTag("rec:reencode"))
                    if (opts.on(Opt.TOOLS) && duration > 0) {
                        MetroText(stringResource(R.string.rec_tools), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetroButton(stringResource(R.string.rec_tool_normalise), Modifier.fillMaxWidth().testTag("rec:normalise")) { edit("normalised", R.string.rec_chip_loud, AudioEdit.Plan(normalise = true)) }
                            MetroButton(stringResource(R.string.rec_tool_fade), Modifier.fillMaxWidth().testTag("rec:fade")) { edit("faded", R.string.rec_chip_faded, AudioEdit.Plan(fadeMs = 800)) }
                            MetroButton(stringResource(R.string.rec_tool_silence), Modifier.fillMaxWidth().testTag("rec:silence")) { edit("trimmed", R.string.rec_chip_edited, AudioEdit.Plan(dropSilence = true)) }
                            MetroButton(stringResource(R.string.rec_export_wav), Modifier.fillMaxWidth().testTag("rec:wav")) { export("wav") }
                            if (remember { AudioEdit.opusAvailable() }) MetroButton(stringResource(R.string.rec_export_opus), Modifier.fillMaxWidth().testTag("rec:opus")) { export("opus") }
                        }
                    }
                }
                Part.Text -> {
                    if (mode == Transcriber.Mode.None) PageNote(stringResource(R.string.rec_transcribe_unsupported), Modifier.padding(top = 10.dp, bottom = 8.dp).testTag("rec:unsupported"))
                    else {
                        PageNote(stringResource(if (mode == Transcriber.Mode.OnDevice) R.string.rec_transcribe_offline else R.string.rec_transcribe_online), Modifier.padding(top = 10.dp, bottom = 8.dp).testTag("rec:mode"))
                        if (transcribing) MetroButton(stringResource(R.string.rec_transcribe_cancel), Modifier.fillMaxWidth().testTag("rec:transcribe")) { transcriber.cancel(); transcribing = false }
                        else MetroButton(stringResource(R.string.rec_transcribe), Modifier.fillMaxWidth().testTag("rec:transcribe")) { if (mode == Transcriber.Mode.Service) consent = true else startTranscribing() }
                    }
                    if (missing) {
                        PageNote(stringResource(R.string.rec_lang_missing, java.util.Locale.getDefault().displayLanguage), Modifier.padding(top = 6.dp).testTag("rec:langmissing"))
                        MetroButton(stringResource(R.string.rec_lang_download), Modifier.fillMaxWidth().padding(top = 8.dp).testTag("rec:langdownload")) {
                            toast(if (transcriber.downloadLanguage()) R.string.rec_lang_downloading else R.string.rec_transcribe_unsupported)
                        }
                    } else if (unknownLanguage) PageNote(stringResource(R.string.rec_lang_unknown, java.util.Locale.getDefault().displayLanguage), Modifier.padding(top = 6.dp).testTag("rec:langunknown"))
                    else if (transcriptFailed) PageNote(stringResource(R.string.rec_transcribe_failed), Modifier.padding(top = 6.dp).testTag("rec:failed"))
                    if (transcribing) MetroProgressBar(Modifier.padding(top = 10.dp).testTag("rec:transcribing"))
                    if (transcript.isNotEmpty()) {
                        if (meta.transcribedAt != 0L && !transcribing) PageNote(stringResource(R.string.rec_text_saved_on, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(meta.transcribedAt)), Modifier.padding(top = 10.dp))
                        if (opts.on(Opt.SUMMARY) && !transcribing && RecorderFormat.summary(transcript).length < transcript.length * 0.8) {
                            MetroText(stringResource(R.string.rec_summary), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 12.dp))
                            MetroText(RecorderFormat.summary(transcript), size = 18.sp, modifier = Modifier.padding(top = 4.dp).testTag("rec:summary"))
                        }
                        MetroText(transcript, size = 19.sp, modifier = Modifier.padding(top = 12.dp).testTag("rec:text"))
                        MetroButton(stringResource(R.string.rec_share_text), Modifier.fillMaxWidth().padding(top = 10.dp).testTag("rec:sharetext")) {
                            runCatching { context.startActivity(RecorderShare.text(transcript, Recordings.title(file.name))) }
                        }
                        MetroButton(stringResource(R.string.rec_to_notes), Modifier.fillMaxWidth().padding(top = 10.dp).testTag("rec:tonotes")) {
                            val text = transcript
                            val title = Recordings.title(file.name)
                            val linked = meta.noteId
                            scope.launch {
                                val (id, saved, updated) = withContext(Dispatchers.IO) {
                                    val notes = NoteStore(context)
                                    val now = System.currentTimeMillis()
                                    runCatching {
                                        val all = notes.load()
                                        val old = all.firstOrNull { it.id == linked }
                                        // The note of this recording is changed, not another one added each time.
                                        if (old != null) Triple(old.id, notes.save(all.map { if (it.id == old.id) it.copy(text = text, updated = now) else it }), true)
                                        else { val id = UUID.randomUUID().toString(); Triple(id, notes.save(all + Note(id, title, text, created = now, updated = now)), false) }
                                    }.getOrDefault(Triple("", false, false))
                                }
                                if (saved && id != linked) saveMeta(meta.copy(noteId = id))
                                toast(if (!saved) R.string.notes_save_failed else if (updated) R.string.rec_note_updated else R.string.rec_sent_to_notes)
                            }
                        }
                        if (meta.transcript.isNotEmpty()) MetroButton(stringResource(R.string.rec_text_forget), Modifier.fillMaxWidth().padding(top = 10.dp).testTag("rec:forget")) {
                            saveMeta(meta.copy(transcript = "", transcribedAt = 0L)); transcript = ""
                        }
                    }
                    if (opts.on(Opt.LABELS)) MetroButton(
                        if (meta.label.isEmpty()) stringResource(R.string.rec_label_add) else stringResource(R.string.rec_label_change, meta.label),
                        Modifier.fillMaxWidth().padding(top = 18.dp).testTag("rec:label"),
                    ) { name = meta.label; naming = "label" }
                }
            }
            if (busy) {
                MetroProgressBar(Modifier.padding(top = 14.dp))
                PageNote(stringResource(R.string.rec_working), Modifier.padding(top = 8.dp))
                cancelEdit?.let { stop -> MetroButton(stringResource(R.string.rec_busy_cancel), Modifier.padding(top = 8.dp).testTag("rec:cancel")) { stop() } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetroButton(stringResource(R.string.recorder_rename), Modifier.weight(1f).testTag("rec:rename")) { name = Recordings.nameOf(file.name); naming = "rename" }
                MetroButton(stringResource(R.string.recorder_share), Modifier.weight(1f).testTag("rec:share")) {
                    RecorderShare.audio(context, listOf(file))?.let { runCatching { context.startActivity(it) } }
                }
                MetroButton(stringResource(if (confirm) R.string.notes_delete_confirm else R.string.recorder_delete), Modifier.weight(1f).testTag("rec:delete")) {
                    if (confirm) { player.drop(file); onDelete(listOf(file)); onClose() } else confirm = true
                }
            }
        }
    }
    if (consent) DialogFrame(onCancel = { consent = false }) {
        MetroText(stringResource(R.string.rec_consent_text), size = 18.sp, weight = FontWeight.Normal, modifier = Modifier.padding(bottom = 14.dp))
        MetroButton(stringResource(R.string.rec_consent_go), Modifier.fillMaxWidth().testTag("rec:consent")) { consent = false; startTranscribing() }
    }
    naming?.let { mode2 ->
        DialogFrame(onCancel = { naming = null }) {
            MetroTextField(name, { name = if (mode2 == "label") it.filter { c -> c.isLetterOrDigit() || c == ' ' || c == '-' }.take(16) else Recordings.cleanName(it) }, stringResource(if (mode2 == "label") R.string.rec_label_hint else R.string.recorder_name_hint), Modifier.testTag("rec:name"))
            MetroButton(stringResource(R.string.recorder_rename_save), Modifier.fillMaxWidth().testTag("rec:name:ok")) {
                when (mode2) {
                    "rename" -> {
                        val old = file
                        scope.launch {
                            val renamed = withContext(Dispatchers.IO) { env.store.rename(old, name) }
                            if (renamed != null) { player.drop(old); onChanged(); naming = null; onClose() } else toast(R.string.recorder_rename_failed)
                        }
                    }
                    "label" -> { saveMeta(meta.copy(label = name.trim())); naming = null }
                    else -> {
                        val s = startMs
                        val e = endMs
                        val label = name
                        naming = null
                        busy = true
                        scope.launch {
                            val made = withContext(Dispatchers.IO) {
                                // A recording of its own, a moment later than the others; the one cut is left as it was.
                                val target = RecorderData.nextFile(env.store)
                                val ok = if (cuttable) AudioTools.cut(file, s, e, target) else AudioEdit.render(listOf(file), target, AudioEdit.Plan(startMs = s, endMs = e))
                                if (!ok) null else RecorderData.named(env.store, target, label).also { env.metas.save(RecorderFormat.stampOf(it.name), RecMeta(made = "cut")) }
                            }
                            busy = false
                            if (made != null) { onChanged(); toast(R.string.rec_cut_done); onSelect(made) } else toast(R.string.rec_cut_failed)
                        }
                    }
                }
            }
        }
    }
}

private fun speedText(v: Float) = if (v == v.toInt().toFloat()) "${v.toInt()}" else "%.2f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
