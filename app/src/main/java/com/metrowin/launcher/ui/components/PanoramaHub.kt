package com.metrowin.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.gesture.PANORAMA_SPEED
import com.metrowin.launcher.ui.gesture.panoramaOffset
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle

/** How fast the backdrop of a panorama hub moves compared with the sections: slower than the title, which is slower than the sections. */
const val PANORAMA_BACKDROP_SPEED = 0.25f

/** How much of the next section peeks in at the right edge, so it is clear the hub goes on. */
private val PEEK = 36.dp

/**
 * The panorama of a Windows Phone hub (Photos, News): a huge title cut by the left and right edge that moves at half the speed of the
 * sections, a backdrop that moves slower still, and sections side by side with their own accent header and a sliver of the next one in
 * view. Without the Classic look it is the plain title, pivot and pager the hubs always had. [content] draws one section.
 */
@Composable
fun ColumnScope.HubPager(
    title: String,
    titles: List<String>,
    pager: PagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    /** The tags of the title and of the pager, for the tests. */
    titleTag: String = "hub:title",
    pagerTag: String = "hub:pager",
    content: @Composable (page: Int) -> Unit,
) {
    val style = LocalStyle.current
    val accent = LocalAccent.current
    val side = 24.dp
    if (!style.isClassic) {
        MetroText(title.lowercase(), size = 62.sp, modifier = Modifier.padding(start = side, top = 4.dp).testTag(titleTag))
        PivotHeader(titles, pager, Modifier.padding(start = side, top = 2.dp, bottom = 8.dp), size = 27.sp, color = accent)
        HorizontalPager(pager, modifier.weight(1f).fillMaxWidth().clipToBounds().testTag(pagerTag), beyondViewportPageCount = 0, userScrollEnabled = userScrollEnabled) { content(it) }
        return
    }
    val speed = if (LocalReduceMotion.current) 1f else PANORAMA_SPEED
    val backdrop = if (LocalReduceMotion.current) 1f else PANORAMA_BACKDROP_SPEED
    val foreground = LocalMetroColors.current.foreground
    Box(
        modifier.weight(1f).fillMaxWidth().clipToBounds()
            // The backdrop: wide soft bands of the accent, one per section, sliding at a quarter of the speed of the sections.
            .drawBehind {
                val position = pager.currentPage + pager.currentPageOffsetFraction
                val band = size.width
                val shift = -position * band * backdrop
                for (k in 0..titles.size) {
                    val left = shift + k * band
                    drawRect(
                        Brush.horizontalGradient(listOf(accent.copy(alpha = if (k % 2 == 0) 0.22f else 0.06f), Color.Transparent), startX = left, endX = left + band),
                        topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(band, size.height),
                    )
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // The title is as wide as its letters need and is cut by the edge of the screen; it is moved in the draw phase only.
            MetroText(
                title.lowercase(), size = 96.sp, weight = FontWeight.Light, maxLines = 1, minScale = 1f, color = foreground,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints())
                        layout(constraints.maxWidth, placeable.height) {
                            placeable.placeWithLayer(0, 0) {
                                val position = pager.currentPage + pager.currentPageOffsetFraction
                                translationX = panoramaOffset(0, position, constraints.maxWidth.toFloat(), speed)
                            }
                        }
                    }
                    .padding(start = 16.dp).testTag(titleTag),
            )
            HorizontalPager(
                pager, Modifier.weight(1f).fillMaxWidth().testTag(pagerTag), contentPadding = PaddingValues(end = PEEK),
                beyondViewportPageCount = 0, userScrollEnabled = userScrollEnabled,
            ) { page ->
                Column(Modifier.fillMaxSize()) {
                    MetroText(titles.getOrElse(page) { "" }, size = 27.sp, color = accent, maxLines = 1, modifier = Modifier.padding(start = side, bottom = 8.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) { content(page) }
                }
            }
        }
    }
}
