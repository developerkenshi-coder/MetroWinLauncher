package com.metrowin.launcher.recorder

import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The one player of the recorder. It belongs to the screen, not to a page, so a recording goes on playing when its page is closed (a small
 * player stays at the bottom of the list), and a queue of recordings can play one after the other. It prepares in the background, so opening
 * a recording never waits for the sound.
 */
class RecorderPlayer {
    var file by mutableStateOf<File?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var speed by mutableFloatStateOf(1f)
        private set

    /** The recordings that follow the one playing, and the one playing among them. */
    var queue by mutableStateOf(emptyList<File>())
        private set
    private var at = 0

    private var player: MediaPlayer? = null
    private var stopAt = -1L
    private var startWhenReady = false
    private var pendingSeek = -1L

    /** Prepares [target] (not again when it is the one loaded). */
    fun load(target: File) {
        if (file?.path == target.path && player != null) return
        release()
        file = target
        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setDataSource(target.path)
            mp.setOnPreparedListener {
                if (player !== mp) return@setOnPreparedListener
                ready = true
                duration = it.duration.toLong()
                if (pendingSeek >= 0) { it.seekTo(pendingSeek.toInt()); position = pendingSeek; pendingSeek = -1 }
                if (startWhenReady) { startWhenReady = false; begin() }
            }
            mp.setOnErrorListener { _, _, _ -> failed = true; playing = false; true }
            mp.setOnCompletionListener { playing = false; position = duration; if (!advance()) stopAt = -1 }
            mp.prepareAsync()
        }.onFailure { failed = true }
    }

    private fun begin() {
        val mp = player ?: return
        runCatching {
            mp.start()
            applySpeed()
            playing = true
        }.onFailure { failed = true }
    }

    private fun applySpeed() {
        val mp = player ?: return
        runCatching { mp.playbackParams = PlaybackParams().setSpeed(speed) }
    }

    /** Plays from [from] to [to] (the whole sound when [to] is not given): a press while it plays holds it. */
    fun toggle(from: Long = 0L, to: Long = -1L) {
        val mp = player ?: return
        if (playing) { pause(); return }
        stopAt = to
        val limit = if (to >= 0) to else duration
        if (!ready) { pendingSeek = from; startWhenReady = true; return }
        if (position < from || (limit > 0 && position >= limit - 50)) { runCatching { mp.seekTo(from.toInt()) }; position = from }
        begin()
    }

    /** Plays a short stretch and holds at its end (what a cut sounds like at one edge). */
    fun preview(from: Long, to: Long) {
        val mp = player ?: return
        stopAt = to
        runCatching { mp.seekTo(from.toInt()) }
        position = from
        if (ready) begin() else startWhenReady = true
    }

    fun pause() {
        runCatching { player?.pause() }
        playing = false
        startWhenReady = false
    }

    fun seek(ms: Long) {
        val target = ms.coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        if (!ready) { pendingSeek = target; position = target; return }
        runCatching { player?.seekTo(target.toInt()) }
        position = target
    }

    fun skip(delta: Long) = seek(position + delta)

    fun setSpeed(value: Float) {
        speed = value
        if (playing) applySpeed()
    }

    /** What is read from the player now; the screen calls it a few times a second while something plays. */
    fun tick() {
        val mp = player ?: return
        if (!playing) return
        position = runCatching { mp.currentPosition.toLong() }.getOrDefault(position)
        if (stopAt in 0..position) pause()
    }

    /** Plays [files] one after the other, from the one at [index]. */
    fun playQueue(files: List<File>, index: Int) {
        if (files.isEmpty()) return
        queue = files
        at = index.coerceIn(0, files.size - 1)
        load(files[at])
        startWhenReady = true
        if (ready) { startWhenReady = false; begin() }
    }

    fun hasNext() = queue.isNotEmpty() && at < queue.size - 1
    fun hasPrevious() = queue.isNotEmpty() && at > 0

    fun next(): Boolean = advance()

    fun previous() {
        if (!hasPrevious()) return
        at--
        file = null
        load(queue[at])
        startWhenReady = true
    }

    private fun advance(): Boolean {
        if (!hasNext()) return false
        at++
        val target = queue[at]
        file = null
        load(target)
        startWhenReady = true
        return true
    }

    /** Lets the sound go and forgets the queue. */
    fun release() {
        runCatching { player?.release() }
        player = null
        file = null
        ready = false
        playing = false
        failed = false
        position = 0L
        duration = 0L
        stopAt = -1L
        startWhenReady = false
        pendingSeek = -1L
    }

    /** A recording was deleted or renamed: if it is the one loaded, it goes. */
    fun drop(target: File) {
        if (file?.path == target.path) { release(); queue = emptyList() }
        else if (target in queue) queue = queue - target
    }
}
