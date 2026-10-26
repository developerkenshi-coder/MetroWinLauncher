package com.metrowin.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LivePermissions
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * The Metro lock screen: its photo, what it shows, and how it gets on the phone. Android does not let a launcher
 * replace its lock screen, so the page says what is possible: the photo can be set as the wallpaper of the lock
 * screen, and the Metro one runs as the screen saver.
 */
@Composable
fun LockScreenPage(env: SettingsEnv) {
    val settings = env.settings
    val context = LocalContext.current
    // The calendar is read with the calendar permission, asked once when the switch is turned on.
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) env.onPickLockPhoto(uri)
    }
    SettingsPage {
        ActionSetting(stringResource(R.string.lock_preview), onClick = env.onPreviewLock, tag = "lock:preview", id = "lock.preview")
        Column(Modifier.settingAnchor("lock.photo")) {
            SettingLabel(stringResource(R.string.lock_photo))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(90.dp, 160.dp).background(LocalMetroColors.current.surface)) {
                    env.lockPhoto?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    MetroButton(stringResource(R.string.lock_choose_photo), Modifier.fillMaxWidth().testTag("lock:choose")) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (settings.hasLockPhoto) {
                        MetroButton(stringResource(R.string.lock_remove_photo), Modifier.fillMaxWidth().testTag("lock:remove"), onClick = env.onClearLockPhoto)
                    }
                    SettingHint(stringResource(R.string.lock_photo_hint))
                }
            }
        }
        ActionSetting(
            stringResource(R.string.lock_apply), onClick = env.onApplyLockPhoto,
            hint = stringResource(R.string.lock_apply_hint), tag = "lock:apply", id = "lock.apply",
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.lock_show_title))
            ToggleSetting(
                stringResource(R.string.lock_show_alarm), settings.lockShowAlarm,
                onChange = { value -> env.onChange { it.copy(lockShowAlarm = value) } }, tag = "lock:alarm", id = "lock.alarm",
            )
            ToggleSetting(
                stringResource(R.string.lock_show_notifications), settings.lockShowNotifications,
                onChange = { value -> env.onChange { it.copy(lockShowNotifications = value) } }, tag = "lock:notifications", id = "lock.notifications",
            )
            ToggleSetting(
                stringResource(R.string.lock_show_battery), settings.lockShowBattery,
                onChange = { value -> env.onChange { it.copy(lockShowBattery = value) } }, tag = "lock:battery", id = "lock.battery",
            )
            ToggleSetting(
                stringResource(R.string.lock_show_weather), settings.lockShowWeather,
                onChange = { value -> env.onChange { it.copy(lockShowWeather = value) } },
                hint = stringResource(R.string.lock_show_weather_hint), tag = "lock:weather", id = "lock.weather",
            )
            ToggleSetting(
                stringResource(R.string.lock_show_event), settings.lockShowEvent,
                onChange = { value ->
                    env.onChange { it.copy(lockShowEvent = value) }
                    if (value && !LivePermissions.isGranted(context, LivePermissions.calendar)) calendarPermission.launch(LivePermissions.calendar)
                },
                hint = stringResource(R.string.lock_show_event_hint), tag = "lock:event", id = "lock.event",
            )
        }
        LockQuickSettings(env)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.lock_dream))
            ActionSetting(
                stringResource(R.string.lock_dream_button), onClick = env.onOpenDreamSettings,
                hint = stringResource(R.string.lock_dream_hint), tag = "lock:dream", id = "lock.dream",
            )
        }
    }
}
