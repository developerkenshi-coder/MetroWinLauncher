package com.metrowin.launcher.ui.pages

import com.metrowin.launcher.ui.components.Spacing
import com.metrowin.launcher.ui.components.MetroType
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BootCount
import com.metrowin.launcher.data.TimerFormat
import com.metrowin.launcher.model.StopwatchState
import com.metrowin.launcher.model.TimerState
import com.metrowin.launcher.model.TimerStatus
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.delay

/**
 * The page of a timer: the time left, start and pause, the lengths to choose from and a length of your own. The number changes
 * once a second, and only while the page is on screen; the alarm that rings is one of Android's, set when the timer starts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerPage(
    label: String,
    state: TimerState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onLength: (Long) -> Unit,
    onAdd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedWhileStarted(state) {
        now = System.currentTimeMillis()
        while (state.status(now) == TimerStatus.Running) {
            delay(TimerFormat.untilNextCountdownTick(state.remaining(now)))
            now = System.currentTimeMillis()
        }
    }
    val status = state.status(now)
    val colors = LocalMetroColors.current
    PageFrame(stringResource(R.string.timer_page_header), label, modifier) {
        MetroText(
            TimerFormat.countdown(if (status == TimerStatus.Idle) state.durationMs else state.remaining(now)),
            size = 84.sp, maxLines = 1, modifier = Modifier.testTag("timer:page:time"),
        )
        PageNote(
            when (status) {
                TimerStatus.Idle -> stringResource(R.string.timer_page_idle, TimerFormat.length(state.durationMs))
                TimerStatus.Running -> stringResource(R.string.timer_page_running)
                TimerStatus.Paused -> stringResource(R.string.timer_paused)
                TimerStatus.Finished -> stringResource(R.string.timer_done)
            },
            Modifier.testTag("timer:page:status"),
        )
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.Large)) {
            if (status == TimerStatus.Running) {
                MetroButton(stringResource(R.string.timer_pause), Modifier.weight(1f).testTag("timer:page:pause"), onClick = onPause)
            } else {
                MetroButton(
                    stringResource(if (status == TimerStatus.Finished) R.string.timer_again else R.string.timer_start),
                    Modifier.weight(1f).testTag("timer:page:start"), onClick = onStart,
                )
            }
            MetroButton(stringResource(R.string.timer_reset), Modifier.weight(1f).testTag("timer:page:reset"), onClick = onReset)
        }
        if (status != TimerStatus.Finished) {
            FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                TimerFormat.ADDITIONS_MS.forEach { ms ->
                    PageChip(stringResource(R.string.timer_add, (ms / 60_000L).toInt()), false, "timer:page:add:${ms / 60_000L}") { onAdd(ms) }
                }
            }
        }
        if (status != TimerStatus.Running) {
            MetroText(stringResource(R.string.timer_length), size = MetroType.Lead, color = colors.subtle, modifier = Modifier.padding(top = 22.dp, bottom = Spacing.Medium))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                TimerState.PRESET_MINUTES.forEach { minutes ->
                    val ms = minutes * 60_000L
                    PageChip(
                        if (minutes >= 60) stringResource(R.string.timer_preset_hours, minutes / 60) else stringResource(R.string.timer_preset_minutes, minutes),
                        state.durationMs == ms, "timer:page:preset:$minutes",
                    ) { onLength(ms) }
                }
            }
            CustomLength(onLength)
        }
        PageNote(stringResource(R.string.timer_page_battery), Modifier.padding(top = Spacing.Page, bottom = Spacing.Page))
    }
}

/** Hours, minutes and seconds typed by hand. */
@Composable
private fun CustomLength(onLength: (Long) -> Unit) {
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("") }
    val parsed = TimerFormat.parseLength(hours, minutes, seconds)
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            MetroTextField(hours, { hours = it.filter(Char::isDigit).take(3) }, stringResource(R.string.timer_hours), Modifier.testTag("timer:page:hours"), keyboardType = KeyboardType.Number)
        }
        Column(Modifier.weight(1f)) {
            MetroTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, stringResource(R.string.timer_minutes), Modifier.testTag("timer:page:minutes"), keyboardType = KeyboardType.Number)
        }
        Column(Modifier.weight(1f)) {
            MetroTextField(seconds, { seconds = it.filter(Char::isDigit).take(3) }, stringResource(R.string.timer_seconds), Modifier.testTag("timer:page:seconds"), keyboardType = KeyboardType.Number)
        }
    }
    MetroButton(
        stringResource(R.string.timer_set_length),
        Modifier.fillMaxWidth().padding(top = 10.dp).testTag("timer:page:set"),
    ) {
        if (parsed != null) {
            onLength(parsed)
            hours = ""
            minutes = ""
            seconds = ""
        }
    }
}

/** The page of a stopwatch: the time with its tenths while it runs, start, stop, lap and reset, and the laps. */
@Composable
fun StopwatchPage(
    label: String,
    state: StopwatchState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val boot = remember { BootCount.read(context) }
    var elapsed by remember { mutableLongStateOf(state.elapsed(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot)) }
    LaunchedWhileStarted(state) {
        while (true) {
            elapsed = state.elapsed(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot)
            if (!state.running) break
            // A tenth of a second: the page is in the hands of somebody who is looking at it.
            delay(TimerFormat.untilNextStopwatchTick(elapsed, 100L))
        }
    }
    val colors = LocalMetroColors.current
    PageFrame(stringResource(R.string.stopwatch_page_header), label, modifier) {
        MetroText(TimerFormat.stopwatch(elapsed, tenths = true), size = 72.sp, maxLines = 1, modifier = Modifier.testTag("stopwatch:page:time"))
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.Large)) {
            if (state.running) {
                MetroButton(stringResource(R.string.stopwatch_stop), Modifier.weight(1f).testTag("stopwatch:page:stop"), onClick = onStop)
                MetroButton(stringResource(R.string.stopwatch_lap), Modifier.weight(1f).testTag("stopwatch:page:lap"), onClick = onLap)
            } else {
                MetroButton(
                    stringResource(if (elapsed > 0L) R.string.stopwatch_resume else R.string.stopwatch_start),
                    Modifier.weight(1f).testTag("stopwatch:page:start"), onClick = onStart,
                )
                if (elapsed > 0L) MetroButton(stringResource(R.string.timer_reset), Modifier.weight(1f).testTag("stopwatch:page:reset"), onClick = onReset)
            }
        }
        if (state.laps.isNotEmpty()) {
            MetroText(
                pluralStringResource(R.plurals.stopwatch_laps, state.laps.size, state.laps.size), size = MetroType.Lead, color = colors.subtle,
                modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
            )
            val lengths = state.lapLengths()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium), modifier = Modifier.padding(bottom = Spacing.Page)) {
                state.laps.indices.reversed().forEach { i ->
                    Row(Modifier.fillMaxWidth().testTag("stopwatch:page:lap:$i"), verticalAlignment = Alignment.CenterVertically) {
                        MetroText("${i + 1}", size = MetroType.Subtitle, color = colors.subtle, modifier = Modifier.padding(end = 16.dp))
                        MetroText(TimerFormat.stopwatch(lengths[i], tenths = true), size = 22.sp, modifier = Modifier.weight(1f))
                        MetroText(TimerFormat.stopwatch(state.laps[i], tenths = true), size = MetroType.Lead, weight = FontWeight.Normal, color = colors.subtle)
                    }
                }
            }
        }
    }
}
