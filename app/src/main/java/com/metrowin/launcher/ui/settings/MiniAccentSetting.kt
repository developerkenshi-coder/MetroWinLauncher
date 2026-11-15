package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import com.metrowin.launcher.R
import com.metrowin.launcher.data.MetroCatalog
import com.metrowin.launcher.data.MiniApps
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.Spacing

/** One mini app and the colour it opens in: [AccentGrid] for a colour of its own, or back to the one of the theme. */
@Composable
fun MiniAccentSetting(env: SettingsEnv) {
    val apps = remember { MetroCatalog.apps.filter { it.id in MiniApps.activities } }
    var picked by remember { mutableIntStateOf(0) }
    val app = apps[picked]
    Column(Modifier.settingAnchor("theme.miniaccent"), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        DropdownSetting(stringResource(R.string.accent_mini), apps.map { stringResource(it.label) }, picked, { picked = it }, tag = "theme:miniapp")
        AccentGrid(selected = env.settings.miniAccents[app.id] ?: -1) { index -> env.onChange { it.copy(miniAccents = it.miniAccents + (app.id to index)) } }
        MetroButton(stringResource(R.string.accent_mini_theme), Modifier.fillMaxWidth().testTag("theme:miniaccent:reset")) {
            env.onChange { it.copy(miniAccents = it.miniAccents - app.id) }
        }
        SettingHint(stringResource(R.string.accent_mini_hint))
    }
}
