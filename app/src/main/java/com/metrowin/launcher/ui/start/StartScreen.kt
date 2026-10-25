package com.metrowin.launcher.ui.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.metrowin.launcher.ui.theme.LocalHighContrast
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import com.metrowin.launcher.ui.components.cutoutSidesPadding
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.Placement
import com.metrowin.launcher.data.TileGrid
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Folder
import com.metrowin.launcher.model.StartEntry
import com.metrowin.launcher.model.Tile
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.notifications.TilePeek
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.turnstile
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.TileWhite
import kotlinx.coroutines.delay
import kotlin.math.min

/** One thing drawn on the Start grid: an app tile, a folder, or an app shown inside an open folder. */
@Immutable
sealed interface StartCell {
    /** Identity for animations. Unique, also for the apps of an open folder. */
    val key: String
    val size: TileSize

    /** The id of the pinned entry this cell belongs to, as the data layer knows it. */
    val entryId: String
}

@Immutable
data class AppCell(val tile: Tile, val item: AppItem, val folderId: String? = null) : StartCell {
    override val key get() = if (folderId == null) tile.id else "$folderId/${tile.id}"
    override val size get() = tile.size
    override val entryId get() = tile.id
}

@Immutable
data class FolderCell(val folder: Folder, val items: List<AppItem>, val expanded: Boolean) : StartCell {
    override val key get() = folder.id
    override val size get() = folder.size
    override val entryId get() = folder.id
}

/** An app inside an open folder cannot be dragged or resized. */
val StartCell.isTopLevel: Boolean get() = this !is AppCell || folderId == null

/** The apps of the open folder are laid out right after it, as medium tiles. */
fun buildStartCells(entries: List<StartEntry>, items: List<AppItem>, openFolderId: String?): List<StartCell> {
    val byId = items.associateBy { it.id }
    return buildList {
        for (entry in entries) when (entry) {
            is Tile -> byId[entry.id]?.let { add(AppCell(entry, it)) }
            is Folder -> {
                val apps = entry.children.mapNotNull { byId[it] }
                val open = entry.id == openFolderId
                add(FolderCell(entry, apps, expanded = open))
                if (open) apps.forEach { add(AppCell(Tile(it.id, TileSize.Medium), it, folderId = entry.id)) }
            }
        }
    }
}

/** The ids passed to [onMove] and [onMerge] are [StartCell.entryId]s. */
class StartActions(
    val onClick: (StartCell) -> Unit,
    val onLongClick: (StartCell) -> Unit,
    val onUnpin: (StartCell) -> Unit,
    val onResize: (StartCell) -> Unit,
    val onRename: (FolderCell) -> Unit,
    val onMove: (draggedId: String, targetId: String) -> Unit,
    val onMerge: (draggedId: String, ontoId: String) -> Unit,
    val onBackgroundTap: () -> Unit,
    /** A double tap on the empty part of Start, when the settings give it something to do. */
    val onDoubleTap: (() -> Unit)? = null,
    /** The arrow after the last tile: on to the next screen, or to the app list after the last one. */
    val onNextPage: () -> Unit,
    /** Where a tile is on the screen, said just before [onClick] for it, for an app that grows out of the tile. */
    val onTileBounds: (Rect) -> Unit = {},
)

/** The tile that was tapped; every tile turns away, that one last, before the app opens. */
@Immutable
data class ExitRequest(val key: String)

private val TileGap = 6.dp

/**
 * The Windows Phone Start screen: a column of live tiles that scrolls vertically, with an arrow at
 * the end that leads to the app list. Tiles swing in around their left edge when the screen opens
 * and swing out when one is tapped. A long press picks a tile up to move it, or to drop it on another
 * tile and make a folder. With a [background] photo the tiles of the built-in apps turn transparent
 * and the photo moves slower than the tiles as the screen scrolls (parallax).
 */
