package com.metrowin.launcher.data

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.metrowin.launcher.recorder.RecorderService
import java.io.File

/**
 * Records the microphone to a file of the recordings and plays them back. The playing ends when the page closes; the recording does not:
 * it belongs to the process, with a service and its notification ([RecorderService]) to keep it alive, and any page can stop it.
 */
class VoiceRecorder(private val context: Context, private val recordings: Recordings) {
    private var player: MediaPlayer? = null

    /** False when the microphone could not be opened (in use by a call, no permission). */
    fun start(): Boolean {
        stopPlaying()
        if (active) return true
        val target = recordings.newFile()
        @Suppress("DEPRECATION") // the constructor without a context is the only one before Android 12
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context.applicationContext) else MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(target.path)
            r.prepare()
            r.start()
            recorder = r
            file = target
            startedAt = SystemClock.elapsedRealtime()
            true
        }.getOrElse {
            runCatching { r.release() }
            target.delete()
            false
        }.also {
            // The service makes the recording a foreground job, so that it goes on with the screen off or the launcher left.
            if (it) runCatching { ContextCompat.startForegroundService(context.applicationContext, Intent(context.applicationContext, RecorderService::class.java)) }
        }
    }

    fun stop(): File? = stopRecording()

    fun play(target: File, onDone: () -> Unit): Boolean {
        stopPlaying()
        return runCatching {
            player = MediaPlayer().apply {
                setDataSource(target.path)
                setOnCompletionListener { stopPlaying(); onDone() }
                prepare()
                start()
            }
            true
        }.getOrElse {
            stopPlaying()
            false
        }
    }

    fun stopPlaying() {
        player?.let { p -> runCatching { p.release() } }
        player = null
    }

    /** Only the playing: a recording goes on when the page is left. */
    fun release() = stopPlaying()

    companion object {
        private var recorder: MediaRecorder? = null
        private var file: File? = null

        /** When the recording began (on the clock since boot), 0 when there is none; the pages read it, so they follow a stop from the notification too. */
        var startedAt by mutableLongStateOf(0L)
            private set

        val active get() = startedAt != 0L

        /** Ends the recording; the file, or null when it was too short to hold anything (it is then thrown away). */
        @Synchronized
        fun stopRecording(): File? {
            val r = recorder ?: return null
            recorder = null
            startedAt = 0L
            val saved = file
            file = null
            val ok = runCatching { r.stop() }.isSuccess
            runCatching { r.release() }
            if (!ok) saved?.delete()
            return saved?.takeIf { ok }
        }
    }
}
