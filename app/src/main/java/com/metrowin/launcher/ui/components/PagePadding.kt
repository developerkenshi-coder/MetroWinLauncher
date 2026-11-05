package com.metrowin.launcher.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.PagePadding

/** The room the pages leave at the sides, as the settings chose (see [pageSide]). */
val LocalPagePadding = staticCompositionLocalOf { PagePadding.Normal }

/** The margin at each side of a page whose design has [base]: that, less or more as the settings say, and never so much that the page is too narrow. */
@Composable
fun pageSide(base: Dp): Dp {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
    return LocalPagePadding.current.sideDp(base.value, width).dp
}

/** The widest a page's content goes: on a bigger screen it stays this wide, centred, and only the background fills the screen. */
val PageMaxWidth = 640.dp

/** Margins at both sides that keep the content under [PageMaxWidth] on a wide screen; none on a phone held upright. */
@Composable
fun Modifier.centeredContent(): Modifier {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return padding(horizontal = ((width - PageMaxWidth) / 2).coerceAtLeast(0.dp))
}
