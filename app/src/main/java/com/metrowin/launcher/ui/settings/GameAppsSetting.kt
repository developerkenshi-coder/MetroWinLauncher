package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.gestures.GameApps
import com.metrowin.launcher.model.AppCategory
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.SettingLabel

/**
 * The apps the bars slide away for: the ones the phone says are games and the ones the user added by hand, because not every game says what
 * it is. A search finds any app to switch on (or a wrong one to switch off).
 */
@Composable
fun GameAppsSetting(env: SettingsEnv) {
    val settings = env.settings
    var query by remember { mutableStateOf("") }
    Column(Modifier.settingAnchor("gest.gameapps"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingLabel(stringResource(R.string.gest_game_apps))
        MetroTextField(query, { query = it }, stringResource(R.string.search_apps), Modifier.testTag("gestures:gameapps:query"))
        val choices = remember(env.apps, settings.gameAdd, settings.gameSkip, query) {
            val byPackage = env.apps.filter { it.packageName != null }.distinctBy { it.packageName }
            // Without a search: the games of the phone and the apps the user added. With one: what it finds.
            if (query.isBlank()) byPackage.filter { it.category == AppCategory.Game || it.packageName in settings.gameAdd }
            else AppSearch.filter(byPackage, query).take(GAME_APPS_SHOWN)
        }
        choices.forEach { app ->
            val pkg = app.packageName ?: return@forEach
            ToggleSetting(
                app.label, GameApps.isGame(pkg, settings.gameAdd, settings.gameSkip) { app.category == AppCategory.Game },
                onChange = { on ->
                    env.onChange {
                        if (on) it.copy(gameAdd = it.gameAdd + pkg, gameSkip = it.gameSkip - pkg)
                        else it.copy(gameAdd = it.gameAdd - pkg, gameSkip = it.gameSkip + pkg)
                    }
                },
                tag = "gestures:gameapps:$pkg",
            )
        }
        SettingHint(stringResource(R.string.gest_game_apps_hint))
    }
}

private const val GAME_APPS_SHOWN = 30
