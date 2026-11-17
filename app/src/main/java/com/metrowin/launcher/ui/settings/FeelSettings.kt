package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.metrowin.launcher.data.MetroSound
import com.metrowin.launcher.ui.anim.MetroSoundPlayer
import com.metrowin.launcher.ui.anim.TapFeedbackPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val volumes = listOf(0, 25, 50, 75, 100)
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
        DropdownSetting(
            label = stringResource(R.string.feel_volume), options = volumes.map { stringResource(R.string.feel_percent, it) }, selected = nearest(volumes, s.soundVolume),
            onSelect = { i -> env.onChange { it.copy(soundVolume = volumes[i]) } }, tag = "gestures:volume", id = "gest.soundvolume",
        )
        SoundPreview(s.soundVolume)
    }
}

/** The button that plays the click and the chime at the volume chosen, so it can be judged without leaving the page. */
@Composable
private fun SoundPreview(volume: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tap = remember(context) { TapFeedbackPlayer(context) }
    val metro = remember(context) { MetroSoundPlayer(context) }
    DisposableEffect(tap, metro) { onDispose { tap.release(); metro.release() } }
    ActionSetting(
        stringResource(R.string.feel_volume_try),
        onClick = {
            scope.launch {
                withContext(Dispatchers.IO) { tap.prepare(); metro.prepare() }
                // The sounds load by themselves a moment after they are made ready.
                delay(250)
                tap.click(volume / 100f)
                delay(300)
                metro.play(MetroSound.Notify, volume / 100f)
            }
        },
        tag = "gestures:volumetry",
    )
}
