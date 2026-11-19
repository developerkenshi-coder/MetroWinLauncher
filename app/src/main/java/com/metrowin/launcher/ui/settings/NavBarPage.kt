package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R

/** The MetroWin navigation bar (the three buttons of Windows Phone): the bar itself, the order of its buttons, their spacing and the hold time, and hiding the bars in games. */
@Composable
fun NavBarPage(env: SettingsEnv) {
    val settings = env.settings
    val context = androidx.compose.ui.platform.LocalContext.current
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ToggleSetting(
                label = stringResource(R.string.gest_navbar),
                checked = settings.metroNavBar && env.lockServiceEnabled,
                onChange = { value -> env.onChange { it.copy(metroNavBar = value) }; if (value && !env.lockServiceEnabled) env.onOpenAccessibilitySettings() },
                hint = stringResource(R.string.gest_navbar_hint), tag = "gestures:navbar", id = "gest.navbar",
            )
            if (!com.metrowin.launcher.gestures.NavBarOverlay.threeButtons(context)) SettingHint(stringResource(R.string.gest_navbar_needs_buttons))
            NavOrderSetting(env)
            ToggleSetting(
                label = stringResource(R.string.gest_game_bars),
                checked = settings.hideBarsInGames,
                onChange = { value -> env.onChange { it.copy(hideBarsInGames = value) } },
                hint = stringResource(R.string.gest_game_bars_hint), tag = "gestures:gamebars", id = "gest.gamebars",
            )
            if (settings.hideBarsInGames) GameAppsSetting(env)
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
    }
}

/** One step of the time the logo is held, in milliseconds. */
private const val NAV_HOLD_STEP = 100
