package com.metrowin.launcher.ui.start

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads the summary of an info tile aloud, in the language of the phone. The speech engine of Android is started on the first use and kept. */
object InfoSpeech {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var waiting: String? = null

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        val current = engine
        if (current != null && ready) {
            current.speak(text, TextToSpeech.QUEUE_FLUSH, null, "info")
            return
        }
        waiting = text
        if (current == null) {
            engine = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    engine?.language = Locale.getDefault()
                    waiting?.let { engine?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "info") }
                }
                waiting = null
            }
        }
    }

    /** Lets the engine go (it is started again on the next use). */
    fun stop() {
        engine?.shutdown()
        engine = null
        ready = false
    }
}
