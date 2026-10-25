package com.metrowin.launcher.ui.components

import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.fieldTextStyle
import com.metrowin.launcher.ui.theme.glass
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.TileWhite

/**
 * Windows Phone buttons are plain outlined rectangles. In Modern Glass a button is a capsule of frosted glass, in Modern
 * Droid a filled tonal pill.
 */
@Composable
fun MetroButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val style = LocalStyle.current
    val colors = LocalMetroColors.current
    val pressable = modifier.metroPress(onClick = onClick)
    val surface = when (style.kind) {
        ThemeStyle.Classic -> pressable.border(2.dp, colors.foreground)
        ThemeStyle.Glass -> pressable.glass(style.controlShape, veil = if (colors.dark) 0.16f else 0.55f)
        ThemeStyle.Droid -> pressable.clip(style.controlShape).background(colors.containers.getOrElse(1) { colors.surface })
    }
    val ink = if (style.kind == ThemeStyle.Droid) colors.onContainers.getOrElse(1) { colors.foreground } else colors.foreground
    val wide = if (style.isClassic) 16.dp else 22.dp
    Box(
        surface.padding(horizontal = wide, vertical = if (style.isClassic) 11.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(text, size = 19.sp, weight = FontWeight.Normal, color = ink)
    }
}

/** A short caption above a setting, in the secondary colour. */
@Composable
fun SettingLabel(text: String, modifier: Modifier = Modifier) {
    MetroText(
        text, size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/** Mutually exclusive options side by side, the selected one filled with the accent colour. */
@Composable
fun ChoiceRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val style = LocalStyle.current
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            val press = Modifier.weight(1f).metroPress(onClick = { onSelect(index) })
            val surface = when (style.kind) {
                ThemeStyle.Classic -> press
                    .border(2.dp, if (isSelected) accent else colors.foreground)
                    .background(if (isSelected) accent else Color.Transparent)
                ThemeStyle.Glass -> press.glass(style.controlShape, tint = if (isSelected) accent else null, tintAlpha = 0.55f, veil = if (colors.dark) 0.12f else 0.5f)
                ThemeStyle.Droid -> press.clip(style.controlShape).background(if (isSelected) colors.containers.getOrElse(1) { accent } else colors.surface)
            }
            val ink = when {
                style.kind == ThemeStyle.Droid && isSelected -> colors.onContainers.getOrElse(1) { colors.foreground }
                style.kind == ThemeStyle.Classic && isSelected -> TileWhite
                style.kind == ThemeStyle.Glass && isSelected -> colors.tileContent
                else -> colors.foreground
            }
            Box(surface.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                MetroText(label, size = 19.sp, weight = FontWeight.Normal, color = ink)
            }
        }
    }
}

/**
 * The Windows Phone toggle: a rectangular track with a sliding block and the word "On" or "Off" beside it. Modern Glass
 * has the round switch of iOS, Modern Droid the Material switch with its large thumb.
 */
