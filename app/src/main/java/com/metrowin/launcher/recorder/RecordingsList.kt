package com.metrowin.launcher.recorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.SelectableRow
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the list shows and the ways to act on it. */
internal class ListState(
    val recs: List<Rec>,
    val loaded: Boolean,
    val picked: Set<String>,
    val query: String,
    val label: String,
    val recording: Boolean,
    val elapsed: Long,
    val importing: Pair<Int, Int>?,
    val canUndo: Boolean,
    val playingName: String?,
)

/** The second page: the recordings, searched and ordered, each with what it is, and the small player below. */
@Composable
internal fun RecordingsPage(
    state: ListState,
    opts: RecorderOptions,
    onQuery: (String) -> Unit,
    onLabel: (String) -> Unit,
    onSort: () -> Unit,
    onOpen: (Rec) -> Unit,
    onPick: (Rec) -> Unit,
    onGoRecord: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val sort = opts[Opt.SORT]
    val shown = RecorderFormat.arrange(state.recs, state.query, sort, state.label)
    val pretty = opts.on(Opt.PRETTY)
    val rich = opts.on(Opt.RICH_ROWS)
    val labels = if (opts.on(Opt.LABELS)) state.recs.map { it.meta.label }.filter { it.isNotEmpty() }.distinct().sorted() else emptyList()
    Column(modifier.fillMaxSize()) {
        state.importing?.let { (done, total) ->
            com.metrowin.launcher.ui.components.MetroProgressBar(Modifier.padding(vertical = 6.dp).testTag("rec:importing"))
            PageNote(stringResource(R.string.rec_importing, done + 1, total), Modifier.padding(bottom = 4.dp))
        }
        if (state.canUndo) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PageNote(stringResource(R.string.rec_deleted), Modifier.weight(1f))
            MetroButton(stringResource(R.string.rec_undo), Modifier.testTag("rec:undo"), onClick = onUndo)
        }
        if (state.recs.size > 1 || state.query.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { MetroTextField(state.query, onQuery, stringResource(R.string.rec_search), Modifier.testTag("rec:search")) }
            MetroText(
                stringArray(R.array.rec_sorts)[sort.coerceIn(0, 3)], size = 15.sp, weight = FontWeight.Normal, color = LocalAccent.current,
                modifier = Modifier.padding(start = 12.dp).metroPress(onClick = onSort).testTag("rec:sort"),
            )
        }
        if (labels.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.rec_all), Modifier.metroPress(onClick = { onLabel("") }), filled = state.label.isEmpty())
            labels.forEach { l -> Chip(l, Modifier.metroPress(onClick = { onLabel(if (state.label == l) "" else l) }), filled = state.label == l) }
        }
        if (state.recording) Row(
            Modifier.fillMaxWidth().metroPress(onClick = onGoRecord).padding(vertical = 10.dp).testTag("rec:live-row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordingDot(Modifier.padding(end = 12.dp))
            MetroText(stringResource(R.string.rec_in_progress), size = 21.sp, weight = FontWeight.Normal, color = RecordRed, modifier = Modifier.weight(1f))
            MetroText(TimerFormat.stopwatch(state.elapsed), size = 18.sp, weight = FontWeight.Normal, color = colors.subtle)
        }
        if (state.loaded && state.recs.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MicIllustration(Modifier.padding(bottom = 12.dp))
                PageNote(stringResource(R.string.rec_empty_guide), Modifier.testTag("rec:empty"))
            }
        } else if (state.loaded && shown.isEmpty()) PageNote(stringResource(R.string.rec_no_match), Modifier.padding(top = 12.dp))
        val listState = rememberLazyListState()
        val grouped = opts.on(Opt.GROUP_DAYS) && sort <= 1
        var lastDay = ""
        LazyColumn(Modifier.weight(1f).fillMaxWidth().scrollIndicator(listState).testTag("rec:list"), state = listState) {
            itemsIndexed(shown, key = { _, r -> r.name }) { index, rec ->
                val day = RecorderFormat.dayOf(rec.name)
                if (grouped && (index == 0 || RecorderFormat.dayOf(shown[index - 1].name) != day)) {
                    MetroText(dayLabel(context, day), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = if (index == 0) 4.dp else 14.dp, bottom = 2.dp))
                }
                val chosen = rec.name in state.picked
                SelectableRow(
                    selecting = state.picked.isNotEmpty(), checked = chosen, onClick = { if (state.picked.isNotEmpty()) onPick(rec) else if (!state.recording) onOpen(rec) },
                    onLongClick = { onPick(rec) }, modifier = Modifier.featherRow(index).testTag("rec:row").padding(vertical = if (rich) 8.dp else 10.dp),
                ) {
                    RecRowBody(rec, chosen, state.playingName == rec.name, pretty, rich, opts.on(Opt.LABELS), state.query)
                }
            }
        }
    }
}

