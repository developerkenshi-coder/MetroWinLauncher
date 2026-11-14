package com.metrowin.launcher.data

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.metrowin.launcher.recorder.AudioTools
import com.metrowin.launcher.recorder.Opt
import com.metrowin.launcher.recorder.RecMetaStore
import com.metrowin.launcher.recorder.RecorderConfig
import com.metrowin.launcher.recorder.RecorderPrefs
import com.metrowin.launcher.recorder.RecorderService
import java.io.File

/**
 * Records the microphone to a file of the recordings and plays them back. The playing ends when the page closes; the recording does not:
 * it belongs to the process, with a service and its notification ([RecorderService]) to keep it alive, and any page can stop it.
 *
 * With the crash-proof switch on, the sound is first written as a stream of AAC frames (a file that is readable up to the last frame even
 * if the phone dies) and copied into the .m4a only when the recording ends; a part left by a crash is turned into a recording the next time.
 */
class VoiceRecorder(private val context: Context, private val recordings: Recordings) {
    private var player: MediaPlayer? = null

    /** False when the microphone could not be opened (in use by a call, no permission) or the storage is nearly full: [failure] says which. Any thread. */
    fun start(): Boolean {
        stopPlaying()
        if (active) return true
        failure = FAIL_NONE
        val app = context.applicationContext
        val target = recordings.newFile()
        val dir = target.parentFile ?: return false
        if (freeBytes(dir) < RecorderConfig.START_FREE_BYTES) { failure = FAIL_SPACE; return false }
        val safe = RecorderPrefs.on(app, Opt.SAFE)
        val out = if (safe) File(dir, PART + target.nameWithoutExtension) else target
        val quality = RecorderConfig.QUALITIES[RecorderPrefs.int(app, Opt.QUALITY)]
        val limit = RecorderConfig.MAX_MINUTES[RecorderPrefs.int(app, Opt.MAX_MIN)]
        @Suppress("DEPRECATION") // the constructor without a context is the only one before Android 12
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(if (safe) MediaRecorder.OutputFormat.AAC_ADTS else MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(quality.bitRate)
            r.setAudioSamplingRate(quality.rate)
            r.setAudioChannels(quality.channels)
            if (limit > 0) {
                r.setMaxDuration(limit * 60_000)
                r.setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) Thread { stopRecording(); notice = NOTICE_LIMIT; RecorderService.tell(app, NOTICE_LIMIT) }.start()
                }
            }
            r.setOutputFile(out.path)
            r.prepare()
            r.start()
            synchronized(Companion) {
                recorder = r
                file = target
                part = out.takeIf { safe }
                appContext = app
                marks = emptyList()
                notice = NOTICE_NONE
                pausedAt = 0L
                startedAt = SystemClock.elapsedRealtime()
            }
            if (RecorderPrefs.on(app, Opt.FOCUS)) takeFocus(app)
            true
        }.getOrElse {
            runCatching { r.release() }
            out.delete()
            failure = FAIL_MIC
            false
        }.also {
            // The service makes the recording a foreground job, so that it goes on with the screen off or the launcher left.
            if (it) runCatching { ContextCompat.startForegroundService(app, Intent(app, RecorderService::class.java)) }
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
        private const val PART = ".part-"
        const val FAIL_NONE = 0
        const val FAIL_MIC = 1
        const val FAIL_SPACE = 2
        const val NOTICE_NONE = 0
        const val NOTICE_SPACE = 1
        const val NOTICE_LIMIT = 2

        private var recorder: MediaRecorder? = null
        private var file: File? = null
        private var part: File? = null
        private var appContext: Context? = null
        private var focusRequest: AudioFocusRequest? = null

        /** Why the last start failed (one of FAIL_), for the page to say. */
        var failure by mutableIntStateOf(FAIL_NONE)

        /** Why the recording ended by itself (one of NOTICE_), until the next one starts. */
        var notice by mutableIntStateOf(NOTICE_NONE)

        /**
         * When the recording began (on the clock since boot), 0 when there is none; the pages read it, so they follow a stop from the notification
         * too. A pause moves it on by the time paused, so "now minus this" is always what was recorded.
         */
        var startedAt by mutableLongStateOf(0L)
            private set

        /** The moment the recording was paused, 0 when it is not. */
        var pausedAt by mutableLongStateOf(0L)
            private set

        /** The markers of the recording going on, in milliseconds from its start. */
        var marks by mutableStateOf(emptyList<Long>())
            private set

        val active get() = startedAt != 0L
        val paused get() = pausedAt != 0L

        /** What has been recorded so far, in milliseconds (the clock stands still while paused). */
        fun elapsedMs(): Long = if (!active) 0L else (if (paused) pausedAt else SystemClock.elapsedRealtime()) - startedAt

        /** How loud it is now, 0..1 (the biggest swing since the last time it was asked); 0 when there is no recording. */
        @Synchronized
        fun level(): Float = if (paused) 0f else runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f).coerceIn(0f, 1f)

        /** Holds the recording without ending it; false when there is none or the phone cannot. */
        @Synchronized
        fun pause(): Boolean {
            val r = recorder ?: return false
            if (paused) return true
            if (!runCatching { r.pause() }.isSuccess) return false
            pausedAt = SystemClock.elapsedRealtime()
            return true
        }

        @Synchronized
        fun resume(): Boolean {
            val r = recorder ?: return false
            if (!paused) return true
            if (!runCatching { r.resume() }.isSuccess) return false
            startedAt += SystemClock.elapsedRealtime() - pausedAt
            pausedAt = 0L
            return true
        }

        /** A marker at this moment of the recording. */
        @Synchronized
        fun addMark() { if (active) marks = marks + elapsedMs() }

        /** Ends the recording; the file, or null when it was too short to hold anything (it is then thrown away). */
        @Synchronized
        fun stopRecording(): File? {
            val r = recorder ?: return null
            recorder = null
            startedAt = 0L
            pausedAt = 0L
            val saved = file
            val written = part
            file = null
            part = null
            giveFocusBack()
            val ok = runCatching { r.stop() }.isSuccess
            runCatching { r.release() }
            var result = saved
            if (written != null) result = if (ok && saved != null) finish(written, saved) else { written.delete(); null }
            else if (!ok) saved?.delete()
            if (!ok) result = null
            val kept = marks
            marks = emptyList()
            val context = appContext
            if (result != null && kept.isNotEmpty() && context != null) {
                val stamp = com.metrowin.launcher.recorder.RecorderFormat.stampOf(result.name)
                RecMetaStore(context).update(stamp) { it.copy(marks = kept) }
            }
            return result
        }

        /** The stream of frames into the .m4a; when that fails the stream itself is kept as a recording of its own (.aac), never thrown away. */
        private fun finish(stream: File, target: File): File {
            if (stream.length() == 0L) { stream.delete(); return target }
            if (AudioTools.copyAll(stream, target)) { stream.delete(); return target }
            val raw = File(target.parentFile, target.nameWithoutExtension + ".aac")
            return if (!raw.exists() && stream.renameTo(raw)) raw else stream
        }

        /** Parts that a crash left (a recording going on is not one): each becomes a recording. The files made. */
        fun recover(recordings: Recordings): List<File> {
            val dir = recordings.newFile().parentFile ?: return emptyList()
            if (active) return emptyList()
            return dir.listFiles { f -> f.name.startsWith(PART) }.orEmpty().mapNotNull { stream ->
                val target = File(dir, stream.name.removePrefix(PART) + ".m4a")
                if (target.exists() || stream.length() == 0L) { if (stream.length() == 0L) stream.delete(); null } else finish(stream, target)
            }
        }

        /** What is free where the recordings are kept. */
        fun freeBytes(dir: File): Long = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)

        /**
         * Called every few seconds while recording: ends the recording when the storage is nearly full. True when it did (the page and the
         * notification say so).
         */
        fun guard(dir: File): Boolean {
            if (!active || freeBytes(dir) >= RecorderConfig.MIN_FREE_BYTES) return false
            stopRecording()
            notice = NOTICE_SPACE
            return true
        }

        private fun takeFocus(context: Context) {
            val audio = context.getSystemService(AudioManager::class.java) ?: return
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { change ->
                    // A call or another recorder took the microphone: the recording is held, and the user lets it go on when it is over.
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
                }.build()
            focusRequest = request
            runCatching { audio.requestAudioFocus(request) }
        }

        private fun giveFocusBack() {
            val request = focusRequest ?: return
            focusRequest = null
            val context = appContext ?: return
            runCatching { context.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request) }
        }
    }
}
