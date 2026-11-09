package com.metrowin.launcher.ui.start

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.PhotoFit
import com.metrowin.launcher.data.PhotoFraming
import com.metrowin.launcher.model.Tile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** How the Start photo is framed: zoom in percent, and the point that stays in view in percent. */
@Immutable
data class PhotoFrame(val zoom: Int = 100, val x: Int = 50, val y: Int = 50)

/** What a tile needs to show its part of the Start photo (see [StartScreen]'s `photoInside`): the photo as it is framed, and the Start area its place is measured in. */
@Immutable
class PhotoWindow(
    val image: ImageBitmap, val scroll: ScrollState, val parallax: Boolean, val framing: PhotoFrame, val fit: PhotoFit,
    val area: androidx.compose.runtime.State<LayoutCoordinates?>,
)

val LocalPhotoWindow = androidx.compose.runtime.staticCompositionLocalOf<PhotoWindow?> { null }

/**
 * The whole Start photo drawn at the size of the Start area and moved so that the part under this tile lands on it: the tile is a window onto the
 * same photo (with its parallax) that would otherwise be behind everything. The tile clips it.
 */
@Composable
internal fun PhotoSlice(window: PhotoWindow, tile: () -> LayoutCoordinates?) {
    val size = window.area.value?.size ?: return
    val density = LocalDensity.current
    Box(
        Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
            .graphicsLayer {
                // Read here, so the photo is placed again whenever Start scrolls (the tile moves with it).
                window.scroll.value
                val area = window.area.value
                val at = tile()
                if (area != null && at != null && area.isAttached && at.isAttached) {
                    val p = area.localPositionOf(at, Offset.Zero)
                    translationX = -p.x
                    translationY = -p.y
                }
                transformOrigin = TransformOrigin(0f, 0f)
            },
    ) { ParallaxBackground(window.image, { window.scroll.value }, window.parallax, window.framing, window.fit) }
}

/**
 * The Start photo, behind the pager instead of inside each Start screen, so swiping from one Start to the next leaves it where it is and only
 * the tiles move. With a different photo per Start the neighbour's one fades in over the current one as the pages turn. Past the last Start
 * the photo goes with it (and fades), as it did when it was part of the page. Parallax follows the scroll of the pages in view, blended.
 */
@Composable
internal fun StartBackdrop(
    pager: PagerState, startPages: Int, reverse: Boolean, scrolls: List<ScrollState>, imageOf: (Int) -> ImageBitmap?,
    parallax: Boolean, framing: PhotoFrame, fit: PhotoFit,
) {
    val current = pager.currentPage.coerceIn(0, startPages - 1)
    val image = imageOf(current)
    val scroll = {
        val f = pager.currentPageOffsetFraction
        val own = scrolls[current].value
        val next = scrolls[(current + if (f > 0f) 1 else -1).coerceIn(0, startPages - 1)].value
        (own + (next - own) * abs(f)).roundToInt()
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val away = (pager.currentPage + pager.currentPageOffsetFraction - (startPages - 1)).coerceIn(0f, 1f)
            translationX = size.width * away * (if (reverse) 1f else -1f)
            alpha = 1f - away * away
        },
    ) {
        for (page in (current - 1)..(current + 1)) {
            if (page !in 0 until startPages) continue
            val photo = imageOf(page) ?: continue
            // The same photo on both sides: nothing to fade, the current one already is it.
            if (page != current && photo === image) continue
            key(page) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val f = pager.currentPageOffsetFraction
                        alpha = if (page == current) 1f else max(0f, if (page > current) f else -f)
                    },
                ) { ParallaxBackground(photo, scroll, parallax, framing, fit) }
            }
        }
    }
}

/** The photo, drawn taller than the screen and moved up slower than the tiles. */
@Composable
internal fun ParallaxBackground(image: ImageBitmap, scroll: () -> Int, parallax: Boolean, framing: PhotoFrame, fit: PhotoFit) {
    val travel = if (parallax) 120.dp else 0.dp
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val travelPx = with(LocalDensity.current) { travel.toPx() }
        val moving = Modifier
            .fillMaxWidth()
            // Taller than the area, as the comment above says: a plain height() is cut back to the area, and the photo moved up by the
            // parallax left a black band (as high as the travel) at the bottom. Unbounded, and placed from the top, it really is that tall.
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .requiredHeight(maxHeight + travel)
            .graphicsLayer {
                translationY = -(scroll() * PARALLAX).coerceAtMost(travelPx)
                // Zoomed in around the point the user chose, so that point stays where it is (only when the photo covers the screen).
                val zoom = if (fit == PhotoFit.Fill) framing.zoom / 100f else 1f
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(framing.x / 100f, framing.y / 100f)
            }
        if (fit == PhotoFit.Tile) {
            Box(moving.drawBehind { drawRect(ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated))) })
        } else {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = fit.contentScale(),
                alignment = if (fit == PhotoFit.Fill) BiasAlignment(PhotoFraming.bias(framing.x), PhotoFraming.bias(framing.y)) else Alignment.Center,
                modifier = moving,
            )
        }
        // A light shade keeps the white glyphs readable on a bright photo.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
    }
}

private const val PARALLAX = 0.2f
