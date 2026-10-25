package com.metrowin.launcher.ui.apps

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.metrowin.launcher.R
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroFont
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.AppSection
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.ui.anim.Motion
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.turnstile
import com.metrowin.launcher.ui.components.AppSquare
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.TileWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

sealed interface ListRow {
    val key: String

    /** The search box at the top of the list. */
    data object Search : ListRow {
        override val key = "search"
    }

    data class Header(val letter: String) : ListRow {
        override val key = "header:$letter"
    }

    data class App(val item: AppItem) : ListRow {
        override val key = "app:${item.id}"
    }
}

/** The alphabetical app list flattened into rows: a letter header, then the apps under it. */
@Immutable
class AppListModel(sections: List<AppSection>) {
    val rows: List<ListRow> = listOf<ListRow>(ListRow.Search) + sections.flatMap { section ->
        listOf<ListRow>(ListRow.Header(section.key)) + section.items.map { ListRow.App(it) }
    }
    val headerIndex: Map<String, Int> =
        rows.withIndex().filter { it.value is ListRow.Header }.associate { (it.value as ListRow.Header).letter to it.index }
    val letters: Set<String> get() = headerIndex.keys

    /** Position of the row of app [id], or null when the list does not show it. */
    fun indexOfApp(id: String): Int? = rows.indexOfFirst { it is ListRow.App && it.item.id == id }.takeIf { it >= 0 }
}

/** An app row that glows for a moment: where the star of the jump list took you. [token] makes a repeat glow again. */
@Immutable
data class ListHighlight(val appId: String, val token: Int)

private val RowHeight = 64.dp
private val SquareSize = 52.dp

/**
 * The Windows Phone app list: every app in alphabetical order under its letter. Tapping a letter
 * opens the jump list. Rows swing in like a turnstile when the page opens and swing out when an app
 * is launched.
 *
 * [replayToken] restarts the entrance, used after the jump list moves the list somewhere else.
 */
@Composable
fun AppListScreen(
    model: AppListModel,
    listState: LazyListState,
    replayToken: Int,
    exiting: Boolean,
    onLaunch: (AppItem) -> Unit,
    onLongPress: (item: AppItem, top: Float, height: Float) -> Unit,
    onHeaderClick: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<AppItem>,
    modifier: Modifier = Modifier,
    highlight: ListHighlight? = null,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    // While searching the list is just the matches, without letter headers.
    val rows = remember(model, query, results) {
        if (query.isBlank()) model.rows else listOf<ListRow>(ListRow.Search) + results.map { ListRow.App(it) }
    }
    // Scrolling the list puts the keyboard away, and a new search starts from the top.
    val focus = LocalFocusManager.current
    LaunchedEffect(listState, focus) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { focus.clearFocus() }
    }
    LaunchedEffect(query) { listState.scrollToItem(0) }
    // Rows composed within this window after the page opens animate in; rows that scroll into view later do not.
    val openedAt = remember(replayToken) { System.nanoTime() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = insets.calculateTopPadding() + 12.dp,
            bottom = insets.calculateBottomPadding() + 12.dp,
        ),
    ) {
        itemsIndexed(rows, key = { _, row -> row.key }, contentType = { _, row -> row::class }) { index, row ->
            when (row) {
                ListRow.Search -> SearchRow(query, onQueryChange)
                is ListRow.Header -> HeaderRow(row.letter, index, listState, openedAt, exiting, onHeaderClick)
                is ListRow.App -> AppRow(
                    row.item, index, listState, openedAt, exiting, onLaunch, onLongPress,
                    highlight?.takeIf { it.appId == row.item.id },
                )
            }
        }
    }
}

private const val GLOW_START = 0.5f
private const val GLOW_MS = 1_400

private fun isEntering(openedAt: Long) = System.nanoTime() - openedAt < ENTRY_WINDOW_NANOS

private const val ENTRY_WINDOW_NANOS = 700_000_000L

