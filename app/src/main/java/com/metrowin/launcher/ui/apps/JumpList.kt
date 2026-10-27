package com.metrowin.launcher.ui.apps

import com.metrowin.launcher.ui.anim.metroTween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.components.GlyphIcon
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.delay

private const val COLUMNS = 4

/** The cell that is not a letter: the last app opened. */
const val RECENT_KEY = "recent"

/**
 * The jump list: every letter in a grid, the ones that have apps in the accent colour. It opens
 * when a letter header of the app list is tapped, and the squares flip in one after the other.
 */
@Composable
fun JumpList(
    keys: List<String>,
    available: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** The star, first in the grid, takes you to the app opened last. It is off when there is none. */
    hasRecent: Boolean = false,
    onPickRecent: () -> Unit = {},
) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    Box(
        modifier
            .fillMaxSize()
            .background(LocalMetroColors.current.background.copy(alpha = 0.94f))
            .pointerInput(Unit) { detectTapGestures { currentDismiss() } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            (listOf(RECENT_KEY) + keys).chunked(COLUMNS).forEachIndexed { rowIndex, rowKeys ->
                Row(Modifier.fillMaxWidth()) {
                    rowKeys.forEachIndexed { colIndex, key ->
                        JumpCell(
                            key = key,
                            enabled = if (key == RECENT_KEY) hasRecent else key in available,
                            order = rowIndex * COLUMNS + colIndex,
                            onPick = if (key == RECENT_KEY) ({ onPickRecent() }) else onPick,
                            modifier = Modifier.weight(1f).padding(3.dp),
                        )
                    }
                    repeat(COLUMNS - rowKeys.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun JumpCell(key: String, enabled: Boolean, order: Int, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val away = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(order * 16L)
        away.animateTo(0f, metroTween(durationMillis = 300, easing = Motion.Decelerate))
    }
    Box(
        modifier
            .aspectRatio(1f)
            .testTag("jump:$key")
            .graphicsLayer {
                // Each square flips up around its top edge.
                transformOrigin = TransformOrigin(0.5f, 0f)
                rotationX = 90f * away.value
                cameraDistance = Motion.CAMERA * density
                alpha = 1f - away.value
            }
            .then(if (enabled) Modifier.metroPress(onClick = { onPick(key) }) else Modifier)
            .litFill(enabled),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) onAccent() else colors.subtle.copy(alpha = 0.5f)
        if (key == RECENT_KEY) GlyphIcon(Glyph.Star, 30.dp, color = tint) else MetroText(key.lowercase(), size = 30.sp, color = tint)
    }
}
