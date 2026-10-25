package com.metrowin.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R

/** Saving the layout in a file and bringing it back. */
@Composable
fun BackupPage(env: SettingsEnv) {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) env.onExportLayout(uri)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) env.onImportLayout(uri)
    }
    SettingsPage {
        ActionSetting(
            text = stringResource(R.string.backup_export),
            onClick = { export.launch("metrowin-layout.json") },
            hint = stringResource(R.string.backup_export_hint),
            tag = "backup:export",
        )
        ActionSetting(
            text = stringResource(R.string.backup_import),
            onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            hint = stringResource(R.string.backup_import_hint),
            tag = "backup:import",
        )
    }
}