/** 1 = turned away, 0 = facing the screen. Handles both the entrance and the exit of a row. */
@Composable
private fun rememberRowAway(index: Int, listState: LazyListState, openedAt: Long, exiting: Boolean): Animatable<Float, *> {
    // A replay (openedAt changes) builds a new Animatable that starts turned away, so no frame shows the row in place.
    val away = remember(openedAt) { Animatable(if (isEntering(openedAt)) 1f else 0f) }
    LaunchedEffect(away) {
        if (away.value < 1f) return@LaunchedEffect
        delay((index - listState.firstVisibleItemIndex).coerceIn(0, 14) * 26L)
        away.animateTo(0f, tween(durationMillis = 380, easing = Motion.Decelerate))
    }
    LaunchedEffect(exiting, away) {
        if (!exiting) return@LaunchedEffect
        delay((index - listState.firstVisibleItemIndex).coerceIn(0, 14) * 14L)
        away.animateTo(1f, tween(durationMillis = 220, easing = Motion.Accelerate))
    }
    return away
}

/** The search box: a flat surface with a magnifier, the typed text and a button to clear it. */
@Composable
private fun SearchRow(query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalMetroColors.current
    val focus = LocalFocusManager.current
    val currentChange by rememberUpdatedState(onQueryChange)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(52.dp)
            .background(colors.surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Search, 24.dp, color = colors.subtle)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) MetroText(stringResource(R.string.search_apps), size = 20.sp, color = colors.subtle)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.foreground, fontSize = 20.sp, fontFamily = MetroFont, fontWeight = FontWeight.Light),
                cursorBrush = SolidColor(LocalAccent.current),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Canvas(
                Modifier
                    .size(34.dp)
                    .pointerInput(Unit) { detectTapGestures { currentChange("") } }
                    .padding(10.dp),
            ) {
                val stroke = 2.dp.toPx()
                drawLine(colors.subtle, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
                drawLine(colors.subtle, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun HeaderRow(
    letter: String,
    index: Int,
    listState: LazyListState,
    openedAt: Long,
    exiting: Boolean,
    onClick: () -> Unit,
) {
    val away = rememberRowAway(index, listState, openedAt, exiting)
    Box(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .graphicsLayer { turnstile(away.value) }
            .metroPress(onClick = onClick, enabled = !exiting)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.size(SquareSize).background(LocalAccent.current), contentAlignment = Alignment.Center) {
            MetroText(letter.lowercase(), size = 32.sp, color = TileWhite)
        }
    }
}

@Composable
private fun AppRow(
    item: AppItem,
    index: Int,
    listState: LazyListState,
    openedAt: Long,
    exiting: Boolean,
    onLaunch: (AppItem) -> Unit,
    onLongPress: (AppItem, Float, Float) -> Unit,
    highlight: ListHighlight?,
) {
    val away = rememberRowAway(index, listState, openedAt, exiting)
    // The glow starts bright and fades; it is drawn behind the row, only while it lasts.
    val glow = remember { Animatable(0f) }
    LaunchedEffect(highlight) {
        if (highlight == null) return@LaunchedEffect
        glow.snapTo(GLOW_START)
        glow.animateTo(0f, tween(durationMillis = GLOW_MS))
    }
    val accent = LocalAccent.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    AppRowContent(
        item,
        Modifier
            .testTag("app:${item.id}")
            .onGloballyPositioned { coordinates = it }
            .graphicsLayer { turnstile(away.value) }
            .drawBehind { if (glow.value > 0f) drawRect(accent.copy(alpha = glow.value)) }
            .metroPress(
                onClick = { onLaunch(item) },
                onLongClick = {
                    coordinates?.takeIf { it.isAttached }?.boundsInRoot()?.let { onLongPress(item, it.top, it.height) }
                },
                enabled = !exiting,
            ),
    )
}

/** One app: its square, then its name. Also drawn over the dimmed list by the context menu. */
@Composable
fun AppRowContent(item: AppItem, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppSquare(item, SquareSize)
        Spacer(Modifier.width(14.dp))
        MetroText(item.label, size = 22.sp, modifier = Modifier.weight(1f))
    }
}