@Composable
fun StartScreen(
    cells: List<StartCell>,
    live: LiveTileData,
    badges: Map<String, Int>,
    columns: Int,
    background: ImageBitmap?,
    enterToken: Int,
    exitRequest: ExitRequest?,
    editingKey: String?,
    scrollState: ScrollState,
    actions: StartActions,
    modifier: Modifier = Modifier,
    /** The latest notification of each app, for the tiles that flip to it. */
    peeks: Map<String, TilePeek> = emptyMap(),
    pullConnection: NestedScrollConnection? = null,
    /** Tiles that cannot be moved or edited, and no arrow to the app list: the Kid's Corner. */
    readOnly: Boolean = false,
    /** The photo moves slower than the tiles when Start scrolls. */
    parallax: Boolean = true,
    header: (@Composable () -> Unit)? = null,
) {
    val currentActions by rememberUpdatedState(actions)
    Box(modifier.fillMaxSize()) {
        if (background != null) ParallaxBackground(background, scrollState, parallax)
        Column(
            Modifier
                .fillMaxSize()
                .then(if (pullConnection != null) Modifier.nestedScroll(pullConnection) else Modifier)
                // A double tap is only waited for when it has something to do: it delays the single tap a little.
                .pointerInput(actions.onDoubleTap != null) {
                    detectTapGestures(
                        onDoubleTap = if (actions.onDoubleTap != null) ({ currentActions.onDoubleTap?.invoke() }) else null,
                        onTap = { currentActions.onBackgroundTap() },
                    )
                }
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .cutoutSidesPadding()
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            header?.invoke()
            TileGridView(
                cells, live, badges, peeks, columns, background != null,
                enterToken, exitRequest, editingKey, scrollState, actions, readOnly,
            )
            Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp), contentAlignment = Alignment.CenterEnd) {
                if (!readOnly) NextPageArrow(actions.onNextPage)
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** The photo, drawn taller than the screen and moved up slower than the tiles. */
@Composable
private fun ParallaxBackground(image: ImageBitmap, scrollState: ScrollState, parallax: Boolean) {
    val travel = if (parallax) 120.dp else 0.dp
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val travelPx = with(LocalDensity.current) { travel.toPx() }
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight + travel)
                .graphicsLayer { translationY = -(scrollState.value * PARALLAX).coerceAtMost(travelPx) },
        )
        // A light shade keeps the white glyphs readable on a bright photo.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
    }
}

private const val PARALLAX = 0.2f

/** State of the tile being dragged. The position is its top left corner in grid coordinates. */
@Stable
private class TileDragState {
    var draggedKey by mutableStateOf<String?>(null)
    var position by mutableStateOf(Offset.Zero)

    /** The tile the finger rests on. After a moment it becomes [mergeTarget] and a drop makes a folder. */
    var pendingMerge by mutableStateOf<String?>(null)
    var mergeTarget by mutableStateOf<String?>(null)

    /** The tile just swapped with, so one move is not repeated before the layout has caught up. */
    var justMovedOver by mutableStateOf<String?>(null)

    fun start(key: String, at: Offset) {
        draggedKey = key
        position = at
        pendingMerge = null
        mergeTarget = null
        justMovedOver = null
    }

    fun finish() {
        draggedKey = null
        pendingMerge = null
        mergeTarget = null
        justMovedOver = null
    }
}

private fun placementRect(p: Placement, unitPx: Float, gapPx: Float): Rect {
    val x = p.col * (unitPx + gapPx)
    val y = p.row * (unitPx + gapPx)
    return Rect(x, y, x + p.cols * unitPx + (p.cols - 1) * gapPx, y + p.rows * unitPx + (p.rows - 1) * gapPx)
}

