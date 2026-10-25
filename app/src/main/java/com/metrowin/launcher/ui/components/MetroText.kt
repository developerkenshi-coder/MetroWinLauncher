package com.metrowin.launcher.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Text in the Metro typeface. Light by default, like the big Windows Phone titles.
 *
 * A text is never cut off and a word is never broken in two: it goes to a new line when it is too wide, and when the room it
 * was given is too narrow for its longest word (or for its one line, with [maxLines] of 1) or too short for its lines (a
 * button, a tile, a row of a fixed height) it is drawn smaller, down to [minScale] of its size, until it fits. Only what the
 * user wrote (the name of an app, a note) is meant to end in "…": those places say [maxLines].
 */
@Composable
fun MetroText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    weight: FontWeight = FontWeight.Light,
    color: Color = LocalMetroColors.current.foreground,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    minScale: Float = DEFAULT_MIN_SCALE,
) {
    val style = LocalStyle.current
    BasicText(
        text = text,
        modifier = if (minScale < 1f) modifier.shrinkToFit(minScale, singleLine = maxLines == 1) else modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = style.weight(weight),
            fontFamily = style.font,
            lineHeight = lineHeight,
            textAlign = textAlign,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Lays the text out as if the room were wider and taller than it is, and draws it smaller by the same factor, so that the
 * whole of it fits: the longest word on a line of its own (the whole line for a single line), and all the lines in the height.
 * One pass of the layout, no state: nothing is drawn at a wrong size first.
 */
private fun Modifier.shrinkToFit(minScale: Float, singleLine: Boolean): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val maxWidth = constraints.maxWidth
    // What the text needs across: its longest word, or its whole line when it must stay on one.
    val needed = if (singleLine) measurable.maxIntrinsicWidth(Constraints.Infinity) else measurable.minIntrinsicWidth(Constraints.Infinity)
    // One pixel of room on top: the text is measured in whole pixels, and a word that is a fraction too wide breaks in two.
    var scale = if (needed > maxWidth && needed > 0) (maxWidth.toFloat() / (needed + 1)).coerceAtLeast(minScale) else 1f
    var placeable = measurable.measure(virtual(constraints, scale))
    // Fewer lines at a smaller size: the height is looked at again until the size stops changing.
    if (constraints.hasBoundedHeight) {
        repeat(MAX_PASSES) {
            val height = placeable.height * scale
            if (height <= constraints.maxHeight || scale <= minScale) return@repeat
            scale = (scale * constraints.maxHeight / height).coerceAtLeast(minScale)
            placeable = measurable.measure(virtual(constraints, scale))
        }
    }
    val width = (placeable.width * scale).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = (placeable.height * scale).roundToInt().coerceAtLeast(constraints.minHeight)
    layout(width, height) {
        if (scale >= 1f) {
            placeable.place(0, 0)
        } else {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** The constraints of a room [scale] times as big as the real one in both ways. */
private fun virtual(c: Constraints, scale: Float): Constraints {
    if (scale >= 1f) return Constraints(minWidth = c.minWidth, maxWidth = c.maxWidth, minHeight = c.minHeight, maxHeight = Constraints.Infinity)
    val maxWidth = ceil(c.maxWidth / scale).toInt()
    return Constraints(minWidth = ceil(c.minWidth / scale).toInt().coerceAtMost(maxWidth), maxWidth = maxWidth)
}

/** How far the type of a text may shrink to fit, when nothing else is said. */
const val DEFAULT_MIN_SCALE = 0.5f

private const val MAX_PASSES = 4
