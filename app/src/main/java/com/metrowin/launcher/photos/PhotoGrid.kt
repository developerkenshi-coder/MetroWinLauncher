package com.metrowin.launcher.photos

import com.metrowin.launcher.ui.components.scrollIndicator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.arrival
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalStyle
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private val SIDE = 24.dp

/** A titled run of pictures for [PhotoSections]; no [title] means no heading (an album, the favourites). */
internal class GridSection(val key: String, val title: String?, val photos: List<Photo>)

/** The pictures picked in the hub: [mode] is on from the first long press (or the select button) until it is left. */
internal class Selection {
    var mode by mutableStateOf(false)
    var ids by mutableStateOf(emptySet<Long>())

    fun toggle(id: Long) { ids = if (id in ids) ids - id else ids + id }
    fun clear() { ids = emptySet(); mode = false }
}

private class Entry(val key: String, val title: String?, val header: Boolean, val photos: List<Photo> = emptyList(), val row: GridRow? = null)

@Composable
private fun Placeholder(neutral: Boolean) {
    val colors = LocalMetroColors.current
    val tint = if (neutral) colors.subtle.copy(alpha = 0.25f) else LocalAccent.current.copy(alpha = if (LocalStyle.current.isClassic) 1f else 0.35f)
    Box(Modifier.fillMaxSize().background(tint))
}

/** One small picture: a thumbnail (it fades in over the placeholder if it was not ready), the heart of a favourite and the tick of a selection. [fill] takes the size of the parent instead of a square. */
@Composable
internal fun Thumb(photo: Photo, modifier: Modifier = Modifier, prefs: PhotoPrefs? = null, selected: Boolean = false, fill: Boolean = false) {
    val bitmap = PhotoThumbs.load(photo)
    val cached = remember { bitmap != null }
    Box((if (fill) modifier else modifier.aspectRatio(1f)).clipToBounds()) {
        Placeholder(prefs?.get(PhotoOption.NeutralPlaceholder) ?: false)
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().arrival(instant = cached), contentScale = ContentScale.Crop)
        if (photo.favorite && prefs?.get(PhotoOption.FavoriteMark) == true) {
            PhotoIconView(PhotoIcon.HeartFilled, Modifier.align(Alignment.BottomEnd).padding(4.dp), 14.dp, androidx.compose.ui.graphics.Color.White)
        }
        if (selected) {
            Box(Modifier.fillMaxSize().background(LocalAccent.current.copy(alpha = 0.35f)).border(3.dp, LocalAccent.current))
            PhotoIconView(PhotoIcon.Check, Modifier.align(Alignment.TopEnd).padding(4.dp), 18.dp, androidx.compose.ui.graphics.Color.White)
        }
    }
}

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

internal fun day(date: java.time.LocalDate): String = date.format(dateFormat)

@Composable
internal fun describe(photo: Photo): String {
    val date = day(PhotoLibrary.dayOf(photo))
    return when {
        photo.favorite && photo.album.isNotEmpty() -> stringResource(R.string.ph_describe_favorite_album, date, photo.album)
        photo.favorite -> stringResource(R.string.ph_describe_favorite, date)
        photo.album.isNotEmpty() -> stringResource(R.string.ph_describe_album, date, photo.album)
        else -> date
    }
}

/** What the list needs from the hub: how pictures are opened and picked. */
internal class GridContext(val prefs: PhotoPrefs, val selection: Selection, val onOpen: (Photo, Rect) -> Unit)

