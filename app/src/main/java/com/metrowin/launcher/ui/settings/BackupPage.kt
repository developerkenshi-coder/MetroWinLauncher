package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.data.BackupCopies
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.BackupSchedule
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.SettingLabel
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
    val exportApps = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) env.onExportApps(uri)
    }
    val importApps = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) env.onImportApps(uri)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) env.onPickBackupFolder(uri)
    }
    val settings = env.settings
    val hasFolder = settings.backupFolder.isNotBlank()
    // The copies in the folder are read when the page opens, and again when the folder is changed.
    LaunchedEffect(settings.backupFolder) { env.onRefreshCopies() }
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
                hint = stringResource(R.string.backup_folder_hint), tag = "backup:folder", id = "backup.folder",
            )
            if (hasFolder) {
                DropdownSetting(
                    stringResource(R.string.backup_every), intervalNames,
                    selected = intervals.indexOf(settings.backupEveryDays).coerceAtLeast(0),
                    onSelect = { i -> env.onChange { it.copy(backupEveryDays = intervals[i]) } },
                    tag = "backup:every", id = "backup.every",
                )
                InfoSetting(
                    stringResource(R.string.backup_last),
                    if (settings.lastBackupAt > 0L) {
                        DateUtils.getRelativeTimeSpanString(settings.lastBackupAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    } else {
                        stringResource(R.string.backup_never)
                    },
                )
                ActionSetting(stringResource(R.string.backup_now), onClick = env.onBackupNow, tag = "backup:now", id = "backup.now")
                val keeps = listOf(1, 2, 3, 5, 10)
                DropdownSetting(
                    stringResource(R.string.backup_copies), keeps.map { if (it == 1) stringResource(R.string.backup_copies_one) else stringResource(R.string.backup_copies_n, it) },
                    selected = keeps.indexOf(keeps.minByOrNull { kotlin.math.abs(it - settings.backupCopies) }),
                    onSelect = { i -> env.onChange { it.copy(backupCopies = keeps[i]) } }, tag = "backup:copies", id = "backup.copies",
                )
                SettingHint(stringResource(R.string.backup_copies_hint))
                ActionSetting(stringResource(R.string.backup_restore), onClick = env.onRestoreBackup, tag = "backup:restore", id = "backup.restore")
                if (env.backupCopies.isNotEmpty()) {
                    SettingLabel(stringResource(R.string.backup_copies_list))
                    val format = remember { java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT) }
                    env.backupCopies.forEach { name ->
                        MetroText(
                            BackupCopies.parse(name)?.format(format) ?: name, size = 20.sp, weight = FontWeight.Normal, maxLines = 1,
                            modifier = Modifier.fillMaxWidth().testTag("backup:copy:$name").metroPress(onClick = { env.onRestoreCopy(name) }).padding(vertical = 8.dp),
                        )
                    }
                }
                ActionSetting(stringResource(R.string.backup_forget_folder), onClick = env.onClearBackupFolder, tag = "backup:forget", id = "backup.forget")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingHeading(stringResource(R.string.sync_heading))
            if (!hasFolder) {
                SettingHint(stringResource(R.string.sync_needs_folder), Modifier.testTag("sync:needs_folder"))
            } else {
                ToggleSetting(
                    stringResource(R.string.sync_enable), settings.syncEnabled,
                    onChange = { value -> env.onChange { it.copy(syncEnabled = value) } }, tag = "sync:enable", id = "sync.enable",
                )
                if (settings.syncEnabled) {
                    MetroTextField(
                        settings.syncPassphrase, { value -> env.onChange { it.copy(syncPassphrase = value) } },
                        stringResource(R.string.sync_passphrase), Modifier.testTag("sync:passphrase").settingAnchor("sync.passphrase"), password = true,
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
                        enabled = settings.syncPassphrase.isNotEmpty(), hint = stringResource(R.string.sync_hint), tag = "sync:now", id = "sync.now",
                    )
                }
            }
        }
        SettingHeading(stringResource(R.string.backup_export))
        ActionSetting(
            text = stringResource(R.string.backup_export),
            onClick = { export.launch("metrowin-layout.json") },
            hint = stringResource(R.string.backup_export_hint),
            tag = "backup:export", id = "backup.export",
        )
        ActionSetting(
            text = stringResource(R.string.backup_import),
            onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            hint = stringResource(R.string.backup_import_hint),
            tag = "backup:import", id = "backup.import",
        )
        ActionSetting(
            text = stringResource(R.string.backup_share_layout), onClick = env.onShareLayout,
            hint = stringResource(R.string.backup_share_hint), tag = "backup:share:layout", id = "backup.share",
        )
        ActionSetting(text = stringResource(R.string.backup_share_theme), onClick = env.onShareTheme, tag = "backup:share:theme")
        SettingHeading(stringResource(R.string.apps_backup_heading))
        ActionSetting(
            text = stringResource(R.string.apps_backup_save),
            onClick = { exportApps.launch("metrowin-apps.json") },
            hint = stringResource(R.string.apps_backup_save_hint),
            tag = "backup:apps:save", id = "backup.apps",
        )
        ActionSetting(
            text = stringResource(R.string.apps_backup_read_action),
            onClick = { importApps.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            hint = stringResource(R.string.apps_backup_read_hint),
            tag = "backup:apps:read",
        )
        if (env.missingApps.isNotEmpty()) {
            SettingLabel(stringResource(R.string.apps_backup_missing, env.missingApps.size))
            env.missingApps.forEach { app ->
                MetroText(
                    app.label, size = 20.sp, weight = FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.fillMaxWidth().testTag("backup:apps:missing:${app.packageName}").metroPress(onClick = { env.onOpenStore(app.packageName) }).padding(vertical = 8.dp),
                )
            }
        }
    }
}
