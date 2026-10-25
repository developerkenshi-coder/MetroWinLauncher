package com.metrowin.launcher.ui.settings

import androidx.annotation.StringRes
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
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppSquare
import com.metrowin.launcher.ui.components.MetroText

/** What the app list shows: the apps that were hidden from it, and a way to bring each one back. */
@Composable
fun AppsPage(env: SettingsEnv) {
    val hidden = remember(env.apps, env.settings.hiddenApps) { AppFilter.hidden(env.apps, env.settings) }
    val renamed = remember(env.apps, env.settings.appLabels) { env.apps.filter { it.id in env.settings.appLabels && !it.pinOnly } }
    val locked = remember(env.apps, env.settings.lockedApps) { env.apps.filter { it.id in env.settings.lockedApps && !it.pinOnly } }
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.apps_list_title))
            ToggleSetting(
                stringResource(R.string.apps_frequent), env.settings.showFrequentApps,
                onChange = { value -> env.onChange { it.copy(showFrequentApps = value) } },
                hint = stringResource(R.string.apps_frequent_hint), tag = "apps:frequent",
            )
            ToggleSetting(
                stringResource(R.string.apps_new), env.settings.showNewApps,
                onChange = { value -> env.onChange { it.copy(showNewApps = value) } },
                hint = stringResource(R.string.apps_new_hint), tag = "apps:new",
            )
            ToggleSetting(
                stringResource(R.string.apps_categories), env.settings.showCategories,
                onChange = { value -> env.onChange { it.copy(showCategories = value) } },
                hint = stringResource(R.string.apps_categories_hint), tag = "apps:categories",
            )
        }
        AppsToUndo(
            R.string.apps_renamed, R.string.apps_renamed_none, R.string.apps_renamed_hint, "apps:renamed", renamed,
        ) { app -> env.onChange { it.copy(appLabels = it.appLabels - app.id) } }
        AppsToUndo(
            R.string.apps_locked, R.string.apps_locked_none, R.string.apps_locked_hint, "apps:locked", locked,
        ) { app -> env.onChange { it.copy(lockedApps = it.lockedApps - app.id) } }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.apps_search_title))
            ToggleSetting(
                stringResource(R.string.apps_search_contacts), env.settings.searchContacts,
                onChange = { value -> env.onChange { it.copy(searchContacts = value) } }, tag = "apps:search:contacts",
            )
            ToggleSetting(
                stringResource(R.string.apps_search_settings), env.settings.searchSettings,
                onChange = { value -> env.onChange { it.copy(searchSettings = value) } }, tag = "apps:search:settings",
            )
            ToggleSetting(
                stringResource(R.string.apps_search_web), env.settings.searchWeb,
                onChange = { value -> env.onChange { it.copy(searchWeb = value) } },
                hint = stringResource(R.string.apps_search_hint), tag = "apps:search:web",
            )
        }
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

/** A heading, the apps that have something done to them, and a touch on one undoes it. */
@Composable
private fun AppsToUndo(
    @StringRes title: Int,
    @StringRes none: Int,
    @StringRes hint: Int,
    tag: String,
    apps: List<AppItem>,
    onUndo: (AppItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingHeading(stringResource(title))
        if (apps.isEmpty()) {
            SettingHint(stringResource(none), Modifier.testTag("$tag:none"))
        } else {
            apps.forEach { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .testTag("$tag:${app.id}")
                        .metroPress(onClick = { onUndo(app) })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppSquare(app, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    MetroText(app.label, size = 20.sp, modifier = Modifier.weight(1f))
                }
            }
            SettingHint(stringResource(hint), Modifier.padding(top = 6.dp))
        }
    }
}
