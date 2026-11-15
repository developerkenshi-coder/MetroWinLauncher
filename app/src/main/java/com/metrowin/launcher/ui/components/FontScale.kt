package com.metrowin.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** The most the text grows (1.0 is the size asked for) in the things that sit in a box of a fixed size, with the biggest text of the phone (2.0). */
object TextScaleCap {
    const val TILE_LABEL = 1.2f
    const val BAR = 1.4f
    const val PIVOT = 1.3f
}

/**
 * Draws [content] with the text no bigger than [max] times its size, whatever the user asked for. For the small things that
 * sit in a box of a fixed size, a tile or a button of the panel: with the biggest text of the phone and the biggest the settings
 * allow, their words would have to be drawn smaller than they were to begin with.
 */
@Composable
fun FontScaleAtMost(max: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, max)) { content() }
    }
}
