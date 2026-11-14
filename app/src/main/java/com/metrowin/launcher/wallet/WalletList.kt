package com.metrowin.launcher.wallet

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalHighContrast
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One line of the list: the name of a kind (when the cards are grouped) or a card. */
private sealed interface Entry {
    val key: String

    data class Header(val kind: CardKind) : Entry {
        override val key get() = "h:${kind.name}"
    }

    data class Item(val card: Card) : Entry {
        override val key get() = card.id
    }
}

private fun entries(rows: List<Card>, grouped: Boolean): List<Entry> =
    if (!grouped) rows.map { Entry.Item(it) } else CardKind.entries.flatMap { kind -> rows.filter { it.kind == kind }.let { part -> if (part.isEmpty()) emptyList() else listOf<Entry>(Entry.Header(kind)) + part.map { Entry.Item(it) } } }

@Composable
internal fun CardList(
    cards: List<Card>?, prefs: WalletPrefs, today: Long, unreadable: Boolean,
    onOpen: (Card) -> Unit, onNew: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onOptions: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // One page of the pivot for all the cards and one for each kind.
    val kinds = listOf<CardKind?>(null) + CardKind.entries
    val pager = rememberPagerState { kinds.size }
    val style = LocalStyle.current
    val grid = prefs.grid.on
    // The strips of a stack only in the look of Windows Phone and not in a grid.
    val stacked = style.isClassic && !grid
    val sortLabel = stringResource(
        when (prefs.sort) {
            SortMode.Name -> R.string.wal_sort_name
            SortMode.Expiry -> R.string.wal_sort_expiry
            SortMode.LastUsed -> R.string.wal_sort_used
        },
    )
    MiniFrame {
        PivotHeader(kinds.map { stringResource(if (it == null) R.string.notes_all else kindName(it)) }, pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
        Column(Modifier.weight(1f)) {
            if (unreadable) PageNote(stringResource(R.string.wal_unreadable), Modifier.padding(horizontal = 24.dp, vertical = 4.dp).testTag("wal:unreadable"))
            MetroTextField(query, { query = it.take(SEARCH_LIMIT) }, stringResource(R.string.wal_search), Modifier.padding(horizontal = 24.dp, vertical = 4.dp).testTag("wal:search"))
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val kind = kinds[page]
                // Sorted and filtered once for a change of the cards, the search or the order, not at every redraw.
                val rows = remember(cards, query, kind, prefs.sort) { Wallet.sorted(cards.orEmpty().filter { (kind == null || it.kind == kind) && Wallet.matches(it, query) }, prefs.sort) }
                val lines = remember(rows, grid, prefs.group.on, kind) { entries(rows, prefs.group.on && kind == null && !grid) }
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    when {
                        cards == null -> Skeleton(prefs.motion.on)
                        rows.isEmpty() -> EmptyState(query.isBlank(), onNew)
                        grid -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().padding(top = 8.dp).testTag("wal:list"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(rows, key = { it.id }) { card -> WalletTile(card, today, stacked = false, whole = true, index = 0, prefs = prefs, height = CARD_HEIGHT) { onOpen(card) } }
                        }
                        else -> {
                            // The cards are stacked: each shows the strip with its name, as if the next one lay over it; the last one is whole. Touching a strip opens that card.
                            val listState = rememberLazyListState()
                            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp).scrollIndicator(listState).testTag("wal:list"), state = listState, verticalArrangement = Arrangement.spacedBy(if (stacked) 0.dp else 8.dp)) {
                                itemsIndexed(lines, key = { _, line -> line.key }) { index, line ->
                                    when (line) {
                                        is Entry.Header -> MetroText(stringResource(kindName(line.kind)), size = 16.sp, weight = FontWeight.SemiBold, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                                        is Entry.Item -> WalletTile(line.card, today, stacked, whole = lines.getOrNull(index + 1) !is Entry.Item, index = index, prefs = prefs) { onOpen(line.card) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("＋", stringResource(R.string.notes_new), onNew),
                AppBarItem("⇪", stringResource(R.string.notes_backup), onExport).icon(Mark.Up),
                AppBarItem("⇩", stringResource(R.string.notes_restore), onImport).icon(Mark.Down),
            ),
            menu = listOf(
                AppBarItem("↕", sortLabel) { prefs.nextSort() },
                AppBarItem("⚙", stringResource(R.string.wal_menu_options), onOptions),
            ),
        )
    }
}

/** Grey blocks where the cards will be while the file is being read, instead of an empty page. */
@Composable
private fun Skeleton(motion: Boolean) {
    val colors = LocalMetroColors.current
    val pulse = if (motion && !LocalReduceMotion.current) rememberInfiniteTransition(label = "skeleton").animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha").value else 0.5f
    Column(Modifier.fillMaxSize().padding(top = 8.dp).testTag("wal:loading"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { Box(Modifier.fillMaxWidth().height(TILE_HEIGHT).alpha(pulse).background(colors.subtle.copy(alpha = 0.3f))) }
    }
}

@Composable
private fun EmptyState(offerAdd: Boolean, onNew: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardGlyph(Modifier.size(88.dp))
        PageNote(stringResource(if (offerAdd) R.string.wal_empty else R.string.wal_no_match), Modifier.testTag("wal:empty"))
        if (offerAdd) MetroButton(stringResource(R.string.wal_empty_add), Modifier.fillMaxWidth().testTag("wal:empty:add"), onClick = onNew)
    }
}

/**
 * A card as a tile of its colour; stacked, only the top strip shows, except for the last card, which is whole. The tile grows with big text,
 * and an expired card is dimmed, one that is soon no good says in how many days.
 */
@Composable
internal fun WalletTile(
    card: Card, today: Long, stacked: Boolean, whole: Boolean, index: Int, prefs: WalletPrefs,
    modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp? = null, tag: String = "wal:card", onClick: () -> Unit,
) {
    val style = LocalStyle.current
    val bg = cardColor(card.color) ?: LocalAccent.current
    val ink = inkFor(bg)
    val rich = prefs.rich.on
    val state = if (prefs.expiry.on) Wallet.expiryState(card, today) else ExpiryState.None
    val left = Wallet.daysLeft(card, today)
    val shape = if (rich && !stacked) style.smallTileShape else RectangleShape
    var pressed by remember { mutableStateOf(false) }
    val lift by animateDpAsState(if (pressed && rich && prefs.motion.on && !LocalReduceMotion.current) (-10).dp else 0.dp, label = "lift")
    val depth = if (rich && style.kind == ThemeStyle.Droid && !stacked) Modifier.shadow(4.dp, shape) else Modifier
    val sheen = if (rich) Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)), shape) else Modifier
    val frame = when {
        LocalHighContrast.current -> Modifier.border(2.dp, ink, shape)
        stacked -> Modifier.border(1.dp, Color.Black.copy(alpha = 0.25f))
        else -> Modifier
    }
    val minimum = height ?: if (!stacked) TILE_HEIGHT else if (whole) CARD_HEIGHT else CARD_STRIP
    Box(
        modifier.fillMaxWidth().zIndex(if (pressed) 1f else 0f).offset { IntOffset(0, lift.roundToPx()) }.heightIn(min = minimum)
            .featherRow(index).testTag(tag).alpha(if (state == ExpiryState.Expired) 0.55f else 1f).then(depth)
            .metroPress(onClick = onClick, onPressedChange = { pressed = it })
            .background(bg, shape).then(sheen).then(frame),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = if (stacked) CARD_STRIP else TILE_HEIGHT).padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
            MetroText(card.name.ifBlank { " " }, size = if (stacked) 22.sp else 24.sp, color = ink, maxLines = if (stacked) 1 else 2)
            val sub = when {
                left == null -> stringResource(kindName(card.kind))
                left < 0 -> stringResource(if (prefs.expiry.on) R.string.wal_expired_label else R.string.wal_expired)
                state == ExpiryState.Soon -> pluralStringResource(R.plurals.wal_days_left, left.toInt(), left.toInt())
                else -> stringResource(R.string.wal_expires, LocalDate.ofEpochDay(card.expiry).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
            }
            MetroText(sub, size = 14.sp, weight = if (state == ExpiryState.Soon) FontWeight.SemiBold else FontWeight.Normal, color = ink.copy(alpha = if (state == ExpiryState.Soon) 1f else 0.85f), maxLines = 1)
        }
        if (card.favorite) MetroText("★", size = 18.sp, color = ink, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        // A dot on a card used in the last week, when the wallet is asked to remember.
        if (prefs.history.on && card.lastUsed > 0 && System.currentTimeMillis() - card.lastUsed < RECENT_MS) {
            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(8.dp).clip(CircleShape).background(ink.copy(alpha = 0.85f)))
        }
    }
}

private const val RECENT_MS = 7L * 24 * 3_600_000
