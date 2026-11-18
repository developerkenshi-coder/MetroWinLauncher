package com.metrowin.launcher.recorder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.TimerFormat
import com.metrowin.launcher.ui.components.ChoiceRow
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun recorderLabel(opt: Opt): Int = when (opt) {
    Opt.PAUSE -> R.string.rec_opt_pause
    Opt.MARKS -> R.string.rec_opt_marks
    Opt.FOCUS -> R.string.rec_opt_focus
    Opt.SAFE -> R.string.rec_opt_safe
    Opt.LEVEL -> R.string.rec_opt_level
    Opt.SPEED -> R.string.rec_opt_speed
    Opt.SEEK -> R.string.rec_opt_seek
    Opt.NUDGE -> R.string.rec_opt_nudge
    Opt.CUT_ANY -> R.string.rec_opt_cut_any
    Opt.TOOLS -> R.string.rec_opt_tools
    Opt.SPECTRO -> R.string.rec_opt_spectro
    Opt.KEEP_TEXT -> R.string.rec_opt_keep_text
    Opt.SUMMARY -> R.string.rec_opt_summary
    Opt.LABELS -> R.string.rec_opt_labels
    Opt.BACKUP -> R.string.rec_opt_backup
    Opt.ROUND -> R.string.rec_opt_round
    Opt.RICH_ROWS -> R.string.rec_opt_rich_rows
    Opt.PRETTY -> R.string.rec_opt_pretty
    Opt.GROUP_DAYS -> R.string.rec_opt_group_days
    Opt.TRANSITIONS -> R.string.rec_opt_transitions
    Opt.MINI -> R.string.rec_opt_mini
    Opt.WAVE_THEME -> R.string.rec_opt_wave_theme
    Opt.TWO_PANE -> R.string.rec_opt_two_pane
    Opt.CONTRAST -> R.string.rec_opt_contrast
    Opt.START_OPEN -> R.string.rec_opt_start_open
    Opt.VOLUME_MARK -> R.string.rec_opt_volume_mark
    Opt.QUALITY, Opt.MAX_MIN, Opt.SORT -> R.string.rec_options
}

/** The switches of the recorder (the menu "…"): what records, plays, edits, writes down, keeps and looks like. The first of a group is its name. */
@Composable
internal fun OptionsDialog(opts: RecorderOptions, onClose: () -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            opts.backupTree = uri.toString()
            opts[Opt.BACKUP] = 1
        }
    }
    DialogFrame(onCancel = onClose, title = stringResource(R.string.rec_options)) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Group(R.string.rec_group_record)
            Toggles(opts, Opt.PAUSE, Opt.MARKS, Opt.FOCUS, Opt.LEVEL, Opt.SAFE, Opt.START_OPEN, Opt.VOLUME_MARK)
            SettingLabel(stringResource(R.string.rec_opt_quality), Modifier.padding(top = 8.dp))
            ChoiceRow(context.resources.getStringArray(R.array.rec_qualities).toList(), opts[Opt.QUALITY], { opts[Opt.QUALITY] = it })
            SettingLabel(stringResource(R.string.rec_opt_max), Modifier.padding(top = 8.dp))
            ChoiceRow(context.resources.getStringArray(R.array.rec_limits).toList(), opts[Opt.MAX_MIN], { opts[Opt.MAX_MIN] = it })
            Group(R.string.rec_group_play)
            Toggles(opts, Opt.SPEED, Opt.SEEK, Opt.MINI)
            Group(R.string.rec_group_edit)
            Toggles(opts, Opt.NUDGE, Opt.CUT_ANY, Opt.TOOLS, Opt.SPECTRO)
            Group(R.string.rec_group_text)
            Toggles(opts, Opt.KEEP_TEXT, Opt.SUMMARY)
            Group(R.string.rec_group_library)
            Toggles(opts, Opt.LABELS)
            MetroToggle(stringResource(R.string.rec_opt_backup), opts.on(Opt.BACKUP), { on -> if (on && opts.backupTree == null) picker.launch(null) else opts[Opt.BACKUP] = if (on) 1 else 0 }, Modifier.testTag("rec:opt:backup"))
            if (opts.on(Opt.BACKUP)) MetroButton(stringResource(R.string.rec_backup_folder), Modifier.fillMaxWidth()) { picker.launch(null) }
            PageNote(stringResource(R.string.rec_backup_note))
            Group(R.string.rec_group_look)
            Toggles(opts, Opt.ROUND, Opt.RICH_ROWS, Opt.PRETTY, Opt.GROUP_DAYS, Opt.TRANSITIONS, Opt.WAVE_THEME, Opt.TWO_PANE, Opt.CONTRAST)
        }
    }
}

@Composable
private fun Toggles(opts: RecorderOptions, vararg list: Opt) {
    list.forEach { o -> MetroToggle(stringResource(recorderLabel(o)), opts.on(o), { opts.toggle(o) }, Modifier.testTag("rec:opt:" + o.key)) }
}

@Composable
private fun Group(title: Int) {
    MetroText(stringResource(title), size = 17.sp, weight = FontWeight.Normal, color = LocalAccent.current, modifier = Modifier.padding(top = 14.dp))
}

/** How much has been recorded: the number, the hours, the space, and the hours of each of the last eight weeks as bars. */
@Composable
internal fun StatsDialog(recs: List<Rec>, onClose: () -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val total = recs.sumOf { it.durationMs }
    val today = LocalDate.now()
    val weeks = LongArray(8)
    recs.forEach { r ->
        val day = RecorderFormat.dayDate(RecorderFormat.dayOf(r.name)) ?: return@forEach
        val ago = (ChronoUnit.DAYS.between(day, today) / 7).toInt()
        if (ago in 0..7) weeks[7 - ago] += r.durationMs
    }
    DialogFrame(onCancel = onClose, title = stringResource(R.string.rec_stats)) {
        MetroText(androidx.compose.ui.res.pluralStringResource(R.plurals.rec_stats_count, recs.size, recs.size), size = 20.sp, weight = FontWeight.Normal)
        MetroText(stringResource(R.string.rec_stats_total, TimerFormat.length(total), RecorderFormat.size(recs.sumOf { it.size })), size = 17.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 4.dp))
        MetroText(stringResource(R.string.rec_stats_weeks), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        val top = (weeks.max()).coerceAtLeast(1L)
        Canvas(Modifier.fillMaxWidth().height(90.dp).testTag("rec:stats")) {
            val slot = size.width / weeks.size
            weeks.forEachIndexed { i, v ->
                val h = (v.toFloat() / top * size.height).coerceAtLeast(if (v > 0) 3f else 1f)
                drawRect(accent, Offset(i * slot + slot * 0.15f, size.height - h), Size(slot * 0.7f, h))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MetroText(stringResource(R.string.rec_stats_old), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle)
            MetroText(stringResource(R.string.rec_stats_now), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle)
        }
    }
}
