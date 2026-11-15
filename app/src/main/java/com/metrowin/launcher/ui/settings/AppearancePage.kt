package com.metrowin.launcher.ui.settings

import androidx.compose.runtime.Composable

/** The Look page: the theme, the style, the colours and the photo, then the theme files, in one scroll. (The icons are on their own page.) */
@Composable
fun AppearancePage(env: SettingsEnv) {
    SettingsPage {
        StartThemeSettings(env)
        ThemeFilesSettings(env)
    }
}