@Composable
private fun TileGridView(
    cells: List<StartCell>,
    live: LiveTileData,
    badges: Map<String, Int>,
    peeks: Map<String, TilePeek>,
    columns: Int,
    hasBackground: Boolean,
    enterToken: Int,
    exitRequest: ExitRequest?,
    editingKey: String?,
    scrollState: ScrollState,
    actions: StartActions,
    readOnly: Boolean,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unit = (maxWidth - TileGap * (columns - 1)) / columns
        val unitPx = with(density) { unit.toPx() }
        val gapPx = with(density) { TileGap.toPx() }
        val sizes = cells.map { it.size }
        val layout = remember(sizes, columns) { TileGrid.place(sizes, columns) }
        val gridHeight = if (layout.rows == 0) 0.dp else unit * layout.rows + TileGap * (layout.rows - 1)

        val drag = remember { TileDragState() }
        var gridTop by remember { mutableFloatStateOf(0f) }
        val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

        // The drag handlers live as long as the grid, so they read the current values through these.
        val currentCells by rememberUpdatedState(cells)
        val currentLayout by rememberUpdatedState(layout)
        val currentActions by rememberUpdatedState(actions)
        val currentMetrics by rememberUpdatedState(unitPx to gapPx)

        fun rectOf(index: Int) = placementRect(currentLayout.placements[index], currentMetrics.first, currentMetrics.second)

        val onDragStart: (StartCell, Offset) -> Unit = { cell, at -> drag.start(cell.key, at) }

        val onDragBy: (Offset) -> Unit = onDragBy@{ delta ->
            val draggedKey = drag.draggedKey ?: return@onDragBy
            drag.position += delta
            val list = currentCells
            val draggedIndex = list.indexOfFirst { it.key == draggedKey }
            if (draggedIndex < 0) return@onDragBy
            val dragged = list[draggedIndex]
            val own = rectOf(draggedIndex)
            val center = drag.position + Offset(own.width / 2, own.height / 2)

            val outcome = dragOutcome(
                rects = list.indices.map(::rectOf),
                topLevel = list.map { it.isTopLevel },
                dragged = draggedIndex,
                center = center,
                canMerge = dragged is AppCell,
            )
            when (outcome) {
                DragOutcome.Nothing -> {
                    drag.pendingMerge = null
                    drag.mergeTarget = null
                }
                is DragOutcome.Hover -> {
                    // Resting on the middle of a tile: after a moment a drop makes a folder.
                    val target = list[outcome.target]
                    if (drag.pendingMerge != target.key) {
                        drag.pendingMerge = target.key
                        drag.mergeTarget = null
                    }
                }
                is DragOutcome.Reorder -> {
                    drag.pendingMerge = null
                    drag.mergeTarget = null
                    val target = list[outcome.target]
                    if (drag.justMovedOver != target.key) {
                        drag.justMovedOver = target.key
                        currentActions.onMove(dragged.entryId, target.entryId)
                    }
                }
            }
        }

        val onDragEnd: () -> Unit = {
            val draggedKey = drag.draggedKey
            val mergeKey = drag.mergeTarget
            drag.finish()
            if (draggedKey != null && mergeKey != null) {
                val list = currentCells
                val dragged = list.firstOrNull { it.key == draggedKey }
                val onto = list.firstOrNull { it.key == mergeKey }
                if (dragged != null && onto != null) currentActions.onMerge(dragged.entryId, onto.entryId)
            }
        }

        // The layout moved on after a swap: allow the next one.
        LaunchedEffect(cells.map { it.key }) { drag.justMovedOver = null }

        // Holding a tile on the middle of another one arms the folder drop.
        LaunchedEffect(drag.pendingMerge) {
            val pending = drag.pendingMerge ?: return@LaunchedEffect
            delay(MERGE_HOVER_MS)
            drag.mergeTarget = pending
        }

        // Dragging near the top or bottom edge scrolls Start.
        LaunchedEffect(drag.draggedKey) {
            if (drag.draggedKey == null) return@LaunchedEffect
            val edge = with(density) { 96.dp.toPx() }
            val topInset = with(density) { 72.dp.toPx() }
            val maxStep = with(density) { 14.dp.toPx() }
            while (true) {
                withFrameNanos { }
                val key = drag.draggedKey ?: break
                val index = currentCells.indexOfFirst { it.key == key }
                if (index < 0) break
                val centerY = gridTop + drag.position.y + rectOf(index).height / 2
                val step = when {
                    centerY < topInset + edge -> -maxStep * ((topInset + edge - centerY) / edge).coerceIn(0f, 1f)
                    centerY > screenHeightPx - edge -> maxStep * ((centerY - (screenHeightPx - edge)) / edge).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (step != 0f) {
                    val consumed = scrollState.scrollBy(step)
                    drag.position += Offset(0f, consumed)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .onGloballyPositioned { gridTop = it.positionInRoot().y },
        ) {
            val topLevel = cells.filter { it.isTopLevel }.map { it.entryId }
            cells.forEachIndexed { index, cell ->
                key(cell.key) {
                    val at = topLevel.indexOf(cell.entryId).takeIf { cell.isTopLevel } ?: -1
                    PositionedTile(
                        cell, index, layout.placements[index], unit, live, badges, peeks, hasBackground,
                        enterToken, exitRequest, editingKey, drag, actions,
                        onDragStart, onDragBy, onDragEnd, readOnly,
                        previousId = topLevel.getOrNull(at - 1).takeIf { at > 0 },
                        nextId = topLevel.getOrNull(at + 1).takeIf { at >= 0 },
                    )
                }
            }
        }
    }
}

/** What dropping the dragged tile right now would do, given where its centre is. */
internal sealed interface DragOutcome {
    data object Nothing : DragOutcome

    /** Over the edge of tile [target]: the dragged tile takes its place. */
    data class Reorder(val target: Int) : DragOutcome

    /** On the middle of tile [target]: held there for a moment, a drop makes a folder. */
    data class Hover(val target: Int) : DragOutcome
}

/**
 * [rects] are the tiles' rectangles, [topLevel] says which of them can be a target (the apps of an
 * open folder cannot), [dragged] is the index of the tile being dragged and [center] its centre.
 */
internal fun dragOutcome(rects: List<Rect>, topLevel: List<Boolean>, dragged: Int, center: Offset, canMerge: Boolean): DragOutcome {
    val target = rects.indices.firstOrNull { it != dragged && topLevel[it] && rects[it].contains(center) }
        ?: return DragOutcome.Nothing
    val rect = rects[target]
    val core = rect.deflate(min(rect.width, rect.height) * MERGE_CORE_INSET)
    return if (canMerge && core.contains(center)) DragOutcome.Hover(target) else DragOutcome.Reorder(target)
}

/** How much of each side of a tile is the edge, where a drag reorders, as opposed to the core, where it makes a folder. */
private const val MERGE_CORE_INSET = 0.22f
private const val MERGE_HOVER_MS = 320L

/** Slides to its place in the grid when tiles are resized, pinned, unpinned or moved around it. */
@Composable
private fun PositionedTile(
    cell: StartCell,
    index: Int,
    placement: Placement,
    unit: Dp,
    live: LiveTileData,
    badges: Map<String, Int>,
    peeks: Map<String, TilePeek>,
    hasBackground: Boolean,
    enterToken: Int,
    exitRequest: ExitRequest?,
    editingKey: String?,
    drag: TileDragState,
    actions: StartActions,
    onDragStart: (StartCell, Offset) -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    readOnly: Boolean,
    /** The tiles either side of this one, for the "move earlier" and "move later" actions of TalkBack. */
    previousId: String? = null,
    nextId: String? = null,
) {
    val density = LocalDensity.current
    val reduced = LocalReduceMotion.current
    val highContrast = LocalHighContrast.current
    val currentActions by rememberUpdatedState(actions)
    val currentCell by rememberUpdatedState(cell)
    val step = unit + TileGap
    val slot = with(density) { IntOffset((step * placement.col).roundToPx(), (step * placement.row).roundToPx()) }
    val width by animateDpAsState(unit * placement.cols + TileGap * (placement.cols - 1), spring(stiffness = Spring.StiffnessLow), label = "tileWidth")
    val height by animateDpAsState(unit * placement.rows + TileGap * (placement.rows - 1), spring(stiffness = Spring.StiffnessLow), label = "tileHeight")

    val dragging = drag.draggedKey == cell.key
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val position = remember { Animatable(slot, IntOffset.VectorConverter) }
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(slot, dragging) {
        if (dragging) {
            wasDragging = true
        } else {
            // After a drop the tile glides from where it was let go to its place.
            if (wasDragging) {
                wasDragging = false
                position.snapTo(drag.position.round())
            }
            position.animateTo(slot, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow))
        }
    }

    // 1 = turned away, 0 = facing the screen. A new token makes a new Animatable that starts turned away:
    // resetting an existing one from an effect would only happen after the frame that shows the tiles in place.
    val away = remember(enterToken) { Animatable(1f) }
    LaunchedEffect(away) {
        // Reduced motion: every tile fades in at once, quickly.
        if (!reduced) delay(entryDelay(placement, index))
        away.animateTo(0f, tween(durationMillis = if (reduced) REDUCED_FADE_MS else 420, easing = Motion.Decelerate))
    }
    LaunchedEffect(exitRequest, away) {
        if (exitRequest == null) return@LaunchedEffect
        if (!reduced) delay(if (exitRequest.key == cell.key) TAPPED_EXIT_DELAY_MS else exitDelay(placement))
        away.animateTo(1f, tween(durationMillis = if (reduced) REDUCED_FADE_MS else 240, easing = Motion.Accelerate))
    }

    val selected = editingKey == cell.key
    val editScale by animateFloatAsState(
        targetValue = if (editingKey != null && !selected) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "editScale",
    )
    val lift by animateFloatAsState(if (dragging) 1.07f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "lift")
    val armedForFolder = drag.mergeTarget == cell.key

    // The built-in apps are transparent over a Start background photo, the others keep the accent.
    val transparent = hasBackground && cell is AppCell && cell.item.glyph != null && !highContrast
    val tileColor = if (transparent) Color.Black.copy(alpha = 0.12f) else LocalAccent.current

    Box(
        Modifier
            .zIndex(if (dragging) 1f else 0f)
            .offset { if (dragging) drag.position.round() else position.value }
            .size(width, height)
            // After the offset: the semantics (and so TalkBack and the tests) must be where the tile really is.
            .testTag("tile:${cell.key}")
            .tileSemantics(cell, badges, actions, previousId, nextId, readOnly)
            .onGloballyPositioned { coordinates = it }
            .graphicsLayer {
                turnstile(away.value, reduced)
                val scale = editScale * lift * (if (armedForFolder) 1.06f else 1f)
                scaleX = scale
                scaleY = scale
            }
            .metroPress(
                onClick = {
                    coordinates?.takeIf { it.isAttached }?.let { currentActions.onTileBounds(it.boundsInWindow()) }
                    currentActions.onClick(currentCell)
                },
                onLongClick = if (readOnly) null else ({ currentActions.onLongClick(currentCell) }),
                enabled = exitRequest == null,
            )
            .then(
                if (cell.isTopLevel && !readOnly) {
                    Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val at = position.value
                                onDragStart(currentCell, Offset(at.x.toFloat(), at.y.toFloat()))
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                onDragBy(amount)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        )
                    }
                } else {
                    Modifier
                },
            )
            .background(tileColor)
            .then(if (armedForFolder) Modifier.border(3.dp, TileWhite) else Modifier)
            .then(if (highContrast) Modifier.border(HIGH_CONTRAST_FRAME, TileWhite) else Modifier)
            .clipToBounds(),
    ) {
        when (cell) {
            is AppCell -> TileFace(
                cell.item, cell.size, width, height, live,
                seed = cell.key.hashCode(), notifications = badges[cell.item.packageName] ?: 0,
                style = cell.tile.style, peek = peeks[cell.item.packageName],
                interactive = editingKey == null, onLongClick = { currentActions.onLongClick(currentCell) },
            )
            is FolderCell -> FolderFace(
                cell.folder, cell.items, cell.expanded, cell.size, width, height,
                notifications = cell.items.sumOf { badges[it.packageName] ?: 0 },
            )
        }
        if (selected) {
            EditButton(
                EditButtonKind.Remove,
                onClick = { currentActions.onUnpin(currentCell) },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
            if (cell.isTopLevel) {
                EditButton(
                    EditButtonKind.Resize,
                    onClick = { currentActions.onResize(currentCell) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                )
            }
            if (cell is FolderCell) {
                EditButton(
                    EditButtonKind.Rename,
                    onClick = { (currentCell as? FolderCell)?.let(currentActions.onRename) },
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }
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
private const val REDUCED_FADE_MS = 120
private val HIGH_CONTRAST_FRAME = 2.dp

/**
 * What TalkBack knows about a tile besides its text: how many notifications it has (or, for a folder, how many apps),
 * and what can be done to it without a long press and a drag: unpin, change the size, move it earlier or later, and
 * for a folder rename it or take it apart.
 */
@Composable
private fun Modifier.tileSemantics(
    cell: StartCell,
    badges: Map<String, Int>,
    actions: StartActions,
    previousId: String?,
    nextId: String?,
    readOnly: Boolean,
): Modifier {
    val currentActions by rememberUpdatedState(actions)
    val currentCell by rememberUpdatedState(cell)
    val notifications = (cell as? AppCell)?.let { badges[it.item.packageName] ?: 0 } ?: 0
    val state = when {
        cell is FolderCell -> stringResource(R.string.a11y_folder, cell.folder.name, cell.items.size)
        notifications > 0 -> stringResource(R.string.a11y_tile_notifications, (cell as AppCell).item.label, notifications)
        else -> null
    }
    val unpin = stringResource(if (cell is FolderCell) R.string.a11y_release else R.string.a11y_unpin)
    val resize = stringResource(R.string.a11y_resize)
    val earlier = stringResource(R.string.a11y_move_earlier)
    val later = stringResource(R.string.a11y_move_later)
    val rename = stringResource(R.string.a11y_rename)
    return semantics {
        if (state != null) stateDescription = state
        if (!readOnly) {
            val list = mutableListOf<CustomAccessibilityAction>()
            list += CustomAccessibilityAction(unpin) { currentActions.onUnpin(currentCell); true }
            if (cell.isTopLevel) list += CustomAccessibilityAction(resize) { currentActions.onResize(currentCell); true }
            if (previousId != null) list += CustomAccessibilityAction(earlier) { currentActions.onMove(currentCell.entryId, previousId); true }
            if (nextId != null) list += CustomAccessibilityAction(later) { currentActions.onMove(currentCell.entryId, nextId); true }
            if (cell is FolderCell) list += CustomAccessibilityAction(rename) { (currentCell as? FolderCell)?.let(currentActions.onRename); true }
            customActions = list
        }
    }
}
private const val TAPPED_EXIT_DELAY_MS = 140L

private enum class EditButtonKind { Remove, Resize, Rename }

/** The round buttons of a selected tile: remove on the top right, resize at the bottom right, rename (folders) on the top left. */
@Composable
private fun EditButton(kind: EditButtonKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val currentClick by rememberUpdatedState(onClick)
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(TileWhite)
            .pointerInput(Unit) { detectTapGestures { currentClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val stroke = 2.2.dp.toPx()
            when (kind) {
                EditButtonKind.Remove -> {
                    line(0.1f, 0.1f, 0.9f, 0.9f, stroke)
                    line(0.9f, 0.1f, 0.1f, 0.9f, stroke)
                }
                EditButtonKind.Resize -> {
                    // Arrow towards the bottom right corner: the tile grows or wraps back to small.
                    line(0.1f, 0.1f, 0.9f, 0.9f, stroke)
                    line(0.9f, 0.4f, 0.9f, 0.9f, stroke)
                    line(0.4f, 0.9f, 0.9f, 0.9f, stroke)
                }
                EditButtonKind.Rename -> {
                    // A pencil: the body, its tip, and the end that sits in the hand.
                    line(0.3f, 0.7f, 0.78f, 0.22f, stroke * 1.6f)
                    line(0.12f, 0.88f, 0.3f, 0.7f, stroke)
                    line(0.12f, 0.88f, 0.4f, 0.88f, stroke)
                }
            }
        }
    }
}

private fun DrawScope.line(x0: Float, y0: Float, x1: Float, y1: Float, strokeWidth: Float) {
    drawLine(
        Color.Black,
        Offset(size.width * x0, size.height * y0),
        Offset(size.width * x1, size.height * y1),
        strokeWidth,
        StrokeCap.Round,
    )
}

/** The circled arrow after the last tile, which leads to the app list. */
@Composable
private fun NextPageArrow(onClick: () -> Unit) {
    val color = LocalMetroColors.current.foreground
    Box(
        Modifier
            .size(46.dp)
            .metroPress(onClick = onClick)
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 2.4.dp.toPx()
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.58f, size.height * 0.12f), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.58f, size.height * 0.88f), Offset(size.width, size.height / 2), stroke, StrokeCap.Round)
        }
    }
}
