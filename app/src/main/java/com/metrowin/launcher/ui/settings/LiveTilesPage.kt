package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.data.WeatherBlock
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.metrowin.launcher.ui.theme.LocalAccent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.metrowin.launcher.R
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import com.metrowin.launcher.data.PlaceMatch
import com.metrowin.launcher.data.SavedCities
import com.metrowin.launcher.notifications.LiveReportLine
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.launch

/** The city of the weather tile and its units. */
@Composable
fun LiveTilesPage(env: SettingsEnv) {
    val settings = env.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    // null: no search made yet. Empty: the search found nothing.
    var results by remember { mutableStateOf<List<PlaceMatch>?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank()) return
        scope.launch {
            val found = env.searchPlaces(query)
            failed = found == null
            results = found
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) env.onUseLocation()
    }

    SettingsPage {
        Column(Modifier.settingAnchor("live.city"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingHeading(stringResource(R.string.weather_city))
            InfoSetting(
                label = stringResource(R.string.weather_city),
                value = settings.weatherCity.ifBlank { stringResource(R.string.weather_city_none) },
            )
            MetroTextField(
                value = query, onValueChange = { query = it },
                placeholder = stringResource(R.string.weather_search_hint),
                modifier = Modifier.testTag("weather:query"),
                onSearch = ::search,
            )
            ActionSetting(stringResource(R.string.weather_search), onClick = ::search, tag = "weather:search")
            when {
                failed -> SettingHint(stringResource(R.string.weather_search_failed), Modifier.testTag("weather:failed"))
                results?.isEmpty() == true -> SettingHint(stringResource(R.string.weather_no_results), Modifier.testTag("weather:none"))
            }
            results?.forEachIndexed { index, place ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .testTag("weather:result:$index")
                        .metroPress(onClick = {
                            env.onChoosePlace(place)
                            results = null
                            query = ""
                        })
                        .padding(vertical = 6.dp),
                ) {
                    MetroText(place.name, size = 22.sp)
                    val detail = listOf(place.region, place.country).filter { it.isNotBlank() }.joinToString(", ")
                    if (detail.isNotEmpty()) {
                        MetroText(detail, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
                    }
                }
                if (settings.weatherCities.size < SavedCities.MAX) {
                    MetroText(
                        stringResource(R.string.weather_follow), size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
                        modifier = Modifier.testTag("weather:follow:$index").metroPress(onClick = {
                            env.onFollowPlace(place)
                            results = null
                            query = ""
                        }).padding(bottom = 6.dp),
                    )
                }
            }
            if (settings.weatherCities.isNotEmpty()) {
                SettingLabel(stringResource(R.string.weather_followed))
                settings.weatherCities.forEachIndexed { index, city ->
                    Row(Modifier.fillMaxWidth().testTag("weather:city:$index"), verticalAlignment = Alignment.CenterVertically) {
                        MetroText(city.name, size = 18.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                        MetroText(
                            stringResource(R.string.weather_unfollow), size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
                            modifier = Modifier.testTag("weather:unfollow:$index").metroPress(onClick = { env.onUnfollowCity(city) }).padding(8.dp),
                        )
                    }
                }
            }
            ActionSetting(
                stringResource(R.string.weather_use_location),
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) env.onUseLocation() else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                tag = "weather:location",
            )
        }
        ToggleSetting(
            label = stringResource(R.string.weather_fahrenheit),
            checked = settings.weatherFahrenheit,
            onChange = { value -> env.onChange { it.copy(weatherFahrenheit = value) } },
            hint = stringResource(R.string.weather_hint),
            tag = "weather:fahrenheit", id = "live.fahrenheit",
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingHeading(stringResource(R.string.wx_heading))
            SettingHint(stringResource(R.string.wx_intro))
            WeatherBlock.entries.forEach { block ->
                ToggleSetting(
                    label = stringResource(block.label),
                    checked = block.code in settings.weatherBlocks,
                    onChange = { value -> env.onChange { it.copy(weatherBlocks = if (value) it.weatherBlocks + block.code else it.weatherBlocks - block.code) } },
                    hint = if (block == WeatherBlock.Air) stringResource(R.string.wx_air_hint) else null,
                    tag = "weather:block:${block.code}", id = "live.wx.${block.code}",
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SettingHeading(stringResource(R.string.tiles_more_heading))
            TileTransitionSetting(env)
            NewsSourceSetting(env)
            FinanceBaseSetting(env)
            FinanceItemsSetting(env)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingHeading(stringResource(R.string.live_heading))
            SettingHint(stringResource(R.string.live_intro))
            ToggleSetting(
                stringResource(R.string.live_master), settings.notificationPeek,
                onChange = { v -> env.onChange { it.copy(notificationPeek = v) } },
                hint = stringResource(R.string.live_master_hint), tag = "live:master", id = "live.master",
            )
            if (!env.notificationAccess) {
                ActionSetting(
                    stringResource(R.string.live_access), onClick = env.onManageNotificationAccess,
                    hint = stringResource(R.string.live_access_hint), tag = "live:access", id = "live.access",
                )
            }
            val on = settings.notificationPeek && env.notificationAccess
            if (!on) SettingHint(stringResource(R.string.live_off), Modifier.testTag("live:off"))
            ToggleSetting(
                stringResource(R.string.live_messages), settings.liveMessages,
                onChange = { v -> env.onChange { it.copy(liveMessages = v) } },
                hint = stringResource(R.string.live_messages_hint), enabled = on, tag = "live:messages", id = "live.messages",
            )
            ToggleSetting(
                stringResource(R.string.live_hide_text), settings.liveHideText,
                onChange = { v -> env.onChange { it.copy(liveHideText = v) } },
                hint = stringResource(R.string.live_hide_text_hint), enabled = on, tag = "live:hidetext", id = "live.hidetext",
            )
            ToggleSetting(
                stringResource(R.string.live_pictures), settings.livePictures,
                onChange = { v -> env.onChange { it.copy(livePictures = v) } },
                hint = stringResource(R.string.live_pictures_hint), enabled = on, tag = "live:pictures", id = "live.pictures",
            )
            ToggleSetting(
                stringResource(R.string.live_playing), settings.liveNowPlaying,
                onChange = { v -> env.onChange { it.copy(liveNowPlaying = v) } },
                hint = stringResource(R.string.live_playing_hint), enabled = on, tag = "live:playing", id = "live.playing",
            )
            ToggleSetting(
                stringResource(R.string.live_progress), settings.liveProgress,
                onChange = { v -> env.onChange { it.copy(liveProgress = v) } },
                hint = stringResource(R.string.live_progress_hint), enabled = on, tag = "live:progress", id = "live.progress",
            )
            // The apps that show something now, and those that were switched off, each with a switch.
            val labels = env.apps.associate { (it.packageName ?: it.id) to it.label }
            val packages = (env.liveTilePackages + settings.liveOffApps).sortedBy { (labels[it] ?: it).lowercase() }
            SettingLabel(stringResource(R.string.live_apps), Modifier.settingAnchor("live.apps"))
            if (packages.isEmpty()) {
                SettingHint(stringResource(R.string.live_apps_none), Modifier.testTag("live:none"))
            } else {
                packages.forEach { pkg ->
                    ToggleSetting(
                        label = labels[pkg] ?: pkg,
                        checked = pkg !in settings.liveOffApps,
                        onChange = { v -> env.onChange { it.copy(liveOffApps = if (v) it.liveOffApps - pkg else it.liveOffApps + pkg) } },
                        tag = "live:app:$pkg",
                    )
                }
                SettingHint(stringResource(R.string.live_apps_hint))
            }
            // What the launcher reads from the notifications now, so a face that is missing can be told from a switch that is off.
            SettingLabel(stringResource(R.string.live_report), Modifier.settingAnchor("live.report"))
            if (!on || env.liveReport.isEmpty()) {
                SettingHint(stringResource(R.string.live_report_none), Modifier.testTag("live:report:none"))
            } else {
                env.liveReport.forEach { line ->
                    Column(Modifier.fillMaxWidth().testTag("live:report:${line.packageName}")) {
                        MetroText(labels[line.packageName] ?: line.packageName, size = 20.sp)
                        MetroText(
                            reportText(line), size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
                            lineHeight = 19.sp,
                        )
                    }
                }
                SettingHint(stringResource(R.string.live_report_hint))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.tp_heading))
            ToggleSetting(
                label = stringResource(R.string.tp_toggle),
                checked = settings.thirdPartyTiles,
                onChange = { value -> env.onChange { it.copy(thirdPartyTiles = value) } },
                hint = stringResource(R.string.tp_hint),
                tag = "tiles:thirdparty", id = "live.published",
            )
            if (env.publishedTiles.isEmpty()) {
                SettingHint(stringResource(R.string.tp_none), Modifier.testTag("tiles:none"))
            } else {
                env.publishedTiles.forEach { tile ->
                    val label = env.apps.firstOrNull { it.packageName == tile.packageName }?.label ?: tile.packageName
                    Row(Modifier.fillMaxWidth().testTag("tiles:published:${tile.packageName}"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            MetroText(label, size = 20.sp)
                            MetroText(
                                listOf(tile.title, tile.text, if (tile.count > 0) "(${tile.count})" else "").filter { it.isNotBlank() }.joinToString(" "),
                                size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
                            )
                        }
                        Box(Modifier.metroPress(onClick = { env.onClearPublishedTile(tile.packageName) }).padding(8.dp).testTag("tiles:clear:${tile.packageName}")) {
                            MetroText(stringResource(R.string.tp_clear), size = 17.sp, weight = FontWeight.Normal, color = LocalAccent.current)
                        }
                    }
                }
            }
        }
    }
}

/** What was found for an app, in a line: "chats 2 · messages 5 · pictures 1". */
@Composable
private fun reportText(line: LiveReportLine): String {
    val parts = buildList {
        if (line.conversations > 0) add(stringResource(R.string.live_report_chats, line.conversations, line.messages))
        if (line.others > 0) add(stringResource(R.string.live_report_others, line.others))
        if (line.pictures > 0) add(stringResource(R.string.live_report_pictures, line.pictures))
        if (line.playing) add(stringResource(R.string.live_report_playing))
        if (line.progress > 0) add(stringResource(R.string.live_report_progress, line.progress))
        if (line.ongoing > 0) add(stringResource(R.string.live_report_ongoing, line.ongoing))
    }
    return parts.joinToString(" · ").ifEmpty { stringResource(R.string.live_report_nothing) }
}
