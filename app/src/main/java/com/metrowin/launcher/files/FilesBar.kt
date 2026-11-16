package com.metrowin.launcher.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.ActionBarStyle
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.LocalActionBar
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** A button of the bar of the file manager. */
class FilesBarItem(val icon: FilesIcon, val label: String, val onClick: () -> Unit)

/**
 * The bar of the file manager: up to seven buttons that share the width (the shared bar has room for five), each at least 48 dp wide and
 * 56 dp high, with its drawn sign, its label under it and the label as its description for TalkBack. In the Retro look the shared bar is
 * used as it is: it already keeps four buttons and puts the others in its list of texts.
 */
@Composable
fun FilesBar(items: List<FilesBarItem>, modifier: Modifier = Modifier) {
    if (LocalActionBar.current.retro) {
        AppBar(items.map { AppBarItem(it.icon.symbol, it.label, it.onClick) }, modifier)
        return
    }
    val colors = LocalMetroColors.current
    Row(
        modifier.fillMaxWidth().background(colors.containers.getOrElse(0) { colors.surface }).padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Column(
                Modifier.weight(1f).defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
                    .semantics { contentDescription = item.label; role = Role.Button }
                    .metroPress(onClick = item.onClick).padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(34.dp).border(2.dp, colors.foreground, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                    FilesGlyph(item.icon, 18.dp)
                }
                MetroText(item.label, size = 12.sp, color = colors.subtle, maxLines = 1, minScale = 0.75f, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
