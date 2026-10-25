package com.metrowin.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.metrowin.launcher.data.BackupSchedule
import com.metrowin.launcher.ui.components.MetroTextField
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
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) env.onPickBackupFolder(uri)
    }
    val settings = env.settings
    val hasFolder = settings.backupFolder.isNotBlank()
    val intervals = BackupSchedule.intervals
    val intervalNames = listOf(
        stringResource(R.string.backup_every_off), stringResource(R.string.backup_every_day),
        stringResource(R.string.backup_every_week), stringResource(R.string.backup_every_month),
    )
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingHeading(stringResource(R.string.backup_folder))
            InfoSetting(
                stringResource(R.string.backup_folder),
                if (hasFolder) env.backupFolderName ?: "…" else stringResource(R.string.backup_folder_none),
            )
            ActionSetting(
                stringResource(R.string.backup_choose_folder), onClick = { folderPicker.launch(null) },
                hint = stringResource(R.string.backup_folder_hint), tag = "backup:folder",
            )
            if (hasFolder) {
                DropdownSetting(
                    stringResource(R.string.backup_every), intervalNames,
                    selected = intervals.indexOf(settings.backupEveryDays).coerceAtLeast(0),
                    onSelect = { i -> env.onChange { it.copy(backupEveryDays = intervals[i]) } },
                    tag = "backup:every",
                )
                InfoSetting(
                    stringResource(R.string.backup_last),
                    if (settings.lastBackupAt > 0L) {
                        DateUtils.getRelativeTimeSpanString(settings.lastBackupAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    } else {
                        stringResource(R.string.backup_never)
                    },
                )
                ActionSetting(stringResource(R.string.backup_now), onClick = env.onBackupNow, tag = "backup:now")
                ActionSetting(stringResource(R.string.backup_restore), onClick = env.onRestoreBackup, tag = "backup:restore")
                ActionSetting(stringResource(R.string.backup_forget_folder), onClick = env.onClearBackupFolder, tag = "backup:forget")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingHeading(stringResource(R.string.sync_heading))
            if (!hasFolder) {
                SettingHint(stringResource(R.string.sync_needs_folder), Modifier.testTag("sync:needs_folder"))
            } else {
                ToggleSetting(
                    stringResource(R.string.sync_enable), settings.syncEnabled,
                    onChange = { value -> env.onChange { it.copy(syncEnabled = value) } }, tag = "sync:enable",
                )
                if (settings.syncEnabled) {
                    MetroTextField(
                        settings.syncPassphrase, { value -> env.onChange { it.copy(syncPassphrase = value) } },
                        stringResource(R.string.sync_passphrase), Modifier.testTag("sync:passphrase"), password = true,
                    )
                    SettingHint(stringResource(R.string.sync_passphrase_hint))
                    InfoSetting(
                        stringResource(R.string.sync_last),
                        if (settings.syncLastAt > 0L) {
                            DateUtils.getRelativeTimeSpanString(settings.syncLastAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                        } else {
                            stringResource(R.string.sync_never)
                        },
                    )
                    ActionSetting(
                        stringResource(R.string.sync_now), onClick = env.onSyncNow,
                        enabled = settings.syncPassphrase.isNotEmpty(), hint = stringResource(R.string.sync_hint), tag = "sync:now",
                    )
                }
            }
        }
        SettingHeading(stringResource(R.string.backup_export))
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
