package com.metrowin.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ActionBarStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** Which action bar the launcher draws (see [ActionBarStyle]); set once at the root, next to the theme. */
val LocalActionBar = staticCompositionLocalOf { ActionBarStyle.Super }

/**
 * One round button of an [AppBar]: a [symbol] in a ring, with its [label] under it. In the Retro bar the first four are
 * buttons and the rest are text items; [marked] shows the item that is on (the size a tile has now).
 */
class AppBarItem(val symbol: String, val label: String, val onClick: () -> Unit) {
    var marked = false
        private set

    fun marked(on: Boolean) = apply { marked = on }

    /** A drawn mark in place of the [symbol] text, for the signs the font of the launcher does not have. */
    var icon: Mark? = null
        private set

    fun icon(mark: Mark) = apply { icon = mark }
}

/**
 * The application bar at the bottom of a Windows Phone app. The Super bar is a few round buttons with a label each; in the Retro
 * bar (see [ActionBarStyle]) the same items are the bar of Windows Phone 8.1. [menu] holds the less common actions: the Super
 * bar shows them under a "…" button, the Retro one as the text items under its own "…".
 */
@Composable
fun AppBar(items: List<AppBarItem>, modifier: Modifier = Modifier, menu: List<AppBarItem> = emptyList()) {
    if (LocalActionBar.current == ActionBarStyle.Retro) {
        RetroBar(items.take(RETRO_BUTTONS), items.drop(RETRO_BUTTONS) + menu, modifier)
        return
    }
    if (menu.isEmpty()) {
        SuperBar(items, modifier)
        return
    }
    var more by remember { mutableStateOf(false) }
    SuperBar(items + AppBarItem("…", stringResource(R.string.mail_more)) { more = !more }, modifier)
    if (more) SuperBar(menu, modifier)
}

@Composable
private fun SuperBar(items: List<AppBarItem>, modifier: Modifier) {
    val colors = LocalMetroColors.current
    Row(
        modifier.fillMaxWidth().background(colors.containers.getOrElse(0) { colors.surface }).padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.metroPress(onClick = item.onClick).padding(horizontal = 12.dp)) {
                Box(Modifier.size(40.dp).border(2.dp, colors.foreground, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                    item.icon?.let { MarkIcon(it, 20.dp) } ?: MetroText(item.symbol, size = 20.sp, weight = FontWeight.Normal)
                }
                MetroText(item.label, size = 11.sp, color = colors.subtle, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
