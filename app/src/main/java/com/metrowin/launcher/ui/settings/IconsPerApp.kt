package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.data.IconPick
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppSquare
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroType
import com.metrowin.launcher.ui.components.Spacing
import com.metrowin.launcher.ui.kids.CheckSquare
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * The icon of each app: the apps with a choice of their own first, a search for the others, and a touch picks an app. The picked apps get their
 * icon changed together (the dialog is the one the long press of an app opens). Each row shows the icon the app has now.
 */
@Composable
fun ColumnScope.IconsPerApp(env: SettingsEnv) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val picks = env.settings.iconPicks
    val shown = remember(env.apps, picks, query) {
        val real = env.apps.filter { it.launch is Launch.Activity && it.glyph == null && !it.pinOnly }.distinctBy { it.id }
        val found = if (query.isBlank()) real.sortedByDescending { it.id in picks } else AppSearch.filter(real, query)
        found.take(SHOWN)
    }
    Column(Modifier.settingAnchor("icons.perapp"), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SettingHeading(stringResource(R.string.iconpick_heading))
        MetroTextField(query, { query = it }, stringResource(R.string.search_apps), Modifier.testTag("iconperapp:query"))
        shown.forEach { app ->
            AppRow(app, describe(picks[app.id], env), app.id in picked) { picked = if (app.id in picked) picked - app.id else picked + app.id }
        }
        SettingHint(stringResource(R.string.iconpick_hint))
        if (shown.isNotEmpty()) {
            ActionSetting(stringResource(R.string.iconpick_select_all), { picked = picked + shown.map { it.id } }, tag = "iconperapp:all")
        }
        if (picked.isNotEmpty()) {
            ActionSetting(stringResource(R.string.iconpick_change_n, picked.size), { env.onPickIcons(picked.toList()) }, tag = "iconperapp:change")
            ActionSetting(stringResource(R.string.iconpick_clear_selection), { picked = emptySet() }, tag = "iconperapp:clear")
        }
    }
}

/** What the app has now, in a few words: the pack and the icon, or nothing when it has the default. */
@Composable
private fun describe(value: String?, env: SettingsEnv): String? {
    val pick = IconPick.parse(value) ?: return null
    if (pick.pack.isEmpty()) return stringResource(R.string.iconpick_own)
    val label = env.iconPacks.firstOrNull { it.packageName == pick.pack }?.label ?: pick.pack
    return if (pick.drawable == null) label else label + " · " + pick.drawable.replace('_', ' ')
}

@Composable
private fun AppRow(app: AppItem, caption: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("iconperapp:${app.id}")
            .semantics { this.selected = selected }
            .metroPress(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSquare(selected)
        Spacer(Modifier.width(12.dp))
        AppSquare(app, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            MetroText(app.label, size = MetroType.Subtitle)
            if (caption != null) MetroText(caption, size = MetroType.Note, color = LocalMetroColors.current.subtle)
        }
    }
}

private const val SHOWN = 30
