package com.metrowin.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R

/** Less motion, more contrast, and what the launcher says to TalkBack. */
@Composable
fun AccessibilityPage(env: SettingsEnv) {
    val settings = env.settings
    SettingsPage {
        ToggleSetting(
            stringResource(R.string.access_reduce_motion), settings.reduceMotion,
            onChange = { value -> env.onChange { it.copy(reduceMotion = value) } },
            hint = stringResource(R.string.access_reduce_motion_hint), tag = "access:motion",
        )
        ToggleSetting(
            stringResource(R.string.access_high_contrast), settings.highContrast,
            onChange = { value -> env.onChange { it.copy(highContrast = value) } },
            hint = stringResource(R.string.access_high_contrast_hint), tag = "access:contrast",
        )
        SettingHeading(stringResource(R.string.access_talkback))
        SettingHint(stringResource(R.string.access_talkback_hint))
        ActionSetting(
            stringResource(R.string.access_open_settings), onClick = env.onOpenAccessibilitySettings, tag = "access:open",
        )
    }
}
