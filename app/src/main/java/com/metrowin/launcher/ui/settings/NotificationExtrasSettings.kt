package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.pages.PageChip

/** What can be done with the notifications beyond showing them: silence an app, leave out some words, put messages first, a timed quiet mode. */
@Composable
fun NotificationExtrasSettings(env: SettingsEnv) {
    val s = env.settings
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleSetting(
            label = stringResource(R.string.ac_mute), checked = s.muteFromPanel,
            onChange = { v -> env.onChange { it.copy(muteFromPanel = v) } },
            hint = stringResource(R.string.ac_mute_hint), enabled = env.notificationAccess, tag = "notifications:mute", id = "ac.mute",
        )
        ToggleSetting(
            label = stringResource(R.string.ac_priority), checked = s.notifPriority,
            onChange = { v -> env.onChange { it.copy(notifPriority = v) } },
            hint = stringResource(R.string.ac_priority_hint), enabled = env.notificationAccess, tag = "notifications:priority", id = "ac.priority",
        )
        ToggleSetting(
            label = stringResource(R.string.ac_filter), checked = s.notifFilterOn,
            onChange = { v -> env.onChange { it.copy(notifFilterOn = v) } },
            hint = stringResource(R.string.ac_filter_hint), enabled = env.notificationAccess, tag = "notifications:filter", id = "ac.filter",
        )
        FilterWords(env)
        val lengths = listOf(15, 30, 60, 120, 240)
        DropdownSetting(
            label = stringResource(R.string.ac_dnd_minutes),
            options = lengths.map { if (it < 60) stringResource(R.string.dnd_minutes, it) else stringResource(R.string.dnd_hours, it / 60) },
            selected = lengths.indexOf(lengths.minByOrNull { kotlin.math.abs(it - s.dndMinutes) }),
            onSelect = { i -> env.onChange { it.copy(dndMinutes = lengths[i]) } },
            tag = "notifications:dnd", id = "ac.dndminutes",
        )
    }
}

/** The words the filter leaves out, as chips that go away when touched, and a field to add one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterWords(env: SettingsEnv) {
    var word by remember { mutableStateOf("") }
    val words = env.settings.notifFilterWords
    Column(Modifier.settingAnchor("ac.filterwords"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingLabel(stringResource(R.string.ac_filter_words))
        if (words.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                words.forEach { w ->
                    PageChip("$w  ×", false, "filter:word:$w") { env.onChange { s -> s.copy(notifFilterWords = s.notifFilterWords - w) } }
                }
            }
        }
        MetroTextField(word, { word = it.take(MAX_WORD) }, stringResource(R.string.ac_filter_hint_field), Modifier.testTag("filter:field"))
        ActionSetting(
            text = stringResource(R.string.ac_filter_add),
            onClick = {
                val clean = word.trim()
                if (clean.isNotEmpty()) env.onChange { s -> s.copy(notifFilterWords = (s.notifFilterWords + clean).distinct().take(SettingsCodec.NOTIF_WORDS_MAX)) }
                word = ""
            },
            enabled = word.isNotBlank() && words.size < SettingsCodec.NOTIF_WORDS_MAX, tag = "filter:add",
        )
    }
}

private const val MAX_WORD = 30
