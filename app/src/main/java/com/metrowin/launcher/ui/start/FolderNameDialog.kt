package com.metrowin.launcher.ui.start

import com.metrowin.launcher.ui.components.hideKeyboardWhenGone
import com.metrowin.launcher.ui.theme.panel
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.fieldTextStyle

/** Asks for the name of a Start folder. */
@Composable
fun FolderNameDialog(
    initial: String,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the field is for: a folder by default; the app list asks for the name of an app with it too. */
    @StringRes title: Int = R.string.folder_name,
    maxLength: Int = MAX_NAME,
) {
    val colors = LocalMetroColors.current
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onCancel() } }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .panel()
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MetroText(stringResource(title), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(maxLength) },
                singleLine = true,
                textStyle = fieldTextStyle(colors.foreground, 26.sp),
                cursorBrush = SolidColor(LocalAccent.current),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(name) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).hideKeyboardWhenGone(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(stringResource(android.R.string.ok), Modifier.weight(1f)) { onDone(name) }
                MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f), onClick = onCancel)
            }
        }
    }
}

private const val MAX_NAME = 24
