package com.metrowin.launcher.ui.settings

import android.content.Intent
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.data.DebugReport
import com.metrowin.launcher.data.DebugReports
import kotlinx.coroutines.launch

/**
 * The reports for whoever develops the launcher: the battery and the log, of the app and of the whole phone, each saved as a text
 * file where the user chooses. Android gives the system parts only through adb and the usage access, and the page says which are missing.
 */
@Composable
fun DebugPage(@Suppress("UNUSED_PARAMETER") env: SettingsEnv) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(DebugReports.access(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = DebugReports.access(context) }
    var pending by remember { mutableStateOf<DebugReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val kind = pending
        pending = null
        if (uri == null || kind == null) return@rememberLauncherForActivityResult
        busy = true
        result = null
        scope.launch {
            val written = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { DebugReports.write(context, kind, it) }
            }
            busy = false
            result = written.fold(
                { context.getString(R.string.dbg_saved, Formatter.formatShortFileSize(context, it)) },
                { context.getString(R.string.dbg_failed, it.message.orEmpty()) },
            )
        }
    }
    fun generate(kind: DebugReport) {
        pending = kind
        create.launch(DebugReports.fileName(kind, System.currentTimeMillis()))
    }

    val allowed = stringResource(R.string.dbg_allowed)
    val missing = stringResource(R.string.dbg_missing)
    fun state(on: Boolean) = if (on) allowed else missing

    SettingsPage {
        Column(Modifier.settingAnchor("debug.access"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoSetting(stringResource(R.string.dbg_dump), state(access.dump))
            InfoSetting(stringResource(R.string.dbg_logs), state(access.readLogs))
            InfoSetting(stringResource(R.string.dbg_usage), state(access.usage))
            SettingHint(stringResource(R.string.dbg_privacy))
            if (!access.dump || !access.readLogs) {
                SettingHint(stringResource(R.string.dbg_adb) + "\n" + DebugReports.adbGrants(context.packageName).joinToString("\n"))
            }
        }
        if (!access.usage) {
            ActionSetting(
                stringResource(R.string.dbg_usage_open),
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                hint = stringResource(R.string.dbg_usage_hint), tag = "debug:usage",
            )
        }
        ActionSetting(
            stringResource(R.string.dbg_app_battery), onClick = { generate(DebugReport.AppBattery) },
            hint = stringResource(R.string.dbg_app_battery_hint), enabled = !busy, tag = "debug:appbattery", id = "debug.appbattery",
        )
        ActionSetting(
            stringResource(R.string.dbg_device_battery), onClick = { generate(DebugReport.DeviceBattery) },
            hint = stringResource(R.string.dbg_device_battery_hint), enabled = !busy, tag = "debug:devicebattery", id = "debug.devicebattery",
        )
        ActionSetting(
            stringResource(R.string.dbg_device_log), onClick = { generate(DebugReport.DeviceLog) },
            hint = stringResource(R.string.dbg_device_log_hint), enabled = !busy, tag = "debug:devicelog", id = "debug.devicelog",
        )
        ActionSetting(
            stringResource(R.string.dbg_app_log), onClick = { generate(DebugReport.AppLog) },
            hint = stringResource(R.string.dbg_app_log_hint), enabled = !busy, tag = "debug:applog", id = "debug.applog",
        )
        if (busy) SettingHint(stringResource(R.string.dbg_working))
        result?.let { SettingHint(it) }
    }
}
