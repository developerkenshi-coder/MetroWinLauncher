package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.AppFonts

/** Everything that makes the launcher easier to read and to follow: the size and weight of the text, less motion, more contrast, and TalkBack. */
@Composable
fun AccessibilityPage(env: SettingsEnv) {
    val settings = env.settings
    SettingsPage {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.access_text_title))
            StepperSetting(
                label = stringResource(R.string.text_size),
                value = "${settings.textScale}%",
                onDecrement = { env.onChange { s -> s.copy(textScale = LauncherSettings.TEXT_SCALES.getOrElse(LauncherSettings.TEXT_SCALES.indexOf(s.textScale) - 1) { s.textScale }) } },
                onIncrement = { env.onChange { s -> s.copy(textScale = LauncherSettings.TEXT_SCALES.getOrElse(LauncherSettings.TEXT_SCALES.indexOf(s.textScale) + 1) { s.textScale }) } },
                canDecrement = settings.textScale > LauncherSettings.TEXT_SCALES.first(),
                canIncrement = settings.textScale < LauncherSettings.TEXT_SCALES.last(),
                hint = stringResource(R.string.text_size_hint),
                tag = "access:textsize",
                id = "access.textsize",
            )
            ToggleSetting(
                label = stringResource(R.string.theme_bold),
                checked = settings.boldText,
                onChange = { value -> env.onChange { it.copy(boldText = value) } },
                hint = stringResource(R.string.theme_bold_hint),
                tag = "access:bold",
                id = "access.bold",
            )
            ToggleSetting(
                label = stringResource(R.string.theme_system_font),
                checked = settings.systemFont,
                onChange = { value -> env.onChange { it.copy(systemFont = value) } },
                hint = stringResource(R.string.theme_system_font_hint),
                tag = "access:systemfont",
                id = "access.systemfont",
            )
            FontChoice(env)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.access_motion_title))
            ToggleSetting(
                stringResource(R.string.access_reduce_motion), settings.reduceMotion,
                onChange = { value -> env.onChange { it.copy(reduceMotion = value) } },
                hint = stringResource(R.string.access_reduce_motion_hint), tag = "access:motion", id = "access.motion",
            )
            ToggleSetting(
                stringResource(R.string.access_high_contrast), settings.highContrast,
                onChange = { value -> env.onChange { it.copy(highContrast = value) } },
                hint = stringResource(R.string.access_high_contrast_hint), tag = "access:contrast", id = "access.contrast",
            )
        }
        Column(Modifier.settingAnchor("access.talkback"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingHeading(stringResource(R.string.access_talkback))
            SettingHint(stringResource(R.string.access_talkback_hint))
            ActionSetting(
                stringResource(R.string.access_open_settings), onClick = env.onOpenAccessibilitySettings, tag = "access:open",
            )
        }
    }
}

/** The font of the launcher: the one of the look, one of the twenty that come with it, or a file of the user's own. */
@Composable
private fun FontChoice(env: SettingsEnv) {
    val settings = env.settings
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) env.onPickFont(uri) }
    val fonts = AppFonts.all
    val hasCustom = AppFonts.customFile(context).isFile
    val options = listOf(stringResource(R.string.access_font_default)) + fonts.map { it.label } +
        listOfNotNull(stringResource(R.string.access_font_custom).takeIf { hasCustom })
    val selected = when {
        settings.appFont == AppFonts.CUSTOM && hasCustom -> options.lastIndex
        else -> fonts.indexOfFirst { it.code == settings.appFont } + 1
    }
    DropdownSetting(
        label = stringResource(R.string.access_font), options = options, selected = selected,
        onSelect = { i -> env.onChange { it.copy(appFont = when { i == 0 -> ""; i <= fonts.size -> fonts[i - 1].code; else -> AppFonts.CUSTOM }) } },
        tag = "access:font", id = "access.font",
    )
    MetroText(stringResource(R.string.access_font_sample), size = 20.sp)
    MetroButton(stringResource(R.string.access_font_pick), Modifier.fillMaxWidth()) { picker.launch(arrayOf("*/*")) }
    SettingHint(stringResource(R.string.access_font_hint))
}
