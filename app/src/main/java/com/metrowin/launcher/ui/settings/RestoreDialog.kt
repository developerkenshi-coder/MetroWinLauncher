package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LayoutBackup
import com.metrowin.launcher.data.RestorePart
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.ThemeCodec
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.Spacing

/** What a backup holds, in numbers, to put beside each part of the restore so the choice is made knowing what changes. */
internal object RestorePreview {
    fun tiles(backup: LayoutBackup): Int = backup.pages.sumOf { it.size }

    /** The look of the backup is the one the phone has already (a part that would change nothing). */
    fun sameLook(backup: LayoutBackup, current: LauncherSettings): Boolean = ThemeCodec.encode(backup.settings) == ThemeCodec.encode(current)

    /** How many settings of the backup are not what this phone has now (the ones that belong to this phone alone are left out of the count). */
    fun settingsChanged(backup: LayoutBackup, current: LauncherSettings): Int {
        val a = org.json.JSONObject(SettingsCodec.encode(backup.settings, portable = true))
        val b = org.json.JSONObject(SettingsCodec.encode(current, portable = true))
        return a.keys().asSequence().count { a.get(it).toString() != b.opt(it)?.toString() }
    }

    /** Things to do, reminders and named layouts in the file; null when the file carries none of the extras at all. */
    fun data(backup: LayoutBackup): Triple<Int, Int, Int>? = backup.extras?.let { Triple(it.todos.size, it.reminders.size, it.layouts.size) }
}

/**
 * Asks which parts of a backup to put back: the Start screens, the settings, how the launcher looks. All of them are chosen to begin with, and
 * each says what the backup holds against what the phone has now ([currentTiles] is the number of tiles on Start now).
 */
@Composable
fun RestoreDialog(
    backup: LayoutBackup, current: LauncherSettings, currentTiles: Int,
    onDone: (Set<RestorePart>) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier,
) {
    var parts by remember { mutableStateOf(RestorePart.entries.toSet()) }
    DialogFrame(onCancel, modifier, title = stringResource(R.string.restore_title)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHint(stringResource(R.string.restore_hint))
            RestorePart.entries.forEach { part ->
                Column {
                    MetroToggle(
                        stringResource(
                            when (part) {
                                RestorePart.Start -> R.string.restore_start
                                RestorePart.Settings -> R.string.restore_settings
                                RestorePart.Theme -> R.string.restore_theme
                                RestorePart.Data -> R.string.restore_data
                            },
                        ),
                        part in parts,
                        onChange = { on -> parts = if (on) parts + part else parts - part },
                        modifier = Modifier.testTag("restore:${part.name}"),
                    )
                    preview(part, backup, current, currentTiles)?.let { SettingHint(it, Modifier.testTag("restore:${part.name}:preview")) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Large)) {
                MetroButton(stringResource(R.string.restore_go), Modifier.weight(1f).testTag("restore:go")) { if (parts.isNotEmpty()) onDone(parts) }
                MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag("restore:cancel"), onClick = onCancel)
            }
        }
    }
}

@Composable
private fun preview(part: RestorePart, backup: LayoutBackup, current: LauncherSettings, currentTiles: Int): String? = when (part) {
    RestorePart.Start -> stringResource(R.string.restore_preview_tiles, RestorePreview.tiles(backup), currentTiles)
    RestorePart.Theme -> stringResource(if (RestorePreview.sameLook(backup, current)) R.string.restore_preview_look_same else R.string.restore_preview_look_differs)
    RestorePart.Data -> RestorePreview.data(backup)?.let { (todos, reminders, layouts) -> stringResource(R.string.restore_preview_data, todos, reminders, layouts) }
        ?: stringResource(R.string.restore_preview_data_none)
    RestorePart.Settings -> stringResource(R.string.restore_preview_settings, RestorePreview.settingsChanged(backup, current))
}
