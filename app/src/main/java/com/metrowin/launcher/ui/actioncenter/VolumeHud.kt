package com.metrowin.launcher.ui.actioncenter

import com.metrowin.launcher.ui.anim.metroTween
import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.VolumeHud
import com.metrowin.launcher.data.VolumeRow
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.FontScaleAtMost
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroSlider
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.panel
import com.metrowin.launcher.model.Glyph
import kotlinx.coroutines.delay

/** How long the panel stays after the last key, and after the last touch when it is opened out. */
const val VOLUME_HUD_MS = 2_400L
const val VOLUME_HUD_EXPANDED_MS = 5_000L

/**
 * The volume panel of Windows Phone, drawn over the launcher when a volume key is pressed: what the key moved (the call, the
 * music with the name of the app that plays, the ringer), where it is, and a slider to set it. A touch on the arrow opens out the
 * other volumes. It is drawn in the look in use (the flat panel of Classic, the frosted glass, the tonal card of Droid), and goes
 * by itself a moment after the last key.
 */
@Composable
fun VolumeHudPanel(
    hud: VolumeHud,
    onSet: (VolumeRow, Float) -> Unit,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReduceMotion.current
    val shown = remember { Animatable(0f) }
    val currentDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) { shown.animateTo(1f, metroTween(durationMillis = if (reduced) 60 else 200, easing = Motion.Decelerate)) }
    // Every key press (a new token) and every touch of a slider starts the wait again.
    LaunchedEffect(hud.token, hud.expanded) {
        delay(if (hud.expanded) VOLUME_HUD_EXPANDED_MS else VOLUME_HUD_MS)
        currentDismiss()
    }
    val colors = LocalMetroColors.current
    // A panel that comes and goes in a moment is not drawn with the biggest text: the name of the app would not fit beside the level.
    FontScaleAtMost(VOLUME_FONT_SCALE) {
        Column(
            modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = -(1f - shown.value) * 120.dp.toPx(); alpha = shown.value }
                .panel()
                .testTag("volume:hud")
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VolumeLine(hud.main, hud.app, main = true, expanded = hud.expanded, onSet = onSet, onToggle = onToggle)
            if (hud.expanded) {
                hud.others.forEach { row -> VolumeLine(row, null, main = false, expanded = true, onSet = onSet, onToggle = {}) }
                if (hud.main.stream == com.metrowin.launcher.data.VolumeStream.Music && hud.app != null) {
                    MetroText(
                        stringResource(R.string.volume_local_note), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle,
                        maxLines = Int.MAX_VALUE, lineHeight = 16.sp, modifier = Modifier.testTag("volume:note"),
                    )
                }
            }
        }
    }
}

/** The text of the volume panel is not drawn bigger than this, however big the text of the phone is. */
private const val VOLUME_FONT_SCALE = 1.3f

@Composable
private fun VolumeLine(row: VolumeRow, app: String?, main: Boolean, expanded: Boolean, onSet: (VolumeRow, Float) -> Unit, onToggle: () -> Unit) {
    val colors = LocalMetroColors.current
    val name = row.stream?.let { stringResource(it.label) } ?: stringResource(R.string.volume_media)
    val title = when {
        row.remotePackage != null -> stringResource(R.string.volume_remote, app ?: name)
        app != null -> stringResource(R.string.volume_of_app, app, name)
        else -> name
    }
    val state = when {
        row.stream == com.metrowin.launcher.data.VolumeStream.Ring && row.ringerMode == AudioManager.RINGER_MODE_VIBRATE -> stringResource(R.string.volume_vibrate)
        row.stream == com.metrowin.launcher.data.VolumeStream.Ring && row.ringerMode == AudioManager.RINGER_MODE_SILENT -> stringResource(R.string.volume_silent)
        row.muted -> stringResource(R.string.volume_muted)
        else -> null
    }
    val tag = row.stream?.name?.lowercase() ?: "remote"
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(row.stream?.glyph ?: Glyph.Music, if (main) 28.dp else 22.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                MetroText(title, size = if (main) 19.sp else 16.sp, maxLines = 3, modifier = Modifier.testTag("volume:title:$tag"))
                if (state != null) MetroText(state, size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.testTag("volume:state:$tag"))
            }
            if (row.max > row.min) {
                MetroText(
                    "${row.level}", size = if (main) 30.sp else 22.sp, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("volume:level:$tag"),
                )
            }
            if (main) {
                val description = stringResource(if (expanded) R.string.volume_less else R.string.volume_more)
                Box(
                    Modifier.size(44.dp).testTag("volume:expand").semantics { contentDescription = description }.metroPress(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    // A chevron drawn, not typed: it does not depend on the font.
                    val tint = colors.foreground
                    Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
                        val stroke = 2.5.dp.toPx()
                        val tipY = if (expanded) 0f else size.height
                        val baseY = if (expanded) size.height else 0f
                        drawLine(tint, Offset(0f, baseY), Offset(size.width / 2f, tipY), strokeWidth = stroke)
                        drawLine(tint, Offset(size.width / 2f, tipY), Offset(size.width, baseY), strokeWidth = stroke)
                    }
                }
            }
        }
        // A player that only takes steps (no level to show) has no slider.
        if (row.max > row.min) {
            MetroSlider(
                value = row.fraction, onChange = { onSet(row, it) },
                description = stringResource(R.string.volume_slider, name), modifier = Modifier.testTag("volume:slider:$tag"),
            )
        }
    }
}
