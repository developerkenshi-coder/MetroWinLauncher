package com.metrowin.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.metrowin.launcher.R

/** What keeps your things safe: the banking apps (no windows of the launcher over them) and the apps locked behind your fingerprint. */
@Composable
fun PrivacyPage(env: SettingsEnv) {
    val locked = remember(env.apps, env.settings.lockedApps) { env.apps.filter { it.id in env.settings.lockedApps && !it.pinOnly } }
    SettingsPage {
        BankSafeSetting(env)
        AppsToUndo(
            R.string.apps_locked, R.string.apps_locked_none, R.string.apps_locked_hint, "apps:locked", "apps.locked", locked,
        ) { app -> env.onChange { it.copy(lockedApps = it.lockedApps - app.id) } }
    }
}
