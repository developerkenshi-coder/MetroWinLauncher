package com.metrowin.launcher.recorder

import com.metrowin.launcher.ui.components.isWide
import androidx.compose.runtime.saveable.rememberSaveable
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.data.VoiceRecorder
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Put in the intent of the activity to start a recording as soon as it opens (a shortcut or a tile of the launcher can do it). */
const val EXTRA_RECORD_NOW = "record_now"

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun RecorderScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val store = remember { Recordings(context) }
    val metas = remember { RecMetaStore(context) }
    val opts = remember { RecorderOptions(context) }
    val recorder = remember { VoiceRecorder(context, store) }
    val player = remember { RecorderPlayer() }
    val env = remember { RecEnv(store, metas, opts, player) }
    val activity = remember { context.activity() }

    // A deleted recording waits a few seconds in the trash; leaving the app makes that final.
    var undo by remember { mutableStateOf<List<Pair<File, File>>?>(null) }
    DisposableEffect(recorder) { onDispose { recorder.release(); player.release(); RecTrash.empty(context) } }
    LaunchedEffect(player.playing) { while (player.playing) { player.tick(); delay(80) } }

    // ---- the permissions
    var allowed by remember { mutableStateOf(LivePermissions.isGranted(context, Manifest.permission.RECORD_AUDIO)) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        // Refused with no question asked again: only the settings of the phone can allow it.
        denied = !ok && activity != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = LivePermissions.isGranted(context, Manifest.permission.RECORD_AUDIO)
        if (allowed) denied = false
    }

    // ---- the recordings
    var recs by remember { mutableStateOf(emptyList<Rec>()) }
    var loaded by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(version, VoiceRecorder.active) {
        val first = !loaded
        recs = withContext(Dispatchers.IO) { RecorderData.load(context, store, metas, first) }
        loaded = true
        val tree = opts.backupTree
        if (opts.on(Opt.BACKUP) && tree != null) withContext(Dispatchers.IO) { RecorderBackup.sync(context, Uri.parse(tree), recs.map { it.file }) }
    }
    var selected by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var working by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    val pager = rememberPagerState { 2 }

    // ---- the recording going on
    val recording = VoiceRecorder.active
    val paused = VoiceRecorder.paused
    var elapsed by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var levels by remember { mutableStateOf(emptyList<Float>()) }
    var level by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(recording, paused) {
        while (recording) {
            elapsed = VoiceRecorder.elapsedMs()
            delay(250L)
        }
    }
    // The loudness of the last moments, one bar each, while recording.
    LaunchedEffect(recording) {
        levels = emptyList()
        peak = 0f
        while (recording) {
            level = VoiceRecorder.level()
            peak = maxOf(level, peak * 0.97f)
            levels = (levels + level).takeLast(WAVE_BARS)
            delay(WAVE_STEP_MS)
        }
        level = 0f
    }

    fun say(id: Int) { view.announceForAccessibility(context.getString(id)) }

    fun startRecording() {
        if (busy) return
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) { recorder.start() }
            busy = false
            if (ok) say(R.string.rec_announce_started)
        }
    }

    fun stopRecording() {
        if (busy) return
        scope.launch {
            busy = true
            withContext(Dispatchers.IO) { recorder.stop() }
            busy = false
            version++
            say(R.string.rec_announce_stopped)
        }
    }

    // The notification of the phone is asked for once, before the first recording: without it the service still records, but unseen.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startRecording() }
    fun begin() {
        val prefs = RecorderPrefs.file(context)
        if (Build.VERSION.SDK_INT >= 33 && !LivePermissions.isGranted(context, Manifest.permission.POST_NOTIFICATIONS) && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startRecording()
    }
    LaunchedEffect(allowed) {
        val intent = activity?.intent
        if (allowed && intent?.getBooleanExtra(EXTRA_RECORD_NOW, false) == true && !VoiceRecorder.active) {
            intent.removeExtra(EXTRA_RECORD_NOW)
            begin()
        }
    }

    // ---- what is done to the recordings
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = 0 to uris.size
            val (made, refused) = withContext(Dispatchers.IO) { RecorderShare.import(context, store, uris) { done, total -> importing = done to total } }
            importing = null
            // The new ones join the list; the rest of it is not read again.
            val added = withContext(Dispatchers.IO) { made.map { Rec(it, RecorderData.durationOf(it), it.length(), RecMeta()) } }
            recs = recs + added
            val text = listOfNotNull(
                if (made.isNotEmpty()) context.resources.getQuantityString(R.plurals.rec_imported, made.size, made.size) else null,
                if (refused > 0) context.resources.getQuantityString(R.plurals.rec_import_failed, refused, refused) else null,
            ).joinToString("\n")
            MetroToast.show(context, text)
        }
    }

    fun forgetUndo() {
        val pending = undo ?: return
        undo = null
        scope.launch(Dispatchers.IO) {
            RecTrash.empty(context)
            // The meta of what is gone for good goes with it.
            pending.forEach { (_, back) -> metas.delete(RecorderFormat.stampOf(back.name)) }
        }
    }

    fun delete(files: List<File>) {
        forgetUndo()
        scope.launch {
            val moved = withContext(Dispatchers.IO) { files.mapNotNull { f -> RecTrash.put(context, f)?.let { it to f } } }
            moved.forEach { player.drop(it.second) }
            undo = moved
            picked = emptySet()
            armed = false
            version++
        }
    }

    LaunchedEffect(undo) {
        if (undo != null) { delay(RecorderConfig.UNDO_MS); forgetUndo() }
    }

    fun restore() {
        val pending = undo ?: return
        undo = null
        scope.launch {
            withContext(Dispatchers.IO) { pending.forEach { (trashed, back) -> RecTrash.restore(trashed, back) } }
            version++
        }
    }

    fun merge(files: List<File>) {
        if (working || files.size < 2) return
        working = true
        scope.launch {
            val made = withContext(Dispatchers.IO) {
                val target = RecorderData.nextFile(store)
                val ok = AudioEdit.render(files.sortedBy { it.name }, target, AudioEdit.Plan())
                if (!ok) null else RecorderData.named(store, target, context.getString(R.string.rec_chip_merged)).also { metas.save(RecorderFormat.stampOf(it.name), RecMeta(made = "merged")) }
            }
            working = false
            picked = emptySet()
            if (made != null) { version++; MetroToast.show(context, R.string.rec_edit_done); selected = made.name } else MetroToast.show(context, R.string.rec_edit_failed)
        }
    }

    val open = recs.firstOrNull { it.name == selected }
    val wide = opts.on(Opt.TWO_PANE) && isWide()
    BackHandler(enabled = open != null || picked.isNotEmpty()) { if (open != null) selected = null else { picked = emptySet(); armed = false } }

    val detail: @Composable (Rec, Modifier) -> Unit = { rec, modifier ->
        RecordingDetail(rec, env, onChanged = { version++ }, onClose = { selected = null }, onSelect = { selected = it.name; version++ }, onDelete = ::delete, modifier = modifier)
    }

    val animated = opts.on(Opt.TRANSITIONS) && !LocalReduceMotion.current
    AnimatedContent(
        targetState = if (wide) null else open?.name,
        transitionSpec = {
            if (!animated) ContentTransform(EnterTransition.None, ExitTransition.None)
            else if (targetState != null) (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (fadeOut())
            else fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
        },
        label = "recDetail",
    ) { name ->
        val rec = recs.firstOrNull { it.name == name }
        if (rec != null) {
            MiniFrame {
                detail(rec, Modifier.weight(1f).padding(horizontal = 24.dp))
                AppBar(listOf(AppBarItem("←", stringResource(R.string.rec_back)) { selected = null }))
            }
            return@AnimatedContent
        }
        MiniFrame {
            PivotHeader(listOf(stringResource(R.string.rec_pivot_record), stringResource(R.string.rec_pivot_list)), pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
            androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    if (page == 0) {
                        RecordPage(
                            RecordState(allowed, denied, recording, paused, elapsed, levels, level, peak, VoiceRecorder.marks.size, busy, VoiceRecorder.failure, VoiceRecorder.notice),
                            opts,
                            onAllow = { ask.launch(Manifest.permission.RECORD_AUDIO) },
                            onSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) } },
                            onToggle = { if (recording) stopRecording() else begin() },
                            onPause = { if (VoiceRecorder.paused) VoiceRecorder.resume() else VoiceRecorder.pause() },
                            onMark = { VoiceRecorder.addMark(); say(R.string.rec_announce_marked) },
                        )
                    } else {
                        val list = ListState(recs, loaded, picked, query, label, recording, elapsed, importing, undo != null, player.file?.name)
                        val onOpenRec: (Rec) -> Unit = { selected = it.name }
                        val onPickRec: (Rec) -> Unit = { r -> picked = if (r.name in picked) picked - r.name else picked + r.name; armed = false }
                        val page1: @Composable (Modifier) -> Unit = { m ->
                            RecordingsPage(
                                list, opts, onQuery = { query = it }, onLabel = { label = it }, onSort = { opts.cycle(Opt.SORT) },
                                onOpen = onOpenRec, onPick = onPickRec, onGoRecord = { scope.launch { pager.animateScrollToPage(0) } }, onUndo = ::restore,
                                modifier = m,
                            )
                        }
                        if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                            page1(Modifier.weight(0.42f))
                            Box(Modifier.weight(0.58f).fillMaxHeight().padding(start = 20.dp)) {
                                if (open != null) detail(open, Modifier.fillMaxSize()) else androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) { MicIllustration(Modifier.align(androidx.compose.ui.Alignment.Center)) }
                            }
                        } else page1(Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
            if (working) com.metrowin.launcher.ui.components.MetroProgressBar(Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            if (opts.on(Opt.MINI) && pager.currentPage == 1 && player.file != null) MiniPlayer(player, opts.on(Opt.PRETTY), onOpen = { player.file?.let { f -> selected = f.name } })
            val menu = listOf(
                AppBarItem("⚙", stringResource(R.string.rec_options)) { showOptions = true },
                AppBarItem("∑", stringResource(R.string.rec_stats)) { showStats = true },
            )
            if (pager.currentPage == 1) {
                // The bar of the list: bring sound in, and act on the recordings that are ticked (a long press ticks one).
                AppBar(
                    buildList {
                        add(AppBarItem("＋", stringResource(R.string.rec_import)) { importer.launch(RecorderShare.IMPORT_TYPES) })
                        if (picked.isNotEmpty()) {
                            val chosen = recs.filter { it.name in picked }.map { it.file }
                            add(AppBarItem("⇧", stringResource(R.string.recorder_share)) { RecorderShare.audio(context, chosen)?.let { runCatching { context.startActivity(it) } } })
                            add(AppBarItem("", stringResource(R.string.rec_play_all)) { player.playQueue(chosen.sortedBy { it.name }, 0); picked = emptySet() }.icon(Mark.Play))
                            if (chosen.size >= 2 && !working) add(AppBarItem("⊕", stringResource(R.string.rec_merge)) { merge(chosen) })
                            add(AppBarItem("⌫", stringResource(if (armed) R.string.notes_delete_confirm else R.string.recorder_delete)) { if (armed) delete(chosen) else armed = true }.marked(armed))
                            add(AppBarItem("✕", stringResource(R.string.rec_clear)) { picked = emptySet(); armed = false })
                        }
                    },
                    menu = menu,
                )
            } else AppBar(emptyList(), menu = menu)
        }
    }
    if (showOptions) OptionsDialog(opts) { showOptions = false }
    if (showStats) StatsDialog(recs) { showStats = false }
}
