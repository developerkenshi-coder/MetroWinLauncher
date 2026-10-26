package com.metrowin.launcher.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Draws a block of content smaller, down to [minScale] of its size, when it is taller than the room it was given: the lock screen
 * with all its lines on a short screen, or with the text at its biggest. The content is laid out as if the room were wider by the
 * same factor (so the lines that were wrapped take fewer rows) and drawn scaled from its top left corner, and the block takes the
 * height it ends up with. With room enough, or with no limit on the height, nothing changes. Like the text version in
 * [MetroText], one pass of the layout and no state: nothing is drawn at a wrong size first.
 */
fun Modifier.shrinkToHeight(minScale: Float): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    var scale = 1f
    var placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
    repeat(MAX_PASSES) {
        val shown = placeable.height * scale
        if (shown <= constraints.maxHeight || scale <= minScale) return@repeat
        scale = (scale * constraints.maxHeight / shown).coerceAtLeast(minScale)
        placeable = measurable.measure(wider(constraints, scale))
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

/** The constraints of a room [scale] times as wide as the real one and as tall as it takes. */
private fun wider(c: Constraints, scale: Float): Constraints {
    if (!c.hasBoundedWidth) return c.copy(maxHeight = Constraints.Infinity)
    val maxWidth = ceil(c.maxWidth / scale).toInt()
    return Constraints(minWidth = ceil(c.minWidth / scale).toInt().coerceAtMost(maxWidth), maxWidth = maxWidth)
}

private const val MAX_PASSES = 4
