package com.metrowin.launcher.ui.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
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

    /** A phrase came in and waits [waitMs] for the user to read it, edit it or go on, before it is carried out. */
    data class Confirm(val heard: String, val waitMs: Long) : VoiceUiState

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

    /** How long what was understood is shown before it is carried out; 0 carries it out at once. */
    var previewMs: Long = 0L

    /** The user chose to edit what was understood: the host puts the text in the search box. */
    var onEdit: (String) -> Unit = {}

    private val handler = Handler(Looper.getMainLooper())
    private var waiting: Runnable? = null
    private var alternatives: List<String> = emptyList()

    private fun cancelWait() {
        waiting?.let(handler::removeCallbacks)
        waiting = null
    }

    /** The preview is over (the time ran out, or the user said go): the phrase is carried out. */
    fun go() {
        val heard = alternatives.takeIf { it.isNotEmpty() && state is VoiceUiState.Confirm } ?: return
        cancelWait()
        state = VoiceUiState.Understood(heard.first())
        onHeard(heard)
    }

    /** The user wants to change what was understood before it is used. */
    fun edit() {
        val text = (state as? VoiceUiState.Confirm)?.heard ?: return
        close()
        onEdit(text)
    }

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
                if (previewMs > 0) {
                    this@VoiceSession.alternatives = alternatives
                    state = VoiceUiState.Confirm(alternatives.first(), previewMs)
                    cancelWait()
                    waiting = Runnable { go() }.also { handler.postDelayed(it, previewMs) }
                    return
                }
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
        cancelWait()
        // A retry from the problem screen goes on counting; anything else starts afresh.
        if (state !is VoiceUiState.Problem) failures = 0
        state = VoiceUiState.Listening()
        recognizer.start(preferOffline)
    }

    /** A phrase the user touched instead of speaking it: carried out as if it had been heard. */
    fun say(text: String) {
        recognizer.release()
        cancelWait()
        failures = 0
        state = VoiceUiState.Understood(text)
        onHeard(listOf(text))
    }

    /** Shows [message] without listening, for what the host found out itself. */
    fun problem(@StringRes message: Int, argument: String? = null, webQuery: String? = null) {
        recognizer.release()
        state = VoiceUiState.Problem(message, argument, webQuery)
    }

    fun close() {
        cancelWait()
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
