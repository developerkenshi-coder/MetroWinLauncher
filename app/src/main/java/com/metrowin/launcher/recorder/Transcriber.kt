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
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

/**
 * Writes down what is said in a recording, with the speech recognition of the phone. It prefers the recognizer that works on the phone
 * itself (no internet, the sound does not leave it) and says so when the language pack it needs is missing; without one it asks the
 * speech service the user chose, which may use a server. The sound is given as a stream (Android 13 and later). No model is shipped:
 * what the phone knows is what is used, and when it cannot, [onFailed] says why and nothing else happens.
 */
class Transcriber(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var pcm: File? = null
    private var source: ParcelFileDescriptor? = null

    /** Where the writing is done: on the phone, by the service of the phone (maybe online), or nowhere. */
    enum class Mode { OnDevice, Service, None }

    /** What the phone offers now. Before Android 13 a recognizer cannot be given a file, so nothing can be done. */
    val mode: Mode
        get() {
            if (Build.VERSION.SDK_INT < 33) return Mode.None
            return when {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> Mode.OnDevice
                SpeechRecognizer.isRecognitionAvailable(context) -> Mode.Service
                else -> Mode.None
            }
        }

    val isSupported: Boolean get() = mode != Mode.None

    /** How the last writing was done (when it began to listen); [mode] before that. */
    var usedMode: Mode = Mode.None
        private set

    /** The language asked for: the one of the phone. */
    val language: String get() = Locale.getDefault().toLanguageTag()

    /** Starts. [onText] is called with what is written so far (the pieces joined); [onDone] when it is over; [onFailed] if it cannot be done. */
    fun start(file: File, onText: (String) -> Unit, onDone: () -> Unit, onFailed: (Int) -> Unit) {
        if (!isSupported) return onFailed(ERROR_UNSUPPORTED)
        Thread {
            val raw = File(context.cacheDir, "transcribe.pcm")
            pcm = raw
            val decoded = AudioTools.toSpeechPcm(file, raw)
            main.post { if (decoded) prepare(raw, onText, onDone, onFailed) else onFailed(ERROR_DECODE) }
        }.start()
    }

    private fun recognizerIntent(descriptor: ParcelFileDescriptor): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, true)
        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptor)
        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioTools.SPEECH_RATE)

    private fun prepare(raw: File, onText: (String) -> Unit, onDone: () -> Unit, onFailed: (Int) -> Unit) {
        stopRecognizer()
        val descriptor = runCatching { ParcelFileDescriptor.open(raw, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull() ?: return onFailed(ERROR_DECODE)
        source = descriptor
        val intent = recognizerIntent(descriptor)
        if (Build.VERSION.SDK_INT < 33) return onFailed(ERROR_UNSUPPORTED)
        if (mode != Mode.OnDevice) return listen(SpeechRecognizer.createSpeechRecognizer(context), Mode.Service, intent, onText, onDone, onFailed)
        val local = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
            ?: return listen(SpeechRecognizer.createSpeechRecognizer(context), Mode.Service, intent, onText, onDone, onFailed)
        recognizer = local
        // Is the language pack of the recognizer here? Without it the recognizer would fail in a way nobody can read.
        local.checkRecognitionSupport(intent, ContextCompat.getMainExecutor(context), object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                when {
                    languageAvailable(support.installedOnDeviceLanguages, language) -> listen(local, Mode.OnDevice, intent, onText, onDone, onFailed)
                    languageAvailable(support.supportedOnDeviceLanguages, language) -> { release(); onFailed(ERROR_LANGUAGE_MISSING) }
                    // Not a language it knows: the service of the phone may (online).
                    SpeechRecognizer.isRecognitionAvailable(context) -> { stopRecognizer(); listen(SpeechRecognizer.createSpeechRecognizer(context), Mode.Service, intent, onText, onDone, onFailed) }
                    else -> { release(); onFailed(ERROR_LANGUAGE_UNKNOWN) }
                }
            }

            // The check itself failed: try to listen, the recognizer will say what is wrong.
            override fun onError(error: Int) = listen(local, Mode.OnDevice, intent, onText, onDone, onFailed)
        })
    }

    /** Asks the phone to download the language pack of the on-device recognizer (Android 13 and later); the download goes on without the app. */
    fun downloadLanguage(): Boolean {
        if (Build.VERSION.SDK_INT < 33 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return false
        val local = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull() ?: return false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        val ok = runCatching { local.triggerModelDownload(intent) }.isSuccess
        // The recognizer is let go a little later: the request is handed to the service first.
        main.postDelayed({ runCatching { local.destroy() } }, 3000)
        return ok
    }

    private fun listen(service: SpeechRecognizer?, mode: Mode, intent: Intent, onText: (String) -> Unit, onDone: () -> Unit, onFailed: (Int) -> Unit) {
        if (service == null) return onFailed(ERROR_UNSUPPORTED)
        if (recognizer != null && recognizer !== service) stopRecognizer()
        recognizer = service
        usedMode = mode
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
        runCatching { service.startListening(intent) }.onFailure { release(); onFailed(ERROR_UNSUPPORTED) }
    }

    /** Lets the recognizer go, and keeps the sound: it is still to be read. */
    private fun stopRecognizer() {
        recognizer?.let { r -> runCatching { r.cancel() }; runCatching { r.destroy() } }
        recognizer = null
    }

    fun release() {
        stopRecognizer()
        runCatching { source?.close() }
        source = null
        pcm?.delete()
        pcm = null
    }

    companion object {
        const val ERROR_UNSUPPORTED = -1
        const val ERROR_DECODE = -2
        /** The recognizer of the phone knows the language but has not got its pack: [downloadLanguage] asks for it. */
        const val ERROR_LANGUAGE_MISSING = -3
        /** Neither the recognizer of the phone nor a service knows the language. */
        const val ERROR_LANGUAGE_UNKNOWN = -4

        /** Whether [tags] (BCP 47, "en-US") holds [wanted] or another region of its language: a pack for "en-GB" reads "en-US" speech well enough. */
        fun languageAvailable(tags: List<String>, wanted: String): Boolean {
            val language = wanted.substringBefore('-').substringBefore('_').lowercase()
            return tags.any { it.equals(wanted, ignoreCase = true) || it.substringBefore('-').substringBefore('_').lowercase() == language }
        }
    }
}
