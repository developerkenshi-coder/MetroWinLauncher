package com.metrowin.launcher.ui.gesture

import com.metrowin.launcher.ui.components.topBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion

/** How fast a title moves compared with the pages: half as fast, so it is left behind as the pages slide (the panorama of Windows Phone). */
const val PANORAMA_SPEED = 0.5f

/** How much of the colour of the text the titles keep: they are a backdrop, the tiles and the rows are read on top of them. */
private const val PANORAMA_ALPHA = 0.13f

private val TITLE_SIZE = 150.sp

/**
 * Where the title of the page [anchor] is, in pixels from the left edge, when the pager is at [position] (the page plus the part of
 * the next one that has come into view): at the edge while its page is in view, and moving [speed] times as fast as the pages on
 * the way in and out. Pure, so it is tested.
 */
fun panoramaOffset(anchor: Int, position: Float, pageWidth: Float, speed: Float = PANORAMA_SPEED, reverse: Boolean = false): Float =
    (anchor - position) * pageWidth * speed * (if (reverse) -1f else 1f)

/**
 * How much of a title is seen: all of it while the pager is on one of its pages ([anchor] to [last]), none from half a page away. The
 * titles run on a strip that is slower than the pages, so two of them would otherwise cross; this way one is gone before the other is there.
 */
fun panoramaVisibility(anchor: Int, last: Int, position: Float): Float {
    val distance = if (position < anchor) anchor - position else if (position > last) position - last else 0f
    return (1f - 2f * distance).coerceIn(0f, 1f)
}

/**
 * The big titles of the pages ("start" behind the Start screens, "apps" behind the app list) as one strip that moves slower than
 * the pages, so the pages slide over it. It is drawn behind the pager (or over it, with a Start photo, which would hide it) and
 * takes no touch. The position of the pager is read only while it is drawn: sliding the pages never recomposes anything here.
 * With reduced motion the titles follow the pages one to one.
 */
@Composable
fun PanoramaTitles(
    state: PagerState,
    startPages: Int,
    startTitle: String,
    appsTitle: String,
    modifier: Modifier = Modifier,
    /** The pages run from the right (see [LauncherPager]). */
    reverse: Boolean = false,
) {
    val color = LocalMetroColors.current.foreground.copy(alpha = PANORAMA_ALPHA)
    val speed = if (LocalReduceMotion.current) 1f else PANORAMA_SPEED
    Box(modifier.fillMaxSize().clipToBounds()) {
        PanoramaTitle(startTitle, 0, startPages - 1, state, speed, color, "panorama:start", reverse)
        PanoramaTitle(appsTitle, startPages, startPages, state, speed, color, "panorama:apps", reverse)
    }
}

/** One title of the strip, anchored at the page [anchor]: as wide as its letters need, and moved in the draw phase only. */
@Composable
private fun PanoramaTitle(text: String, anchor: Int, last: Int, state: PagerState, speed: Float, color: Color, tag: String, reverse: Boolean) {
    MetroText(
        text,
        Modifier
            .layout { measurable, constraints ->
                // A title may reach beyond the edge of the screen: it is measured with all the room it wants.
                val placeable = measurable.measure(Constraints())
                val pageWidth = constraints.maxWidth
                layout(pageWidth, placeable.height) {
                    placeable.placeWithLayer(0, 0) {
                        val position = state.currentPage + state.currentPageOffsetFraction
                        translationX = panoramaOffset(anchor, position, pageWidth.toFloat(), speed, reverse) + if (reverse) pageWidth - placeable.width.toFloat() else 0f
                        alpha = panoramaVisibility(anchor, last, position)
                    }
                }
            }
            // After the layout above, so that the tag is on the part that moves.
            .testTag(tag)
            .topBarsPadding()
            .padding(start = 8.dp, top = 4.dp),
        size = TITLE_SIZE, weight = FontWeight.Light, color = color, maxLines = 1, minScale = 1f,
    )
}
