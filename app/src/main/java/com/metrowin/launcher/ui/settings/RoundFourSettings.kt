package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.padding
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.data.SectionReset
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.VolumeProfiles
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.IconShape
import kotlin.math.abs

/** A list of numbers to pick one from; a value that is not in the list (a setting that was imported) selects the nearest one. */
@Composable
internal fun NumberDropdown(
    label: String,
    values: List<Int>,
    current: Int,
    format: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    tag: String,
    id: String,
) {
    val selected = values.indices.minByOrNull { abs(values[it] - current) } ?: 0
    DropdownSetting(label = label, options = values.map { format(it) }, selected = selected, onSelect = { onSelect(values[it]) }, tag = tag, id = id)
}

@Composable
private fun minutesOrOff(minutes: Int): String = if (minutes == 0) stringResource(R.string.r4_off) else stringResource(R.string.r4_minutes, minutes)

/** How Start behaves: the step goal, going back to the top, keeping the layout, starting lower for the thumb. */
@Composable
fun StartBehaviourSettings(env: SettingsEnv) {
    val s = env.settings
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingHeading(stringResource(R.string.start_behaviour_title))
        NumberDropdown(
            stringResource(R.string.steps_goal_setting), listOf(0, 5_000, 7_500, 10_000, 12_500, 15_000, 20_000), s.stepGoal,
            format = { if (it == 0) stringResource(R.string.steps_goal_off) else it.toString() },
            onSelect = { v -> env.onChange { it.copy(stepGoal = v) } }, tag = "start:stepgoal", id = "start.stepgoal",
        )
        NumberDropdown(
            stringResource(R.string.start_top_after), listOf(0, 1, 2, 5, 10, 30, 60), s.startTopAfterMin,
            format = { minutesOrOff(it) },
            onSelect = { v -> env.onChange { it.copy(startTopAfterMin = v) } }, tag = "start:topafter", id = "start.topafter",
        )
        SettingHint(stringResource(R.string.start_top_after_hint))
        ToggleSetting(
            label = stringResource(R.string.start_lock), checked = s.lockStart,
            onChange = { v -> env.onChange { it.copy(lockStart = v) } },
            hint = stringResource(R.string.start_lock_hint), tag = "start:lock", id = "start.lock",
        )
        NumberDropdown(
            stringResource(R.string.start_lower), listOf(0, 15, 25, 35, 45), s.startLowerPercent,
            format = { if (it == 0) stringResource(R.string.r4_off) else stringResource(R.string.feel_percent, it) },
            onSelect = { v -> env.onChange { it.copy(startLowerPercent = v) } }, tag = "start:lower", id = "start.lower",
        )
        SettingHint(stringResource(R.string.start_lower_hint))
        NumberDropdown(
            stringResource(R.string.start_undo_steps), listOf(1, 3, 5, 10), s.undoSteps,
            format = { it.toString() },
            onSelect = { v -> env.onChange { it.copy(undoSteps = v) } }, tag = "start:undosteps", id = "start.undosteps",
        )
        ActionSetting(
            text = stringResource(R.string.start_undo_now, env.undoDepth), onClick = env.onUndo,
            enabled = env.undoDepth > 0, hint = stringResource(R.string.start_undo_hint), tag = "start:undonow", id = "start.undonow",
        )
    }
}

/** The shape of the icons, and the colour of an app tile taken from its icon. */
@Composable
fun IconLookSettings(env: SettingsEnv) {
    val s = env.settings
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DropdownSetting(
            label = stringResource(R.string.icons_shape), options = IconShape.entries.map { stringResource(it.label) },
            selected = IconShape.entries.indexOf(s.iconShape),
            onSelect = { i -> env.onChange { it.copy(iconShape = IconShape.entries[i]) } }, tag = "icons:shape", id = "icons.shape",
        )
        ToggleSetting(
            label = stringResource(R.string.icons_tile_colors), checked = s.iconTileColors,
            onChange = { v -> env.onChange { it.copy(iconTileColors = v) } },
            hint = stringResource(R.string.icons_tile_colors_hint), tag = "icons:tilecolors", id = "icons.tilecolors",
        )
    }
}

