package com.metrowin.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import androidx.compose.ui.Modifier
import com.metrowin.launcher.data.FileType
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.data.SearchEngine

/** What the search box looks in, what it answers by itself, which engine it uses for the web, and the searches it remembers. */
@Composable
fun SearchPage(env: SettingsEnv) {
    val context = LocalContext.current
    // The calendar is read with the calendar permission, asked once when the switch is turned on.
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val filePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.search_where_title))
            ToggleSetting(
                stringResource(R.string.apps_search_contacts), env.settings.searchContacts,
                onChange = { value -> env.onChange { it.copy(searchContacts = value) } }, tag = "search:contacts", id = "search.contacts",
            )
            ToggleSetting(
                stringResource(R.string.apps_search_settings), env.settings.searchSettings,
                onChange = { value -> env.onChange { it.copy(searchSettings = value) } }, tag = "search:settings", id = "search.settings",
            )
            ToggleSetting(
                stringResource(R.string.apps_search_launcher), env.settings.searchLauncherSettings,
                onChange = { value -> env.onChange { it.copy(searchLauncherSettings = value) } },
                hint = stringResource(R.string.apps_search_launcher_hint), tag = "search:launcher", id = "search.launcher",
            )
            ToggleSetting(
                stringResource(R.string.apps_search_tiles), env.settings.searchTiles,
                onChange = { value -> env.onChange { it.copy(searchTiles = value) } },
                hint = stringResource(R.string.apps_search_tiles_hint), tag = "search:tiles", id = "search.tiles",
            )
            ToggleSetting(
                stringResource(R.string.apps_calendar), env.settings.searchCalendar,
                onChange = { value ->
                    env.onChange { it.copy(searchCalendar = value) }
                    if (value && !LivePermissions.isGranted(context, LivePermissions.calendar)) calendarPermission.launch(LivePermissions.calendar)
                },
                hint = stringResource(R.string.apps_calendar_hint), tag = "search:calendar", id = "search.calendar",
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleSetting(
                stringResource(R.string.search_notifications), env.settings.searchNotifications,
                onChange = { value -> env.onChange { it.copy(searchNotifications = value) } },
                hint = stringResource(R.string.search_notifications_hint), tag = "search:notifications", id = "search.notifications",
            )
            ToggleSetting(
                stringResource(R.string.search_operators), env.settings.searchOperators,
                onChange = { value -> env.onChange { it.copy(searchOperators = value) } },
                hint = stringResource(R.string.search_operators_hint), tag = "search:operators", id = "search.operators",
            )
        }
        Column(Modifier.settingAnchor("search.files"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.search_files_title))
            FileType.entries.forEach { type ->
                val on = type.code in env.settings.searchFileTypes
                ToggleSetting(
                    stringResource(type.label), on,
                    onChange = { value ->
                        env.onChange { it.copy(searchFileTypes = if (value) it.searchFileTypes + type.code else it.searchFileTypes - type.code) }
                        if (value && !LivePermissions.isGranted(context, type.permission)) filePermission.launch(type.permission)
                    },
                    tag = "search:files:${type.code}",
                )
            }
            SettingHint(stringResource(R.string.search_files_hint))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.search_web_title))
            ToggleSetting(
                stringResource(R.string.apps_search_web), env.settings.searchWeb,
                onChange = { value -> env.onChange { it.copy(searchWeb = value) } },
                hint = stringResource(R.string.apps_search_hint), tag = "search:web", id = "search.web",
            )
            DropdownSetting(
                stringResource(R.string.apps_engine),
                options = SearchEngine.entries.map { it.label },
                selected = env.settings.searchEngine.ordinal,
                onSelect = { index -> env.onChange { it.copy(searchEngine = SearchEngine.entries[index]) } },
                tag = "search:engine", id = "search.engine",
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.search_answers_title))
            ToggleSetting(
                stringResource(R.string.apps_answers), env.settings.searchAnswers,
                onChange = { value -> env.onChange { it.copy(searchAnswers = value) } },
                hint = stringResource(R.string.apps_answers_hint), tag = "search:answers", id = "search.answers",
            )
            ToggleSetting(
                stringResource(R.string.apps_assistant), env.settings.searchAssistant,
                onChange = { value -> env.onChange { it.copy(searchAssistant = value) } },
                hint = stringResource(R.string.apps_assistant_hint), tag = "search:assistant", id = "search.assistant",
            )
            ToggleSetting(
                stringResource(R.string.apps_commands), env.settings.searchCommands,
                onChange = { value -> env.onChange { it.copy(searchCommands = value) } },
                hint = stringResource(R.string.apps_commands_hint), tag = "search:commands", id = "search.commands",
            )
            ToggleSetting(
                stringResource(R.string.apps_tip), env.settings.tipOfDay,
                onChange = { value -> env.onChange { it.copy(tipOfDay = value) } },
                hint = stringResource(R.string.apps_tip_hint), tag = "search:tip", id = "search.tip",
            )
            ToggleSetting(
                stringResource(R.string.apps_dial), env.settings.searchSmartDial,
                onChange = { value -> env.onChange { it.copy(searchSmartDial = value) } },
                hint = stringResource(R.string.apps_dial_hint), tag = "search:dial", id = "search.dial",
            )
            ToggleSetting(
                stringResource(R.string.apps_today), env.settings.searchToday,
                onChange = { value -> env.onChange { it.copy(searchToday = value) } },
                hint = stringResource(R.string.apps_today_hint), tag = "search:today", id = "search.today",
            )
            ToggleSetting(
                stringResource(R.string.today_show_weather), env.settings.todayWeather,
                onChange = { value -> env.onChange { it.copy(todayWeather = value) } },
                enabled = env.settings.searchToday,
                hint = stringResource(R.string.today_parts_hint), tag = "search:today:weather", id = "search.todayweather",
            )
            ToggleSetting(
                stringResource(R.string.today_show_near), env.settings.todayNear,
                onChange = { value -> env.onChange { it.copy(todayNear = value) } },
                enabled = env.settings.searchToday, tag = "search:today:near", id = "search.todaynear",
            )
            ToggleSetting(
                stringResource(R.string.today_show_date), env.settings.todayDate,
                onChange = { value -> env.onChange { it.copy(todayDate = value) } },
                enabled = env.settings.searchToday, tag = "search:today:date", id = "search.todaydate",
            )
            ToggleSetting(
                stringResource(R.string.today_show_alarm), env.settings.todayAlarm,
                onChange = { value -> env.onChange { it.copy(todayAlarm = value) } },
                enabled = env.settings.searchToday, tag = "search:today:alarm", id = "search.todayalarm",
            )
            ToggleSetting(
                stringResource(R.string.apps_currency), env.settings.searchCurrency,
                onChange = { value -> env.onChange { it.copy(searchCurrency = value) } },
                enabled = env.settings.searchAnswers,
                hint = stringResource(R.string.apps_currency_hint), tag = "search:currency", id = "search.currency",
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.search_history_title))
            ToggleSetting(
                stringResource(R.string.apps_remember), env.settings.rememberSearches,
                onChange = { value -> env.onChange { it.copy(rememberSearches = value) } },
                hint = stringResource(R.string.apps_remember_hint), tag = "search:remember", id = "search.remember",
            )
            ActionSetting(stringResource(R.string.apps_forget), onClick = env.onClearSearches, tag = "search:forget", id = "search.forget")
        }
    }
}
