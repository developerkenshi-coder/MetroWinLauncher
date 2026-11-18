package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.ui.components.Spacing
import androidx.compose.ui.res.pluralStringResource
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
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
            label = stringResource(R.string.ac_clear_on_open), checked = s.clearOnOpen,
            onChange = { v -> env.onChange { it.copy(clearOnOpen = v) } },
            hint = stringResource(R.string.ac_clear_on_open_hint), enabled = env.notificationAccess, tag = "notifications:clearonopen", id = "ac.clearonopen",
        )
        ToggleSetting(
            label = stringResource(R.string.ac_priority), checked = s.notifPriority,
            onChange = { v -> env.onChange { it.copy(notifPriority = v) } },
            hint = stringResource(R.string.ac_priority_hint), enabled = env.notificationAccess, tag = "notifications:priority", id = "ac.priority",
        )
        QuietScheduleSettings(env)
        AdvancedGroup(listOf("ac.filter", "ac.filterwords", "ac.dndminutes")) {
        ToggleSetting(
            label = stringResource(R.string.ac_filter), checked = s.notifFilterOn,
            onChange = { v -> env.onChange { it.copy(notifFilterOn = v) } },
            hint = stringResource(R.string.ac_filter_hint), enabled = env.notificationAccess, tag = "notifications:filter", id = "ac.filter",
        )
        FilterWords(env)
        val lengths = listOf(15, 30, 60, 120, 240)
        DropdownSetting(
            label = stringResource(R.string.ac_dnd_minutes),
            options = lengths.map { if (it < 60) pluralStringResource(R.plurals.dnd_minutes, it, it) else stringResource(R.string.dnd_hours, it / 60) },
            selected = lengths.indexOf(lengths.minByOrNull { kotlin.math.abs(it - s.dndMinutes) }),
            onSelect = { i -> env.onChange { it.copy(dndMinutes = lengths[i]) } },
            tag = "notifications:dnd", id = "ac.dndminutes",
        )
        }
    }
}

/** Do not disturb at fixed hours: it needs the "do not disturb" access of Android, so turning it on without that opens the screen that gives it. */
@Composable
private fun QuietScheduleSettings(env: SettingsEnv) {
    val context = LocalContext.current
    val s = env.settings
    fun granted() = context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
    var allowed by remember { mutableStateOf(granted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = granted() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleSetting(
            label = stringResource(R.string.ac_quiet_schedule), checked = s.quietSchedule && allowed,
            onChange = { v ->
                env.onChange { it.copy(quietSchedule = v) }
                if (v && !allowed) runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            hint = stringResource(if (allowed) R.string.ac_quiet_schedule_hint else R.string.ac_quiet_needs), tag = "notifications:quiet", id = "ac.quietschedule",
        )
        // Always listed (the page and the index must agree), but only live while the schedule is on.
        val live = s.quietSchedule && allowed
        run {
            StepperSetting(
                label = stringResource(R.string.ac_quiet_from), value = hourLabel(s.quietFromHour), canDecrement = live, canIncrement = live,
                onDecrement = { env.onChange { it.copy(quietFromHour = (it.quietFromHour + 23) % 24) } },
                onIncrement = { env.onChange { it.copy(quietFromHour = (it.quietFromHour + 1) % 24) } },
                tag = "notifications:quietfrom", id = "ac.quietfrom",
            )
            StepperSetting(
                label = stringResource(R.string.ac_quiet_to), value = hourLabel(s.quietToHour), canDecrement = live, canIncrement = live,
                onDecrement = { env.onChange { it.copy(quietToHour = (it.quietToHour + 23) % 24) } },
                onIncrement = { env.onChange { it.copy(quietToHour = (it.quietToHour + 1) % 24) } },
                tag = "notifications:quietto", id = "ac.quietto",
            )
            ToggleSetting(
                label = stringResource(R.string.ac_quiet_people), checked = s.quietPeople,
                onChange = { v -> env.onChange { it.copy(quietPeople = v) } },
                hint = stringResource(R.string.ac_quiet_people_hint), enabled = live, tag = "notifications:quietpeople", id = "ac.quietpeople",
            )
        }
    }
}

/** The words the filter leaves out, as chips that go away when touched, and a field to add one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterWords(env: SettingsEnv) {
    var word by remember { mutableStateOf("") }
    val words = env.settings.notifFilterWords
    Column(Modifier.settingAnchor("ac.filterwords"), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        SettingLabel(stringResource(R.string.ac_filter_words))
        if (words.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
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