@Composable
fun MetroToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val style = LocalStyle.current
    val accent = LocalAccent.current
    val currentChange by rememberUpdatedState(onChange)
    Row(
        modifier
            .fillMaxWidth()
            .pointerInput(checked) { detectTapGestures { currentChange(!checked) } }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(label, size = 20.sp, modifier = Modifier.weight(1f))
        when (style.kind) {
            ThemeStyle.Classic -> {
                val thumbX by animateDpAsState(if (checked) TRACK_WIDTH - THUMB_WIDTH - 4.dp else 0.dp, tween(160), label = "toggleThumb")
                MetroText(
                    stringResource(if (checked) R.string.toggle_on else R.string.toggle_off),
                    size = 15.sp, weight = FontWeight.Normal, color = colors.subtle,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Box(
                    Modifier
                        .size(TRACK_WIDTH, 26.dp)
                        .border(2.dp, if (checked) accent else colors.foreground)
                        .background(if (checked) accent else Color.Transparent)
                        .padding(2.dp),
                ) {
                    Box(
                        Modifier
                            .offset { IntOffset(thumbX.roundToPx(), 0) }
                            .size(THUMB_WIDTH, 18.dp)
                            .background(if (checked) TileWhite else colors.foreground),
                    )
                }
            }
            ThemeStyle.Glass -> {
                // The round switch of iOS: a capsule track, a white thumb that slides over it.
                val thumbX by animateDpAsState(if (checked) 20.dp else 0.dp, tween(180), label = "switchThumb")
                Box(
                    Modifier
                        .size(51.dp, 31.dp)
                        .clip(CircleShape)
                        .background(if (checked) accent else colors.inactive)
                        .padding(2.dp),
                ) {
                    Box(
                        Modifier
                            .offset { IntOffset(thumbX.roundToPx(), 0) }
                            .size(27.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(0.5.dp, Color.Black.copy(alpha = 0.12f), CircleShape),
                    )
                }
            }
            ThemeStyle.Droid -> {
                // The Material switch: the thumb grows when it is on, and the track is the main color.
                val thumb by animateDpAsState(if (checked) 24.dp else 16.dp, tween(180), label = "switchThumbSize")
                val x by animateDpAsState(if (checked) 24.dp else 4.dp, tween(180), label = "switchThumbX")
                Box(
                    Modifier
                        .size(52.dp, 32.dp)
                        .clip(CircleShape)
                        .background(if (checked) accent else colors.inactive)
                        .then(if (checked) Modifier else Modifier.border(2.dp, colors.subtle, CircleShape)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .offset { IntOffset(x.roundToPx(), 0) }
                            .size(thumb)
                            .clip(CircleShape)
                            .background(if (checked) (if (colors.dark) colors.background else Color.White) else colors.subtle),
                    )
                }
            }
        }
    }
}

/**
 * A slider from 0 to 1 that is dragged or tapped. The [track] is a gradient (the hues, say) that shows what the thumb
 * chooses; without one the track is a bar in the accent color up to the thumb. Screen readers get it as a range.
 */
@Composable
fun MetroSlider(
    value: Float,
    onChange: (Float) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    track: Brush? = null,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val style = LocalStyle.current
    val currentChange by rememberUpdatedState(onChange)
    var width by remember { mutableFloatStateOf(1f) }
    val shape = if (style.isClassic) RectangleShape else CircleShape
    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                setProgress { target -> currentChange(target.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) { detectTapGestures { at -> currentChange((at.x / width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> change.consume(); currentChange((change.position.x / width).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        val fraction = value.coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (style.isClassic) 8.dp else 12.dp)
                .clip(shape)
                .background(colors.inactive)
                .then(if (track != null) Modifier.background(track) else Modifier),
        )
        if (track == null) {
            Box(Modifier.fillMaxWidth(fraction).height(if (style.isClassic) 8.dp else 12.dp).clip(shape).background(accent))
        }
        val thumb = if (style.isClassic) 14.dp else 28.dp
        Box(
            Modifier
                .offset { IntOffset(((width - thumb.toPx()) * fraction).roundToInt(), 0) }
                .size(thumb, if (style.isClassic) 34.dp else 28.dp)
                .clip(if (style.isClassic) RectangleShape else CircleShape)
                .background(if (style.isClassic) colors.foreground else Color.White)
                .border(if (style.isClassic) 0.dp else 1.dp, Color.Black.copy(alpha = 0.18f), if (style.isClassic) RectangleShape else CircleShape),
        )
    }
}

private val TRACK_WIDTH = 56.dp
private val THUMB_WIDTH = 14.dp

/** A single line of text to type in, on a flat surface, with a hint while it is empty. */
@Composable
fun MetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    /** Shows dots instead of the letters, and tells the keyboard not to learn or suggest the text. */
    password: Boolean = false,
) {
    val colors = LocalMetroColors.current
    val style = LocalStyle.current
    val field = RoundedCornerShape(16.dp)
    val surface = when (style.kind) {
        ThemeStyle.Classic -> modifier.fillMaxWidth().height(52.dp).background(colors.surface)
        ThemeStyle.Glass -> modifier.fillMaxWidth().height(52.dp).glass(field, veil = if (colors.dark) 0.12f else 0.55f)
        ThemeStyle.Droid -> modifier.fillMaxWidth().height(52.dp).clip(field).background(colors.surface)
    }
    Row(
        surface.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) MetroText(placeholder, size = 20.sp, color = colors.subtle)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = fieldTextStyle(colors.foreground, 20.sp),
                cursorBrush = SolidColor(LocalAccent.current),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                    autoCorrectEnabled = !password,
                ),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
