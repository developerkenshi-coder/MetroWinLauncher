package com.metrowin.launcher.ui.anim

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.metrowin.launcher.data.ClickSound
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

/**
 * What a touch does besides moving something: called when a button, a tile or a switch is tapped. It makes the click and the
 * short vibration the settings ask for (see [rememberTapFeedback]); where nothing provides it, it does nothing.
 */
val LocalTapFeedback = staticCompositionLocalOf<() -> Unit> { {} }

/** The click and the vibration of a tap, ready to be played. */
class TapFeedbackPlayer(context: Context) {
    private val app = context.applicationContext
    private var pool: SoundPool? = null
    private var soundId = 0

    @Volatile
    private var loaded = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Makes the click ready (blocking: it writes a small file once and loads it). Call it off the main thread, before it is wanted. */
    @Synchronized
    fun prepare() {
        if (pool != null) return
        val file = File(app.cacheDir, CLICK_FILE)
        runCatching {
            if (!file.exists() || file.length() == 0L) file.writeBytes(ClickSound.wav())
        }.onFailure { return }
        // The sound of the system when it answers a touch: it follows the volume of the phone and is silent on a silent phone.
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val made = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
        made.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        soundId = made.load(file.path, 1)
        pool = made
    }

    /** Lets go of the sound while the launcher is not on screen. */
    @Synchronized
    fun release() {
        loaded = false
        pool?.release()
        pool = null
    }

    fun click() {
        if (loaded) pool?.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    /** A short tap of the motor, the one Android uses for a key: whatever the touch vibration switch of the phone says. */
    fun tick() {
        val motor = vibrator ?: return
        runCatching {
            if (!motor.hasVibrator()) return
            motor.vibrate(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                } else {
                    VibrationEffect.createOneShot(TICK_MS, TICK_AMPLITUDE)
                },
            )
        }
    }

    private companion object {
        const val CLICK_FILE = "tap_click.wav"
        const val TICK_MS = 14L
        const val TICK_AMPLITUDE = 110
    }
}

/**
 * The click and the short vibration that go with a tap, when the settings ask for them. They are the launcher's own, so they
 * work whether or not the touch sounds and the vibration on touch of Android are switched on; the click follows the volume of
 * the phone and is silent when the phone is. Called by every button, tile and switch through [LocalTapFeedback]. The sound is
 * kept ready only while the launcher is on screen.
 */
@Composable
fun rememberTapFeedback(sound: Boolean, haptics: Boolean): () -> Unit {
    val context = LocalContext.current
    val player = remember(context) { TapFeedbackPlayer(context) }
    val withSound by rememberUpdatedState(sound)
    val withHaptics by rememberUpdatedState(haptics)
    LaunchedWhileStarted(sound) {
        if (!sound) return@LaunchedWhileStarted
        try {
            withContext(Dispatchers.IO) { player.prepare() }
            awaitCancellation()
        } finally {
            player.release()
        }
    }
    return remember(player) {
        {
            if (withSound) player.click()
            if (withHaptics) player.tick()
        }
    }
}