/** Another accent colour for when the dark theme is on (at night, by the hours or the sun, or always dark). */
@Composable
fun NightAccentSettings(env: SettingsEnv) {
    val s = env.settings
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleSetting(
            label = stringResource(R.string.theme_night_accent), checked = s.accentNightIndex >= 0,
            onChange = { v -> env.onChange { it.copy(accentNightIndex = if (v) s.accentIndex else -1) } },
            hint = stringResource(R.string.theme_night_accent_hint), tag = "theme:nightaccent", id = "theme.nightaccent",
        )
        if (s.accentNightIndex >= 0) {
            AccentGrid(selected = s.accentNightIndex, onSelect = { index -> env.onChange { it.copy(accentNightIndex = index) } })
        }
    }
}

/** The volume presets and the flashlight timer of the Action Center. */
@Composable
fun ActionCenterExtrasSettings(env: SettingsEnv) {
    val s = env.settings
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberDropdown(
            stringResource(R.string.ac_torch_off), listOf(0, 1, 2, 5, 10, 15, 30), s.torchAutoOffMin,
            format = { minutesOrOff(it) },
            onSelect = { v -> env.onChange { it.copy(torchAutoOffMin = v) } }, tag = "actions:torchoff", id = "ac.torchoff",
        )
        SettingHint(stringResource(R.string.ac_torch_off_hint))
        ToggleSetting(
            label = stringResource(R.string.ac_profiles), checked = s.volumeProfilesShow,
            onChange = { v -> env.onChange { it.copy(volumeProfilesShow = v) } },
            hint = stringResource(R.string.ac_profiles_hint), tag = "actions:profiles", id = "ac.profiles",
        )
        s.volumeProfiles.forEachIndexed { index, profile ->
            Row(
                Modifier.fillMaxWidth().testTag("profiles:row:$index"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetroText(
                    stringResource(R.string.ac_profile_line, profile.name, profile.ring, profile.media, profile.alarm),
                    size = 17.sp, weight = FontWeight.Normal, maxLines = 2, modifier = Modifier.weight(1f),
                )
                MetroButton(stringResource(R.string.layouts_delete), Modifier.testTag("profiles:delete:$index")) {
                    env.onChange { it.copy(volumeProfiles = it.volumeProfiles.filterIndexed { i, _ -> i != index }) }
                }
            }
        }
        if (s.volumeProfiles.size < VolumeProfiles.MAX) {
            MetroTextField(
                name, { name = it.take(VolumeProfiles.MAX_NAME) }, stringResource(R.string.ac_profile_name),
                Modifier.testTag("profiles:name"),
            )
            ActionSetting(
                text = stringResource(R.string.ac_profile_save), onClick = { if (env.onSaveVolumeProfile(name)) name = "" },
                enabled = name.isNotBlank(), tag = "profiles:save",
            )
            SettingHint(stringResource(R.string.ac_profile_save_hint))
        }
    }
}

/** What the page on screen needs to be put back to the start: its section, the settings now, and how. */
class PageReset(val section: String, val settings: LauncherSettings, val reset: () -> Unit)

val LocalPageReset = androidx.compose.runtime.compositionLocalOf<PageReset?> { null }

/** How long the first touch on the line waits for the second. */
private const val RESET_ARMED_MS = 4_000L

/**
 * The last line of a page that has been changed: "put this page back to the start". The first touch asks, the second does it. It is
 * at the end of the page so that nothing above it moves when it appears.
 */
@Composable
fun PageResetLine() {
    val page = LocalPageReset.current ?: return
    if (!SectionReset.canReset(page.section) || !SectionReset.changed(page.settings, page.section)) return
    var armed by remember(page.section) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(armed) {
        if (armed) {
            kotlinx.coroutines.delay(RESET_ARMED_MS)
            armed = false
        }
    }
    MetroText(
        stringResource(if (armed) R.string.reset_page_confirm else R.string.reset_page), size = 16.sp, weight = FontWeight.Normal,
        color = com.metrowin.launcher.ui.theme.LocalAccent.current,
        modifier = Modifier
            .testTag("settings:resetpage")
            .metroPress(onClick = {
                if (armed) {
                    page.reset()
                    armed = false
                } else {
                    armed = true
                }
            })
            .padding(vertical = 10.dp),
    )
}
