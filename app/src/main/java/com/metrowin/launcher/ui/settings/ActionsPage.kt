package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.ui.actioncenter.QuickButton
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** Which four buttons sit on top of the Action Center, and whether notifications can be read. */
@Composable
fun ActionsPage(env: SettingsEnv) {
    val colors = LocalMetroColors.current
    val slots = QuickAction.slots(env.settings.quickActions)
    var selected by remember { mutableIntStateOf(0) }
    SettingsPage {
        Column {
            SettingLabel(stringResource(R.string.settings_quick_actions))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slots.forEachIndexed { index, action ->
                    QuickButton(
                        action, on = true, onClick = { selected = index },
                        modifier = Modifier
                            .weight(1f)
                            .then(if (index == selected) Modifier.border(3.dp, colors.foreground) else Modifier),
                    )
                }
            }
            MetroText(
                stringResource(R.string.settings_pick_action),
                size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            )
            QuickAction.entries.chunked(QuickAction.SLOTS).forEach { row ->
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { action ->
                        QuickButton(
                            action, on = action in slots,
                            onClick = {
                                env.onChange { s -> s.copy(quickActions = QuickAction.assign(slots, selected, action).map { it.id }) }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        ToggleSetting(
            label = stringResource(R.string.notif_peek),
            checked = env.settings.notificationPeek,
            onChange = { value -> env.onChange { it.copy(notificationPeek = value) } },
            hint = stringResource(R.string.notif_peek_hint),
            enabled = env.notificationAccess,
            tag = "notifications:peek",
        )
        ToggleSetting(
            label = stringResource(R.string.notif_inline_reply),
            checked = env.settings.inlineReply,
            onChange = { value -> env.onChange { it.copy(inlineReply = value) } },
            hint = stringResource(R.string.notif_inline_reply_hint),
            enabled = env.notificationAccess,
            tag = "notifications:reply",
        )
        ToggleSetting(
            label = stringResource(R.string.notif_group),
            checked = env.settings.groupConversations,
            onChange = { value -> env.onChange { it.copy(groupConversations = value) } },
            hint = stringResource(R.string.notif_group_hint),
            enabled = env.notificationAccess,
            tag = "notifications:group",
        )
        Column {
            SettingLabel(stringResource(R.string.settings_notification_access))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetroText(
                    stringResource(if (env.notificationAccess) R.string.toggle_on else R.string.toggle_off),
                    size = 22.sp, modifier = Modifier.weight(1f),
                )
                MetroButton(
                    stringResource(if (env.notificationAccess) R.string.settings_manage else R.string.ac_turn_on),
                    onClick = env.onManageNotificationAccess,
                )
            }
        }
    }
}
