package com.metrowin.launcher.wallet

import androidx.compose.foundation.lazy.itemsIndexed
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.components.PivotHeader
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import com.metrowin.launcher.ui.actioncenter.MetroToast
import androidx.compose.foundation.lazy.rememberLazyListState
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.readAtMost
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.mini.DateTimeDialog
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroAccents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

private sealed interface WalletPage {
    data object List : WalletPage
    data class View(val id: String) : WalletPage
    data class Edit(val card: Card) : WalletPage
}

private fun cardColor(index: Int): Color? = MetroAccents.getOrNull(index - 1)

@Composable
fun WalletScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vault = remember { WalletVault(context) }
    val lock = remember { Mutex() }
    var cards by remember { mutableStateOf<List<Card>?>(null) }
    var page by remember { mutableStateOf<WalletPage>(WalletPage.List) }
    LaunchedEffect(Unit) { cards = withContext(Dispatchers.IO) { vault.load() } }
    val all = cards.orEmpty()

    fun commit(next: List<Card>) {
        cards = next
        scope.launch(Dispatchers.IO) {
            if (!lock.withLock { vault.save(next) }) withContext(Dispatchers.Main) { MetroToast.show(context, R.string.wal_save_failed) }
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = Wallet.encode(all)
        scope.launch(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val incoming = withContext(Dispatchers.IO) { runCatching { Wallet.decode(context.contentResolver.openInputStream(uri)!!.use { String(it.readAtMost(4_000_000)!!) }) }.getOrDefault(emptyList()) }
            if (incoming.isEmpty()) MetroToast.show(context, R.string.wal_import_failed)
            else { commit(Wallet.merge(all, incoming)); MetroToast.show(context, context.resources.getQuantityString(R.plurals.wal_imported, incoming.size, incoming.size)) }
        }
    }

    when (val shown = page) {
        WalletPage.List -> CardList(
            cards, onOpen = { page = WalletPage.View(it.id) },
            onNew = { page = WalletPage.Edit(Card(UUID.randomUUID().toString(), "", "", created = System.currentTimeMillis())) },
            onExport = { export.launch("metrowin-wallet-" + LocalDate.now() + ".json") },
            onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
        is WalletPage.View -> {
            val card = all.firstOrNull { it.id == shown.id }
            if (card == null) page = WalletPage.List
            else CardView(card, onEdit = { page = WalletPage.Edit(card) }, onDelete = { commit(all.filter { it.id != card.id }); page = WalletPage.List }, onBack = { page = WalletPage.List })
        }
        is WalletPage.Edit -> CardEdit(
            shown.card, onSave = { saved ->
                commit(if (all.any { it.id == saved.id }) all.map { if (it.id == saved.id) saved else it } else all + saved)
                page = WalletPage.View(saved.id)
            },
            onCancel = { page = if (all.any { it.id == shown.card.id }) WalletPage.View(shown.card.id) else WalletPage.List },
        )
    }
}

@Composable
private fun CardList(cards: List<Card>?, onOpen: (Card) -> Unit, onNew: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    val colors = LocalMetroColors.current
    var query by remember { mutableStateOf("") }
    val today = remember { LocalDate.now().toEpochDay() }
    // One page of the pivot for all the cards and one for each kind.
    val kinds = listOf<CardKind?>(null) + CardKind.entries
    val pager = rememberPagerState { kinds.size }
    val stacked = LocalStyle.current.isClassic
    fun shown(kind: CardKind?) = cards.orEmpty().filter { (kind == null || it.kind == kind) && (query.isBlank() || it.name.contains(query, true)) }.sortedBy { it.name.lowercase() }
    MiniFrame {
        PivotHeader(kinds.map { stringResource(if (it == null) R.string.notes_all else kindName(it)) }, pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
        Column(Modifier.weight(1f)) {
            MetroTextField(query, { query = it.take(60) }, stringResource(R.string.wal_search), Modifier.padding(horizontal = 24.dp, vertical = 4.dp).testTag("wal:search"))
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                val rows = shown(kinds[page])
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    if (cards != null && rows.isEmpty()) PageNote(stringResource(R.string.wal_empty), Modifier.padding(top = 10.dp).testTag("wal:empty"))
                    // The cards are stacked: each shows the strip with its name, as if the next one lay over it; the last one is whole. Touching a strip opens that card.
                    val listState = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp).scrollIndicator(listState).testTag("wal:list"), state = listState, verticalArrangement = Arrangement.spacedBy(if (stacked) 0.dp else 8.dp)) {
                        itemsIndexed(rows, key = { _, c -> c.id }) { i, card -> WalletTile(card, today, stacked, whole = i == rows.lastIndex, index = i) { onOpen(card) } }
                    }
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("＋", stringResource(R.string.notes_new), onNew),
                AppBarItem("⇪", stringResource(R.string.notes_backup), onExport),
                AppBarItem("⇩", stringResource(R.string.notes_restore), onImport),
            ),
        )
    }
}

