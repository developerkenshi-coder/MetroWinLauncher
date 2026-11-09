package com.metrowin.launcher.music

import android.media.MediaMetadata
import android.media.session.MediaController
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.notifications.MediaInfo
import com.metrowin.launcher.notifications.rememberMediaInfo
import com.metrowin.launcher.ui.anim.featherIn
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.start.Transport
import com.metrowin.launcher.ui.start.TransportButton
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.delay

/** Where the song is: the position the player gave, moved on by the time since it said so (at the speed it plays), kept inside the song. Pure, so it is tested. */
fun playbackPosition(position: Long, updatedAt: Long, now: Long, speed: Float, playing: Boolean, duration: Long): Long {
    val moved = if (playing) ((now - updatedAt) * speed).toLong().coerceAtLeast(0) else 0L
    val at = (position + moved).coerceAtLeast(0)
    return if (duration > 0) at.coerceAtMost(duration) else at
}

private fun mmss(millis: Long): String = "%d:%02d".format(millis / 60_000, millis / 1000 % 60)

/**
 * Now Playing: the cover of the song across the whole page under a flat dark veil (no blur, as in Windows Phone), the title and
 * the artist large, a bar of where the song is (a touch seeks), the transport buttons, and the queue of the player with the song
 * that plays marked. Needs the notification access, which the launcher asks for anyway; without music it says so.
 */
@Composable
fun NowPlayingScreen() {
    val media = rememberMediaInfo(true)
    val colors = LocalMetroColors.current
    val cover = remember(media?.title, media?.artist) { media?.controller?.metadata?.let(::bigCover) ?: media?.art }
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (cover != null) {
            Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // A flat veil, as dark as the text needs to be read on any picture.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
        }
        MiniFrame {
            if (media == null) {
                MetroText(stringResource(R.string.np_nothing), size = 22.sp, color = colors.subtle, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(24.dp).testTag("np:empty"))
            } else {
                Player(media, cover)
            }
        }
    }
}

private fun bigCover(meta: MediaMetadata): ImageBitmap? =
    (meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.asImageBitmap()

@Composable
private fun Player(media: MediaInfo, cover: ImageBitmap?) {
    val onDark = if (cover != null) Color.White else LocalMetroColors.current.foreground
    val faint = if (cover != null) Color.White.copy(alpha = 0.7f) else LocalMetroColors.current.subtle
    val accent = LocalAccent.current
    val controller = media.controller
    // The position is read again twice a second while the song plays, from what the player last said.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(media.playing) { while (media.playing) { delay(500); now = SystemClock.elapsedRealtime() } }
    val state = controller.playbackState
    val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val at = state?.let { playbackPosition(it.position, it.lastPositionUpdateTime.takeIf { t -> t > 0 } ?: now, now, it.playbackSpeed, media.playing, duration) } ?: 0L
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        MetroText(stringResource(R.string.np_now).uppercase(), size = 14.sp, weight = FontWeight.SemiBold, color = faint)
        if (cover != null) {
            // Where the cover is not the whole page (a small one), it is also here, whole and square, as the album shows it.
            Image(cover, null, Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(1f).clipToBounds().testTag("np:cover"), contentScale = ContentScale.Crop)
        }
        MetroText(media.title, size = 34.sp, color = onDark, maxLines = 2, lineHeight = 40.sp, modifier = Modifier.padding(top = 16.dp).testTag("np:title"))
        if (media.artist.isNotBlank()) MetroText(media.artist, size = 22.sp, color = faint, maxLines = 1, modifier = Modifier.testTag("np:artist"))
        if (duration > 0) {
            Box(
                Modifier.padding(top = 18.dp).fillMaxWidth().height(24.dp).testTag("np:seek")
                    .pointerInput(duration) { detectTapGestures { p -> runCatching { controller.transportControls.seekTo((duration * (p.x / size.width)).toLong()) } } },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(faint.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxWidth((at.toFloat() / duration).coerceIn(0f, 1f)).height(4.dp).background(accent))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetroText(mmss(at), size = 13.sp, weight = FontWeight.Normal, color = faint)
                MetroText(mmss(duration), size = 13.sp, weight = FontWeight.Normal, color = faint)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TransportButton(Transport.Previous, "np:prev", onDark) { media.previous() }
            TransportButton(if (media.playing) Transport.Pause else Transport.Play, "np:toggle", onDark) { media.playPause() }
            TransportButton(Transport.Next, "np:next", onDark) { media.next() }
        }
        Queue(controller, onDark, faint)
    }
}

/** The queue of the player: what is next, with the song that plays in the accent colour; a touch jumps to it. Nothing when the player keeps none. */
@Composable
private fun Queue(controller: MediaController, ink: Color, faint: Color) {
    val queue = controller.queue.orEmpty()
    if (queue.size < 2) return
    val playing = controller.playbackState?.activeQueueItemId ?: -1L
    MetroText(stringResource(R.string.np_queue), size = 18.sp, color = faint, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
    queue.take(30).forEachIndexed { index, item ->
        val title = item.description.title?.toString().orEmpty().ifBlank { return@forEachIndexed }
        Column(Modifier.fillMaxWidth().featherIn(index, controller.sessionToken).metroPress(onClick = { runCatching { controller.transportControls.skipToQueueItem(item.queueId) } }).padding(vertical = 6.dp).testTag("np:queue")) {
            MetroText(title, size = 20.sp, color = if (item.queueId == playing) LocalAccent.current else ink, maxLines = 1)
            item.description.subtitle?.toString()?.takeIf { it.isNotBlank() }?.let { MetroText(it, size = 14.sp, weight = FontWeight.Normal, color = faint, maxLines = 1) }
        }
    }
}
