package com.metrowin.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R

/** The gestures of the launcher: what a pull or a swipe from the edge does, and the feedback of a tap. */
@Composable
fun GesturesPage(env: SettingsEnv) {
    val settings = env.settings
    SettingsPage {
        ToggleSetting(
            label = stringResource(R.string.gest_edge_pull),
            checked = settings.edgePull,
            onChange = { value -> env.onChange { it.copy(edgePull = value) } },
            hint = stringResource(R.string.gest_edge_pull_hint),
            tag = "gestures:edgepull",
        )
        ToggleSetting(
            label = stringResource(R.string.gest_edge_back_pan),
            checked = settings.edgeBackPan,
            onChange = { value -> env.onChange { it.copy(edgeBackPan = value) } },
            hint = stringResource(R.string.gest_edge_back_pan_hint),
            tag = "gestures:edgepan",
        )
        ToggleSetting(
            label = stringResource(R.string.gest_tap_sound),
            checked = settings.tapSound,
            onChange = { value -> env.onChange { it.copy(tapSound = value) } },
            tag = "gestures:sound",
        )
        ToggleSetting(
            label = stringResource(R.string.gest_tap_haptics),
            checked = settings.tapHaptics,
            onChange = { value -> env.onChange { it.copy(tapHaptics = value) } },
            hint = stringResource(R.string.gest_tap_hint),
            tag = "gestures:haptics",
        )
    }
}
