package com.metrowin.launcher.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.StatusItem

/** The status bar of Windows Phone: whether it is shown, in the apps too, and what it shows (each icon can be turned off). */
@Composable
fun StatusBarPage(env: SettingsEnv) {
    val settings = env.settings
    val phoneState = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleSetting(
                label = stringResource(R.string.theme_metro_status_bar),
                checked = settings.metroStatusBar,
                onChange = { value -> env.onChange { it.copy(metroStatusBar = value) } },
                hint = stringResource(R.string.theme_metro_status_bar_hint),
                tag = "statusbar:metro",
                id = "statusbar.metro",
            )
            ToggleSetting(
                label = stringResource(R.string.theme_metro_status_bar_apps),
                checked = settings.metroStatusBarOverApps && env.lockServiceEnabled,
                onChange = { value -> env.onChange { it.copy(metroStatusBarOverApps = value) }; if (value && !env.lockServiceEnabled) env.onOpenAccessibilitySettings() },
                hint = stringResource(R.string.theme_metro_status_bar_apps_hint),
                tag = "statusbar:apps",
                id = "statusbar.apps",
            )
            ToggleSetting(
                label = stringResource(R.string.charge_animation),
                checked = settings.chargeAnimation,
                onChange = { value -> env.onChange { it.copy(chargeAnimation = value) } },
                hint = stringResource(R.string.charge_animation_hint),
                tag = "statusbar:charge",
                id = "statusbar.charge",
            )
            SettingHeading(stringResource(R.string.statusbar_items))
            SettingHint(stringResource(R.string.statusbar_items_hint))
            StatusItem.entries.forEach { item ->
                ToggleSetting(
                    label = stringResource(item.label),
                    checked = item.id !in StatusItem.hiddenIds(settings.statusBarHidden),
                    onChange = { value ->
                        env.onChange { it.copy(statusBarHidden = StatusItem.withShown(it.statusBarHidden, item, value)) }
                        // The kind of network (5G, 4G...) is a thing the phone only tells to an app that may read the phone state.
                        if (value && item == StatusItem.Network) phoneState.launch(Manifest.permission.READ_PHONE_STATE)
                    },
                    hint = if (item == StatusItem.Network) stringResource(R.string.statusbar_network_hint) else null,
                    tag = "statusbar:${item.id}",
                    id = "statusbar.${item.id}",
                )
            }
        }
    }
}