@Composable
private fun RecRowBody(rec: Rec, chosen: Boolean, playing: Boolean, pretty: Boolean, rich: Boolean, labels: Boolean, query: String) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val classic = LocalStyle.current.isClassic
    val title = RecorderFormat.title(rec.name, pretty)
    val format = rec.file.extension.lowercase()
    if (!rich) {
        MetroText(
            (if (chosen && !classic) "✓ " else "") + title + (if (format != "m4a") "  ·  " + format.uppercase() else ""),
            size = 21.sp, weight = FontWeight.Normal,
        )
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The format in a square (a circle in the other looks): the picture of a file of sound; a recording that plays has the accent.
        Box(
            Modifier.size(44.dp).then(if (playing) Modifier.background(accent, if (classic) androidx.compose.ui.graphics.RectangleShape else CircleShape) else Modifier.border(2.dp, colors.subtle, if (classic) androidx.compose.ui.graphics.RectangleShape else CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            MetroText(format.uppercase().take(4), size = 12.sp, weight = FontWeight.SemiBold, color = if (playing) androidx.compose.ui.graphics.Color.White else colors.subtle, maxLines = 1)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            MetroText(title, size = 20.sp, weight = FontWeight.Normal, maxLines = 1)
            val line = TimerFormat.stopwatch(rec.durationMs) + "  ·  " + RecorderFormat.size(rec.size)
            val found = RecorderFormat.snippet(rec.meta.transcript, query)
            MetroText(if (found.isEmpty()) line else found, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
        }
        Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (rec.meta.transcript.isNotEmpty() || rec.meta.noteId.isNotEmpty()) Chip(stringResource(R.string.rec_chip_text))
            if (rec.meta.made.isNotEmpty()) Chip(madeLabel(rec.meta.made))
            if (labels && rec.meta.label.isNotEmpty()) Chip(rec.meta.label, filled = true)
            AnimatedVisibility(chosen && !classic, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Box(Modifier.size(26.dp).background(accent, CircleShape).semantics { contentDescription = "✓" }, contentAlignment = Alignment.Center) {
                    MetroText("✓", size = 16.sp, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
internal fun madeLabel(kind: String): String = stringResource(
    when (kind) {
        "cut" -> R.string.rec_chip_cut
        "normalised" -> R.string.rec_chip_loud
        "merged" -> R.string.rec_chip_merged
        "faded" -> R.string.rec_chip_faded
        else -> R.string.rec_chip_edited
    },
)

@Composable
private fun stringArray(id: Int): Array<String> = LocalContext.current.resources.getStringArray(id)

/** "Today", "Yesterday", or the date. */
private fun dayLabel(context: android.content.Context, day: String): String {
    val date = RecorderFormat.dayDate(day) ?: return day
    val today = LocalDate.now()
    return when (date) {
        today -> context.getString(R.string.rec_today)
        today.minusDays(1) -> context.getString(R.string.rec_yesterday)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    }
}

/** The small player at the bottom of the list: what plays, and the buttons to hold it, to go to the next and to close it. */
@Composable
internal fun MiniPlayer(player: RecorderPlayer, pretty: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val file = player.file ?: return
    val colors = LocalMetroColors.current
    Row(
        modifier.fillMaxWidth().background(colors.containers.getOrElse(0) { colors.surface }).padding(horizontal = 14.dp, vertical = 8.dp).testTag("rec:mini"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f).metroPress(onClick = onOpen)) {
            MetroText(RecorderFormat.title(file.name, pretty), size = 17.sp, weight = FontWeight.Normal, maxLines = 1)
            MetroText(TimerFormat.stopwatch(player.position) + " / " + TimerFormat.stopwatch(player.duration), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
        }
        if (player.hasPrevious()) GlyphButton(Glyph.Previous, stringResource(R.string.rec_previous), size = 44.dp, onClick = { player.previous() })
        GlyphButton(if (player.playing) Glyph.Pause else Glyph.Play, stringResource(if (player.playing) R.string.rec_pause else R.string.rec_play), Modifier.testTag("rec:mini:play"), size = 44.dp) { player.toggle() }
        if (player.hasNext()) GlyphButton(Glyph.Next, stringResource(R.string.rec_next), size = 44.dp, onClick = { player.next() })
        MetroText("✕", size = 22.sp, modifier = Modifier.metroPress(onClick = { player.release() }).padding(8.dp).semantics { contentDescription = "close" })
    }
}

/** The name a recording gets from its stamp: kept for the callers that need the plain title. */
internal fun plainTitle(name: String) = Recordings.title(name)
