package com.metrowin.launcher.ui.voice

import androidx.compose.animation.core.animateFloatAsState
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
                        stringResource(R.string.voice_listening), size = 40.sp, maxLines = 2,
                        modifier = Modifier.testTag("voice:title"),
                    )
                    if (state.heard.isEmpty()) {
                        MetroText(
                            stringResource(R.string.voice_hint), size = 18.sp, color = colors.subtle, maxLines = 4,
                            modifier = Modifier.testTag("voice:hint"),
                        )
                    } else {
                        MetroText(state.heard, size = 30.sp, color = accent, maxLines = 4, modifier = Modifier.testTag("voice:heard"))
                    }
                }
                is VoiceUiState.Understood -> {
                    MetroText(stringResource(R.string.voice_heard), size = 18.sp, color = colors.subtle)
                    MetroText(state.heard, size = 30.sp, color = accent, maxLines = 4, modifier = Modifier.testTag("voice:heard"))
                }
                is VoiceUiState.Problem -> {
                    val text = if (state.argument != null) stringResource(state.message, state.argument) else stringResource(state.message)
                    MetroText(text, size = 28.sp, maxLines = 5, modifier = Modifier.testTag("voice:message"))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetroButton(stringResource(R.string.voice_try_again), Modifier.testTag("voice:retry"), onRetry)
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
            val level by animateFloatAsState((state as? VoiceUiState.Listening)?.level ?: 0f, tween(90), label = "voiceLevel")
            val listening = state is VoiceUiState.Listening
            Box(
                Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        val scale = 1f + level * MIC_GROWTH
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .border(3.dp, if (listening) accent else colors.subtle, CircleShape)
                    .metroPress(onClick = { if (!listening) onRetry() })
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
