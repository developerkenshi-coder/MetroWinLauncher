package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.ui.components.Spacing
import com.metrowin.launcher.ui.components.MetroType
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.metrowin.launcher.data.PhoneInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.data.UpdateState
import com.metrowin.launcher.data.LauncherStatus

/** The version of the app, the phone it runs on, and who made what it is built from. */
@Composable
fun AboutPage(env: SettingsEnv) {
    val about = env.about
    SettingsPage {
        Column(Modifier.settingAnchor("about.version"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoSetting(stringResource(R.string.about_version), "${about.versionName} (${about.versionCode})")
            InfoSetting(stringResource(R.string.about_android), "${about.androidRelease} (API ${about.sdk})")
            InfoSetting(stringResource(R.string.about_device), about.device)
            if (about.refreshHz > 0) InfoSetting(stringResource(R.string.about_refresh), "${about.refreshHz} Hz")
        }
        UpdateBlock(env)
        ToggleSetting(
            label = stringResource(R.string.about_phone_toggle), checked = env.settings.showPhoneInfo,
            onChange = { v -> env.onChange { it.copy(showPhoneInfo = v) } },
            hint = stringResource(R.string.about_phone_toggle_hint), tag = "about:phoneinfo", id = "about.phoneinfo",
        )
        if (env.settings.showPhoneInfo) PhoneInfoBlock()
        ActionSetting(
            stringResource(R.string.tut_replay), onClick = env.onShowTutorial,
            hint = stringResource(R.string.tut_replay_hint), tag = "about:tutorial", id = "about.tutorial",
        )
        ActionSetting(
            stringResource(R.string.whatsnew_open), onClick = env.onShowNews,
            hint = stringResource(R.string.whatsnew_open_hint), tag = "about:whatsnew", id = "about.whatsnew",
        )
        StatusBlock(env)
        Column(Modifier.settingAnchor("about.credits"), verticalArrangement = Arrangement.spacedBy(Spacing.Large)) {
            SettingHeading(stringResource(R.string.about_credits))
            SettingHint(stringResource(R.string.about_inspired))
            SettingHint(stringResource(R.string.about_font))
            SettingHint(stringResource(R.string.about_icons))
            SettingHint(stringResource(R.string.about_libraries))
            SettingHint(stringResource(R.string.about_weather))
        }
    }
}

/** The updates from GitHub: the check every 12 hours, the check now, and the download that goes on to the installer. */
@Composable
private fun UpdateBlock(env: SettingsEnv) {
    val state = env.update
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    Column(Modifier.settingAnchor("about.update").testTag("about:update"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingHeading(stringResource(R.string.update_title))
        SettingHint(
            when (state) {
                UpdateState.Idle -> stringResource(R.string.update_idle)
                UpdateState.Checking -> stringResource(R.string.update_checking)
                UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                is UpdateState.Available -> stringResource(R.string.update_available, state.version)
                is UpdateState.Downloading -> stringResource(R.string.update_downloading, state.percent)
                UpdateState.NeedsPermission -> stringResource(R.string.update_needs_permission)
                UpdateState.Failed -> stringResource(R.string.update_failed)
            },
            Modifier.testTag("about:update:state"),
        )
        ActionSetting(
            stringResource(R.string.update_check), onClick = env.onCheckUpdate, enabled = !busy,
            tag = "about:update:check", id = "about.updatecheck",
        )
        // What the new version brings, to read before updating.
        if (state is UpdateState.Available && state.notes.isNotBlank()) {
            SettingLabel(stringResource(R.string.update_notes_title, state.version))
            SettingHint(state.notes, Modifier.testTag("about:update:notes"), size = 17.sp, lineHeight = 24.sp, minScale = 1f)
        }
        if (state is UpdateState.Available || state is UpdateState.NeedsPermission) {
            val version = (state as? UpdateState.Available)?.version.orEmpty()
            ActionSetting(
                stringResource(R.string.update_install, version), onClick = env.onInstallUpdate,
                hint = stringResource(R.string.update_install_hint), tag = "about:update:install",
            )
        }
    }
    ToggleSetting(
        label = stringResource(R.string.update_auto), checked = env.settings.updateAuto,
        onChange = { v -> env.onChange { it.copy(updateAuto = v) } },
        hint = stringResource(R.string.update_auto_hint), tag = "about:updateauto", id = "about.updateauto",
    )
}

/** The phone itself: what it is, what it runs, how much memory and space it has, and how long it has been on. */
@Composable
private fun PhoneInfoBlock() {
    val context = LocalContext.current
    val info = remember { PhoneInfo.read(context) }
    Column(Modifier.settingAnchor("about.phone").testTag("about:phone"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingHeading(stringResource(R.string.about_phone_title))
        StackedInfo(stringResource(R.string.about_phone_model), info.model)
        if (info.securityPatch.isNotEmpty()) StackedInfo(stringResource(R.string.about_phone_patch), info.securityPatch)
        StackedInfo(stringResource(R.string.about_phone_ram), PhoneInfo.size(info.ramBytes))
        StackedInfo(stringResource(R.string.about_phone_storage), PhoneInfo.size(info.storageFreeBytes) + " / " + PhoneInfo.size(info.storageTotalBytes))
        StackedInfo(stringResource(R.string.about_phone_screen), info.screen)
        StackedInfo(stringResource(R.string.about_phone_uptime), PhoneInfo.uptime(info.uptimeMillis))
        if (info.abi.isNotEmpty()) StackedInfo(stringResource(R.string.about_phone_abi), info.abi)
    }
}

/** A fact with a long value: the label over it, so neither is squeezed at a large text size. */
@Composable
private fun StackedInfo(label: String, value: String) {
    Column {
        MetroText(label, size = MetroType.Note, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        MetroText(value, size = MetroType.Subtitle, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE)
    }
}

/** What the launcher works with, as it is now: version, permissions, services and the last crash, with a button that copies it for a bug report. */
@Composable
private fun StatusBlock(env: SettingsEnv) {
    val context = LocalContext.current
    var tick by androidx.compose.runtime.mutableIntStateOf(0)
    // The permissions are given on other screens: they are read again when this one comes back.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { tick++ }
    val lines = remember(tick) { LauncherStatus.read(context) }
    val error = remember(tick) { LauncherStatus.lastError(context) }
    val yes = stringResource(R.string.status_yes)
    val no = stringResource(R.string.status_no)
    val about = env.about
    Column(Modifier.settingAnchor("about.status").testTag("about:status"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // What is wrong, with how to put it right: only the lines that are off and have something to say; each opens the screen of Android where it is fixed.
        val problems = lines.filter { it.problem }
        SettingHeading(stringResource(R.string.fix_title))
        if (problems.isEmpty()) SettingHint(stringResource(R.string.fix_none), Modifier.testTag("about:fix:none"))
        problems.forEach { line ->
            val intent = LauncherStatus.fixIntent(context, line.fix)
            Column(
                Modifier.testTag("about:fix:${line.fix.name}").then(
                    if (intent != null) Modifier.metroPress(onClick = { runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }) else Modifier,
                ),
            ) {
                MetroText(stringResource(line.label), size = MetroType.Subtitle, color = LocalAccent.current)
                SettingHint(stringResource(line.hint))
            }
        }
        SettingHeading(stringResource(R.string.status_title))
        lines.forEach { line -> InfoSetting(stringResource(line.label), if (line.ok == true) yes else no) }
        InfoSetting(stringResource(R.string.status_last_error), error ?: stringResource(R.string.status_no_error))
        val copied = stringResource(R.string.status_copied)
        val facts = lines.map { stringResource(it.label) to if (it.ok == true) yes else no }
        val header = "MetroWin Launcher ${about.versionName} (${about.versionCode}), Android ${about.androidRelease} (API ${about.sdk}), ${about.device}"
        val errorLabel = stringResource(R.string.status_last_error)
        ActionSetting(
            stringResource(R.string.status_copy), tag = "about:status:copy",
            onClick = {
                val text = LauncherStatus.report(header, facts, error, errorLabel)
                context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("MetroWin", text))
                com.metrowin.launcher.ui.actioncenter.MetroToast.show(context, copied)
            },
        )
    }
}
