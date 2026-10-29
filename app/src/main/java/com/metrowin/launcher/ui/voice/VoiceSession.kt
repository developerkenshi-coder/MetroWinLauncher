package com.metrowin.launcher.ui.voice

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.metrowin.launcher.R
import com.metrowin.launcher.data.VoiceError
import com.metrowin.launcher.data.VoiceListener
import com.metrowin.launcher.data.VoiceRecognizer

/** What the voice screen shows. */
sealed interface VoiceUiState {
    /** The microphone is open. [heard] is what has been understood so far, [level] how loud the voice is (0..1). */
    data class Listening(val heard: String = "", val level: Float = 0f) : VoiceUiState

    /** A phrase came in and is being carried out. */
    data class Understood(val heard: String) : VoiceUiState

    /**
     * Nothing to carry out. [message] may take [argument] (the name that was not found); with [webQuery] the screen
     * also offers to look for it on the web.
     */
    data class Problem(
        @param:StringRes val message: Int,
        val argument: String? = null,
        val webQuery: String? = null,
        /** Trying again would not help: the screen offers no retry. */
        val final: Boolean = false,
    ) : VoiceUiState
}

/**
 * One conversation with the recognizer: [state] is null while the voice screen is closed. A phrase is handed to
 * [onHeard] with the recognizer's alternatives; the host carries it out and closes the session, or reports a
 * [VoiceUiState.Problem].
 */
@Stable
class VoiceSession(context: Context) {
    var state: VoiceUiState? by mutableStateOf(null)
        private set

    /** Asks for a model that works without a connection, if the phone has one. */
    var preferOffline: Boolean = true

    /** Set by the host, which knows how to carry out a phrase. */
    var onHeard: (List<String>) -> Unit = {}

    private val recognizer = VoiceRecognizer(
        context,
        object : VoiceListener {
            override fun onLevel(level: Float) {
                (state as? VoiceUiState.Listening)?.let { state = it.copy(level = level) }
            }

            override fun onPartial(text: String) {
                (state as? VoiceUiState.Listening)?.let { state = it.copy(heard = text) }
            }

            override fun onResult(alternatives: List<String>) {
                failures = 0
                state = VoiceUiState.Understood(alternatives.first())
                onHeard(alternatives)
            }

            override fun onError(error: VoiceError) = fail(error)
        },
    )

    val isOpen: Boolean get() = state != null

    private var failures = 0

    /** Listening ended without a phrase. After [MAX_TRIES] in a row, or with no recognizer at all, the screen stops offering to try again. */
    internal fun fail(error: VoiceError) {
        failures++
        state = when {
            error == VoiceError.Unavailable -> VoiceUiState.Problem(error.message, final = true)
            failures >= MAX_TRIES -> VoiceUiState.Problem(R.string.voice_gave_up, final = true)
            else -> VoiceUiState.Problem(error.message)
        }
    }

    /** Opens the screen and starts listening. */
    fun open() {
        // A retry from the problem screen goes on counting; anything else starts afresh.
        if (state !is VoiceUiState.Problem) failures = 0
        state = VoiceUiState.Listening()
        recognizer.start(preferOffline)
    }

    /** Shows [message] without listening, for what the host found out itself. */
    fun problem(@StringRes message: Int, argument: String? = null, webQuery: String? = null) {
        recognizer.release()
        state = VoiceUiState.Problem(message, argument, webQuery)
    }

    fun close() {
        failures = 0
        recognizer.release()
        state = null
    }
}

private const val MAX_TRIES = 3

@Composable
fun rememberVoiceSession(): VoiceSession {
    val context = LocalContext.current.applicationContext
    val session = remember { VoiceSession(context) }
    DisposableEffect(session) { onDispose { session.close() } }
    return session
}
