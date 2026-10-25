package com.metrowin.launcher.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import com.metrowin.launcher.R
import java.util.Locale

/** Why listening ended without a phrase. [message] says it to the user. */
enum class VoiceError(@param:StringRes val message: Int) {
    Unavailable(R.string.voice_unavailable),
    Permission(R.string.voice_permission),
    Network(R.string.voice_network),
    Busy(R.string.voice_busy),
    NoMatch(R.string.voice_nothing);

    companion object {
        /** From a code of [SpeechRecognizer]'s error callback. */
        fun fromCode(code: Int): VoiceError = when (code) {
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> NoMatch
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Permission
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
            TOO_MANY_REQUESTS, SERVER_DISCONNECTED, LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE,
            -> Network
            // The recognizer is busy, or the microphone or the service had a hiccup: asking again usually works.
            else -> Busy
        }

        // Codes that older versions of the SDK do not name.
        private const val TOO_MANY_REQUESTS = 10
        private const val SERVER_DISCONNECTED = 11
        private const val LANGUAGE_NOT_SUPPORTED = 12
        private const val LANGUAGE_UNAVAILABLE = 13
    }
}

/** What [VoiceRecognizer] reports while it listens. */
interface VoiceListener {
    /** How loud the voice is, 0..1, for the microphone to breathe with. */
    fun onLevel(level: Float)

    fun onPartial(text: String)

    /** The phrase, as the recognizer's best guesses, the most likely first. */
    fun onResult(alternatives: List<String>)

    fun onError(error: VoiceError)
}

/** Listens with the speech recognition service of the phone, which the user chose and which holds the voice model. */
class VoiceRecognizer(private val context: Context, private val listener: VoiceListener) {
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(preferOffline: Boolean) {
        release()
        if (!isAvailable) {
            listener.onError(VoiceError.Unavailable)
            return
        }
        val created = create(preferOffline)
        if (created == null) {
            listener.onError(VoiceError.Unavailable)
            return
        }
        recognizer = created
        created.setRecognitionListener(Callbacks())
        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_ALTERNATIVES)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
        )
    }

    /** Asks for a model that works without a connection when the phone has one. */
    private fun create(preferOffline: Boolean): SpeechRecognizer? = runCatching {
        if (preferOffline && Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }.getOrNull()

    /** Stops for good; nothing is reported afterwards. */
    fun release() {
        recognizer?.let {
            runCatching {
                it.setRecognitionListener(null)
                it.cancel()
                it.destroy()
            }
        }
        recognizer = null
    }

    private inner class Callbacks : RecognitionListener {
        override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((rmsdB + RMS_FLOOR) / RMS_RANGE).coerceIn(0f, 1f))

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(listener::onPartial)
        }

        override fun onResults(results: Bundle?) {
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
            if (heard.isEmpty()) listener.onError(VoiceError.NoMatch) else listener.onResult(heard)
        }

        override fun onError(error: Int) = listener.onError(VoiceError.fromCode(error))

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val MAX_ALTERNATIVES = 3

        // onRmsChanged reports roughly -2 (silence) to 10 (loud) dB.
        const val RMS_FLOOR = 2f
        const val RMS_RANGE = 12f
    }
}
