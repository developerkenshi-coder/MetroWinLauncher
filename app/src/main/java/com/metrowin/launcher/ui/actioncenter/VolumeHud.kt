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
import com.metrowin.launcher.ui.components.topBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.metrowin.launcher.notifications.rememberMediaInfo
import com.metrowin.launcher.ui.start.Transport
import com.metrowin.launcher.ui.start.TransportButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import kotlin.math.roundToInt
import com.metrowin.launcher.ui.theme.LocalAccent
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
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
    /** The ringer key: ringing or vibrating. */
    onRinger: () -> Unit = {},
) {
    val reduced = LocalReduceMotion.current
    val shown = remember { Animatable(0f) }
    val currentDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) { shown.animateTo(1f, metroTween(durationMillis = if (reduced) 60 else 200, easing = Motion.Decelerate)) }
    // Every key press (a new token) and every touch of a slider starts the wait again.
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(hud.token, hud.expanded, touches) {
        delay(if (hud.expanded) VOLUME_HUD_EXPANDED_MS else VOLUME_HUD_MS)
        currentDismiss()
    }
    val colors = LocalMetroColors.current
    // A panel that comes and goes in a moment is not drawn with the biggest text: the name of the app would not fit beside the level.
    FontScaleAtMost(VOLUME_FONT_SCALE) {
        Column(
            modifier
                .topBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = -(1f - shown.value) * 120.dp.toPx(); alpha = shown.value }
                .panel()
                .testTag("volume:hud")
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VolumeLine(hud.main, hud.app, main = true, expanded = hud.expanded, onSet = onSet, onToggle = onToggle, onRinger = onRinger)
            // Music that an app plays: skip and pause from here, without leaving what is on the screen.
            if (hud.main.stream == com.metrowin.launcher.data.VolumeStream.Music && hud.app != null) MediaKeys { touches++ }
            if (hud.expanded) {
                hud.others.forEach { row -> VolumeLine(row, null, main = false, expanded = true, onSet = onSet, onToggle = {}, onRinger = onRinger) }
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

@Composable
private fun MediaKeys(onTouch: () -> Unit) {
    val media = rememberMediaInfo(true) ?: return
    val ink = LocalMetroColors.current.foreground
    Row(Modifier.fillMaxWidth().testTag("volume:media"), horizontalArrangement = Arrangement.Center) {
        TransportButton(Transport.Previous, "volume:prev", ink, stringResource(R.string.lock_music_previous)) { media.previous(); onTouch() }
        TransportButton(
            if (media.playing) Transport.Pause else Transport.Play, "volume:toggle", ink,
            stringResource(if (media.playing) R.string.lock_music_pause else R.string.lock_music_play),
        ) { media.playPause(); onTouch() }
        TransportButton(Transport.Next, "volume:next", ink, stringResource(R.string.lock_music_next)) { media.next(); onTouch() }
    }
}

/** The text of the volume panel is not drawn bigger than this, however big the text of the phone is. */
private const val VOLUME_FONT_SCALE = 1.3f

@Composable
private fun VolumeLine(row: VolumeRow, app: String?, main: Boolean, expanded: Boolean, onSet: (VolumeRow, Float) -> Unit, onToggle: () -> Unit, onRinger: () -> Unit) {
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
                    Modifier.size(48.dp).testTag("volume:expand").semantics { contentDescription = description }.metroPress(onClick = onToggle),
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
        // The strip of ticks of the panel of Windows Phone: one for each step, the ones up to the level in the colour of the accent.
        if (row.max > row.min) VolumeTicks(row.level - row.min, row.max - row.min, Modifier.testTag("volume:ticks:$tag"))
        // The key of the ringer: touched, the phone goes from ringing to vibrating and back.
        if (row.stream == com.metrowin.launcher.data.VolumeStream.Ring) {
            val vibrating = row.ringerMode != AudioManager.RINGER_MODE_NORMAL
            val word = stringResource(if (vibrating) R.string.volume_vibrate else R.string.volume_ring)
            MetroText(
                word, size = 16.sp, color = LocalAccent.current, maxLines = 1,
                modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).testTag("volume:ringer").metroPress(onClick = onRinger),
            )
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

/** [steps] small ticks in a row, [lit] of them filled with the accent: the level of a volume as the strip of Windows Phone shows it. A long scale is drawn in 30 ticks at most. */
@Composable
private fun VolumeTicks(lit: Int, steps: Int, modifier: Modifier = Modifier) {
    val on = LocalAccent.current
    val off = LocalMetroColors.current.inactive
    val count = steps.coerceIn(1, 30)
    val filled = if (steps > count) (lit.toFloat() / steps * count).roundToInt() else lit
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) drawRect(if (i < filled) on else off, Offset(i * (w + gap), 0f), androidx.compose.ui.geometry.Size(w, size.height))
    }
}
