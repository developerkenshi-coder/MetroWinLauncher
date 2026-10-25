package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AppFilter
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppSquare
import com.metrowin.launcher.ui.components.MetroText

/** What the app list shows: the apps that were hidden from it, and a way to bring each one back. */
@Composable
fun AppsPage(env: SettingsEnv) {
    val hidden = remember(env.apps, env.settings.hiddenApps) { AppFilter.hidden(env.apps, env.settings) }
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingHeading(stringResource(R.string.apps_hidden))
            if (hidden.isEmpty()) {
                SettingHint(stringResource(R.string.apps_hidden_none), Modifier.testTag("apps:hidden:none"))
            } else {
                hidden.forEach { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .testTag("apps:hidden:${app.id}")
                            .metroPress(onClick = { env.onChange { it.copy(hiddenApps = it.hiddenApps - app.id) } })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppSquare(app, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        MetroText(app.label, size = 20.sp, modifier = Modifier.weight(1f))
                    }
                }
                SettingHint(stringResource(R.string.apps_hidden_hint), Modifier.padding(top = 6.dp))
            }
        }
    }
}
