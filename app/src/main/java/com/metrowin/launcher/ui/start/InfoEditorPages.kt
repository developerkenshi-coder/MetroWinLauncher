package com.metrowin.launcher.ui.start

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.calendar.CalendarInfo
import com.metrowin.launcher.data.AlbumInfo
import com.metrowin.launcher.data.Countdown
import com.metrowin.launcher.data.InfoTileState
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.data.SavedCity
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.InfoConfig
import com.metrowin.launcher.model.InfoLayout
import com.metrowin.launcher.model.InfoPace
import com.metrowin.launcher.model.InfoPart
import com.metrowin.launcher.model.InfoRule
import com.metrowin.launcher.model.InfoSlot
import com.metrowin.launcher.model.InfoTrigger
import com.metrowin.launcher.model.RuleKind
import com.metrowin.launcher.model.TriggerKind
import com.metrowin.launcher.model.infoConfigFromShareText
import com.metrowin.launcher.model.toShareText
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.settings.ActionSetting
import com.metrowin.launcher.ui.settings.OptionList
import com.metrowin.launcher.ui.settings.SettingHeading
import com.metrowin.launcher.ui.settings.SettingHint
import com.metrowin.launcher.ui.settings.SettingsPage
import com.metrowin.launcher.ui.settings.StepperSetting
import com.metrowin.launcher.ui.settings.ToggleSetting
import java.time.LocalDate

/** What the editor asks of the launcher: the things only it can read or do. Every function has a plain default so a preview or a test can leave it out. */
class InfoEditorEnv(
    val albums: suspend () -> List<AlbumInfo> = { emptyList() },
    val calendars: suspend () -> List<CalendarInfo> = { emptyList() },
    /** The notes (id and title) that can be shown: only those without the lock of the phone. */
    val notes: suspend () -> List<Pair<String, String>> = { emptyList() },
    /** The apps that publish something to their tile (package and name). */
    val publishers: () -> List<Pair<String, String>> = { emptyList() },
    val followedCities: () -> List<SavedCity> = { emptyList() },
    val searchPlaces: suspend (String) -> List<PlaceMatch>? = { null },
    val followPlace: (PlaceMatch) -> Unit = {},
    /** Opens the list of widgets; the one chosen is put in the tile (see [InfoConfig.widget]). */
    val pickWidget: () -> Unit = {},
    /** Opens the screen of Android where the usage access is given. */
    val openUsageAccess: () -> Unit = {},
    /** Reads what the tile shows again, after a permission is given or a choice changes what it asks for. */
    val refresh: () -> Unit = {},
    val usageAllowed: () -> Boolean = { false },
)

private val Permissions = mapOf(
    InfoPart.Calendar to LivePermissions.calendar, InfoPart.Week to LivePermissions.calendar, InfoPart.Day to LivePermissions.calendar,
    InfoPart.Photos to LivePermissions.photos, InfoPart.Memories to LivePermissions.photos,
    InfoPart.Birthdays to LivePermissions.contacts, InfoPart.People to LivePermissions.contacts,
    InfoPart.Waiting to LivePermissions.calls,
    InfoPart.Steps to Manifest.permission.ACTIVITY_RECOGNITION,
)

private val NeedsUsage = setOf(InfoPart.DataUse, InfoPart.AppsUsed)

/** Which faces turn on which pages, in the order of the page. */
private val FacesOf: Map<InfoPage, List<InfoPart>> = mapOf(
    InfoPage.FacesDay to listOf(InfoPart.Time, InfoPart.Calendar, InfoPart.Alarm, InfoPart.Week, InfoPart.Day, InfoPart.Countdown, InfoPart.Timer, InfoPart.Sky),
    InfoPage.FacesPhone to listOf(
        InfoPart.Battery, InfoPart.BatteryGraph, InfoPart.Steps, InfoPart.Music, InfoPart.Waiting, InfoPart.Todo, InfoPart.Reminders, InfoPart.Birthdays,
        InfoPart.Note, InfoPart.People, InfoPart.Chat, InfoPart.Published, InfoPart.Parking, InfoPart.DataUse, InfoPart.AppsUsed, InfoPart.Toggles, InfoPart.Widget,
    ),
    InfoPage.FacesNature to listOf(InfoPart.Photos, InfoPart.Memories, InfoPart.Weather, InfoPart.WeatherChart, InfoPart.Advice),
    InfoPage.FacesWeb to listOf(InfoPart.News, InfoPart.Finance),
)

/** The dialogs the pages open over themselves. */
internal sealed interface InfoDialog {
    data object Countdown : InfoDialog
    data object Note : InfoDialog
    data object Published : InfoDialog
    data object ZoneAdd : InfoDialog
    data object HomeZone : InfoDialog
    data object Album : InfoDialog
    data object Calendars : InfoDialog
    data object Exclude : InfoDialog
    data object WeatherCity : InfoDialog
    data object Wifi : InfoDialog
    data class SlotParts(val index: Int) : InfoDialog
    data class TriggerParts(val kind: TriggerKind) : InfoDialog
    data object Rule : InfoDialog
    data object Models : InfoDialog
}

