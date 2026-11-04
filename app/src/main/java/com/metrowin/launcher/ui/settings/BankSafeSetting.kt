package com.metrowin.launcher.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AppSearch
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.gestures.SafeMode
import com.metrowin.launcher.ui.components.MetroTextField
import androidx.compose.ui.platform.testTag

/**
 * Banking apps refuse to run when something could read or cover their screen. While one is in front the launcher takes every window of its
 * own away ([SafeMode]); what it cannot hide (the accessibility service being on, the permission to draw over other apps) is said here,
 * with the two ways to switch it off at once. The list is the apps that count: the banks found by name, the ones the user added or took out.
 */
@Composable
fun BankSafeSetting(env: SettingsEnv) {
    val settings = env.settings
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingHeading(stringResource(R.string.bank_safe))
        ToggleSetting(
            stringResource(R.string.bank_safe_toggle), settings.bankSafe,
            onChange = { value -> env.onChange { it.copy(bankSafe = value) } },
            hint = stringResource(R.string.bank_safe_hint), tag = "gestures:banksafe", id = "gest.banksafe",
        )
        if (settings.bankSafe) {
            var query by remember { mutableStateOf("") }
            MetroTextField(query, { query = it }, stringResource(R.string.search_apps), Modifier.testTag("gestures:banksafe:query"))
            val choices = remember(env.apps, settings.bankSafeAdd, settings.bankSafeSkip, query) {
                val byPackage = env.apps.filter { it.packageName != null }.distinctBy { it.packageName }
                // Without a search: the banks the launcher found and the apps the user added. With one: what it finds.
                if (query.isBlank()) byPackage.filter { SafeMode.looksLikeBank(it.packageName!!) || it.packageName in settings.bankSafeAdd }
                else AppSearch.filter(byPackage, query).take(SAFE_APPS_SHOWN)
            }
            choices.forEach { app ->
                val pkg = app.packageName ?: return@forEach
                ToggleSetting(
                    app.label, SafeMode.isSensitive(pkg, settings),
                    onChange = { on ->
                        env.onChange {
                            if (on) it.copy(bankSafeAdd = it.bankSafeAdd + pkg, bankSafeSkip = it.bankSafeSkip - pkg)
                            else it.copy(bankSafeAdd = it.bankSafeAdd - pkg, bankSafeSkip = it.bankSafeSkip + pkg)
                        }
                    },
                    tag = "gestures:banksafe:$pkg",
                )
            }
        }
        SettingHint(stringResource(R.string.bank_safe_cannot))
        if (env.lockServiceEnabled) {
            ActionSetting(
                stringResource(R.string.bank_safe_service_off), onClick = { LockAccessibilityService.turnOff() },
                hint = stringResource(R.string.bank_safe_service_off_hint), tag = "gestures:banksafe:serviceoff",
            )
        }
        ActionSetting(
            stringResource(R.string.bank_safe_overlay_open),
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            hint = stringResource(R.string.bank_safe_overlay_hint), tag = "gestures:banksafe:overlay",
        )
    }
}

private const val SAFE_APPS_SHOWN = 30
