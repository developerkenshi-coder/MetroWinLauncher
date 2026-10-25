package com.metrowin.launcher.ui.anim

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/**
 * The click and the short vibration that go with a tap on a tile or an app, when the settings ask for them.
 * Both follow the system's own switches (touch sounds, vibration on touch).
 */
@Composable
fun rememberTapFeedback(sound: Boolean, haptics: Boolean): () -> Unit {
    val view = LocalView.current
    val withSound by rememberUpdatedState(sound)
    val withHaptics by rememberUpdatedState(haptics)
    return remember(view) {
        {
            if (withSound) view.playSoundEffect(SoundEffectConstants.CLICK)
            if (withHaptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}
