package com.metrowin.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.IconPackInfo
import com.metrowin.launcher.data.IconPick
import com.metrowin.launcher.data.IconPicks
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroType
import com.metrowin.launcher.ui.components.Spacing
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors

/**
 * Chooses the icon of the apps [ids] (one app: any icon of a pack; several: the pack, or back to the default). First the pack, then, for one
 * app, the grid of its icons with a search box. [names] lists the icons of a pack and [preview] draws one; both are asked for as the grid scrolls.
 */
@Composable
fun IconPickDialog(
    ids: List<String>,
    settings: LauncherSettings,
    packs: List<IconPackInfo>,
    names: suspend (String) -> List<String>,
    preview: suspend (String, String, Int) -> ImageBitmap?,
    onChange: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onClose: () -> Unit,
) {
    var pack by remember { mutableStateOf<IconPackInfo?>(null) }
    fun choose(pick: IconPick?) {
        onChange { it.copy(iconPicks = IconPicks.with(it.iconPicks, ids, pick)) }
        onClose()
    }
    DialogFrame(onCancel = onClose, title = stringResource(R.string.iconpick_title)) {
        val chosen = pack
        if (chosen == null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                PickRow(stringResource(R.string.iconpick_reset_default), "iconpick:default") { choose(null) }
                PickRow(stringResource(R.string.iconpick_own), "iconpick:own") { choose(IconPick("")) }
                packs.forEachIndexed { index, info ->
                    PickRow(info.label, "iconpick:pack:$index") { if (ids.size == 1) pack = info else choose(IconPick(info.packageName)) }
                }
            }
        } else {
            IconGrid(chosen, IconPick.parse(settings.iconPicks[ids.single()]), names, preview, onWhole = { choose(IconPick(chosen.packageName)) }) { choose(IconPick(chosen.packageName, it)) }
        }
    }
}

/** A row of the list of choices: at least 48 dp tall, text only. */
@Composable
private fun PickRow(text: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).metroPress(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) { MetroText(text, size = MetroType.Subtitle, modifier = Modifier.weight(1f)) }
}

@Composable
private fun IconGrid(
    pack: IconPackInfo,
    current: IconPick?,
    names: suspend (String) -> List<String>,
    preview: suspend (String, String, Int) -> ImageBitmap?,
    onWhole: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val all by produceState<List<String>?>(null, pack.packageName) { value = names(pack.packageName) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        MetroText(pack.label, size = MetroType.Subtitle)
        MetroButton(stringResource(R.string.iconpick_whole_pack), Modifier.fillMaxWidth().testTag("iconpick:whole"), onClick = onWhole)
        MetroTextField(query, { query = it }, stringResource(R.string.iconpick_search), Modifier.testTag("iconpick:query"))
        val list = all
        if (list == null) {
            MetroText(stringResource(R.string.iconpick_loading), size = MetroType.Body, color = LocalMetroColors.current.subtle)
        } else {
            val shown = remember(list, query) { IconPicks.filterNames(list, query) }
            if (shown.isEmpty()) {
                MetroText(stringResource(R.string.iconpick_empty), size = MetroType.Body, color = LocalMetroColors.current.subtle, modifier = Modifier.testTag("iconpick:empty"))
            } else {
                // A fixed height: the dialog scrolls by itself and a lazy grid cannot be measured without a limit.
                LazyVerticalGrid(
                    GridCells.Adaptive(CELL),
                    Modifier.fillMaxWidth().height(GRID_HEIGHT).testTag("iconpick:grid"),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    items(shown, key = { it }) { name ->
                        IconCell(pack.packageName, name, selected = name == current?.drawable && pack.packageName == current.pack, preview = preview) { onPick(name) }
                    }
                }
            }
        }
    }
}

/** One icon of the pack: a flat square, framed in the accent colour when it is the one in use. */
@Composable
private fun IconCell(pack: String, name: String, selected: Boolean, preview: suspend (String, String, Int) -> ImageBitmap?, onClick: () -> Unit) {
    val px = with(LocalDensity.current) { (CELL - 8.dp).roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, pack, name) { value = preview(pack, name, px) }
    val description = stringResource(R.string.iconpick_cell, name.replace('_', ' '))
    Box(
        Modifier
            .size(CELL)
            .testTag("iconpick:icon:$name")
            .semantics { contentDescription = description; role = Role.Button; this.selected = selected }
            .background(LocalMetroColors.current.surface)
            .then(if (selected) Modifier.border(3.dp, LocalAccent.current) else Modifier)
            .metroPress(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) { bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } }
}

private val CELL = 56.dp
private val GRID_HEIGHT = 288.dp
