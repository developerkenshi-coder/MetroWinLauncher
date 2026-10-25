package com.metrowin.launcher.ui.components

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
import com.metrowin.launcher.ui.theme.MetroFont
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

/** Windows Phone buttons are plain outlined rectangles. */
@Composable
fun MetroButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .metroPress(onClick = onClick)
            .border(2.dp, LocalMetroColors.current.foreground)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(text, size = 19.sp, weight = FontWeight.Normal)
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
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .metroPress(onClick = { onSelect(index) })
                    .border(2.dp, if (isSelected) LocalAccent.current else LocalMetroColors.current.foreground)
                    .background(if (isSelected) LocalAccent.current else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                MetroText(label, size = 19.sp, weight = FontWeight.Normal, color = if (isSelected) TileWhite else LocalMetroColors.current.foreground)
            }
        }
    }
}

/** The Windows Phone toggle: a rectangular track with a sliding block and the word "On" or "Off" beside it. */
@Composable
fun MetroToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    val currentChange by rememberUpdatedState(onChange)
    val thumbX by animateDpAsState(if (checked) TRACK_WIDTH - THUMB_WIDTH - 4.dp else 0.dp, tween(160), label = "toggleThumb")
    Row(
        modifier
            .fillMaxWidth()
            .pointerInput(checked) { detectTapGestures { currentChange(!checked) } }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetroText(label, size = 20.sp, modifier = Modifier.weight(1f))
        MetroText(
            stringResource(if (checked) R.string.toggle_on else R.string.toggle_off),
            size = 15.sp, weight = FontWeight.Normal, color = colors.subtle,
            modifier = Modifier.padding(end = 12.dp),
        )
        Box(
            Modifier
                .size(TRACK_WIDTH, 26.dp)
                .border(2.dp, if (checked) LocalAccent.current else colors.foreground)
                .background(if (checked) LocalAccent.current else androidx.compose.ui.graphics.Color.Transparent)
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
    Row(
        modifier.fillMaxWidth().height(52.dp).background(colors.surface).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) MetroText(placeholder, size = 20.sp, color = colors.subtle)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.foreground, fontSize = 20.sp, fontFamily = MetroFont, fontWeight = FontWeight.Light),
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
