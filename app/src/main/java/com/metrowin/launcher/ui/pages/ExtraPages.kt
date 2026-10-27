package com.metrowin.launcher.ui.pages

import com.metrowin.launcher.data.ParkedCar
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Code128
import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.data.Steps
import com.metrowin.launcher.data.TimerFormat
import com.metrowin.launcher.data.VoiceRecorder
import com.metrowin.launcher.data.WalletCard
import com.metrowin.launcher.data.WalletStore
import com.metrowin.launcher.data.WhereAmI
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The steps of today against the goal, the last seven days as bars, the days in a row at the goal, the average and the best day. */
@Composable
fun StepsPage(
    label: String,
    steps: Int?,
    allowed: Boolean,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
    goal: Int = Steps.GOAL,
    history: Map<Long, Int> = emptyMap(),
    today: java.time.LocalDate = java.time.LocalDate.now(),
) {
    PageFrame(stringResource(R.string.steps_header), label, modifier) {
        if (!allowed) {
            PageNote(stringResource(R.string.steps_need_permission))
            MetroButton(stringResource(R.string.steps_allow), Modifier.padding(top = 14.dp).testTag("steps:allow"), onClick = onAllow)
        } else {
            MetroText((steps ?: 0).toString(), size = 84.sp, maxLines = 1, modifier = Modifier.testTag("steps:page:count"))
            val day = today.toEpochDay()
            // Today is the count of the page, whatever was last written in the history.
            val days = (history + (day to maxOf(steps ?: 0, history[day] ?: 0)))
            if (goal > 0) {
                val fraction = ((steps ?: 0).toFloat() / goal).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).background(LocalMetroColors.current.subtle.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(LocalAccent.current))
                }
                PageNote(stringResource(R.string.steps_goal, goal), Modifier.padding(top = 10.dp))
                Steps.streak(days, goal, day).takeIf { it > 0 }?.let {
                    PageNote(stringResource(R.string.steps_streak, it), Modifier.padding(top = 4.dp).testTag("steps:streak"))
                }
            }
            StepsWeek(days, goal, today)
            PageNote(stringResource(R.string.steps_since_note), Modifier.padding(top = 18.dp, bottom = 24.dp))
        }
    }
}

