package com.metrowin.launcher.recorder

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import java.util.Locale

/**
 * Writes down what is said in a recording, with the speech recognition service of the phone (the one the user chose). The sound is
 * given to it as a stream (Android 13 and later), so it is not played aloud. Whether a service takes a stream, and which languages it
 * knows, is up to it: when it cannot, [onFailed] says so and nothing else happens.
 */
class Transcriber(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var pcm: File? = null

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isRecognitionAvailable(context)

    /** Starts. [onText] is called with what is written so far (the pieces joined); [onDone] when it is over; [onFailed] if it cannot be done. */
    fun start(file: File, onText: (String) -> Unit, onDone: () -> Unit, onFailed: (Int) -> Unit) {
        if (!isSupported) return onFailed(ERROR_UNSUPPORTED)
        Thread {
            val raw = File(context.cacheDir, "transcribe.pcm")
            pcm = raw
            val decoded = AudioTools.toSpeechPcm(file, raw)
            main.post { if (decoded) listen(raw, onText, onDone, onFailed) else onFailed(ERROR_DECODE) }
        }.start()
    }

    private fun listen(raw: File, onText: (String) -> Unit, onDone: () -> Unit, onFailed: (Int) -> Unit) {
        release()
        val descriptor = runCatching { ParcelFileDescriptor.open(raw, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull() ?: return onFailed(ERROR_DECODE)
        val service = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() ?: return onFailed(ERROR_UNSUPPORTED)
        recognizer = service
        val pieces = ArrayList<String>()
        fun joined() = pieces.joinToString(" ")
        service.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partial: Bundle?) {
                val text = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onText((pieces + text).joinToString(" "))
            }

            override fun onSegmentResults(segment: Bundle) {
                segment.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { pieces += it }
                onText(joined())
            }

            override fun onEndOfSegmentedSession() {
                release()
                onText(joined())
                onDone()
            }

            override fun onResults(results: Bundle?) {
                // A service that does not cut the sound in segments gives the whole text here.
                if (pieces.isEmpty()) results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { pieces += it }
                release()
                onText(joined())
                onDone()
            }

            override fun onError(error: Int) {
                release()
                // The end of a stream with a silence at the end is reported as "no match" by some services: what was heard is kept.
                if (pieces.isNotEmpty() && error == SpeechRecognizer.ERROR_NO_MATCH) { onText(joined()); onDone() } else onFailed(error)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, true)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptor)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioTools.SPEECH_RATE)
        runCatching { service.startListening(intent) }.onFailure { release(); onFailed(ERROR_UNSUPPORTED) }
    }

    fun release() {
        recognizer?.let { r -> runCatching { r.cancel() }; runCatching { r.destroy() } }
        recognizer = null
        pcm?.delete()
        pcm = null
    }

    companion object {
        const val ERROR_UNSUPPORTED = -1
        const val ERROR_DECODE = -2
    }
}
