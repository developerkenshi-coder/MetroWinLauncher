package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R

/** Speaking to the launcher: where the microphone is, where the voice is understood, and what can be said. */
@Composable
fun VoicePage(env: SettingsEnv) {
    val settings = env.settings
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ToggleSetting(
                stringResource(R.string.voice_set_button), settings.voiceButton,
                onChange = { value -> env.onChange { it.copy(voiceButton = value) } },
                hint = stringResource(R.string.voice_set_button_hint), tag = "voice:button",
            )
            ToggleSetting(
                stringResource(R.string.voice_set_offline), settings.voiceOffline,
                onChange = { value -> env.onChange { it.copy(voiceOffline = value) } },
                hint = stringResource(R.string.voice_set_offline_hint), tag = "voice:offline",
            )
            ToggleSetting(
                stringResource(R.string.voice_set_call), settings.voiceCallDirect,
                onChange = { value -> env.onChange { it.copy(voiceCallDirect = value) } },
                hint = stringResource(R.string.voice_set_call_hint), tag = "voice:calldirect",
            )
            ActionSetting(stringResource(R.string.voice_try), onClick = env.onTryVoice, tag = "voice:try")
            SettingHint(stringResource(R.string.voice_gesture_hint))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.voice_examples))
            listOf(
                R.string.voice_ex_open, R.string.voice_ex_call, R.string.voice_ex_search,
                R.string.voice_ex_torch, R.string.voice_ex_launcher, R.string.voice_ex_settings,
                R.string.voice_ex_levels, R.string.voice_ex_ringer, R.string.voice_ex_navigate, R.string.voice_ex_message, R.string.voice_ex_system,
            ).forEach { SettingHint(stringResource(it), Modifier.testTag("voice:example")) }
        }
    }
}
