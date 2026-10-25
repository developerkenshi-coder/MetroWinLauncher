package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R

/** The version of the app, the phone it runs on, and who made what it is built from. */
@Composable
fun AboutPage(env: SettingsEnv) {
    val about = env.about
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoSetting(stringResource(R.string.about_version), "${about.versionName} (${about.versionCode})")
            InfoSetting(stringResource(R.string.about_android), "${about.androidRelease} (API ${about.sdk})")
            InfoSetting(stringResource(R.string.about_device), about.device)
            if (about.refreshHz > 0) InfoSetting(stringResource(R.string.about_refresh), "${about.refreshHz} Hz")
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingHeading(stringResource(R.string.about_credits))
            SettingHint(stringResource(R.string.about_inspired))
            SettingHint(stringResource(R.string.about_font))
            SettingHint(stringResource(R.string.about_icons))
            SettingHint(stringResource(R.string.about_libraries))
            SettingHint(stringResource(R.string.about_weather))
        }
    }
}
