package com.metrowin.launcher.recorder

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.VoiceRecorder
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** What the page of the recording shows, all of it read from the screen: the page itself keeps nothing. */
internal class RecordState(
    val allowed: Boolean,
    val denied: Boolean,
    val recording: Boolean,
    val paused: Boolean,
    val elapsed: Long,
    val levels: List<Float>,
    val level: Float,
    val peak: Float,
    val marks: Int,
    val busy: Boolean,
    val failure: Int,
    val notice: Int,
)

/** The first page: the time, the sound as it comes, and the buttons. Scrolls, and puts the buttons beside the time when the screen is wide. */
@Composable
internal fun RecordPage(
    state: RecordState,
    opts: RecorderOptions,
    onAllow: () -> Unit,
    onSettings: () -> Unit,
    onToggle: () -> Unit,
    onPause: () -> Unit,
    onMark: () -> Unit,
) {
    val colors = LocalMetroColors.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!state.allowed) {
            MicIllustration(Modifier.padding(bottom = 14.dp), crossed = state.denied)
            PageNote(stringResource(if (state.denied) R.string.rec_denied else R.string.recorder_need_permission))
            MetroButton(
                stringResource(if (state.denied) R.string.rec_open_settings else R.string.recorder_allow),
                Modifier.padding(top = 14.dp).testTag("rec:allow"),
            ) { if (state.denied) onSettings() else onAllow() }
            return@Column
        }
        val info: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.recording) RecordingDot(Modifier.padding(end = 14.dp), steady = state.paused)
                    RecTimer(if (state.recording) state.elapsed else 0L, if (landscape) 60.sp else 80.sp, Modifier.testTag("rec:time"))
                }
                // A word beside the dot: the blink alone does not say it to those who do not see it, or who have motion off.
                if (state.recording) MetroText(stringResource(if (state.paused) R.string.rec_state_paused else R.string.rec_state_recording), size = 16.sp, color = colors.subtle, modifier = Modifier.padding(top = 2.dp).testTag("rec:state"))
                if (state.recording) LiveWave(state.levels, Modifier.fillMaxWidth().height(72.dp).padding(top = 12.dp).testTag("rec:live"), paused = state.paused)
                if (state.recording && opts.on(Opt.LEVEL)) LevelMeter(state.level, state.peak, Modifier.fillMaxWidth().height(8.dp).padding(top = 0.dp).testTag("rec:meter"))
                if (state.recording && state.marks > 0) PageNote(androidx.compose.ui.res.pluralStringResource(R.plurals.rec_marks, state.marks, state.marks), Modifier.padding(top = 6.dp))
                if (state.failure == VoiceRecorder.FAIL_SPACE) PageNote(stringResource(R.string.rec_no_space), Modifier.padding(top = 8.dp).testTag("rec:failed"))
                else if (state.failure == VoiceRecorder.FAIL_MIC) PageNote(stringResource(R.string.recorder_failed), Modifier.padding(top = 8.dp).testTag("rec:failed"))
                if (!state.recording && state.notice == VoiceRecorder.NOTICE_SPACE) PageNote(stringResource(R.string.rec_stopped_space), Modifier.padding(top = 8.dp))
                if (!state.recording && state.notice == VoiceRecorder.NOTICE_LIMIT) PageNote(stringResource(R.string.rec_stopped_limit), Modifier.padding(top = 8.dp))
            }
        }
        val controls: @Composable () -> Unit = {
            val toggleText = stringResource(if (state.recording) R.string.recorder_stop else R.string.recorder_start)
            val canPause = state.recording && (opts.on(Opt.PAUSE) || state.paused)
            val canMark = state.recording && opts.on(Opt.MARKS)
            if (opts.on(Opt.ROUND)) {
                Row(Modifier.padding(top = if (landscape) 0.dp else 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        if (canMark) GlyphButton(Glyph.Flag, stringResource(R.string.rec_mark), Modifier.testTag("rec:mark"), onClick = onMark)
                    }
                    RecordButton(state.recording, toggleText, Modifier.testTag("rec:toggle"), onClick = onToggle)
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                        if (canPause) GlyphButton(if (state.paused) Glyph.Play else Glyph.Pause, stringResource(if (state.paused) R.string.rec_resume else R.string.rec_pause_action), Modifier.testTag("rec:pause"), onClick = onPause)
                    }
                }
            } else {
                Column(Modifier.padding(top = 24.dp)) {
                    MetroButton(toggleText, Modifier.fillMaxWidth().testTag("rec:toggle"), onClick = onToggle)
                    if (canPause || canMark) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (canPause) MetroButton(stringResource(if (state.paused) R.string.rec_resume else R.string.rec_pause_action), Modifier.weight(1f).testTag("rec:pause"), onClick = onPause)
                        if (canMark) MetroButton(stringResource(R.string.rec_mark), Modifier.weight(1f).testTag("rec:mark"), onClick = onMark)
                    }
                }
            }
        }
        if (landscape) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            info(Modifier.weight(1f))
            controls()
        } else {
            info(Modifier)
            controls()
        }
    }
}
