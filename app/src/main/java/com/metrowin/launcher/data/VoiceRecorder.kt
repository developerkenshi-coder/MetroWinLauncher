package com.metrowin.launcher.data

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records the microphone to a file of the recordings and plays them back: one thing at a time. Released when the page closes. */
class VoiceRecorder(private val context: Context, private val recordings: Recordings) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    /** False when the microphone could not be opened (in use by a call, no permission). */
    fun start(): Boolean {
        stopPlaying()
        if (recorder != null) return true
        val target = recordings.newFile()
        @Suppress("DEPRECATION") // the constructor without a context is the only one before Android 12
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
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
            true
        }.getOrElse {
            runCatching { r.release() }
            target.delete()
            false
        }
    }

    /** Ends the recording; the file, or null when it was too short to hold anything (it is then thrown away). */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        val saved = file
        file = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok) saved?.delete()
        return saved?.takeIf { ok }
    }

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

    fun release() {
        stop()
        stopPlaying()
    }
}