/**
 * The pictures of [sections] in rows (squares, or each with its own proportions), headings that stay on top while they are scrolled,
 * a pinch that changes the size, a long press then a drag that picks pictures, and (with [scrubber]) a handle at the side to jump through the dates.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoSections(sections: List<GridSection>, tag: String, ctx: GridContext, scrubber: Boolean = false, footer: (@Composable () -> Unit)? = null) {
    val prefs = ctx.prefs
    val selection = ctx.selection
    val density = LocalDensity.current
    val keepRatio = prefs[PhotoOption.KeepRatio]
    val cell = prefs.cell
    val gap = prefs.gap
    val sticky = prefs[PhotoOption.DateScrubber]
    val bounds = remember { HashMap<Long, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        Modifier.fillMaxSize().testTag(tag).onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            // The pinch: two fingers apart make the pictures larger, together smaller. Seen on the way in, never consumed: the list still scrolls.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var total = 1f
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size > 1) {
                            total *= event.calculateZoom()
                            if (total > PINCH_STEP) { prefs.cell = (prefs.cell * PINCH_STEP).roundToInt(); total = 1f }
                            else if (total < 1f / PINCH_STEP) { prefs.cell = (prefs.cell / PINCH_STEP).roundToInt(); total = 1f }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            // A long press picks the picture under the finger; going on over others picks (or, when the first was already picked, lets go of) them as well.
            .pointerInput(prefs[PhotoOption.Select]) {
                if (!prefs[PhotoOption.Select]) return@pointerInput
                var adding = true
                fun hit(at: Offset): Long? = bounds.entries.firstOrNull { it.value.contains(at + origin) }?.key
                detectDragGesturesAfterLongPress(
                    onDragStart = { at ->
                        selection.mode = true
                        hit(at)?.let { adding = it !in selection.ids; selection.ids = if (adding) selection.ids + it else selection.ids - it }
                    },
                    onDrag = { change, _ -> hit(change.position)?.let { selection.ids = if (adding) selection.ids + it else selection.ids - it } },
                    onDragEnd = {}, onDragCancel = {},
                )
            },
    ) {
        val widthPx = with(density) { (maxWidth - SIDE * 2).toPx() }
        val entries = remember(sections, widthPx, cell, gap, keepRatio, sticky) {
            val gapPx = with(density) { gap.dp.toPx() }
            val cellPx = with(density) { cell.dp.toPx() }
            buildList {
                sections.forEach { section ->
                    if (section.title != null) add(Entry("h:${section.key}", section.title, true))
                    PhotoRows.layout(section.photos.map { it.ratio }, widthPx, cellPx, gapPx, square = !keepRatio).forEach { row ->
                        add(Entry("r:${section.key}:${section.photos[row.first].id}", section.title, false, section.photos.subList(row.first, row.first + row.fractions.size), row))
                    }
                }
            }
        }
        val background = LocalMetroColors.current.background.copy(alpha = 0.92f)
        LazyColumn(Modifier.fillMaxSize().scrollIndicator(state), state, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = SIDE, end = SIDE, bottom = 16.dp)) {
            entries.forEachIndexed { index, entry ->
                if (entry.header) {
                    val header: @Composable () -> Unit = {
                        MetroText(entry.title.orEmpty(), size = 20.sp, color = LocalAccent.current, maxLines = 1, modifier = Modifier.fillMaxWidth().then(if (sticky) Modifier.background(background) else Modifier).padding(top = 12.dp, bottom = 4.dp).testTag("day:${entry.key.removePrefix("h:")}"))
                    }
                    if (sticky) stickyHeader(key = entry.key) { header() } else item(key = entry.key) { header() }
                } else item(key = entry.key) {
                    val row = entry.row!!
                    Row(Modifier.padding(bottom = gap.dp).featherRow(index), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                        entry.photos.forEachIndexed { i, photo ->
                            PhotoCell(photo, with(density) { (row.fractions[i] * widthPx).toDp() }, with(density) { row.height.toDp() }, ctx, bounds)
                        }
                    }
                }
            }
            if (footer != null) item(key = "footer") { footer() }
        }
        if (scrubber && sticky && entries.size > 12) Scrubber(state, entries.size, { i -> entries.getOrNull(i)?.title.orEmpty() }, Modifier.align(Alignment.CenterEnd))
    }
}

private const val PINCH_STEP = 1.3f

@Composable
private fun PhotoCell(photo: Photo, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, ctx: GridContext, bounds: MutableMap<Long, Rect>) {
    val description = describe(photo)
    val selection = ctx.selection
    val selectLabel = stringResource(R.string.ph_select_toggle)
    var rect by remember { mutableStateOf(Rect.Zero) }
    DisposableEffect(photo.id) { onDispose { bounds.remove(photo.id) } }
    val picked = photo.id in selection.ids
    Thumb(
        photo,
        Modifier.size(width, height).testTag("photo:${photo.id}")
            .semantics { contentDescription = description; selected = picked; if (ctx.prefs[PhotoOption.Select]) customActions = listOf(CustomAccessibilityAction(selectLabel) { selection.mode = true; selection.toggle(photo.id); true }) }
            .onGloballyPositioned { rect = it.boundsInWindow(); bounds[photo.id] = rect }
            .metroPress(onClick = { if (selection.mode) selection.toggle(photo.id) else ctx.onOpen(photo, rect) }),
        prefs = ctx.prefs, selected = picked, fill = true,
    )
}

/** The handle at the side of a long list: dragged, it jumps through the list and says the heading it is over. */
@Composable
private fun Scrubber(state: androidx.compose.foundation.lazy.LazyListState, count: Int, titleAt: (Int) -> String, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val fraction by remember { derivedStateOf { (state.firstVisibleItemIndex.toFloat() / (count - 1).coerceAtLeast(1)).coerceIn(0f, 1f) } }
    val label = stringResource(R.string.ph_scrub)
    var heightPx by remember { mutableStateOf(1) }
    Box(
        modifier.fillMaxHeight().width(32.dp).onGloballyPositioned { heightPx = it.size.height.coerceAtLeast(1) }.semantics { contentDescription = label }
            .pointerInput(count) {
                detectVerticalDragGestures(onDragStart = { dragging = true }, onDragEnd = { dragging = false }, onDragCancel = { dragging = false }) { change, _ ->
                    val f = (change.position.y / size.height).coerceIn(0f, 1f)
                    scope.launch { state.scrollToItem((f * (count - 1)).roundToInt()) }
                }
            },
    ) {
        val colors = LocalMetroColors.current
        Box(Modifier.align(Alignment.TopEnd).padding(end = 2.dp).offset(y = with(LocalDensity.current) { (fraction * (heightPx - 40.dp.toPx())).toDp() }).width(if (dragging) 8.dp else 4.dp).height(40.dp).background(LocalAccent.current))
        if (dragging) MetroText(titleAt(state.firstVisibleItemIndex), size = 18.sp, maxLines = 1, modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = with(LocalDensity.current) { (fraction * (heightPx - 40.dp.toPx())).toDp() }).background(colors.surface).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
