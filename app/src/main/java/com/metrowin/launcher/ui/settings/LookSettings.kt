package com.metrowin.launcher.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroSlider
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.SettingLabel
import com.metrowin.launcher.ui.theme.ColorMath
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.MetroTheme
import com.metrowin.launcher.ui.theme.pageBackground
import com.metrowin.launcher.ui.theme.squareClip
import com.metrowin.launcher.ui.theme.tileColor
import com.metrowin.launcher.ui.theme.tileInk
import com.metrowin.launcher.ui.theme.tileSurface
import com.metrowin.launcher.ui.theme.toArgbInt

/** The three looks, each with a small picture of itself: three tiles in its shapes and colors. */
@Composable
fun StylePicker(selected: ThemeStyle, accent: Color, dark: Boolean, onSelect: (ThemeStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingLabel(stringResource(R.string.style_title), Modifier.padding(bottom = 0.dp))
        ThemeStyle.entries.forEach { style ->
            StyleChoice(style, accent, dark, style == selected) { onSelect(style) }
        }
    }
}

@Composable
private fun StyleChoice(style: ThemeStyle, accent: Color, dark: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val frame = LocalMetroColors.current.foreground
    val name = stringResource(style.label)
    // The picture is drawn in the look it shows, whatever look is in use now.
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("style:${style.code}")
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = isSelected
            }
            .metroPress(onClick = onClick)
            .then(if (isSelected) Modifier.border(3.dp, frame) else Modifier.border(1.dp, frame.copy(alpha = 0.25f)))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroTheme(accent = accent, dark = dark, style = style) {
            StylePreview()
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            MetroText(name, size = 21.sp)
            SettingHint(stringResource(style.description))
        }
    }
}

/** A miniature Start: a wide tile above two squares, on the backdrop of the look. */
@Composable
private fun StylePreview() {
    val style = LocalStyle.current
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    Box(Modifier.size(84.dp, 84.dp).clip(style.squareShape).pageBackground().padding(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(30.dp).tileSurface(colors.tileColor(0, accent), style.smallTileShape))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).height(30.dp).tileSurface(colors.tileColor(1, accent), style.smallTileShape))
                Box(Modifier.weight(1f).height(30.dp).tileSurface(colors.tileColor(2, accent), style.smallTileShape))
            }
        }
    }
}

/**
 * The colors: the 20 of Windows Phone and a color of your own made with three sliders. [dynamicHint] says that the
 * wallpaper decides the main color for now (Modern Droid on Android 12 and later).
 */
@Composable
fun AccentPicker(
    settings: LauncherSettings,
    selectedPreset: Int,
    muted: Boolean,
    onPreset: (Int) -> Unit,
    onCustom: (Int) -> Unit,
    onUsePreset: () -> Unit,
) {
    val custom = settings.customAccent != 0
    var open by remember { mutableStateOf(custom) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            SettingLabel(stringResource(R.string.theme_accent))
            AccentGrid(
                selected = if (custom) -1 else selectedPreset,
                modifier = Modifier.alpha(if (muted) 0.45f else 1f),
                onSelect = onPreset,
            )
        }
        MetroButton(
            stringResource(R.string.accent_custom),
            Modifier.fillMaxWidth().testTag("accent:custom"),
        ) { open = !open }
        if (open || custom) {
            CustomColor(settings.customAccent.takeIf { it != 0 } ?: MetroAccents[selectedPreset.coerceIn(MetroAccents.indices)].toArgbInt(), custom, onCustom, onUsePreset)
        }
    }
}

@Composable
private fun CustomColor(argb: Int, active: Boolean, onChange: (Int) -> Unit, onUsePreset: () -> Unit) {
    val hsv = remember(argb) { ColorMath.argbToHsv(argb) }
    // Keep the hue the user is dragging even when the strength drops to zero, where the color alone cannot remember it.
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var brightness by remember { mutableStateOf(hsv[2]) }
    fun publish() = onChange(ColorMath.hsvToArgb(hue, saturation, brightness))
    val current = Color(ColorMath.hsvToArgb(hue, saturation, brightness))
    val hues = remember { Brush.horizontalGradient((0..6).map { Color(ColorMath.hsvToArgb(it * 60f, 1f, 1f)) }) }
    Column(Modifier.testTag("accent:sliders"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingHint(stringResource(R.string.accent_custom_hint))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .squareClip()
                .background(current)
                .semantics { contentDescription = "#%06X".format(current.toArgbInt() and 0xFFFFFF) },
        )
        SettingLabel(stringResource(R.string.accent_hue), Modifier.padding(top = 8.dp, bottom = 0.dp))
        MetroSlider(hue / 360f, { hue = it * 360f; publish() }, stringResource(R.string.accent_hue), Modifier.testTag("accent:hue"), hues)
        SettingLabel(stringResource(R.string.accent_saturation), Modifier.padding(bottom = 0.dp))
        MetroSlider(
            saturation, { saturation = it; publish() }, stringResource(R.string.accent_saturation), Modifier.testTag("accent:saturation"),
            Brush.horizontalGradient(listOf(Color(ColorMath.hsvToArgb(hue, 0f, brightness)), Color(ColorMath.hsvToArgb(hue, 1f, brightness)))),
        )
        SettingLabel(stringResource(R.string.accent_brightness), Modifier.padding(bottom = 0.dp))
        MetroSlider(
            brightness, { brightness = it; publish() }, stringResource(R.string.accent_brightness), Modifier.testTag("accent:brightness"),
            Brush.horizontalGradient(listOf(Color.Black, Color(ColorMath.hsvToArgb(hue, saturation, 1f)))),
        )
        if (active) {
            MetroButton(stringResource(R.string.accent_use_preset), Modifier.fillMaxWidth().padding(top = 10.dp).testTag("accent:preset"), onUsePreset)
        }
    }
}

/** Android 12 and later give the launcher the colors of the wallpaper, which Modern Droid can use. */
val dynamicColorAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Whether the wallpaper, not the chosen color, makes the main color now. */
fun usesWallpaperColors(settings: LauncherSettings): Boolean =
    settings.themeStyle == ThemeStyle.Droid && settings.dynamicColor && dynamicColorAvailable

/** The 20 accent colours in a grid of five columns. The chosen one has a frame. */
@Composable
internal fun AccentGrid(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val frame = LocalMetroColors.current.foreground
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MetroAccents.chunked(ACCENT_COLUMNS).forEachIndexed { row, colors ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                colors.forEachIndexed { column, color ->
                    val index = row * ACCENT_COLUMNS + column
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .metroPress(onClick = { onSelect(index) })
                            .squareClip()
                            .background(color)
                            .testTag("accent:$index")
                            .then(if (index == selected) Modifier.border(3.dp, frame, LocalStyle.current.squareShape) else Modifier),
                    )
                }
            }
        }
    }
}

private const val ACCENT_COLUMNS = 5
