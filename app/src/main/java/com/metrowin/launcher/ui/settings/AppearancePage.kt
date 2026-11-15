package com.metrowin.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R

/** The Look page: the theme, the style, the colours and the photo, then the icons and the theme files, in one scroll. */
@Composable
fun AppearancePage(env: SettingsEnv) {
    SettingsPage {
        StartThemeSettings(env)
        SettingHeading(stringResource(R.string.look_icons_heading))
        IconsSettings(env)
    }
}
