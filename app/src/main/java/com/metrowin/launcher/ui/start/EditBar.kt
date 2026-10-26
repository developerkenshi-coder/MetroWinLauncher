package com.metrowin.launcher.ui.start

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
@OptIn(ExperimentalLayoutApi::class)
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
    /** The "…" button, which shows or hides the names of the buttons (see [labels]); null leaves it out, and the names with it. */
    onLabels: (() -> Unit)? = null,
    /** The names of the buttons are written under them, as when the app bar of Windows Phone is opened with its "…". */
    labels: Boolean = false,
) {
    val colors = LocalMetroColors.current
    val named = labels && onLabels != null
    // The controls go on as many lines as the width needs: nothing is squeezed, cut or pushed off the screen.
    FlowRow(
        modifier
            .fillMaxWidth()
            .sheet()
            // Swallow touches so the tiles behind the bar do not react.
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (onAdd != null) Named(if (named) stringResource(R.string.edit_label_add) else null) { AddButton(onAdd) }
        if (onFolder != null) Named(if (named) stringResource(R.string.edit_label_folder) else null) { FolderButton(onFolder) }
        TileSize.entries.forEach { option ->
            val name = when (option) {
                TileSize.Small -> R.string.edit_label_small
                TileSize.Medium -> R.string.edit_label_medium
                TileSize.Wide -> R.string.edit_label_wide
            }
            Named(if (named) stringResource(name) else null) { SizeButton(option, selected = option == size, onClick = { onSize(option) }) }
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
            // The arrows and the number of the screen stay together.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArrowButton(true, stringResource(R.string.edit_page_previous), page > 0, "edit:page:previous") { onMovePage(-1) }
                MetroText(
                    stringResource(R.string.edit_page, page + 1),
                    size = 15.sp, weight = FontWeight.Normal, color = colors.subtle,
                )
                ArrowButton(false, stringResource(R.string.edit_page_next), page < pageCount - 1, "edit:page:next") { onMovePage(1) }
            }
        }
        // The "…" and "done" are one piece at the right end: when the line is full they go to the next line together, on its right.
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (onLabels != null) MoreButton(labels, onLabels)
            Box(Modifier.testTag("edit:done").metroPress(onClick = onDone).padding(horizontal = 10.dp, vertical = 10.dp)) {
                MetroText(stringResource(R.string.edit_done), size = 18.sp, weight = FontWeight.Normal, color = LocalAccent.current)
            }
        }
    }
}

/** A button with its name under it when [label] is not null. */
@Composable
private fun Named(label: String?, content: @Composable () -> Unit) {
    if (label == null) {
        content()
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        MetroText(label, size = 12.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 1)
    }
}

/** The "…" of the bar: lit while the names of the buttons are shown. */
@Composable
private fun MoreButton(labels: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (labels) R.string.edit_more_hide else R.string.edit_more_show)
    val tint = if (labels) onAccent() else LocalMetroColors.current.foreground
    Box(
        Modifier
            .size(46.dp)
            .testTag("edit:more")
            .semantics { contentDescription = description }
            .metroPress(onClick = onClick)
            .litFill(labels),
        contentAlignment = Alignment.Center,
    ) {
        // Three dots drawn, not typed: they do not depend on the font.
        Canvas(Modifier.size(width = 26.dp, height = 8.dp)) {
            val radius = 2.6.dp.toPx()
            repeat(3) { i ->
                drawCircle(tint, radius, Offset(radius + i * (this.size.width - 2 * radius) / 2f, this.size.height / 2f))
            }
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
private fun ArrowButton(left: Boolean, description: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val color = if (enabled) LocalMetroColors.current.foreground else LocalMetroColors.current.inactive
    Box(
        Modifier
            .size(40.dp)
            .testTag(tag)
            .semantics { contentDescription = description }
            .then(if (enabled) Modifier.metroPress(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // A chevron drawn, not typed: it does not depend on the font and has no line of text to be cut.
        Canvas(Modifier.size(width = 10.dp, height = 18.dp)) {
            val x = if (left) this.size.width else 0f
            val tip = if (left) 0f else this.size.width
            val stroke = 2.5.dp.toPx()
            drawLine(color, Offset(x, 0f), Offset(tip, this.size.height / 2f), strokeWidth = stroke)
            drawLine(color, Offset(tip, this.size.height / 2f), Offset(x, this.size.height), strokeWidth = stroke)
        }
    }
}
