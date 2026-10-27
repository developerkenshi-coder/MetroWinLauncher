package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R

/** How the launcher feels under the finger: how fast it animates, how far it tilts when pressed, how hard a tap vibrates. */
@Composable
fun FeelSettings(env: SettingsEnv) {
    val s = env.settings
    val speeds = listOf(50, 75, 100, 150, 200)
    val tilts = listOf(0, 50, 100, 150)
    val vibrations = listOf(0, 50, 100, 150, 200)
    fun nearest(values: List<Int>, value: Int) = values.indexOf(values.minByOrNull { kotlin.math.abs(it - value) })
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DropdownSetting(
            label = stringResource(R.string.feel_speed), options = speeds.map { stringResource(R.string.feel_percent, it) }, selected = nearest(speeds, s.animationSpeed),
            onSelect = { i -> env.onChange { it.copy(animationSpeed = speeds[i]) } }, tag = "gestures:speed", id = "gest.animspeed",
        )
        SettingHint(stringResource(R.string.feel_speed_hint))
        DropdownSetting(
            label = stringResource(R.string.feel_tilt), options = tilts.map { stringResource(R.string.feel_percent, it) }, selected = nearest(tilts, s.tiltStrength),
            onSelect = { i -> env.onChange { it.copy(tiltStrength = tilts[i]) } }, tag = "gestures:tilt", id = "gest.tilt",
        )
        SettingHint(stringResource(R.string.feel_tilt_hint))
        DropdownSetting(
            label = stringResource(R.string.feel_vibration), options = vibrations.map { stringResource(R.string.feel_percent, it) }, selected = nearest(vibrations, s.vibrationStrength),
            onSelect = { i -> env.onChange { it.copy(vibrationStrength = vibrations[i]) } }, tag = "gestures:vibration", id = "gest.vibration",
        )
    }
}