/** The content of one page of the editor. */
@Composable
internal fun InfoPageContent(page: InfoPage, item: AppItem, config: InfoConfig, live: LiveTileData, env: InfoEditorEnv, change: (InfoConfig) -> Unit, open: (InfoDialog) -> Unit) {
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { env.refresh() }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val resolver = context.contentResolver
        val kept = uris.filter { uri -> runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess }.map(Uri::toString)
        if (kept.isNotEmpty()) change(config.copy(picked = kept.take(InfoConfig.MAX_PICKED))).also { env.refresh() }
    }
    SettingsPage(Modifier.testTag("info:page:${page.name}")) {
        when (page) {
            InfoPage.FacesDay, InfoPage.FacesPhone, InfoPage.FacesNature, InfoPage.FacesWeb ->
                FacesOf.getValue(page).forEach { part -> FaceToggle(part, config, live, env, change, askPermission::launch) { setup(part, config, change, open, env) } }

            InfoPage.OrderList -> OrderPage(config, change)
            InfoPage.Smart -> SmartPage(config, change)
            InfoPage.Plans -> PlansPage(config, change, open)
            InfoPage.ClockPage -> ClockPage(config, change, open)
            InfoPage.CalendarPage -> CalendarPage(config, open)
            InfoPage.WeatherPage -> WeatherPage(config, open)
            InfoPage.PhotosPage -> PhotosPage(config, change, open, { pickPhotos.launch(arrayOf("image/*")) })
            InfoPage.MemoriesPage -> MemoriesPage(config, change)
            InfoPage.LookPage -> LookPage(config, change)
            InfoPage.PrivacyPage -> PrivacyPage(config, change)
            InfoPage.AutoPage -> AutoPage(config, change, askPermission::launch, open)
            InfoPage.SharePage -> SharePage(item, config, change, open)
        }
    }
}

// --- the faces

@Composable
private fun FaceToggle(
    part: InfoPart,
    config: InfoConfig,
    live: LiveTileData,
    env: InfoEditorEnv,
    change: (InfoConfig) -> Unit,
    ask: (String) -> Unit,
    setup: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val id = InfoIndex.faces.getValue(part)
    val on = part in config.parts
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleSetting(
            label = stringResource(InfoIndex.faceTitle(part)), checked = on,
            onChange = { wanted ->
                if (wanted != on) change(config.toggled(part))
                if (wanted) Permissions[part]?.let { permission -> if (!LivePermissions.isGranted(context, permission) && !(permission == LivePermissions.photos && LivePermissions.canReadPhotos(context))) ask(permission) }
                if (wanted && part in NeedsUsage && !env.usageAllowed()) env.openUsageAccess()
            },
            tag = "info:${part.key}", id = id,
        )
        if (on) {
            val permission = Permissions[part]
            val missing = permission != null && !LivePermissions.isGranted(context, permission) && !(permission == LivePermissions.photos && LivePermissions.canReadPhotos(context))
            when {
                missing -> {
                    SettingHint(stringResource(R.string.infoe_status_permission), Modifier.testTag("info:status:${part.key}"))
                    ActionSetting(stringResource(R.string.infoe_allow), { ask(permission!!) }, tag = "info:allow:${part.key}")
                }
                part in NeedsUsage && !env.usageAllowed() -> {
                    SettingHint(stringResource(R.string.infoe_status_usage), Modifier.testTag("info:status:${part.key}"))
                    ActionSetting(stringResource(R.string.infoe_allow), env.openUsageAccess, tag = "info:allow:${part.key}")
                }
                !part.hasData(config, live, infoEvents(config, live, System.currentTimeMillis()), System.currentTimeMillis()) && part != InfoPart.Timer && part != InfoPart.Toggles ->
                    SettingHint(stringResource(R.string.infoe_status_empty), Modifier.testTag("info:status:${part.key}"))
            }
            setup()
        }
    }
}

/** What a face needs to be told beyond being on: its date, its note, its app, its widget. */
@Composable
private fun ColumnScope.setup(part: InfoPart, config: InfoConfig, change: (InfoConfig) -> Unit, open: (InfoDialog) -> Unit, env: InfoEditorEnv) {
    when (part) {
        InfoPart.Countdown -> ActionSetting(
            if (config.countdownDay == 0L) stringResource(R.string.infoe_choose) else config.countdownName.ifEmpty { Countdown.date(config.countdownDay).toString() },
            { open(InfoDialog.Countdown) }, tag = "info:setup:countdown", id = "setup.countdown",
        )
        InfoPart.Note -> ActionSetting(
            if (config.note.isEmpty()) stringResource(R.string.infoe_choose) else stringResource(R.string.infoe_chosen),
            { open(InfoDialog.Note) }, tag = "info:setup:note", id = "setup.note",
        )
        InfoPart.Published -> ActionSetting(
            env.publishers().firstOrNull { it.first == config.published }?.second ?: stringResource(R.string.infoe_choose),
            { open(InfoDialog.Published) }, tag = "info:setup:published", id = "setup.published",
        )
        InfoPart.Widget -> ActionSetting(
            if (config.widget == 0) stringResource(R.string.infoe_choose) else stringResource(R.string.infoe_widget_change),
            env.pickWidget, tag = "info:setup:widget", id = "setup.widget",
        )
        else -> Unit
    }
}

