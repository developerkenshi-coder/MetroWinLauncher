package com.metrowin.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R

/** Icon packs, and the theme file: how the launcher looks, to share. */
@Composable
fun IconsPage(env: SettingsEnv) {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) env.onExportTheme(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) env.onImportTheme(uri)
    }
    val packs = env.iconPacks
    SettingsPage {
        IconLookSettings(env)
        if (packs.isEmpty()) {
            SettingHint(stringResource(R.string.icons_pack_none), androidx.compose.ui.Modifier.testTag("icons:none"))
        } else {
            val none = stringResource(R.string.icons_pack_default)
            val names = listOf(none) + packs.map { it.label }
            val chosen = packs.indexOfFirst { it.packageName == env.settings.iconPack } + 1
            OptionList(
                label = stringResource(R.string.icons_pack),
                options = names,
                selected = chosen,
                onSelect = { i -> env.onChange { it.copy(iconPack = if (i == 0) "" else packs[i - 1].packageName) } },
                hint = stringResource(R.string.icons_pack_hint),
                tag = "icons:pack", id = "icons.pack",
            )
        }
        SettingHeading(stringResource(R.string.theme_heading))
        ActionSetting(
            text = stringResource(R.string.theme_export),
            onClick = { export.launch("metrowin-theme.json") },
            hint = stringResource(R.string.theme_export_hint),
            tag = "theme:export", id = "icons.export",
        )
        ActionSetting(
            text = stringResource(R.string.theme_import),
            onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            hint = stringResource(R.string.theme_import_hint),
            tag = "theme:import", id = "icons.import",
        )
    }
}
