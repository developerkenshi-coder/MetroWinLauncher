package com.metrowin.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.metroTween
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.onAccent

/** The square of Windows Phone for picking rows: an outline, filled with the accent colour and ticked when picked. */
@Composable
fun SelectionBox(checked: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Box(
        modifier.size(28.dp).border(2.dp, if (checked) accent else LocalMetroColors.current.foreground).background(if (checked) accent else androidx.compose.ui.graphics.Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) MetroText("✓", size = 19.sp, color = onAccent())
    }
}

/**
 * A row of a list that can be picked: while [selecting] a square slides in on its left, and a touch picks instead of opening
 * ([onClick] decides what a touch does). The long press is how picking starts.
 */
@Composable
fun SelectableRow(
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val reduced = LocalReduceMotion.current
    // The other looks keep the tint of a picked row; the square is the classic one.
    val classic = LocalStyle.current.isClassic
    Row(
        modifier.fillMaxWidth().background(if (checked) LocalAccent.current.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent)
            .metroPress(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            selecting && classic,
            enter = if (reduced) EnterTransition.None else expandHorizontally(metroTween(220, easing = Motion.Decelerate)) + fadeIn(metroTween(220)),
            exit = if (reduced) ExitTransition.None else shrinkHorizontally(metroTween(160, easing = Motion.Accelerate)) + fadeOut(metroTween(160)),
        ) { SelectionBox(checked, Modifier.padding(end = 14.dp)) }
        content()
    }
}
