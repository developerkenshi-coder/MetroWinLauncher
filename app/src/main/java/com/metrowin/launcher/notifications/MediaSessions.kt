package com.metrowin.launcher.notifications

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.scale
import androidx.lifecycle.compose.LifecycleStartEffect

/** The music of an app, as the lock screen shows it, with the controller that skips and pauses it. */
@Immutable
class MediaInfo(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val controller: MediaController,
) {
    fun playPause() = runCatching { if (playing) controller.transportControls.pause() else controller.transportControls.play() }
    fun next() = runCatching { controller.transportControls.skipToNext() }
    fun previous() = runCatching { controller.transportControls.skipToPrevious() }
}

/**
 * The media sessions of the phone (what plays in any app), read through the notification access the launcher is given. Used by the
 * lock screen for its music card and by the volume panel to say which app is making the sound.
 */
object MediaSessions {
    fun component(context: Context) = ComponentName(context, NotificationService::class.java)

    private fun manager(context: Context): MediaSessionManager? = context.getSystemService(MediaSessionManager::class.java)

    /** The sessions that are up; empty without the notification access (Android then refuses). */
    fun active(context: Context): List<MediaController> =
        runCatching { manager(context)?.getActiveSessions(component(context)) }.getOrNull().orEmpty()

    /** The session that plays, or else the first one that has something to show. */
    fun best(controllers: List<MediaController>): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null }

    fun info(controller: MediaController): MediaInfo? = runCatching {
        val meta = controller.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        if (title.isBlank() && artist.isBlank()) return null
        val art = (meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART))?.shrunk(ART_PX)?.asImageBitmap()
        MediaInfo(controller.packageName, title, artist, art, controller.playbackState?.state == PlaybackState.STATE_PLAYING, controller)
    }.getOrNull()

    private fun Bitmap.shrunk(side: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= side) return this
        val factor = side.toFloat() / longest
        return scale(maxOf(1, (width * factor).toInt()), maxOf(1, (height * factor).toInt()), true)
    }

    private const val ART_PX = 256
}

/**
 * The music that plays now, kept up to date by the callbacks of Android (nothing is polled): the answer changes when a session
 * starts or ends, a song changes or the playback pauses. Null while nothing plays or without the notification access. With
 * [enabled] off nothing is registered at all, and the callbacks are registered only while the screen that shows the music is
 * started: with another app in front the launcher is not woken by a song that changes.
 */
@Composable
fun rememberMediaInfo(enabled: Boolean): MediaInfo? {
    val context = LocalContext.current.applicationContext
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    LifecycleStartEffect(enabled) {
        if (!enabled) {
            info = null
            return@LifecycleStartEffect onStopOrDispose { }
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val component = MediaSessions.component(context)
        val watched = ArrayList<MediaController>()
        lateinit var callback: MediaController.Callback
        fun refresh() {
            info = MediaSessions.best(watched)?.let(MediaSessions::info)
        }
        callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
            override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
            override fun onSessionDestroyed() = refresh()
        }
        fun watch(list: List<MediaController>?) {
            watched.forEach { runCatching { it.unregisterCallback(callback) } }
            watched.clear()
            list.orEmpty().forEach { controller ->
                runCatching { controller.registerCallback(callback) }
                watched += controller
            }
            refresh()
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
        runCatching {
            manager?.addOnActiveSessionsChangedListener(listener, component)
            watch(manager?.getActiveSessions(component))
        }
        onStopOrDispose {
            runCatching { manager?.removeOnActiveSessionsChangedListener(listener) }
            watched.forEach { runCatching { it.unregisterCallback(callback) } }
            watched.clear()
        }
    }
    return info
}
