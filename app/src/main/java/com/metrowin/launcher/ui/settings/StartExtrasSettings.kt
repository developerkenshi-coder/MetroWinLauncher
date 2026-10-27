package com.metrowin.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Layouts
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField

/** The extras of the tiles of Start, each with its own switch: the Large size, titles of groups, the collage, the agenda, the time left, the music buttons. */
@Composable
fun StartFeaturesSettings(env: SettingsEnv) {
    val s = env.settings
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingHeading(stringResource(R.string.start_features_title))
        ToggleSetting(
            label = stringResource(R.string.start_large), checked = s.largeTiles,
            onChange = { v -> env.onChange { it.copy(largeTiles = v) } },
            hint = stringResource(R.string.start_large_hint), tag = "start:large", id = "start.large",
        )
        ToggleSetting(
            label = stringResource(R.string.start_groups), checked = s.groupTitles,
            onChange = { v -> env.onChange { it.copy(groupTitles = v) } },
            hint = stringResource(R.string.start_groups_hint), tag = "start:groups", id = "start.groups",
        )
        ToggleSetting(
            label = stringResource(R.string.start_collection), checked = s.collectionTiles,
            onChange = { v -> env.onChange { it.copy(collectionTiles = v) } },
            hint = stringResource(R.string.start_collection_hint), tag = "start:collection", id = "start.collection",
        )
        ToggleSetting(
            label = stringResource(R.string.start_agenda), checked = s.calendarAgenda,
            onChange = { v -> env.onChange { it.copy(calendarAgenda = v) } },
            hint = stringResource(R.string.start_agenda_hint), tag = "start:agenda", id = "start.agenda",
        )
        ToggleSetting(
            label = stringResource(R.string.start_battery_left), checked = s.batteryEstimate,
            onChange = { v -> env.onChange { it.copy(batteryEstimate = v) } },
            hint = stringResource(R.string.start_battery_left_hint), tag = "start:batteryleft", id = "start.batteryleft",
        )
        ToggleSetting(
            label = stringResource(R.string.start_music_controls), checked = s.tileMusicControls,
            onChange = { v -> env.onChange { it.copy(tileMusicControls = v) } },
            hint = stringResource(R.string.start_music_controls_hint), tag = "start:musiccontrols", id = "start.musiccontrols",
        )
    }
}

/** Start screens kept under a name, each with an hour from which it may take over by itself. */
@Composable
fun LayoutsSettings(env: SettingsEnv) {
    var name by remember { mutableStateOf("") }
    val s = env.settings
    val none = stringResource(R.string.layouts_no_hour)
    val hours = remember(none) { listOf(none) + (0..23).map { "%02d:00".format(it) } }
    Column(Modifier.settingAnchor("start.layouts"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingHeading(stringResource(R.string.layouts_title))
        SettingHint(stringResource(R.string.layouts_hint))
        env.layouts.forEach { layout ->
            Column(Modifier.fillMaxWidth().testTag("layouts:row:${layout.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetroText(
                    (if (layout.id == s.activeLayout) "● " else "") + layout.name, size = 22.sp, maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp).testTag("layouts:name:${layout.id}"),
                )
                DropdownSetting(
                    label = stringResource(R.string.layouts_hour), options = hours, selected = layout.hour + 1,
                    onSelect = { i -> env.onLayoutHour(layout.id, i - 1) }, tag = "layouts:hour:${layout.id}",
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetroButton(stringResource(R.string.layouts_apply), Modifier.weight(1f).testTag("layouts:apply:${layout.id}")) { env.onApplyLayout(layout.id) }
                    MetroButton(stringResource(R.string.layouts_update), Modifier.weight(1f).testTag("layouts:update:${layout.id}")) { env.onResaveLayout(layout.id) }
                    MetroButton(stringResource(R.string.layouts_delete), Modifier.weight(1f).testTag("layouts:delete:${layout.id}")) { env.onDeleteLayout(layout.id) }
                }
            }
        }
        if (env.layouts.size < Layouts.MAX) {
            MetroTextField(name, { name = it.take(Layouts.MAX_NAME) }, stringResource(R.string.layouts_name_hint), Modifier.testTag("layouts:name"))
            ActionSetting(
                text = stringResource(R.string.layouts_save), onClick = { if (env.onSaveLayout(name)) name = "" },
                enabled = name.isNotBlank(), tag = "layouts:save",
            )
        }
        ToggleSetting(
            label = stringResource(R.string.layouts_schedule), checked = s.layoutSchedule,
            onChange = { v -> env.onChange { it.copy(layoutSchedule = v) } },
            hint = stringResource(R.string.layouts_schedule_hint), tag = "start:layoutschedule", id = "start.layoutschedule",
        )
    }
}

/** A photo of its own for each Start screen, behind the tiles; one that has none shows the photo chosen for all. */
@Composable
fun PageBackgroundsSettings(env: SettingsEnv) {
    val s = env.settings
    var picking by remember { mutableIntStateOf(-1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && picking >= 0) env.onPickPageBackground(picking, uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleSetting(
            label = stringResource(R.string.page_photos), checked = s.pageBackgrounds,
            onChange = { v -> env.onChange { it.copy(pageBackgrounds = v) } },
            hint = stringResource(R.string.page_photos_hint), tag = "theme:pagephotos", id = "theme.pagebackgrounds",
        )
        if (s.pageBackgrounds) {
            repeat(s.startPages) { page ->
                val has = page in env.pageBackgrounds
                Row(Modifier.fillMaxWidth().testTag("pagephoto:row:$page"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetroText(
                        stringResource(R.string.page_photo_label, page + 1) + if (has) "" else " · " + stringResource(R.string.page_photo_shared),
                        size = 18.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f),
                    )
                    MetroButton(stringResource(R.string.theme_choose_photo), Modifier.testTag("pagephoto:choose:$page")) {
                        picking = page
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (has) MetroButton(stringResource(R.string.theme_remove_photo), Modifier.testTag("pagephoto:remove:$page")) { env.onClearPageBackground(page) }
                }
            }
        }
    }
}
