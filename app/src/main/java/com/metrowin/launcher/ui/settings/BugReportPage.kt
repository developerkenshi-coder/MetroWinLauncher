package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.MetroTextField

/** The bug ticket: what to write, then a mail to the author with the log of the app attached. */
@Composable
fun BugReportPage(env: SettingsEnv) {
    var description by rememberSaveable { mutableStateOf("") }
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingHeading(stringResource(R.string.bug_invite_title))
            SettingHint(stringResource(R.string.bug_invite_what))
            SettingHint(stringResource(R.string.bug_invite_how))
            SettingHint(stringResource(R.string.bug_invite_repro))
        }
        MetroTextField(
            description, { description = it }, stringResource(R.string.bug_field),
            Modifier.testTag("bug:field").settingAnchor("bugreport.describe"), singleLine = false,
        )
        ActionSetting(
            stringResource(R.string.bug_send), onClick = { env.onSendBugReport(description) },
            enabled = description.isNotBlank(), hint = stringResource(R.string.bug_send_hint), tag = "bug:send", id = "bugreport.send",
        )
        SettingHint(stringResource(R.string.bug_privacy))
    }
}
