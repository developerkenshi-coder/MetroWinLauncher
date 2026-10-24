package com.metrowin.launcher.ui.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.Placement
import com.metrowin.launcher.data.TileGrid
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.turnstile
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.MetroBlack
import com.metrowin.launcher.ui.theme.MetroWhite
import kotlinx.coroutines.delay

/** A pinned tile together with the app it opens. */
@Immutable
data class StartTile(val tile: Tile, val item: AppItem)

/** The tile that was tapped; every tile turns away, that one last, before the app opens. */
@Immutable
data class ExitRequest(val tileId: String)

private val TileGap = 6.dp

/**
 * The Windows Phone Start screen: a column of live tiles that scrolls vertically, with an arrow at
 * the end that leads to the app list. Tiles swing in around their left edge when the screen opens,
 * and swing out when one of them is tapped.
 */
@Composable
fun StartScreen(
    tiles: List<StartTile>,
    live: LiveTileData,
    enterToken: Int,
    exitRequest: ExitRequest?,
    editingId: String?,
    scrollState: ScrollState,
    onTileClick: (StartTile) -> Unit,
    onTileLongClick: (StartTile) -> Unit,
    onUnpin: (StartTile) -> Unit,
    onResize: (StartTile) -> Unit,
    onBackgroundTap: () -> Unit,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentBackgroundTap by rememberUpdatedState(onBackgroundTap)
    Column(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { currentBackgroundTap() } }
            .verticalScroll(scrollState)
            .statusBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        TileGridView(
            tiles, live, enterToken, exitRequest, editingId,
            onTileClick, onTileLongClick, onUnpin, onResize,
        )
        Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp), contentAlignment = Alignment.CenterEnd) {
            NextPageArrow(onOpenApps)
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun TileGridView(
    tiles: List<StartTile>,
    live: LiveTileData,
    enterToken: Int,
    exitRequest: ExitRequest?,
    editingId: String?,
    onTileClick: (StartTile) -> Unit,
    onTileLongClick: (StartTile) -> Unit,
    onUnpin: (StartTile) -> Unit,
    onResize: (StartTile) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unit = (maxWidth - TileGap * (TileGrid.COLUMNS - 1)) / TileGrid.COLUMNS
        val sizes = tiles.map { it.tile.size }
        val layout = remember(sizes) { TileGrid.place(sizes) }
        val height = if (layout.rows == 0) 0.dp else unit * layout.rows + TileGap * (layout.rows - 1)

        Box(Modifier.fillMaxWidth().height(height)) {
            tiles.forEachIndexed { index, start ->
                key(start.tile.id) {
                    PositionedTile(
                        start, layout.placements[index], index, unit, live,
                        enterToken, exitRequest, editingId,
                        onTileClick, onTileLongClick, onUnpin, onResize,
                    )
                }
            }
        }
    }
}

/** Slides to its place in the grid when tiles are resized, pinned or unpinned around it. */
@Composable
private fun PositionedTile(
    start: StartTile,
    placement: Placement,
    index: Int,
    unit: Dp,
    live: LiveTileData,
    enterToken: Int,
    exitRequest: ExitRequest?,
    editingId: String?,
    onTileClick: (StartTile) -> Unit,
    onTileLongClick: (StartTile) -> Unit,
    onUnpin: (StartTile) -> Unit,
    onResize: (StartTile) -> Unit,
) {
    val density = LocalDensity.current
    val step = unit + TileGap
    val target = with(density) { IntOffset((step * placement.col).roundToPx(), (step * placement.row).roundToPx()) }
    val offset by animateIntOffsetAsState(target, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow), label = "tileOffset")
    val width by animateDpAsState(unit * placement.cols + TileGap * (placement.cols - 1), spring(stiffness = Spring.StiffnessLow), label = "tileWidth")
    val height by animateDpAsState(unit * placement.rows + TileGap * (placement.rows - 1), spring(stiffness = Spring.StiffnessLow), label = "tileHeight")

    // 1 = turned away, 0 = facing the screen.
    val away = remember { Animatable(1f) }
    LaunchedEffect(enterToken) {
        away.snapTo(1f)
        delay(entryDelay(placement, index))
        away.animateTo(0f, tween(durationMillis = 420, easing = Motion.Decelerate))
    }
    LaunchedEffect(exitRequest) {
        if (exitRequest == null) return@LaunchedEffect
        delay(if (exitRequest.tileId == start.tile.id) TAPPED_EXIT_DELAY_MS else exitDelay(placement))
        away.animateTo(1f, tween(durationMillis = 240, easing = Motion.Accelerate))
    }

    val selected = editingId == start.tile.id
    val editScale by animateFloatAsState(
        targetValue = if (editingId != null && !selected) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "editScale",
    )

    Box(
        Modifier
            .offset { offset }
            .size(width, height)
            .graphicsLayer {
                turnstile(away.value)
                scaleX = editScale
                scaleY = editScale
            }
            .metroPress(
                onClick = { onTileClick(start) },
                onLongClick = { onTileLongClick(start) },
                enabled = exitRequest == null,
            )
            .background(LocalAccent.current)
            .clipToBounds(),
    ) {
        TileFace(start.item, start.tile.size, width, height, live, seed = start.tile.id.hashCode())
        if (selected) {
            EditButton(
                unpin = true,
                onClick = { onUnpin(start) },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
            EditButton(
                unpin = false,
                onClick = { onResize(start) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
        }
    }
}

/** Top rows come in first, left before right, with a little spread so it never looks mechanical. */
private fun entryDelay(placement: Placement, index: Int): Long {
    val ripple = (placement.row / 2) * 55L + (placement.col / 2) * 35L
    return (ripple + (index * 37 % 40)).coerceAtMost(MAX_ENTRY_DELAY_MS)
}

private fun exitDelay(placement: Placement): Long =
    ((placement.row / 2) * 18L + (placement.col / 2) * 10L).coerceAtMost(120L)

private const val MAX_ENTRY_DELAY_MS = 450L
private const val TAPPED_EXIT_DELAY_MS = 140L

/** The round buttons of a selected tile: unpin on the top right, resize on the bottom right. */
@Composable
private fun EditButton(unpin: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val currentClick by rememberUpdatedState(onClick)
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MetroWhite)
            .pointerInput(Unit) { detectTapGestures { currentClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val stroke = 2.2.dp.toPx()
            if (unpin) {
                line(0.1f, 0.1f, 0.9f, 0.9f, stroke)
                line(0.9f, 0.1f, 0.1f, 0.9f, stroke)
            } else {
                // Arrow towards the bottom right corner: the tile grows or wraps back to small.
                line(0.1f, 0.1f, 0.9f, 0.9f, stroke)
                line(0.9f, 0.4f, 0.9f, 0.9f, stroke)
                line(0.4f, 0.9f, 0.9f, 0.9f, stroke)
            }
        }
    }
}

private fun DrawScope.line(x0: Float, y0: Float, x1: Float, y1: Float, strokeWidth: Float) {
    drawLine(
        MetroBlack,
        Offset(size.width * x0, size.height * y0),
        Offset(size.width * x1, size.height * y1),
        strokeWidth,
        StrokeCap.Round,
    )
}

/** The circled arrow after the last tile, which leads to the app list. */
@Composable
private fun NextPageArrow(onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .metroPress(onClick = onClick)
            .border(2.dp, MetroWhite, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 2.4.dp.toPx()
            drawLine(MetroWhite, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
            drawLine(MetroWhite, Offset(size.width * 0.58f, size.height * 0.12f), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
            drawLine(MetroWhite, Offset(size.width * 0.58f, size.height * 0.88f), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
        }
    }
}