private val CARD_HEIGHT = 120.dp
private val CARD_STRIP = 60.dp

/** A card as a square tile of its colour; stacked, only the top strip shows, except for the last card, which is whole. */
@Composable
private fun WalletTile(card: Card, today: Long, stacked: Boolean, whole: Boolean, index: Int, onClick: () -> Unit) {
    val left = Wallet.daysLeft(card, today)
    Box(
        Modifier.fillMaxWidth().height(if (!stacked) 72.dp else if (whole) CARD_HEIGHT else CARD_STRIP).featherRow(index).testTag("wal:card").metroPress(onClick = onClick)
            .background(cardColor(card.color) ?: LocalAccent.current).then(if (stacked) Modifier.border(1.dp, Color.Black.copy(alpha = 0.25f)) else Modifier),
    ) {
        Column(Modifier.fillMaxWidth().height(if (stacked) CARD_STRIP else 72.dp).padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
            MetroText(card.name, size = if (stacked) 22.sp else 24.sp, color = Color.White, maxLines = 1)
            val sub = when {
                left == null -> stringResource(kindName(card.kind))
                left < 0 -> stringResource(R.string.wal_expired)
                else -> stringResource(R.string.wal_expires, LocalDate.ofEpochDay(card.expiry).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
            }
            MetroText(sub, size = 14.sp, weight = FontWeight.Normal, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

private fun kindName(kind: CardKind) = when (kind) {
    CardKind.Loyalty -> R.string.wal_kind_loyalty
    CardKind.Ticket -> R.string.wal_kind_ticket
    CardKind.Other -> R.string.wal_kind_other
}

/** [code] drawn as [format] in black on white, with the quiet space on every side that scanners want. */
@Composable
fun CodeImage(code: String, format: CodeFormat, modifier: Modifier = Modifier) {
    val matrix = remember(code, format) { Wallet.matrix(code, format) }
    val ratio = matrix?.let { if (format.twoDimensional) it.width.toFloat() / it.height else 3.4f } ?: 1f
    Canvas(modifier.background(Color.White).aspectRatio(if (format.twoDimensional) ratio.coerceIn(0.5f, 4f) else 3.4f).testTag("wal:code")) {
        val m = matrix ?: return@Canvas
        if (format.twoDimensional) {
            val quiet = 4
            val cell = minOf(size.width / (m.width + 2 * quiet), size.height / (m.height + 2 * quiet))
            val left = (size.width - cell * m.width) / 2f
            val top = (size.height - cell * m.height) / 2f
            for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) drawRect(Color.Black, Offset(left + x * cell, top + y * cell), Size(cell + 0.6f, cell + 0.6f))
        } else {
            val quiet = size.width * 0.05f
            val unit = (size.width - 2 * quiet) / m.width
            val top = size.height * 0.06f
            for (x in 0 until m.width) if (m[x, 0]) drawRect(Color.Black, Offset(quiet + x * unit, top), Size(unit + 0.5f, size.height - 2 * top))
        }
    }
}

@Composable
private fun CardView(card: Card, onEdit: () -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    var confirm by remember { mutableStateOf(false) }
    // The screen is as bright as it goes while a code is shown: a scanner reads it better.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val before = window?.attributes?.screenBrightness
        window?.let { it.attributes = it.attributes.also { a -> a.screenBrightness = 1f } }
        onDispose { window?.let { it.attributes = it.attributes.also { a -> a.screenBrightness = before ?: -1f } } }
    }
    BackHandler { onBack() }
    MiniFrame {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            MetroText(card.name, size = 32.sp, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp).testTag("wal:name"))
            Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CodeImage(card.code, card.format, Modifier.fillMaxWidth())
                MetroText(card.code, size = 18.sp, weight = FontWeight.Normal, color = Color.Black, modifier = Modifier.padding(top = 8.dp))
            }
            if (card.expiry != 0L) {
                MetroText(
                    stringResource(R.string.wal_expires, LocalDate.ofEpochDay(card.expiry).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))), size = 17.sp,
                    weight = FontWeight.Normal, color = if (card.expiry < LocalDate.now().toEpochDay()) LocalAccent.current else colors.subtle, modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (card.note.isNotBlank()) MetroText(card.note, size = 17.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 8.dp))
            if (confirm) MetroButton(stringResource(R.string.notes_delete_confirm), Modifier.fillMaxWidth().padding(top = 16.dp).testTag("wal:delete:confirm")) { onDelete() }
        }
        AppBar(
            listOf(
                AppBarItem("←", stringResource(R.string.rec_back), onBack),
                AppBarItem("✎", stringResource(R.string.notes_edit), onEdit),
                AppBarItem("✕", stringResource(R.string.todo_delete)) { confirm = true },
            ),
        )
    }
}