// --- order and pace

@Composable
private fun OrderPage(config: InfoConfig, change: (InfoConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SettingHeading(stringResource(R.string.infos_order_list), infoId = "order.list")
        config.parts.forEachIndexed { index, part ->
            Row(Modifier.fillMaxWidth().testTag("info:order:${part.key}"), verticalAlignment = Alignment.CenterVertically) {
                MetroText(stringResource(InfoIndex.faceTitle(part)), size = 19.sp, weight = FontWeight.Normal, maxLines = 2, modifier = Modifier.weight(1f))
                OrderButton("↑", stringResource(R.string.infoe_move_up), index > 0, "info:up:${part.key}") { change(config.moved(part, -1)) }
                OrderButton("↓", stringResource(R.string.infoe_move_down), index < config.parts.size - 1, "info:down:${part.key}") { change(config.moved(part, 1)) }
            }
        }
    }
    OptionList(
        label = stringResource(R.string.infos_order_pace),
        options = listOf(R.string.info_pace_slow, R.string.info_pace_normal, R.string.info_pace_fast).map { stringResource(it) },
        selected = listOf(InfoPace.Slow, InfoPace.Normal, InfoPace.Fast).indexOf(config.pace),
        onSelect = { change(config.copy(pace = listOf(InfoPace.Slow, InfoPace.Normal, InfoPace.Fast)[it])) },
        tag = "info:pace", id = "order.pace",
    )
    ToggleSetting(stringResource(R.string.infos_order_dwell), config.dwell, { change(config.copy(dwell = it)) }, tag = "info:dwell", id = "order.dwell")
}

