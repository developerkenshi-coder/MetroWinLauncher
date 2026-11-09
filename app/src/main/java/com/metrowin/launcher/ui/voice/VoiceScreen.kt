package com.metrowin.launcher.ui.voice

import com.metrowin.launcher.ui.anim.metroTween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * The screen the voice commands are spoken to: big light words on the background, what has been understood in the
 * accent colour, and a microphone that grows with the voice. When something goes wrong it says so and offers to try again.
 */
@Composable
fun VoiceScreen(
    state: VoiceUiState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onSearchWeb: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** A suggestion was touched: the phrase, to be carried out as if it had been said. */
    onSuggest: (String) -> Unit = {},
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.96f))
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("voice:screen"),
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 96.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                is VoiceUiState.Listening -> {
                    MetroText(
                        stringResource(R.string.voice_listening), size = 40.sp,
                        modifier = Modifier.testTag("voice:title"),
                    )
                    if (state.heard.isEmpty()) {
                        MetroText(
                            stringResource(R.string.voice_say), size = 18.sp, color = colors.subtle,
                            modifier = Modifier.testTag("voice:hint"),
                        )
                        Suggestions(onSuggest, Modifier.weight(1f))
                    } else {
                        MetroText(state.heard, size = 30.sp, color = accent, modifier = Modifier.testTag("voice:heard"))
                    }
                }
                is VoiceUiState.Understood -> {
                    MetroText(stringResource(R.string.voice_heard), size = 18.sp, color = colors.subtle)
                    MetroText(state.heard, size = 30.sp, color = accent, modifier = Modifier.testTag("voice:heard"))
                }
                is VoiceUiState.Problem -> {
                    val text = if (state.argument != null) stringResource(state.message, state.argument) else stringResource(state.message)
                    MetroText(text, size = 28.sp, modifier = Modifier.testTag("voice:message"))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!state.final) MetroButton(stringResource(R.string.voice_try_again), Modifier.testTag("voice:retry"), onRetry)
                        state.webQuery?.let { query ->
                            MetroButton(stringResource(R.string.voice_search_web), Modifier.testTag("voice:web")) { onSearchWeb(query) }
                        }
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val level by animateFloatAsState((state as? VoiceUiState.Listening)?.level ?: 0f, metroTween(90), label = "voiceLevel")
            val listening = state is VoiceUiState.Listening
            Box(
                Modifier
                    .size(88.dp)
                    // Rings spread from the microphone while it listens, wider and quicker with the voice.
                    .then(if (listening) Modifier.listeningRings(accent, level) else Modifier)
                    .graphicsLayer {
                        val scale = 1f + level * MIC_GROWTH
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .border(3.dp, if (listening) accent else colors.subtle, CircleShape)
                    .metroPress(onClick = { if (!listening && !(state is VoiceUiState.Problem && state.final)) onRetry() })
                    .testTag("voice:mic"),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Mic, 40.dp, color = if (listening) accent else colors.subtle)
            }
            Box(Modifier.metroPress(onClick = onCancel).padding(horizontal = 24.dp, vertical = 8.dp).testTag("voice:cancel")) {
                MetroText(stringResource(R.string.voice_cancel), size = 18.sp, weight = FontWeight.Normal, color = colors.subtle)
            }
        }
    }
}

private const val MIC_GROWTH = 0.28f

/** What can be said, by sections: a touch on a phrase says it. */
@Composable
private fun Suggestions(onSuggest: (String) -> Unit, modifier: Modifier = Modifier) {
    val sections = listOf(
        R.string.voice_sec_apps to R.string.voice_try_apps, R.string.voice_sec_people to R.string.voice_try_people,
        R.string.voice_sec_phone to R.string.voice_try_phone, R.string.voice_sec_web to R.string.voice_try_web,
    )
    Column(modifier.verticalScroll(rememberScrollState()).testTag("voice:suggestions"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        sections.forEachIndexed { index, (section, phrase) ->
            val text = stringResource(phrase)
            Column(Modifier.featherIn(index).metroPress(onClick = { onSuggest(text) }).testTag("voice:suggestion:$index")) {
                MetroText(stringResource(section).uppercase(), size = 13.sp, weight = FontWeight.SemiBold, color = LocalAccent.current)
                MetroText(text, size = 26.sp, maxLines = 2)
            }
        }
    }
}

/** Three rings that spread from the middle and fade as they go, one after the other; with reduced motion, one still ring that follows the level of the voice. */
@Composable
private fun Modifier.listeningRings(color: Color, level: Float): Modifier {
    val reduced = LocalReduceMotion.current
    val phase by rememberInfiniteTransition(label = "rings").animateFloat(0f, 1f, infiniteRepeatable(tween(2_100, easing = LinearEasing)), label = "ringPhase")
    return drawBehind {
        val base = size.minDimension / 2f
        if (reduced) {
            drawCircle(color.copy(alpha = 0.35f), base * (1.15f + level * 0.5f), style = Stroke(2.dp.toPx()))
            return@drawBehind
        }
        repeat(3) { k ->
            val p = (phase + k / 3f) % 1f
            drawCircle(color.copy(alpha = (1f - p) * 0.55f), base * (1.1f + p * (1.2f + level * 1.4f)), style = Stroke(2.dp.toPx()))
        }
    }
}
