package com.metrowin.launcher.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * The bar at the bottom of Start while several tiles are being picked for a folder: what to do, how many are picked, the
 * button that makes the folder (once there are enough), and the one that gives up. [joining] is true when one of the
 * picked entries is already a folder, and the others will be added to it.
 */
@Composable
fun FolderPickBar(
    picked: Int,
    canCreate: Boolean,
    joining: Boolean,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface)
            // Swallow touches so the tiles behind the bar do not react.
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetroText(
            stringResource(if (joining) R.string.folder_pick_hint_folder else R.string.folder_pick_hint),
            size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2,
            modifier = Modifier.testTag("folderpick:hint"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroText(
                pluralStringResource(R.plurals.folder_pick_count, picked, picked), size = 20.sp, color = LocalAccent.current,
                modifier = Modifier.weight(1f).testTag("folderpick:count"),
            )
            Box(Modifier.metroPress(onClick = onCancel).padding(horizontal = 10.dp, vertical = 10.dp).testTag("folderpick:cancel")) {
                MetroText(stringResource(R.string.folder_pick_cancel), size = 18.sp, weight = FontWeight.Normal, color = colors.subtle)
            }
            MetroButton(
                stringResource(R.string.folder_pick_create),
                Modifier.alpha(if (canCreate) 1f else 0.4f).semantics { if (!canCreate) disabled() }.testTag("folderpick:create"),
            ) { if (canCreate) onCreate() }
        }
    }
}