@Composable
private fun OrderButton(symbol: String, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val colors = com.metrowin.launcher.ui.theme.LocalMetroColors.current
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(48.dp)
            .testTag(tag)
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            }
            .then(if (enabled) Modifier.metroPress(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { MetroText(symbol, size = 24.sp, weight = FontWeight.Normal, color = if (enabled) colors.foreground else colors.subtle) }
}

@Composable
private fun SmartPage(config: InfoConfig, change: (InfoConfig) -> Unit) {
    ToggleSetting(stringResource(R.string.infos_smart_on), config.smart, { change(config.copy(smart = it)) }, hint = stringResource(R.string.infoe_smart_hint), tag = "info:smart", id = "smart.on")
    ToggleSetting(stringResource(R.string.infos_smart_saver), config.saver, { change(config.copy(saver = it)) }, tag = "info:saver", id = "smart.saver")
    val soon = listOf(15, 30, 60, 120)
    val at = soon.indexOf(config.soonMinutes).let { if (it < 0) 2 else it }
    StepperSetting(
        label = stringResource(R.string.infos_smart_soon), value = stringResource(R.string.infoe_minutes, soon[at]),
        onDecrement = { change(config.copy(soonMinutes = soon[(at - 1).coerceAtLeast(0)])) }, onIncrement = { change(config.copy(soonMinutes = soon[(at + 1).coerceAtMost(soon.size - 1)])) },
        canDecrement = at > 0, canIncrement = at < soon.size - 1, tag = "info:soon", id = "smart.soon",
    )
    StepperSetting(
        label = stringResource(R.string.infoe_night_from), value = "%02d:00".format(config.nightFrom),
        onDecrement = { change(config.copy(nightFrom = (config.nightFrom + 23) % 24)) }, onIncrement = { change(config.copy(nightFrom = (config.nightFrom + 1) % 24)) },
        tag = "info:night0", id = "smart.night",
    )
    StepperSetting(
        label = stringResource(R.string.infoe_night_to), value = "%02d:00".format(config.nightTo),
        onDecrement = { change(config.copy(nightTo = (config.nightTo + 23) % 24)) }, onIncrement = { change(config.copy(nightTo = (config.nightTo + 1) % 24)) },
        tag = "info:night1",
    )
    ToggleSetting(stringResource(R.string.infos_smart_battery), config.smartBattery, { change(config.copy(smartBattery = it)) }, tag = "info:smartbattery", id = "smart.battery")
    ToggleSetting(stringResource(R.string.infos_smart_rain), config.smartRain, { change(config.copy(smartRain = it)) }, tag = "info:smartrain", id = "smart.rain")
}

@Composable
private fun PlansPage(config: InfoConfig, change: (InfoConfig) -> Unit, open: (InfoDialog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingHeading(stringResource(R.string.infos_plan_slots), infoId = "plan.slots")
        config.slots.forEachIndexed { index, slot ->
            Column(Modifier.testTag("info:slot:$index"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StepperSetting(
                    label = stringResource(R.string.infoe_slot_from), value = "%02d:00".format(slot.hour),
                    onDecrement = { change(config.copy(slots = config.slots.replace(index, slot.copy(hour = (slot.hour + 23) % 24)))) },
                    onIncrement = { change(config.copy(slots = config.slots.replace(index, slot.copy(hour = (slot.hour + 1) % 24)))) },
                    tag = "info:slot:$index:hour",
                )
                ActionSetting(slot.parts.map { stringResource(InfoIndex.faceTitle(it)) }.joinToString(", "), { open(InfoDialog.SlotParts(index)) }, tag = "info:slot:$index:parts")
                ActionSetting(stringResource(R.string.infoe_remove), { change(config.copy(slots = config.slots.filterIndexed { i, _ -> i != index })) }, tag = "info:slot:$index:remove")
            }
        }
        if (config.slots.size < InfoConfig.MAX_SLOTS) {
            ActionSetting(stringResource(R.string.infoe_slot_add), { change(config.copy(slots = config.slots + InfoSlot(7 + config.slots.size * 5, config.parts.take(2).ifEmpty { listOf(InfoPart.Time) }))) }, tag = "info:slot:add")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingHeading(stringResource(R.string.infos_plan_rules), infoId = "plan.rules")
        config.rules.forEachIndexed { index, rule ->
            Row(Modifier.fillMaxWidth().testTag("info:rule:$index"), verticalAlignment = Alignment.CenterVertically) {
                MetroText(ruleText(rule), size = 17.sp, weight = FontWeight.Normal, maxLines = 3, modifier = Modifier.weight(1f))
                OrderButton("×", stringResource(R.string.infoe_remove), true, "info:rule:$index:remove") { change(config.copy(rules = config.rules.filterIndexed { i, _ -> i != index })) }
            }
        }
        if (config.rules.size < InfoConfig.MAX_RULES) ActionSetting(stringResource(R.string.infoe_rule_add), { open(InfoDialog.Rule) }, tag = "info:rule:add")
    }
}

private fun <T> List<T>.replace(index: Int, value: T) = mapIndexed { i, v -> if (i == index) value else v }

@Composable
internal fun ruleText(rule: InfoRule): String = stringResource(
    R.string.infoe_rule_text, stringResource(InfoIndex.faceTitle(rule.part)),
    when (rule.kind) {
        RuleKind.BatteryBelow -> stringResource(R.string.infoe_rule_battery, rule.a)
        RuleKind.Charging -> stringResource(R.string.infoe_rule_charging)
        RuleKind.EventWithin -> stringResource(R.string.infoe_rule_event, rule.a)
        RuleKind.Raining -> stringResource(R.string.infoe_rule_rain)
        RuleKind.NotBetween -> stringResource(R.string.infoe_rule_not_between, "%02d:00".format(rule.a), "%02d:00".format(rule.b))
    },
)

// --- the time, the calendar, the weather

@Composable
private fun ClockPage(config: InfoConfig, change: (InfoConfig) -> Unit, open: (InfoDialog) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingHeading(stringResource(R.string.infos_clock_zones), infoId = "clock.zones")
        if (config.zones.isEmpty()) SettingHint(stringResource(R.string.info_zone_phone))
        config.zones.forEachIndexed { index, zone ->
            Row(Modifier.fillMaxWidth().testTag("info:zone:$index"), verticalAlignment = Alignment.CenterVertically) {
                MetroText(config.zoneNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: zone.substringAfter('/').replace('_', ' '), size = 19.sp, weight = FontWeight.Normal, modifier = Modifier.weight(1f))
                OrderButton("×", stringResource(R.string.infoe_remove), true, "info:zone:$index:remove") {
                    change(config.copy(zones = config.zones.filterIndexed { i, _ -> i != index }, zoneNames = config.zoneNames.filterIndexed { i, _ -> i != index }))
                }
            }
        }
        if (config.zones.size < MAX_ZONES) ActionSetting(stringResource(R.string.infoe_zone_add), { open(InfoDialog.ZoneAdd) }, tag = "info:zone:add")
    }
    ToggleSetting(stringResource(R.string.infos_clock_analog), config.analog, { change(config.copy(analog = it)) }, tag = "info:analog", id = "clock.analog")
    ToggleSetting(stringResource(R.string.infos_clock_timers), config.quickTimers, { change(config.copy(quickTimers = it)) }, tag = "info:quicktimers", id = "clock.timers")
    ToggleSetting(stringResource(R.string.infos_clock_background), config.photoBackground, { change(config.copy(photoBackground = it)) }, tag = "info:clockphoto", id = "clock.background")
}

private const val MAX_ZONES = 3

@Composable
private fun CalendarPage(config: InfoConfig, open: (InfoDialog) -> Unit) {
    ActionSetting(
        stringResource(R.string.infoe_calendars_value, if (config.calendars.isEmpty()) stringResource(R.string.infoe_all) else config.calendars.size.toString()),
        { open(InfoDialog.Calendars) }, tag = "info:calendars", id = "cal.calendars",
    )
}

@Composable
private fun WeatherPage(config: InfoConfig, open: (InfoDialog) -> Unit) {
    ActionSetting(
        stringResource(R.string.infoe_city_value, config.weatherCityName.ifEmpty { stringResource(R.string.infoe_city_tile) }),
        { open(InfoDialog.WeatherCity) }, tag = "info:city", id = "wx.city",
    )
}

// --- photos

@Composable
private fun PhotosPage(config: InfoConfig, change: (InfoConfig) -> Unit, open: (InfoDialog) -> Unit, pick: () -> Unit) {
    ActionSetting(
        stringResource(R.string.info_album, config.albumName.ifEmpty { stringResource(R.string.info_album_latest) }),
        { open(InfoDialog.Album) }, tag = "info:album", id = "photos.album",
    )
    if (config.album.isNotEmpty()) ActionSetting(stringResource(R.string.info_album_reset), { change(config.copy(album = "", albumName = "")) }, tag = "info:album:reset")
    ActionSetting(
        if (config.picked.isEmpty()) stringResource(R.string.infoe_picked_none) else stringResource(R.string.infoe_picked_count, config.picked.size),
        pick, tag = "info:picked", id = "photos.picked",
    )
    if (config.picked.isNotEmpty()) ActionSetting(stringResource(R.string.infoe_picked_clear), { change(config.copy(picked = emptyList())) }, tag = "info:picked:clear")
    ToggleSetting(stringResource(R.string.infos_photos_favorites), config.favorites, { change(config.copy(favorites = it)) }, tag = "info:favorites", id = "photos.favorites")
    ToggleSetting(stringResource(R.string.infos_photos_noshots), config.noShots, { change(config.copy(noShots = it)) }, tag = "info:noshots", id = "photos.noshots")
    ActionSetting(
        if (config.excluded.isEmpty()) stringResource(R.string.infoe_exclude_none) else stringResource(R.string.infoe_exclude_count, config.excluded.size),
        { open(InfoDialog.Exclude) }, tag = "info:exclude", id = "photos.exclude",
    )
}

@Composable
private fun MemoriesPage(config: InfoConfig, change: (InfoConfig) -> Unit) {
    StepperSetting(
        label = stringResource(R.string.infos_mem_days), value = if (config.memoryDays == 0) stringResource(R.string.infoe_today_only) else "± ${config.memoryDays}",
        onDecrement = { change(config.copy(memoryDays = (config.memoryDays - 1).coerceAtLeast(0))) },
        onIncrement = { change(config.copy(memoryDays = (config.memoryDays + 1).coerceAtMost(7))) },
        canDecrement = config.memoryDays > 0, canIncrement = config.memoryDays < 7, tag = "info:memdays", id = "mem.days",
    )
}

// --- look, privacy and what it does by itself

@Composable
private fun LookPage(config: InfoConfig, change: (InfoConfig) -> Unit) {
    ToggleSetting(stringResource(R.string.infos_look_dots), config.dots, { change(config.copy(dots = it)) }, tag = "info:dots", id = "look.dots")
    ToggleSetting(stringResource(R.string.infos_look_tint), config.tint, { change(config.copy(tint = it)) }, tag = "info:tint", id = "look.tint")
    ToggleSetting(stringResource(R.string.infos_look_share), config.shareMemory, { change(config.copy(shareMemory = it)) }, tag = "info:sharememory", id = "look.share")
    OptionList(
        label = stringResource(R.string.infos_look_layout),
        options = listOf(stringResource(R.string.infoe_layout_turn), stringResource(R.string.infoe_layout_board)),
        selected = if (config.layout == InfoLayout.Turn) 0 else 1,
        onSelect = { change(config.copy(layout = if (it == 0) InfoLayout.Turn else InfoLayout.Dashboard)) },
        hint = stringResource(R.string.infoe_layout_hint), tag = "info:layout", id = "look.layout",
    )
}

@Composable
private fun PrivacyPage(config: InfoConfig, change: (InfoConfig) -> Unit) {
    ToggleSetting(stringResource(R.string.infos_priv_titles), config.hideTitles, { change(config.copy(hideTitles = it)) }, tag = "info:hidetitles", id = "priv.titles")
    ToggleSetting(stringResource(R.string.infos_priv_photos), config.hidePhotos, { change(config.copy(hidePhotos = it)) }, tag = "info:hidephotos", id = "priv.photos")
    ToggleSetting(stringResource(R.string.infos_priv_kids), config.kids, { change(config.copy(kids = it)) }, tag = "info:kids", id = "priv.kids")
}

@Composable
private fun AutoPage(config: InfoConfig, change: (InfoConfig) -> Unit, ask: (String) -> Unit, open: (InfoDialog) -> Unit) {
    val context = LocalContext.current
    ToggleSetting(stringResource(R.string.infos_auto_dnd), config.dnd, { change(config.copy(dnd = it)) }, tag = "info:dnd", id = "auto.dnd")
    ToggleSetting(stringResource(R.string.infos_auto_travel), config.travel, { change(config.copy(travel = it)) }, tag = "info:travel", id = "auto.travel")
    if (config.travel) {
        ActionSetting(
            stringResource(R.string.infoe_home_value, config.homeZone.substringAfter('/').replace('_', ' ').ifEmpty { stringResource(R.string.infoe_choose) }),
            { open(InfoDialog.HomeZone) }, tag = "info:home",
        )
    }
    val wifi = config.triggers.firstOrNull { it.kind == TriggerKind.Wifi }
    ToggleSetting(
        stringResource(R.string.infos_auto_wifi), wifi != null,
        { on ->
            change(config.copy(triggers = if (on) config.triggers + InfoTrigger(TriggerKind.Wifi, "", listOf(InfoPart.Time, InfoPart.Photos)) else config.triggers.filter { it.kind != TriggerKind.Wifi }))
            if (on && !LivePermissions.isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)) ask(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        tag = "info:wifi", id = "auto.wifi",
    )
    if (wifi != null) {
        ActionSetting(wifi.value.ifEmpty { stringResource(R.string.infoe_wifi_name) }, { open(InfoDialog.Wifi) }, tag = "info:wifi:name")
        ActionSetting(wifi.parts.map { stringResource(InfoIndex.faceTitle(it)) }.joinToString(", "), { open(InfoDialog.TriggerParts(TriggerKind.Wifi)) }, tag = "info:wifi:parts")
    }
    val bluetooth = config.triggers.firstOrNull { it.kind == TriggerKind.Bluetooth }
    ToggleSetting(
        stringResource(R.string.infos_auto_bt), bluetooth != null,
        { on -> change(config.copy(triggers = if (on) config.triggers + InfoTrigger(TriggerKind.Bluetooth, "", listOf(InfoPart.Time, InfoPart.Weather, InfoPart.Parking)) else config.triggers.filter { it.kind != TriggerKind.Bluetooth })) },
        tag = "info:bt", id = "auto.bt",
    )
    if (bluetooth != null) ActionSetting(bluetooth.parts.map { stringResource(InfoIndex.faceTitle(it)) }.joinToString(", "), { open(InfoDialog.TriggerParts(TriggerKind.Bluetooth)) }, tag = "info:bt:parts")
    StepperSetting(
        label = stringResource(R.string.infos_auto_brief), value = if (config.briefHour < 0) stringResource(R.string.infoe_off) else "%02d:00".format(config.briefHour),
        onDecrement = { change(config.copy(briefHour = if (config.briefHour <= 0) -1 else config.briefHour - 1)) },
        onIncrement = { change(config.copy(briefHour = if (config.briefHour < 0) 6 else (config.briefHour + 1).coerceAtMost(23))) },
        canDecrement = config.briefHour >= 0, canIncrement = config.briefHour < 23, tag = "info:brief", id = "auto.brief",
    )
}

// --- models and sharing

@Composable
private fun SharePage(item: AppItem, config: InfoConfig, change: (InfoConfig) -> Unit, open: (InfoDialog) -> Unit) {
    val context = LocalContext.current
    var pasted by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    ActionSetting(stringResource(R.string.infos_share_presets), { open(InfoDialog.Models) }, tag = "info:presets", id = "share.presets")
    ActionSetting(
        stringResource(R.string.infos_share_send),
        {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, config.toShareText())
            runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        tag = "info:send", id = "share.send", hint = stringResource(R.string.infoe_send_hint),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingHeading(stringResource(R.string.infos_share_import), infoId = "share.import")
        MetroTextField(pasted, { pasted = it; wrong = false }, stringResource(R.string.infoe_import_hint), Modifier.testTag("info:import:text"))
        if (wrong) SettingHint(stringResource(R.string.infoe_import_wrong), Modifier.testTag("info:import:wrong"))
        MetroButton(stringResource(R.string.infoe_import_use), Modifier.fillMaxWidth().testTag("info:import:use")) {
            val parsed = infoConfigFromShareText(pasted)
            if (parsed == null) wrong = true else { change(parsed); pasted = "" }
        }
    }
    ActionSetting(stringResource(R.string.infos_share_speak), { InfoSpeech.speak(context, InfoTileState.summary(item.id).orEmpty()) }, tag = "info:speak", id = "share.speak")
    ActionSetting(stringResource(R.string.infos_share_reset), { change(InfoConfig()) }, tag = "info:reset", id = "share.reset")
    // The models dialog is opened by the editor itself, outside this scrolling page (a dialog with its own scroll cannot sit inside one).
}

// --- the dialogs

@Composable
internal fun InfoDialogs(dialog: InfoDialog, config: InfoConfig, change: (InfoConfig) -> Unit, env: InfoEditorEnv, close: () -> Unit) {
    val context = LocalContext.current
    when (dialog) {
        InfoDialog.Countdown -> CountdownDialog(
            initialTitle = config.countdownName,
            initialDay = config.countdownDay.takeIf { it != 0L } ?: Countdown.defaultDay(LocalDate.now()),
            onDone = { name, day -> change(config.copy(countdownName = name, countdownDay = day)); close() },
            onCancel = close,
        )
        InfoDialog.Note -> {
            val notes by produceState<List<Pair<String, String>>?>(null) { value = runCatching { env.notes() }.getOrDefault(emptyList()) }
            ChoiceDialog(stringResource(R.string.infoe_note_title), notes.orEmpty(), setOf(config.note), false, emptyText = stringResource(R.string.infoe_note_none), onDone = { change(config.copy(note = it.firstOrNull().orEmpty())); close() }, onCancel = close)
        }
        InfoDialog.Published -> ChoiceDialog(
            stringResource(R.string.infoe_published_title), env.publishers(), setOf(config.published), false, emptyText = stringResource(R.string.infoe_published_none),
            onDone = { change(config.copy(published = it.firstOrNull().orEmpty())); close() }, onCancel = close,
        )
        InfoDialog.ZoneAdd -> CityPicker(
            onPick = { city -> change(config.copy(zones = config.zones + city.zone, zoneNames = config.zoneNames + city.name)); close() },
            onDismiss = close,
        )
        InfoDialog.HomeZone -> CityPicker(onPick = { city -> change(config.copy(homeZone = city.zone)); close() }, onDismiss = close)
        InfoDialog.Album -> {
            var granted by remember { mutableStateOf(LivePermissions.canReadPhotos(context)) }
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = LivePermissions.canReadPhotos(context); env.refresh() }
            val albums by produceState<List<AlbumInfo>?>(null, granted) { value = if (granted) env.albums() else emptyList() }
            AlbumPicker(albums = albums, permissionMissing = !granted, onAllow = { ask.launch(LivePermissions.photos) }, onPick = { album -> change(config.copy(album = album.id, albumName = album.name)); close() }, onDismiss = close)
        }
        InfoDialog.Calendars -> {
            val calendars by produceState<List<CalendarInfo>?>(null) { value = runCatching { env.calendars() }.getOrDefault(emptyList()) }
            ChoiceDialog(
                stringResource(R.string.infoe_calendars_title), calendars.orEmpty().map { it.id.toString() to it.name.ifBlank { it.account } }, config.calendars.map { it.toString() }.toSet(), true,
                emptyText = stringResource(R.string.infoe_calendars_none),
                onDone = { change(config.copy(calendars = it.mapNotNull(String::toLongOrNull))); env.refresh(); close() }, onCancel = close,
            )
        }
        InfoDialog.Exclude -> {
            val albums by produceState<List<AlbumInfo>?>(null) { value = runCatching { env.albums() }.getOrDefault(emptyList()) }
            ChoiceDialog(
                stringResource(R.string.infoe_exclude_title), albums.orEmpty().map { it.id to it.name }, config.excluded.toSet(), true, emptyText = stringResource(R.string.album_empty),
                onDone = { change(config.copy(excluded = it.toList())); env.refresh(); close() }, onCancel = close,
            )
        }
        InfoDialog.WeatherCity -> CityChoiceDialog(
            env, config.weatherCity,
            onPick = { key, name -> change(config.copy(weatherCity = key, weatherCityName = name)); env.refresh(); close() },
            onCancel = close,
        )
        InfoDialog.Wifi -> TextDialog(
            stringResource(R.string.infoe_wifi_name), config.triggers.firstOrNull { it.kind == TriggerKind.Wifi }?.value.orEmpty(), stringResource(R.string.infoe_wifi_hint),
            onDone = { name -> change(config.copy(triggers = config.triggers.map { if (it.kind == TriggerKind.Wifi) it.copy(value = name.trim()) else it })); close() },
            onCancel = close,
        )
        is InfoDialog.SlotParts -> PartsDialog(config.slots.getOrNull(dialog.index)?.parts.orEmpty(), { parts -> change(config.copy(slots = config.slots.replace(dialog.index, config.slots[dialog.index].copy(parts = parts)))); close() }, close)
        is InfoDialog.TriggerParts -> PartsDialog(
            config.triggers.firstOrNull { it.kind == dialog.kind }?.parts.orEmpty(),
            { parts -> change(config.copy(triggers = config.triggers.map { if (it.kind == dialog.kind) it.copy(parts = parts) else it })); close() }, close,
        )
        InfoDialog.Models -> InfoModelDialog(onPick = { change(it); close() }, onCancel = close)
        InfoDialog.Rule -> RuleDialog(onDone = { rule -> change(config.copy(rules = config.rules + rule)); close() }, onCancel = close)
    }
}

/** The faces, to choose some of: for a plan, an hour, a network. At least one stays. */
@Composable
private fun PartsDialog(selected: List<InfoPart>, onDone: (List<InfoPart>) -> Unit, onCancel: () -> Unit) {
    val items = InfoPart.entries.map { it.key to stringResource(InfoIndex.faceTitle(it)) }
    ChoiceDialog(
        stringResource(R.string.infoe_parts_title), items, selected.map { it.key }.toSet(), true, emptyText = "",
        onDone = { keys -> onDone(InfoPart.entries.filter { it.key in keys }.ifEmpty { listOf(InfoPart.Time) }) }, onCancel = onCancel,
    )
}

/** The models to start from (see [com.metrowin.launcher.model.InfoPreset]). */
@Composable
internal fun InfoModelDialog(onPick: (InfoConfig) -> Unit, onCancel: () -> Unit) {
    com.metrowin.launcher.ui.components.DialogFrame(onCancel = onCancel, title = stringResource(R.string.infoe_models_title)) {
        Column(Modifier.fillMaxWidth().testTag("info:models"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetroText(stringResource(R.string.infoe_models_text), size = 16.sp, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE, lineHeight = 22.sp)
            com.metrowin.launcher.model.InfoPreset.entries.forEach { preset ->
                MetroButton(
                    stringResource(
                        when (preset) {
                            com.metrowin.launcher.model.InfoPreset.Morning -> R.string.infom_morning
                            com.metrowin.launcher.model.InfoPreset.Travel -> R.string.infom_travel
                            com.metrowin.launcher.model.InfoPreset.Night -> R.string.infom_night
                            com.metrowin.launcher.model.InfoPreset.Essential -> R.string.infom_essential
                            com.metrowin.launcher.model.InfoPreset.Standard -> R.string.infom_standard
                        },
                    ),
                    Modifier.fillMaxWidth().testTag("info:model:${preset.name.lowercase()}"),
                ) { onPick(preset.config) }
            }
        }
    }
}

/** A rule for a face: which face, when it shows. */
@Composable
private fun RuleDialog(onDone: (InfoRule) -> Unit, onCancel: () -> Unit) {
    var part by remember { mutableStateOf(InfoPart.Battery) }
    var kind by remember { mutableStateOf(RuleKind.BatteryBelow) }
    var a by remember { mutableStateOf(20) }
    var b by remember { mutableStateOf(7) }
    com.metrowin.launcher.ui.components.DialogFrame(onCancel = onCancel, title = stringResource(R.string.infoe_rule_title)) {
        Column(Modifier.fillMaxWidth().testTag("info:ruledialog"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.metrowin.launcher.ui.settings.DropdownSetting(
                label = stringResource(R.string.infoe_rule_face), options = InfoPart.entries.map { stringResource(InfoIndex.faceTitle(it)) }, selected = InfoPart.entries.indexOf(part),
                onSelect = { part = InfoPart.entries[it] }, tag = "info:rule:face",
            )
            com.metrowin.launcher.ui.settings.DropdownSetting(
                label = stringResource(R.string.infoe_rule_when),
                options = RuleKind.entries.map { ruleKindText(it) }, selected = RuleKind.entries.indexOf(kind),
                onSelect = { kind = RuleKind.entries[it]; a = when (kind) { RuleKind.BatteryBelow -> 20; RuleKind.EventWithin -> 60; RuleKind.NotBetween -> 22; else -> 0 }; b = 7 },
                tag = "info:rule:kind",
            )
            when (kind) {
                RuleKind.BatteryBelow -> StepperSetting(stringResource(R.string.infoe_rule_number_percent), "$a%", { a = (a - 5).coerceAtLeast(5) }, { a = (a + 5).coerceAtMost(95) }, tag = "info:rule:a")
                RuleKind.EventWithin -> StepperSetting(stringResource(R.string.infoe_rule_number_minutes), stringResource(R.string.infoe_minutes, a), { a = (a - 15).coerceAtLeast(15) }, { a = (a + 15).coerceAtMost(240) }, tag = "info:rule:a")
                RuleKind.NotBetween -> {
                    StepperSetting(stringResource(R.string.infoe_night_from), "%02d:00".format(a), { a = (a + 23) % 24 }, { a = (a + 1) % 24 }, tag = "info:rule:a")
                    StepperSetting(stringResource(R.string.infoe_night_to), "%02d:00".format(b), { b = (b + 23) % 24 }, { b = (b + 1) % 24 }, tag = "info:rule:b")
                }
                else -> Unit
            }
            MetroButton(stringResource(R.string.infoe_rule_add), Modifier.fillMaxWidth().testTag("info:rule:done")) { onDone(InfoRule(part, kind, a, b)) }
        }
    }
}

@Composable
private fun ruleKindText(kind: RuleKind): String = stringResource(
    when (kind) {
        RuleKind.BatteryBelow -> R.string.infoe_kind_battery
        RuleKind.Charging -> R.string.infoe_kind_charging
        RuleKind.EventWithin -> R.string.infoe_kind_event
        RuleKind.Raining -> R.string.infoe_kind_rain
        RuleKind.NotBetween -> R.string.infoe_kind_not_between
    },
)
