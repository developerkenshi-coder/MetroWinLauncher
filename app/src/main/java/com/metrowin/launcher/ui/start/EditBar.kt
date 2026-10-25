package com.metrowin.launcher.ui.start

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.model.TileStyle
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.sheet

/**
 * The bar at the bottom of Start while a tile is selected: the three sizes to choose from (instead of
 * cycling through them with the arrow), which Start screen the tile is on, and the button that ends editing.
 * [page] is the screen of the selected entry, 0 based, out of [pageCount].
 */
@Composable
fun EditBar(
    size: TileSize,
    page: Int,
    pageCount: Int,
    onSize: (TileSize) -> Unit,
    onMovePage: (delta: Int) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** The style of the selected tile, or null for a folder, which has none. */
    style: TileStyle? = null,
    onStyle: () -> Unit = {},
    /** The button that adds a contact, a web page or a widget; null leaves it out. */
    onAdd: (() -> Unit)? = null,
    /** The button that picks several tiles to put in a folder; null leaves it out. */
    onFolder: (() -> Unit)? = null,
) {
    val colors = LocalMetroColors.current
    Row(
        modifier
            .fillMaxWidth()
            .sheet()
            // Swallow touches so the tiles behind the bar do not react.
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onAdd != null) AddButton(onAdd)
        if (onFolder != null) FolderButton(onFolder)
        TileSize.entries.forEach { option ->
            SizeButton(option, selected = option == size, onClick = { onSize(option) })
        }
        if (style != null) {
            val description = stringResource(
                when (style) {
                    TileStyle.Standard -> R.string.edit_style_standard
                    TileStyle.Iconic -> R.string.edit_style_iconic
                    TileStyle.Cycle -> R.string.edit_style_cycle
                },
            )
            val label = stringResource(
                when (style) {
                    TileStyle.Standard -> R.string.edit_style_short_standard
                    TileStyle.Iconic -> R.string.edit_style_short_iconic
                    TileStyle.Cycle -> R.string.edit_style_short_cycle
                },
            )
            Box(
                Modifier
                    .testTag("edit:style")
                    .semantics { contentDescription = description }
                    .metroPress(onClick = onStyle)
                    .litFill(style != TileStyle.Standard)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                MetroText(label, size = 15.sp, weight = FontWeight.Normal, color = if (style != TileStyle.Standard) onAccent() else colors.foreground)
            }
        }
        if (pageCount > 1) {
            Box(Modifier.size(width = 1.dp, height = 28.dp).background(colors.subtle.copy(alpha = 0.4f)))
            ArrowButton("‹", stringResource(R.string.edit_page_previous), page > 0, "edit:page:previous") { onMovePage(-1) }
            MetroText(
                stringResource(R.string.edit_page, page + 1),
                size = 15.sp, weight = FontWeight.Normal, color = colors.subtle,
            )
            ArrowButton("›", stringResource(R.string.edit_page_next), page < pageCount - 1, "edit:page:next") { onMovePage(1) }
        }
        Box(Modifier.weight(1f))
        Box(Modifier.testTag("edit:done").metroPress(onClick = onDone).padding(horizontal = 10.dp, vertical = 10.dp)) {
            MetroText(stringResource(R.string.edit_done), size = 18.sp, weight = FontWeight.Normal, color = LocalAccent.current)
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val description = stringResource(R.string.edit_add)
    Box(
        Modifier
            .size(46.dp)
            .testTag("edit:add")
            .semantics { contentDescription = description }
            .metroPress(onClick = onClick)
            .litFill(false),
        contentAlignment = Alignment.Center,
    ) {
        MetroText("+", size = 30.sp, weight = FontWeight.Normal)
    }
}

@Composable
private fun FolderButton(onClick: () -> Unit) {
    val description = stringResource(R.string.edit_folder)
    Box(
        Modifier
            .size(46.dp)
            .testTag("edit:folder")
            .semantics { contentDescription = description }
            .metroPress(onClick = onClick)
            .litFill(false),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.Folder, 28.dp)
    }
}

@Composable
private fun SizeButton(size: TileSize, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val description = stringResource(
        when (size) {
            TileSize.Small -> R.string.edit_size_small
            TileSize.Medium -> R.string.edit_size_medium
            TileSize.Wide -> R.string.edit_size_wide
        },
    )
    Box(
        Modifier
            .size(46.dp)
            .testTag("edit:size:${size.name}")
            .semantics { contentDescription = description }
            .metroPress(onClick = onClick)
            .litFill(selected),
        contentAlignment = Alignment.Center,
    ) {
        // A little picture of the size: its proportions as a filled rectangle.
        val tint = if (selected) onAccent() else colors.foreground
        Canvas(Modifier.size(30.dp)) {
            val unit = this.size.width / 8f
            val (w, h) = when (size) {
                TileSize.Small -> 2f to 2f
                TileSize.Medium -> 4f to 4f
                TileSize.Wide -> 8f to 4f
            }
            drawRect(tint, Offset((this.size.width - w * unit) / 2, (this.size.height - h * unit) / 2), Size(w * unit, h * unit))
        }
    }
}

@Composable
private fun ArrowButton(symbol: String, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .testTag(tag)
            .semantics { contentDescription = description }
            .then(if (enabled) Modifier.metroPress(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(
            symbol, size = 30.sp, weight = FontWeight.Normal,
            color = if (enabled) LocalMetroColors.current.foreground else LocalMetroColors.current.inactive,
        )
    }
}
