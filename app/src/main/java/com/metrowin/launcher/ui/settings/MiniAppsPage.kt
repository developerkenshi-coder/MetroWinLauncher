package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.ui.components.Spacing

/** The switches of every mini app on one page, one heading per app and the same rows as the rest of the settings (see [MiniOptions]). */
@Composable
fun MiniAppsPage() {
    val context = LocalContext.current
    val values = remember { mutableStateMapOf<String, Boolean>().apply { MiniOptions.all.forEach { put(it.id, it.read(context)) } } }
    SettingsPage {
        MiniOptions.apps.forEach { app ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                SettingHeading(stringResource(MiniOptions.appLabel(app)))
                MiniOptions.all.filter { it.app == app }.forEach { option ->
                    ToggleSetting(
                        stringResource(option.label), values.getValue(option.id),
                        onChange = { values[option.id] = it; option.write(context, it) },
                        hint = option.hint?.let { stringResource(it) }, tag = "mini:${option.id}", id = option.id,
                    )
                }
            }
        }
    }
}
