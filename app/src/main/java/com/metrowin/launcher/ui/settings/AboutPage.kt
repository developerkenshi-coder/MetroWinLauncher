package com.metrowin.launcher.ui.settings

import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.components.MetroText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.metrowin.launcher.data.PhoneInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R

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
        Column(Modifier.settingAnchor("about.credits"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingHeading(stringResource(R.string.about_credits))
            SettingHint(stringResource(R.string.about_inspired))
            SettingHint(stringResource(R.string.about_font))
            SettingHint(stringResource(R.string.about_icons))
            SettingHint(stringResource(R.string.about_libraries))
            SettingHint(stringResource(R.string.about_weather))
        }
    }
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
        MetroText(label, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        MetroText(value, size = 20.sp, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE)
    }
}
