package com.metrowin.launcher.ui.settings

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

/** The Start screen itself: its tiles, the clock and date above them, the status bar, and putting it all back as it was. */
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
        }
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
