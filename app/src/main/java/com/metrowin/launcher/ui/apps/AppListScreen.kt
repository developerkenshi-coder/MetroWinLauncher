package com.metrowin.launcher.ui.apps

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

sealed interface ListRow {
    val key: String

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
    val rows: List<ListRow> = sections.flatMap { section ->
        listOf<ListRow>(ListRow.Header(section.key)) + section.items.map { ListRow.App(it) }
    }
    val headerIndex: Map<String, Int> =
        rows.withIndex().filter { it.value is ListRow.Header }.associate { (it.value as ListRow.Header).letter to it.index }
    val letters: Set<String> get() = headerIndex.keys
}

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
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
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
        itemsIndexed(model.rows, key = { _, row -> row.key }, contentType = { _, row -> row::class }) { index, row ->
            when (row) {
                is ListRow.Header -> HeaderRow(row.letter, index, listState, openedAt, exiting, onHeaderClick)
                is ListRow.App -> AppRow(row.item, index, listState, openedAt, exiting, onLaunch, onLongPress)
            }
        }
    }
}

private fun isEntering(openedAt: Long) = System.nanoTime() - openedAt < ENTRY_WINDOW_NANOS

private const val ENTRY_WINDOW_NANOS = 700_000_000L

/** 1 = turned away, 0 = facing the screen. Handles both the entrance and the exit of a row. */
@Composable
private fun rememberRowAway(index: Int, listState: LazyListState, openedAt: Long, exiting: Boolean): Animatable<Float, *> {
    val away = remember { Animatable(if (isEntering(openedAt)) 1f else 0f) }
    LaunchedEffect(openedAt) {
        if (!isEntering(openedAt)) return@LaunchedEffect
        away.snapTo(1f)
        delay((index - listState.firstVisibleItemIndex).coerceIn(0, 14) * 26L)
        away.animateTo(0f, tween(durationMillis = 380, easing = Motion.Decelerate))
    }
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        delay((index - listState.firstVisibleItemIndex).coerceIn(0, 14) * 14L)
        away.animateTo(1f, tween(durationMillis = 220, easing = Motion.Accelerate))
    }
    return away
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
) {
    val away = rememberRowAway(index, listState, openedAt, exiting)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    AppRowContent(
        item,
        Modifier
            .onGloballyPositioned { coordinates = it }
            .graphicsLayer { turnstile(away.value) }
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
