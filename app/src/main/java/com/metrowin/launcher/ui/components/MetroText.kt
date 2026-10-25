package com.metrowin.launcher.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.theme.MetroFont
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** Text in the Metro typeface. Light by default, like the big Windows Phone titles. */
@Composable
fun MetroText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    weight: FontWeight = FontWeight.Light,
    color: Color = LocalMetroColors.current.foreground,
    maxLines: Int = 1,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = MetroFont,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
