package com.metrowin.launcher.ui.start

import com.metrowin.launcher.ui.components.hideKeyboardWhenGone
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.DialogButtons
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.fieldTextStyle

/** Asks for the name of a Start folder, in the dialog frame of the launcher. */
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
    DialogFrame(onCancel, modifier, title = stringResource(title)) {
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
        DialogButtons({ onDone(name) }, onCancel, okTag = "foldername:ok", cancelTag = "foldername:cancel")
    }
}

private const val MAX_NAME = 24