/** Record, stop and listen again. The sound is kept in the storage of the app and goes nowhere. */
@Composable
fun RecorderPage(label: String, allowed: Boolean, onAllow: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { Recordings(context) }
    val recorder = remember { VoiceRecorder(context, store) }
    DisposableEffect(recorder) { onDispose { recorder.release() } }
    var recording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    val files = remember(version) { store.list() }
    // The recording being given a name, and the name as typed.
    var renaming by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var renameFailed by remember { mutableStateOf(false) }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = SystemClock.elapsedRealtime() - startedAt
            delay(250L)
        }
    }
    PageFrame(stringResource(R.string.recorder_header), label, modifier) {
        if (!allowed) {
            PageNote(stringResource(R.string.recorder_need_permission))
            MetroButton(stringResource(R.string.recorder_allow), Modifier.padding(top = 14.dp).testTag("recorder:allow"), onClick = onAllow)
        } else {
            MetroText(TimerFormat.stopwatch(if (recording) elapsed else 0L, tenths = false), size = 72.sp, maxLines = 1, modifier = Modifier.testTag("recorder:time"))
            MetroButton(
                stringResource(if (recording) R.string.recorder_stop else R.string.recorder_start),
                Modifier.fillMaxWidth().padding(top = 16.dp).testTag("recorder:toggle"),
            ) {
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
        MetroText(
            pluralStringResource(R.plurals.recordings_count, files.size, files.size), size = 18.sp, color = LocalMetroColors.current.subtle,
            modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            files.forEachIndexed { index, file ->
                if (renaming == file.name) {
                    Column(Modifier.fillMaxWidth().testTag("recorder:renamebox"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetroTextField(newName, { newName = Recordings.cleanName(it); renameFailed = false }, stringResource(R.string.recorder_name_hint), Modifier.testTag("recorder:name"))
                        if (renameFailed) PageNote(stringResource(R.string.recorder_rename_failed))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetroButton(stringResource(R.string.recorder_rename_save), Modifier.weight(1f).testTag("recorder:renamesave")) {
                                if (store.rename(file, newName) != null) {
                                    renaming = null
                                    version++
                                } else {
                                    renameFailed = true
                                }
                            }
                            MetroButton(stringResource(R.string.recorder_rename_cancel), Modifier.weight(1f).testTag("recorder:renamecancel")) { renaming = null }
                        }
                    }
                } else {
                    RecordingRow(
                        file, playing == file.name, Modifier.featherIn(index, files.size),
                        onPlay = {
                            if (playing == file.name) {
                                recorder.stopPlaying()
                                playing = null
                            } else if (!recording && recorder.play(file) { playing = null }) {
                                playing = file.name
                            }
                        },
                        onRename = {
                            renaming = file.name
                            newName = Recordings.nameOf(file.name)
                            renameFailed = false
                        },
                        onShare = { shareRecording(context, file) },
                        onDelete = {
                            if (playing == file.name) {
                                recorder.stopPlaying()
                                playing = null
                            }
                            store.delete(file)
                            version++
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(file: File, playing: Boolean, modifier: Modifier, onPlay: () -> Unit, onRename: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Row(modifier.fillMaxWidth().testTag("recorder:row"), verticalAlignment = Alignment.CenterVertically) {
        MetroText(
            (if (playing) "▶ " else "") + Recordings.title(file.name), size = 20.sp, weight = FontWeight.Normal,
            modifier = Modifier.weight(1f).metroPress(onClick = onPlay).padding(vertical = 8.dp).testTag("recorder:play"),
        )
        MetroText(
            stringResource(R.string.recorder_rename), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
            modifier = Modifier.metroPress(onClick = onRename).padding(6.dp).testTag("recorder:rename"),
        )
        MetroText(
            stringResource(R.string.recorder_share), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
            modifier = Modifier.metroPress(onClick = onShare).padding(6.dp).testTag("recorder:share"),
        )
        MetroText(
            stringResource(R.string.recorder_delete), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
            modifier = Modifier.metroPress(onClick = onDelete).padding(6.dp).testTag("recorder:delete"),
        )
    }
}

/** The share sheet of Android for one recording: the file stays in the storage of the app and only the app chosen reads it, once. */
private fun shareRecording(context: android.content.Context, file: File) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("audio/mp4").putExtra(android.content.Intent.EXTRA_STREAM, uri)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** The cards of the wallet: a name and a code each. A touch shows the code as a barcode, large, on white. */
@Composable
fun WalletPage(label: String, cards: List<WalletCard>, onAdd: (String, String) -> Boolean, onRemove: (String) -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf(false) }
    val open = cards.firstOrNull { it.id == shown }
    PageFrame(stringResource(R.string.wallet_header), label, modifier) {
        if (open != null) {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp).testTag("wallet:card"), horizontalAlignment = Alignment.CenterHorizontally) {
                MetroText(open.name, size = 22.sp, color = Color.Black, maxLines = 2)
                Barcode(open.code, Modifier.fillMaxWidth().height(120.dp).padding(vertical = 10.dp))
                MetroText(open.code, size = 18.sp, weight = FontWeight.Normal, color = Color.Black, maxLines = 1)
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(stringResource(R.string.wallet_back), Modifier.weight(1f).testTag("wallet:back")) { shown = null }
                MetroButton(stringResource(R.string.wallet_remove), Modifier.weight(1f).testTag("wallet:remove")) {
                    onRemove(open.id)
                    shown = null
                }
            }
        } else {
            if (cards.isEmpty()) PageNote(stringResource(R.string.wallet_empty))
            cards.forEachIndexed { index, card ->
                MetroText(
                    card.name, size = 24.sp, weight = FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.fillMaxWidth().featherIn(index, cards.size).metroPress(onClick = { shown = card.id }).padding(vertical = 8.dp).testTag("wallet:row"),
                )
            }
            if (cards.size < WalletStore.MAX_CARDS) {
                MetroText(stringResource(R.string.wallet_add), size = 18.sp, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
                MetroTextField(name, { name = it.take(40) }, stringResource(R.string.wallet_name), Modifier.testTag("wallet:name"))
                Box(Modifier.padding(top = 8.dp)) {
                    MetroTextField(code, { code = it.take(WalletStore.MAX_CODE); refused = false }, stringResource(R.string.wallet_code), Modifier.testTag("wallet:code"))
                }
                if (refused) PageNote(stringResource(R.string.wallet_refused), Modifier.padding(top = 6.dp))
                MetroButton(stringResource(R.string.wallet_save), Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 24.dp).testTag("wallet:save")) {
                    if (onAdd(name, code)) {
                        name = ""
                        code = ""
                    } else {
                        refused = true
                    }
                }
            }
        }
    }
}

/** [code] as Code 128 bars in black on white, with the quiet space on both sides that scanners want. */
@Composable
private fun Barcode(code: String, modifier: Modifier = Modifier) {
    val widths = remember(code) { Code128.widths(code) }
    Canvas(modifier) {
        val modules = widths.sum() + 2 * QUIET
        val unit = size.width / modules
        var x = QUIET * unit
        widths.forEachIndexed { i, w ->
            if (i % 2 == 0) drawRect(Color.Black, Offset(x, 0f), Size(w * unit, size.height))
            x += w * unit
        }
    }
}

private const val QUIET = 10

/** Where the phone is: the street and the numbers, to open on the map or to send. */
@Composable
fun WhereAmIPage(
    label: String,
    allowed: Boolean,
    onAllow: () -> Unit,
    locate: suspend () -> WhereAmI?,
    onOpenMap: (WhereAmI) -> Unit,
    onShare: (WhereAmI) -> Unit,
    modifier: Modifier = Modifier,
) {
    var where by remember { mutableStateOf<WhereAmI?>(null) }
    var busy by remember { mutableStateOf(false) }
    var missed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun find() {
        if (busy) return
        busy = true
        missed = false
        scope.launch {
            where = locate()
            missed = where == null
            busy = false
        }
    }
    LaunchedEffect(allowed) { if (allowed) find() }
    PageFrame(stringResource(R.string.where_header), label, modifier) {
        if (!allowed) {
            PageNote(stringResource(R.string.where_need_permission))
            MetroButton(stringResource(R.string.where_allow), Modifier.padding(top = 14.dp).testTag("where:allow"), onClick = onAllow)
            return@PageFrame
        }
        val here = where
        if (here != null) {
            MetroText(here.address ?: stringResource(R.string.where_no_address), size = 28.sp, maxLines = 4, lineHeight = 34.sp, modifier = Modifier.testTag("where:address"))
            PageNote("%.5f, %.5f · ±%d m".format(here.latitude, here.longitude, here.accuracy.toInt()), Modifier.padding(top = 8.dp).testTag("where:numbers"))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(stringResource(R.string.where_map), Modifier.weight(1f).testTag("where:map")) { onOpenMap(here) }
                MetroButton(stringResource(R.string.where_share), Modifier.weight(1f).testTag("where:share")) { onShare(here) }
            }
        }
        if (busy) PageNote(stringResource(R.string.where_searching), Modifier.padding(top = 12.dp))
        if (missed) PageNote(stringResource(R.string.where_missed), Modifier.padding(top = 12.dp))
        MetroButton(stringResource(R.string.where_again), Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp).testTag("where:again")) { find() }
    }
}

/** The last seven days of steps as bars, the goal as a line across them, with the average and the best day under. */
@Composable
private fun StepsWeek(days: Map<Long, Int>, goal: Int, today: java.time.LocalDate) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val week = (6 downTo 0).map { back -> today.minusDays(back.toLong()).let { it to (days[it.toEpochDay()] ?: 0) } }
    val top = maxOf(goal, week.maxOf { it.second }, 1)
    MetroText(stringResource(R.string.steps_week), size = 18.sp, color = colors.subtle, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(110.dp).testTag("steps:week")) {
        val slot = size.width / week.size
        week.forEachIndexed { i, (_, n) ->
            val h = size.height * n / top
            drawRect(if (goal > 0 && n >= goal) accent else colors.subtle.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(i * slot + slot * 0.2f, size.height - h), androidx.compose.ui.geometry.Size(slot * 0.6f, h))
        }
        if (goal > 0) {
            val y = size.height - size.height * goal / top
            drawLine(colors.foreground.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
    }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        week.forEach { (date, _) ->
            MetroText(
                date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()), size = 12.sp, weight = FontWeight.Normal,
                color = colors.subtle, maxLines = 1, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
    val logged = week.filter { it.second > 0 }
    if (logged.isNotEmpty()) {
        PageNote(
            stringResource(R.string.steps_stats, logged.sumOf { it.second } / logged.size, logged.maxOf { it.second }),
            Modifier.padding(top = 10.dp).testTag("steps:stats"),
        )
    }
}

/** Where the car was left: the street, how long ago, a walk back to it, and a button to save the place where the phone is now. */
@Composable
fun ParkingPage(
    label: String,
    car: ParkedCar?,
    allowed: Boolean,
    onAllow: () -> Unit,
    park: suspend () -> ParkedCar?,
    onWalk: (ParkedCar) -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var busy by remember { mutableStateOf(false) }
    var missed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PageFrame(stringResource(R.string.parking_header), label, modifier) {
        if (!allowed) {
            PageNote(stringResource(R.string.parking_need_permission))
            MetroButton(stringResource(R.string.where_allow), Modifier.padding(top = 14.dp).testTag("parking:allow"), onClick = onAllow)
            return@PageFrame
        }
        if (car != null) {
            MetroText(car.address ?: stringResource(R.string.where_no_address), size = 28.sp, maxLines = 4, lineHeight = 34.sp, modifier = Modifier.testTag("parking:address"))
            PageNote(
                android.text.format.DateUtils.getRelativeTimeSpanString(car.savedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString(),
                Modifier.padding(top = 8.dp).testTag("parking:ago"),
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(stringResource(R.string.parking_walk), Modifier.weight(1f).testTag("parking:walk")) { onWalk(car) }
                MetroButton(stringResource(R.string.parking_forget), Modifier.weight(1f).testTag("parking:forget"), onClick = onForget)
            }
        } else {
            PageNote(stringResource(R.string.parking_none), Modifier.testTag("parking:none"))
        }
        if (busy) PageNote(stringResource(R.string.where_searching), Modifier.padding(top = 12.dp))
        if (missed) PageNote(stringResource(R.string.where_missed), Modifier.padding(top = 12.dp))
        MetroButton(
            stringResource(if (car == null) R.string.parking_here else R.string.parking_here_again),
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp).testTag("parking:here"),
        ) {
            if (!busy) {
                busy = true
                missed = false
                scope.launch {
                    missed = park() == null
                    busy = false
                }
            }
        }
    }
}
