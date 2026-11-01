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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** One round button of an [AppBar]: a [symbol] in a ring, with its [label] under it. */
class AppBarItem(val symbol: String, val label: String, val onClick: () -> Unit)

/** The application bar at the bottom of a Windows Phone app: a few round buttons with a label each. */
@Composable
fun AppBar(items: List<AppBarItem>, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    Row(
        modifier.fillMaxWidth().background(colors.containers.getOrElse(0) { colors.surface }).padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.metroPress(onClick = item.onClick).padding(horizontal = 12.dp)) {
                Box(Modifier.size(40.dp).border(2.dp, colors.foreground, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                    MetroText(item.symbol, size = 20.sp, weight = FontWeight.Normal)
                }
                MetroText(item.label, size = 11.sp, color = colors.subtle, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
