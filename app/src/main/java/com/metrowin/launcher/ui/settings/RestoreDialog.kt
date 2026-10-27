package com.metrowin.launcher.ui.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.RestorePart
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.panel

/** Asks which parts of a backup to put back: the Start screens, the settings, how the launcher looks. All of them are chosen to begin with. */
@Composable
fun RestoreDialog(onDone: (Set<RestorePart>) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    var parts by remember { mutableStateOf(RestorePart.entries.toSet()) }
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetroText(stringResource(R.string.restore_title), size = 22.sp)
            MetroText(stringResource(R.string.restore_hint), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE)
            RestorePart.entries.forEach { part ->
                MetroToggle(
                    stringResource(
                        when (part) {
                            RestorePart.Start -> R.string.restore_start
                            RestorePart.Settings -> R.string.restore_settings
                            RestorePart.Theme -> R.string.restore_theme
                        },
                    ),
                    part in parts,
                    onChange = { on -> parts = if (on) parts + part else parts - part },
                    modifier = Modifier.testTag("restore:${part.name}"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(stringResource(R.string.restore_go), Modifier.weight(1f).testTag("restore:go")) { if (parts.isNotEmpty()) onDone(parts) }
                MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag("restore:cancel"), onClick = onCancel)
            }
        }
    }
}
