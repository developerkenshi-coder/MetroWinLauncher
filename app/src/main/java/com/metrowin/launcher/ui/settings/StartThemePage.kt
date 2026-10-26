package com.metrowin.launcher.ui.settings

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.MetroText
import androidx.compose.ui.platform.testTag
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.ui.start.PhotoFrame
import com.metrowin.launcher.ui.components.MetroSlider
import com.metrowin.launcher.data.PhotoFraming
import com.metrowin.launcher.data.ClockHours
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.BiasAlignment
import androidx.annotation.StringRes
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
import com.metrowin.launcher.data.TileSpacing
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.ChoiceRow
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroAccents
import java.util.Locale

/** How Start and the rest look: the theme, light or dark, the accent colour and the photo behind Start. */
@Composable
fun StartThemePage(env: SettingsEnv) {
    val settings = env.settings
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) env.onPickBackground(uri)
    }
    SettingsPage {
        Box(Modifier.settingAnchor("theme.style")) {
            StylePicker(
                selected = settings.themeStyle,
                accent = MetroAccents[settings.accentIndex.coerceIn(MetroAccents.indices)],
                dark = LocalMetroColors.current.dark,
                onSelect = { style -> env.onChange { it.copy(themeStyle = style) } },
            )
        }
        OptionList(
            label = stringResource(R.string.theme_mode),
            options = listOf(
                stringResource(R.string.theme_dark),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_auto_system),
                stringResource(R.string.theme_auto_hours),
                stringResource(R.string.theme_auto_sun),
            ),
            selected = settings.themeMode.ordinal,
            onSelect = { index -> env.onChange { it.copy(themeMode = ThemeMode.entries[index]) } },
            tag = "theme:mode",
            id = "theme.mode",
        )
        if (settings.themeMode == ThemeMode.Schedule || settings.themeMode == ThemeMode.Sun) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (settings.themeMode == ThemeMode.Sun) SettingHint(stringResource(R.string.theme_sun_hint), Modifier.testTag("theme:sunhint"))
                StepperSetting(
                    label = stringResource(R.string.theme_dark_from),
                    value = hourLabel(settings.darkFromHour),
                    onDecrement = { env.onChange { it.copy(darkFromHour = (it.darkFromHour + 23) % 24) } },
                    onIncrement = { env.onChange { it.copy(darkFromHour = (it.darkFromHour + 1) % 24) } },
                    tag = "theme:from",
                    id = "theme.from",
                )
                StepperSetting(
                    label = stringResource(R.string.theme_dark_to),
                    value = hourLabel(settings.darkToHour),
                    onDecrement = { env.onChange { it.copy(darkToHour = (it.darkToHour + 23) % 24) } },
                    onIncrement = { env.onChange { it.copy(darkToHour = (it.darkToHour + 1) % 24) } },
                    tag = "theme:to",
                    id = "theme.to",
                )
                SettingHint(stringResource(R.string.theme_hours_hint))
            }
        }
        ToggleSetting(
            label = stringResource(R.string.theme_oled),
            checked = settings.oledMode,
            onChange = { value -> env.onChange { it.copy(oledMode = value) } },
            hint = stringResource(R.string.theme_oled_hint),
            tag = "theme:oled",
            id = "theme.oled",
        )
        if (settings.themeStyle == ThemeStyle.Droid) {
            ToggleSetting(
                label = stringResource(R.string.style_dynamic),
                checked = settings.dynamicColor,
                onChange = { value -> env.onChange { it.copy(dynamicColor = value) } },
                hint = stringResource(R.string.style_dynamic_hint),
                tag = "theme:dynamic",
                id = "theme.dynamic",
            )
        }
        val automatic = settings.autoAccent && settings.hasStartBackground
        Box(Modifier.settingAnchor("theme.accent")) {
            AccentPicker(
                settings = settings,
                selectedPreset = if (automatic) env.autoAccentIndex ?: settings.accentIndex else settings.accentIndex,
                muted = automatic || usesWallpaperColors(settings),
                onPreset = { index -> env.onChange { it.copy(accentIndex = index, customAccent = 0, autoAccent = false) } },
                onCustom = { argb -> env.onChange { it.copy(customAccent = argb, autoAccent = false) } },
                onUsePreset = { env.onChange { it.copy(customAccent = 0) } },
            )
        }
        ToggleSetting(
            label = stringResource(R.string.theme_auto_accent),
            checked = settings.autoAccent,
            onChange = { value -> env.onChange { it.copy(autoAccent = value) } },
            hint = stringResource(R.string.theme_auto_accent_hint),
            enabled = settings.hasStartBackground,
            tag = "theme:autoaccent",
            id = "theme.autoaccent",
        )
        Column(Modifier.settingAnchor("theme.background")) {
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
        if (settings.hasStartBackground) {
            Column(Modifier.settingAnchor("theme.framing"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingLabel(stringResource(R.string.photo_framing))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BackgroundPreview(env.startBackground, PhotoFrame(settings.startPhotoZoom, settings.startPhotoX, settings.startPhotoY))
                    Column(Modifier.weight(1f)) {
                        FramingSlider(R.string.photo_zoom, PhotoFraming.fractionOfZoom(settings.startPhotoZoom), "theme:photozoom") { f ->
                            env.onChange { it.copy(startPhotoZoom = PhotoFraming.zoomOf(f)) }
                        }
                        FramingSlider(R.string.photo_x, PhotoFraming.fractionOf(settings.startPhotoX), "theme:photox") { f ->
                            env.onChange { it.copy(startPhotoX = PhotoFraming.percentOf(f)) }
                        }
                        FramingSlider(R.string.photo_y, PhotoFraming.fractionOf(settings.startPhotoY), "theme:photoy") { f ->
                            env.onChange { it.copy(startPhotoY = PhotoFraming.percentOf(f)) }
                        }
                        MetroButton(stringResource(R.string.photo_reset), Modifier.fillMaxWidth().testTag("theme:photoreset")) {
                            env.onChange { it.copy(startPhotoZoom = 100, startPhotoX = 50, startPhotoY = 50) }
                        }
                    }
                }
                SettingHint(stringResource(R.string.photo_framing_hint))
            }
        }
        ToggleSetting(
            label = stringResource(R.string.theme_parallax),
            checked = settings.parallax,
            onChange = { value -> env.onChange { it.copy(parallax = value) } },
            enabled = settings.hasStartBackground,
            tag = "theme:parallax",
            id = "theme.parallax",
        )
        ToggleSetting(
            label = stringResource(R.string.theme_panorama),
            checked = settings.panoramaTitles,
            onChange = { value -> env.onChange { it.copy(panoramaTitles = value) } },
            hint = stringResource(R.string.theme_panorama_hint),
            tag = "theme:panorama",
            id = "theme.panorama",
        )
    }
}

/** 19 as "19:00", in the way the phone shows times. */
internal fun hourLabel(hour: Int): String = String.format(Locale.getDefault(), "%02d:00", hour)

@Composable
private fun BackgroundPreview(image: ImageBitmap?, framing: PhotoFrame = PhotoFrame()) {
    val colors = LocalMetroColors.current
    Box(Modifier.size(90.dp, 160.dp).background(colors.surface).clipToBounds()) {
        if (image != null) {
            Image(
                image, contentDescription = null, contentScale = ContentScale.Crop,
                alignment = BiasAlignment(PhotoFraming.bias(framing.x), PhotoFraming.bias(framing.y)),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("theme:photopreview")
                    .graphicsLayer {
                        scaleX = framing.zoom / 100f
                        scaleY = framing.zoom / 100f
                        transformOrigin = TransformOrigin(framing.x / 100f, framing.y / 100f)
                    },
            )
        }
    }
}

/** A slider with its name above it, for the framing of the photo. */
@Composable
private fun FramingSlider(@StringRes label: Int, value: Float, tag: String, onChange: (Float) -> Unit) {
    val name = stringResource(label)
    Column(Modifier.testTag(tag)) {
        MetroText(name, size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
        MetroSlider(value, onChange, name)
    }
}
