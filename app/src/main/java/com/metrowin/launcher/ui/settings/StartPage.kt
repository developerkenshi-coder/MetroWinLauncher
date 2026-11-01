package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.data.WorldCities
import com.metrowin.launcher.data.City
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ClockHours
import com.metrowin.launcher.data.TileSpacing

/** The Start screen itself: its tiles, how many screens there are and where home is, the layouts, and putting it all back as it was. */
@Composable
fun StartPage(env: SettingsEnv) {
    val settings = env.settings
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.start_tiles_title))
            ToggleSetting(
                label = stringResource(R.string.theme_more_tiles),
                checked = settings.moreTiles,
                onChange = { value -> env.onChange { it.copy(moreTiles = value) } },
                id = "start.moretiles",
            )
            ToggleSetting(
                label = stringResource(R.string.theme_tile_names),
                checked = settings.showTileNames,
                onChange = { value -> env.onChange { it.copy(showTileNames = value) } },
                hint = stringResource(R.string.theme_tile_names_hint),
                tag = "start:tilenames",
                id = "start.tilenames",
            )
            DropdownSetting(
                stringResource(R.string.theme_tile_spacing),
                options = TileSpacing.entries.map { stringResource(it.label) },
                selected = TileSpacing.entries.indexOf(settings.tileSpacing),
                onSelect = { i -> env.onChange { it.copy(tileSpacing = TileSpacing.entries[i]) } },
                tag = "start:spacing",
                id = "start.spacing",
            )
            StepperSetting(
                label = stringResource(R.string.theme_start_pages),
                value = settings.startPages.toString(),
                onDecrement = { env.onChange { it.copy(startPages = (it.startPages - 1).coerceAtLeast(1)) } },
                onIncrement = { env.onChange { it.copy(startPages = (it.startPages + 1).coerceAtMost(com.metrowin.launcher.data.LauncherSettings.MAX_START_PAGES)) } },
                canDecrement = settings.startPages > 1,
                canIncrement = settings.startPages < com.metrowin.launcher.data.LauncherSettings.MAX_START_PAGES,
                hint = stringResource(R.string.theme_start_pages_hint),
                tag = "start:pages",
                id = "start.pages",
            )
            // With several screens, one of them is home; the ones before it are to its left, so Start can have a screen on each side.
            if (settings.startPages > 1) {
                // Where the other Start screens are, as seen from the one the launcher opens on: to its right, to its left, or one on each side.
                val last = settings.startPages - 1
                val home = settings.homePage.coerceIn(0, last)
                val options = listOfNotNull(
                    stringResource(R.string.theme_secondary_right),
                    stringResource(R.string.theme_secondary_left),
                    stringResource(R.string.theme_secondary_both).takeIf { settings.startPages > 2 },
                )
                DropdownSetting(
                    label = stringResource(R.string.theme_home_page),
                    options = options,
                    selected = when {
                        home == 0 -> 0
                        home == last -> 1
                        else -> 2
                    },
                    onSelect = { i -> env.onChange { it.copy(homePage = when (i) { 0 -> 0; 1 -> last; else -> 1 }) } },
                    tag = "start:home",
                    id = "start.home",
                )
                SettingHint(stringResource(R.string.theme_home_page_hint))
            }
        }
        ToggleSetting(
            label = stringResource(R.string.theme_apps_left),
            checked = settings.appListLeft,
            onChange = { value -> env.onChange { it.copy(appListLeft = value) } },
            hint = stringResource(R.string.theme_apps_left_hint),
            tag = "start:appsleft",
            id = "start.appsleft",
        )
        StartFeaturesSettings(env)
        LayoutsSettings(env)
        // Two touches: the first one asks, the second one restores.
        var confirmReset by remember { mutableStateOf(false) }
        ActionSetting(
            text = stringResource(if (confirmReset) R.string.theme_reset_start_confirm else R.string.theme_reset_start),
            onClick = {
                if (confirmReset) env.onResetStart()
                confirmReset = !confirmReset
            },
            hint = stringResource(R.string.theme_reset_start_hint),
            tag = "start:resetstart",
            id = "start.reset",
        )
    }
}

