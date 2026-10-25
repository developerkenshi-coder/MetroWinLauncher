package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatterySaving
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent

/** When the launcher saves battery, what that changes, and what it already does not do while it is off screen. */
@Composable
fun BatteryPage(env: SettingsEnv) {
    val settings = env.settings
    val modes = BatterySaving.entries
    SettingsPage {
        Column {
            OptionList(
                label = stringResource(R.string.saving_title),
                options = modes.map { stringResource(it.label) },
                selected = modes.indexOf(settings.batterySaving),
                onSelect = { i -> env.onChange { it.copy(batterySaving = modes[i]) } },
                hint = stringResource(settings.batterySaving.description),
                tag = "battery:saving",
            )
            MetroText(
                stringResource(if (env.powerSaving) R.string.saving_now_on else R.string.saving_now_off),
                size = 18.sp,
                color = if (env.powerSaving) LocalAccent.current else com.metrowin.launcher.ui.theme.LocalMetroColors.current.subtle,
                modifier = Modifier.padding(top = 10.dp).testTag("battery:now"),
            )
        }
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            SettingHeading(stringResource(R.string.saving_while_title))
            SettingHint(stringResource(R.string.saving_while_1))
            SettingHint(stringResource(R.string.saving_while_2))
            SettingHint(stringResource(R.string.saving_while_3))
        }
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            SettingHeading(stringResource(R.string.battery_rest_title))
            SettingHint(stringResource(R.string.battery_rest_1))
            SettingHint(stringResource(R.string.battery_rest_2))
            SettingHint(stringResource(R.string.battery_rest_3))
            SettingHint(stringResource(R.string.battery_rest_4))
        }
    }
}
