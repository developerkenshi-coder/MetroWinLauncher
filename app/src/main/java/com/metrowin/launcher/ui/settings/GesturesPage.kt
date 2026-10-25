package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.GestureAction
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R

/** The gestures of the launcher: what a pull or a swipe from the edge does, and the feedback of a tap. */
@Composable
fun GesturesPage(env: SettingsEnv) {
    val settings = env.settings
    val actions = GestureAction.entries
    val actionNames = actions.map { stringResource(it.label) }
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            DropdownSetting(
                stringResource(R.string.gest_double_tap), actionNames, actions.indexOf(settings.doubleTapAction),
                onSelect = { i -> env.onChange { it.copy(doubleTapAction = actions[i]) } }, tag = "gestures:doubletap",
            )
            DropdownSetting(
                stringResource(R.string.gest_two_down), actionNames, actions.indexOf(settings.twoFingerDownAction),
                onSelect = { i -> env.onChange { it.copy(twoFingerDownAction = actions[i]) } }, tag = "gestures:twodown",
            )
            DropdownSetting(
                stringResource(R.string.gest_two_up), actionNames, actions.indexOf(settings.twoFingerUpAction),
                onSelect = { i -> env.onChange { it.copy(twoFingerUpAction = actions[i]) } }, tag = "gestures:twoup",
            )
            SettingHint(stringResource(R.string.gest_actions_hint))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.gest_recents_title))
            SettingHint(stringResource(R.string.gest_recents_why))
            SettingHint(stringResource(R.string.gest_recents_options))
            env.onAddRecentsTile?.let { add ->
                ActionSetting(stringResource(R.string.gest_recents_tile), onClick = add, tag = "gestures:recentstile")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.gest_lock_service))
            InfoSetting(
                label = stringResource(R.string.gest_lock_service),
                value = stringResource(if (env.lockServiceEnabled) R.string.toggle_on else R.string.toggle_off),
            )
            SettingHint(stringResource(if (env.lockServiceEnabled) R.string.gest_lock_service_on else R.string.gest_lock_service_off))
            ActionSetting(
                stringResource(R.string.gest_lock_service_open), onClick = env.onOpenAccessibilitySettings,
                hint = stringResource(R.string.gest_lock_service_hint), tag = "gestures:lockservice",
            )
        }
        ToggleSetting(
            label = stringResource(R.string.gest_edge_pull),
            checked = settings.edgePull,
            onChange = { value -> env.onChange { it.copy(edgePull = value) } },
            hint = stringResource(R.string.gest_edge_pull_hint),
            tag = "gestures:edgepull",
        )
        ToggleSetting(
            label = stringResource(R.string.gest_edge_back_pan),
            checked = settings.edgeBackPan,
            onChange = { value -> env.onChange { it.copy(edgeBackPan = value) } },
            hint = stringResource(R.string.gest_edge_back_pan_hint),
            tag = "gestures:edgepan",
        )
        ToggleSetting(
            label = stringResource(R.string.gest_tap_sound),
            checked = settings.tapSound,
            onChange = { value -> env.onChange { it.copy(tapSound = value) } },
            tag = "gestures:sound",
        )
        ToggleSetting(
            label = stringResource(R.string.gest_tap_haptics),
            checked = settings.tapHaptics,
            onChange = { value -> env.onChange { it.copy(tapHaptics = value) } },
            hint = stringResource(R.string.gest_tap_hint),
            tag = "gestures:haptics",
        )
    }
}