@Composable
private fun CardEdit(initial: Card, onSave: (Card) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var code by remember(initial.id) { mutableStateOf(initial.code) }
    var format by remember(initial.id) { mutableStateOf(initial.format) }
    var kind by remember(initial.id) { mutableStateOf(initial.kind) }
    var expiry by remember(initial.id) { mutableStateOf(initial.expiry) }
    var note by remember(initial.id) { mutableStateOf(initial.note) }
    var color by remember(initial.id) { mutableStateOf(initial.color) }
    var picking by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val valid = name.isNotBlank() && Wallet.valid(code.trim(), format)
    BackHandler { onCancel() }
    val picture = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }?.let { Wallet.read(it) } }.getOrNull()
            }
            if (found == null) MetroToast.show(context, R.string.wal_picture_failed) else { code = found.first; format = found.second }
        }
    }
    MiniFrame {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetroText(stringResource(if (initial.name.isEmpty() && initial.code.isEmpty()) R.string.wal_new else R.string.notes_edit), size = 34.sp, modifier = Modifier.padding(top = 14.dp))
            MetroTextField(name, { name = it.take(60) }, stringResource(R.string.wallet_name), Modifier.testTag("wal:edit:name"))
            MetroTextField(code, { code = it.take(Wallet.MAX_CODE) }, stringResource(R.string.wal_code), Modifier.testTag("wal:edit:code"))
            MetroButton(stringResource(R.string.wal_from_picture), Modifier.fillMaxWidth().testTag("wal:edit:picture")) { picture.launch("image/*") }
            MetroButton(stringResource(R.string.wal_scan), Modifier.fillMaxWidth().testTag("wal:edit:scan")) { scanning = true }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(CodeFormat.entries) { f -> PageChip(f.name, format == f, "wal:edit:format:${f.name}") { format = f } } }
            if (code.isNotBlank() && !valid && name.isNotBlank()) PageNote(stringResource(R.string.wal_format_invalid), Modifier.testTag("wal:edit:invalid"))
            if (code.isNotBlank() && Wallet.valid(code.trim(), format)) CodeImage(code.trim(), format, Modifier.fillMaxWidth(0.8f))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(CardKind.entries) { k -> PageChip(stringResource(kindName(k)), kind == k, "wal:edit:kind:${k.name}") { kind = k } } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PageChip(
                    if (expiry == 0L) stringResource(R.string.wal_no_expiry) else LocalDate.ofEpochDay(expiry).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    expiry != 0L, "wal:edit:expiry",
                ) { picking = true }
                if (expiry != 0L) PageChip("✕", false, "wal:edit:noexpiry") { expiry = 0L }
            }
            MetroTextField(note, { note = it.take(500) }, stringResource(R.string.cal_notes_hint), Modifier.testTag("wal:edit:note"), singleLine = false)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..7).forEach { index ->
                    Box(
                        Modifier.size(34.dp).testTag("wal:edit:color:$index").metroPress(onClick = { color = index }).background(cardColor(index) ?: LocalAccent.current)
                            .border(if (color == index) 3.dp else 0.dp, colors.foreground),
                    )
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("✕", stringResource(R.string.rec_back), onCancel),
                AppBarItem("✓", stringResource(R.string.todo_save)) {
                    if (valid) onSave(initial.copy(name = name.trim(), code = code.trim(), format = format, kind = kind, expiry = expiry, note = note.trim(), color = color))
                },
            ),
        )
    }
    if (scanning) CodeScanDialog(onUse = { text, f -> code = text.take(Wallet.MAX_CODE); format = f; scanning = false }, onCancel = { scanning = false })
    if (picking) DateTimeDialog(
        (if (expiry == 0L) LocalDate.now().plusMonths(6) else LocalDate.ofEpochDay(expiry)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), dateOnly = true,
        onChoose = { ms -> expiry = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(); picking = false },
        onCancel = { picking = false },
    )
}
