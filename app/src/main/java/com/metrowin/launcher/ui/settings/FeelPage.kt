package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R

/** How a touch feels: the sound and buzz of a tap, how fast the animations run, how far a tile tilts, how hard the vibration is. */
@Composable
fun FeelPage(env: SettingsEnv) {
    val settings = env.settings
    SettingsPage {
        ToggleSetting(
            label = stringResource(R.string.gest_tap_sound),
            checked = settings.tapSound,
            onChange = { value -> env.onChange { it.copy(tapSound = value) } },
            tag = "gestures:sound", id = "gest.tapsound",
        )
        ToggleSetting(
            label = stringResource(R.string.gest_tap_haptics),
            checked = settings.tapHaptics,
            onChange = { value -> env.onChange { it.copy(tapHaptics = value) } },
            hint = stringResource(R.string.gest_tap_hint),
            tag = "gestures:haptics", id = "gest.taphaptics",
        )
        FeelSettings(env)
    }
}
