package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.AppFilter
import com.metrowin.launcher.data.GestureAction
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R

/** The gestures of the launcher: what a pull or a swipe from the edge does, and the feedback of a tap. */
@Composable
fun GesturesPage(env: SettingsEnv) {
    val settings = env.settings
    // With the Action Center off there is nothing for a gesture to open.
    val actions = GestureAction.entries.filter { settings.actionCenterEnabled || it != GestureAction.ActionCenter }
    val actionNames = actions.map { stringResource(it.label) }
    val pickable = remember(env.apps, settings) { AppFilter.visible(env.apps, settings) }
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            DropdownSetting(
                stringResource(R.string.gest_double_tap), actionNames, actions.indexOf(settings.doubleTapAction),
                onSelect = { i -> env.onChange { it.copy(doubleTapAction = actions[i]) } }, tag = "gestures:doubletap", id = "gest.doubletap",
            )
            if (settings.doubleTapAction == GestureAction.OpenApp) {
                AppPickerSetting(
                    stringResource(R.string.gest_app_for, stringResource(R.string.gest_double_tap)), pickable, settings.doubleTapApp,
                    onSelect = { id -> env.onChange { it.copy(doubleTapApp = id) } }, tag = "gestures:doubletap:app",
                )
            }
            DropdownSetting(
                stringResource(R.string.gest_home_double), actionNames, actions.indexOf(settings.homeDoubleAction),
                onSelect = { i -> env.onChange { it.copy(homeDoubleAction = actions[i]) } }, tag = "gestures:homedouble", id = "gest.homedouble",
            )
            if (settings.homeDoubleAction == GestureAction.OpenApp) {
                AppPickerSetting(
                    stringResource(R.string.gest_app_for, stringResource(R.string.gest_home_double)), pickable, settings.homeDoubleApp,
                    onSelect = { id -> env.onChange { it.copy(homeDoubleApp = id) } }, tag = "gestures:homedouble:app",
                )
            }
            DropdownSetting(
                stringResource(R.string.gest_two_down), actionNames, actions.indexOf(settings.twoFingerDownAction),
                onSelect = { i -> env.onChange { it.copy(twoFingerDownAction = actions[i]) } }, tag = "gestures:twodown", id = "gest.twodown",
            )
            if (settings.twoFingerDownAction == GestureAction.OpenApp) {
                AppPickerSetting(
                    stringResource(R.string.gest_app_for, stringResource(R.string.gest_two_down)), pickable, settings.twoFingerDownApp,
                    onSelect = { id -> env.onChange { it.copy(twoFingerDownApp = id) } }, tag = "gestures:twodown:app",
                )
            }
            DropdownSetting(
                stringResource(R.string.gest_two_up), actionNames, actions.indexOf(settings.twoFingerUpAction),
                onSelect = { i -> env.onChange { it.copy(twoFingerUpAction = actions[i]) } }, tag = "gestures:twoup", id = "gest.twoup",
            )
            if (settings.twoFingerUpAction == GestureAction.OpenApp) {
                AppPickerSetting(
                    stringResource(R.string.gest_app_for, stringResource(R.string.gest_two_up)), pickable, settings.twoFingerUpApp,
                    onSelect = { id -> env.onChange { it.copy(twoFingerUpApp = id) } }, tag = "gestures:twoup:app",
                )
            }
            SettingHint(stringResource(R.string.gest_actions_hint))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.gest_recents_title))
            SettingHint(stringResource(R.string.gest_recents_why))
            SettingHint(stringResource(R.string.gest_recents_options))
            env.onAddRecentsTile?.let { add ->
                ActionSetting(stringResource(R.string.gest_recents_tile), onClick = add, tag = "gestures:recentstile", id = "gest.recents")
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
                hint = stringResource(R.string.gest_lock_service_hint), tag = "gestures:lockservice", id = "gest.lockservice",
            )
        }
        BankSafeSetting(env)
        val context = androidx.compose.ui.platform.LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleSetting(
                label = stringResource(R.string.gest_navbar),
                checked = settings.metroNavBar && env.lockServiceEnabled,
                onChange = { value -> env.onChange { it.copy(metroNavBar = value) }; if (value && !env.lockServiceEnabled) env.onOpenAccessibilitySettings() },
                hint = stringResource(R.string.gest_navbar_hint), tag = "gestures:navbar", id = "gest.navbar",
            )
            if (!com.metrowin.launcher.gestures.NavBarOverlay.threeButtons(context)) SettingHint(stringResource(R.string.gest_navbar_needs_buttons))
            ToggleSetting(
                label = stringResource(R.string.gest_game_bars),
                checked = settings.hideBarsInGames,
                onChange = { value -> env.onChange { it.copy(hideBarsInGames = value) } },
                hint = stringResource(R.string.gest_game_bars_hint), tag = "gestures:gamebars", id = "gest.gamebars",
            )
            StepperSetting(
                label = stringResource(R.string.gest_navbar_spacing),
                value = "${settings.navSpacing}%",
                onDecrement = { env.onChange { it.copy(navSpacing = (it.navSpacing - com.metrowin.launcher.data.LauncherSettings.NAV_SPACING_STEP).coerceAtLeast(com.metrowin.launcher.data.LauncherSettings.NAV_SPACING_MIN)) } },
                onIncrement = { env.onChange { it.copy(navSpacing = (it.navSpacing + com.metrowin.launcher.data.LauncherSettings.NAV_SPACING_STEP).coerceAtMost(com.metrowin.launcher.data.LauncherSettings.NAV_SPACING_MAX)) } },
                canDecrement = settings.navSpacing > com.metrowin.launcher.data.LauncherSettings.NAV_SPACING_MIN,
                canIncrement = settings.navSpacing < com.metrowin.launcher.data.LauncherSettings.NAV_SPACING_MAX,
                hint = stringResource(R.string.gest_navbar_spacing_hint), tag = "gestures:navbarspacing", id = "gest.navspacing",
            )
            StepperSetting(
                label = stringResource(R.string.gest_navbar_hold),
                value = "%.1f s".format(settings.navHoldMs / 1000f),
                onDecrement = { env.onChange { it.copy(navHoldMs = (it.navHoldMs - NAV_HOLD_STEP).coerceAtLeast(com.metrowin.launcher.data.LauncherSettings.NAV_HOLD_MIN_MS)) } },
                onIncrement = { env.onChange { it.copy(navHoldMs = (it.navHoldMs + NAV_HOLD_STEP).coerceAtMost(com.metrowin.launcher.data.LauncherSettings.NAV_HOLD_MAX_MS)) } },
                canDecrement = settings.navHoldMs > com.metrowin.launcher.data.LauncherSettings.NAV_HOLD_MIN_MS,
                canIncrement = settings.navHoldMs < com.metrowin.launcher.data.LauncherSettings.NAV_HOLD_MAX_MS,
                hint = stringResource(R.string.gest_navbar_hold_hint), tag = "gestures:navbarhold", id = "gest.navbarhold",
            )
        }
        ToggleSetting(
            label = stringResource(R.string.gest_edge_pull),
            checked = settings.edgePull,
            onChange = { value -> env.onChange { it.copy(edgePull = value) } },
            hint = stringResource(R.string.gest_edge_pull_hint),
            tag = "gestures:edgepull", id = "gest.edgepull",
        )
        ToggleSetting(
            label = stringResource(R.string.gest_edge_back_pan),
            checked = settings.edgeBackPan,
            onChange = { value -> env.onChange { it.copy(edgeBackPan = value) } },
            hint = stringResource(R.string.gest_edge_back_pan_hint),
            tag = "gestures:edgepan", id = "gest.edgeback",
        )
        ToggleSetting(
            label = stringResource(R.string.gest_tap_sound),
            checked = settings.tapSound,
            onChange = { value -> env.onChange { it.copy(tapSound = value) } },
            tag = "gestures:sound", id = "gest.tapsound",
        )
        ToggleSetting(
            label = stringResource(R.string.gest_tap_haptics),
            checked = settings.tapHaptics,
            onChange = { value -> env.onChange { it.copy(tapHaptics = value) } },
            hint = stringResource(R.string.gest_tap_hint),
            tag = "gestures:haptics", id = "gest.taphaptics",
        )
        FeelSettings(env)
    }
}

/** One step of the time the logo is held, in milliseconds. */
private const val NAV_HOLD_STEP = 100
