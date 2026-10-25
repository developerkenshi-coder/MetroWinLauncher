package com.metrowin.launcher.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ThemeMode
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.ChoiceRow
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroAccents
import java.util.Locale

/** Theme, accent colour, the photo behind Start, and how many Start screens there are. */
@Composable
fun StartThemePage(env: SettingsEnv) {
    val settings = env.settings
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) env.onPickBackground(uri)
    }
    SettingsPage {
        StylePicker(
            selected = settings.themeStyle,
            accent = MetroAccents[settings.accentIndex.coerceIn(MetroAccents.indices)],
            dark = LocalMetroColors.current.dark,
            onSelect = { style -> env.onChange { it.copy(themeStyle = style) } },
        )
        OptionList(
            label = stringResource(R.string.theme_mode),
            options = listOf(
                stringResource(R.string.theme_dark),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_auto_system),
                stringResource(R.string.theme_auto_hours),
            ),
            selected = settings.themeMode.ordinal,
            onSelect = { index -> env.onChange { it.copy(themeMode = ThemeMode.entries[index]) } },
            tag = "theme:mode",
        )
        if (settings.themeMode == ThemeMode.Schedule) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepperSetting(
                    label = stringResource(R.string.theme_dark_from),
                    value = hourLabel(settings.darkFromHour),
                    onDecrement = { env.onChange { it.copy(darkFromHour = (it.darkFromHour + 23) % 24) } },
                    onIncrement = { env.onChange { it.copy(darkFromHour = (it.darkFromHour + 1) % 24) } },
                    tag = "theme:from",
                )
                StepperSetting(
                    label = stringResource(R.string.theme_dark_to),
                    value = hourLabel(settings.darkToHour),
                    onDecrement = { env.onChange { it.copy(darkToHour = (it.darkToHour + 23) % 24) } },
                    onIncrement = { env.onChange { it.copy(darkToHour = (it.darkToHour + 1) % 24) } },
                    tag = "theme:to",
                )
                SettingHint(stringResource(R.string.theme_hours_hint))
            }
        }
        if (settings.themeStyle == ThemeStyle.Droid) {
            ToggleSetting(
                label = stringResource(R.string.style_dynamic),
                checked = settings.dynamicColor,
                onChange = { value -> env.onChange { it.copy(dynamicColor = value) } },
                hint = stringResource(R.string.style_dynamic_hint),
                tag = "theme:dynamic",
            )
        }
        val automatic = settings.autoAccent && settings.hasStartBackground
        AccentPicker(
            settings = settings,
            selectedPreset = if (automatic) env.autoAccentIndex ?: settings.accentIndex else settings.accentIndex,
            muted = automatic || usesWallpaperColors(settings),
            onPreset = { index -> env.onChange { it.copy(accentIndex = index, customAccent = 0, autoAccent = false) } },
            onCustom = { argb -> env.onChange { it.copy(customAccent = argb, autoAccent = false) } },
            onUsePreset = { env.onChange { it.copy(customAccent = 0) } },
        )
        ToggleSetting(
            label = stringResource(R.string.theme_auto_accent),
            checked = settings.autoAccent,
            onChange = { value -> env.onChange { it.copy(autoAccent = value) } },
            hint = stringResource(R.string.theme_auto_accent_hint),
            enabled = settings.hasStartBackground,
            tag = "theme:autoaccent",
        )
        Column {
            SettingLabel(stringResource(R.string.theme_start_background))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                BackgroundPreview(env.startBackground)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    MetroButton(stringResource(R.string.theme_choose_photo), Modifier.fillMaxWidth()) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (settings.hasStartBackground) {
                        MetroButton(stringResource(R.string.theme_remove_photo), Modifier.fillMaxWidth(), onClick = env.onClearBackground)
                    }
                    SettingHint(stringResource(R.string.theme_start_background_hint))
                }
            }
        }
        ToggleSetting(
            label = stringResource(R.string.theme_parallax),
            checked = settings.parallax,
            onChange = { value -> env.onChange { it.copy(parallax = value) } },
            enabled = settings.hasStartBackground,
            tag = "theme:parallax",
        )
        ToggleSetting(
            label = stringResource(R.string.theme_show_clock),
            checked = settings.showClock,
            onChange = { value -> env.onChange { it.copy(showClock = value) } },
            tag = "theme:clock",
        )
        ToggleSetting(
            label = stringResource(R.string.theme_more_tiles),
            checked = settings.moreTiles,
            onChange = { value -> env.onChange { it.copy(moreTiles = value) } },
        )
        StepperSetting(
            label = stringResource(R.string.theme_start_pages),
            value = settings.startPages.toString(),
            onDecrement = { env.onChange { it.copy(startPages = (it.startPages - 1).coerceAtLeast(1)) } },
            onIncrement = { env.onChange { it.copy(startPages = (it.startPages + 1).coerceAtMost(com.metrowin.launcher.data.LauncherSettings.MAX_START_PAGES)) } },
            canDecrement = settings.startPages > 1,
            canIncrement = settings.startPages < com.metrowin.launcher.data.LauncherSettings.MAX_START_PAGES,
            hint = stringResource(R.string.theme_start_pages_hint),
            tag = "theme:pages",
        )
    }
}

/** 19 as "19:00", in the way the phone shows times. */
internal fun hourLabel(hour: Int): String = String.format(Locale.getDefault(), "%02d:00", hour)

@Composable
private fun BackgroundPreview(image: ImageBitmap?) {
    val colors = LocalMetroColors.current
    Box(Modifier.size(90.dp, 160.dp).background(colors.surface)) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
