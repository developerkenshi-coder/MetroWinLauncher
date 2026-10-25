package com.metrowin.launcher.ui.start

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.panel

/** A folder of Start the app can be put in: its id, the name it has (empty until it is given one) and how many apps it holds. */
@Immutable
data class FolderChoice(val id: String, val name: String, val apps: Int)

/** "Add to which folder?": the folders of Start, one under the other. */
@Composable
fun FolderChooser(folders: List<FolderChoice>, onPick: (FolderChoice) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMetroColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onCancel() } }
            .testTag("folderchooser"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .panel()
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 18.dp),
        ) {
            MetroText(
                stringResource(R.string.folder_choose_title), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                folders.forEach { folder ->
                    Row(
                        Modifier.fillMaxWidth().testTag("folderchooser:${folder.id}").metroPress(onClick = { onPick(folder) }).padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(48.dp).litFill(true), contentAlignment = Alignment.Center) {
                            GlyphIcon(Glyph.Folder, 30.dp, color = onAccent())
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            MetroText(folder.name.ifBlank { stringResource(R.string.folder_default_name) }, size = 22.sp)
                            MetroText(
                                pluralStringResource(R.plurals.folder_apps_count, folder.apps, folder.apps),
                                size = 14.sp, weight = FontWeight.Normal, color = colors.subtle,
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f).testTag("folderchooser:cancel"), onClick = onCancel)
            }
        }
    }
}