/** The strip at the top of the screen: the clock and date above the tiles, the status bar (Android's, hidden, or the one of Windows Phone) and the app bar. */
@Composable
fun StartBarsPage(env: SettingsEnv) {
    val settings = env.settings
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.start_clock_title))
            ToggleSetting(
                label = stringResource(R.string.theme_show_clock),
                checked = settings.showClock,
                onChange = { value -> env.onChange { it.copy(showClock = value) } },
                tag = "start:clock",
                id = "start.clock",
            )
            DropdownSetting(
                stringResource(R.string.clock_hours),
                options = ClockHours.entries.map { stringResource(it.label) },
                selected = ClockHours.entries.indexOf(settings.clockHours),
                onSelect = { i -> env.onChange { it.copy(clockHours = ClockHours.entries[i]) } },
                tag = "start:hours",
                id = "start.hours",
            )
            ToggleSetting(
                label = stringResource(R.string.clock_show_alarm),
                checked = settings.clockAlarm,
                onChange = { value -> env.onChange { it.copy(clockAlarm = value) } },
                enabled = settings.showClock,
                tag = "start:clockalarm",
                id = "start.alarm",
            )
            ToggleSetting(
                label = stringResource(R.string.clock_show_week),
                checked = settings.clockWeek,
                onChange = { value -> env.onChange { it.copy(clockWeek = value) } },
                enabled = settings.showClock,
                tag = "start:clockweek",
                id = "start.week",
            )
            val zones = remember { listOf<City?>(null) + WorldCities.all.sortedBy { it.name } }
            val none = stringResource(R.string.clock_second_none)
            DropdownSetting(
                label = stringResource(R.string.clock_second_zone),
                options = zones.map { it?.name ?: none },
                selected = zones.indexOfFirst { it?.zone == settings.clockZone }.coerceAtLeast(0),
                onSelect = { i -> env.onChange { it.copy(clockZone = zones[i]?.zone.orEmpty()) } },
                tag = "start:zone",
                id = "start.zone",
            )
            ToggleSetting(
                label = stringResource(R.string.theme_clock_taps),
                checked = settings.clockTaps,
                onChange = { value -> env.onChange { it.copy(clockTaps = value) } },
                hint = stringResource(R.string.theme_clock_taps_hint),
                enabled = settings.showClock,
                tag = "start:clocktaps",
                id = "start.clocktaps",
            )
        }
        ToggleSetting(
            label = stringResource(R.string.theme_hide_status_bar),
            checked = settings.hideStatusBar,
            onChange = { value -> env.onChange { it.copy(hideStatusBar = value) } },
            hint = stringResource(R.string.theme_hide_status_bar_hint),
            tag = "start:hidestatus",
            id = "start.hidestatus",
        )
        ToggleSetting(
            label = stringResource(R.string.theme_app_bar),
            checked = settings.appBar,
            onChange = { value -> env.onChange { it.copy(appBar = value) } },
            hint = stringResource(R.string.theme_app_bar_hint),
            tag = "start:appbar",
            id = "start.appbar",
        )
    }
}

/** How Start behaves: when it goes back to the top, what it locks, the undo, and the care of the screen. */
@Composable
fun StartBehaviourPage(env: SettingsEnv) {
    val settings = env.settings
    SettingsPage {
        StartBehaviourSettings(env)
        ToggleSetting(
            label = stringResource(R.string.theme_anti_burn_in),
            checked = settings.antiBurnIn,
            onChange = { value -> env.onChange { it.copy(antiBurnIn = value) } },
            hint = stringResource(R.string.theme_anti_burn_in_hint),
            tag = "start:antiburnin",
            id = "start.antiburnin",
        )
    }
}
