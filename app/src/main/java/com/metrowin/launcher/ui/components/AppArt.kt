package com.metrowin.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.TileWhite

val LocalIconLoader = staticCompositionLocalOf<IconLoader?> { null }

/** The app's own icon as a flat square. Draws nothing until the icon is loaded. */
@Composable
fun AppIcon(item: AppItem, size: Dp, modifier: Modifier = Modifier) {
    val loader = LocalIconLoader.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, item.id, px, loader, loader?.revision) {
        value = loader?.load(item, px)?.asImageBitmap()
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier.size(size)) }
}

/** A stock app is its Metro glyph, any other app is its own icon. */
@Composable
fun AppArt(item: AppItem, size: Dp, modifier: Modifier = Modifier) {
    val glyph = item.glyph
    if (glyph != null) GlyphIcon(glyph, size, modifier) else AppIcon(item, size, modifier)
}

/** The square next to an app's name in the app list: glyph on the accent colour, or the plain icon. */
@Composable
fun AppSquare(item: AppItem, size: Dp, modifier: Modifier = Modifier) {
    val glyph = item.glyph
    Box(
        modifier.size(size).background(if (glyph != null) LocalAccent.current else LocalMetroColors.current.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) GlyphIcon(glyph, size * GLYPH_IN_SQUARE, color = TileWhite) else AppIcon(item, size)
    }
}

private const val GLYPH_IN_SQUARE = 0.64f
