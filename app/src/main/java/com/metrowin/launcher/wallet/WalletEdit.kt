package com.metrowin.launcher.wallet

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.common.BitMatrix
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.mini.DateTimeDialog
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val FormatSaver = Saver<CodeFormat, String>(save = { it.name }, restore = { CodeFormat.valueOf(it) })
private val KindSaver = Saver<CardKind, String>(save = { it.name }, restore = { CardKind.valueOf(it) })

/** What the editor found when it tried to draw [code] as [format]. */
private class Checked(val code: String, val format: CodeFormat, val matrix: BitMatrix?)

private const val CHECK_DELAY_MS = 150L

@Composable
internal fun CardEdit(initial: Card, prefs: WalletPrefs, today: Long, onSave: (Card) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    // What was typed survives a turn of the phone and the death of the process.
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var code by rememberSaveable(initial.id) { mutableStateOf(initial.code) }
    var format by rememberSaveable(initial.id, stateSaver = FormatSaver) { mutableStateOf(initial.format) }
    var kind by rememberSaveable(initial.id, stateSaver = KindSaver) { mutableStateOf(initial.kind) }
    var expiry by rememberSaveable(initial.id) { mutableStateOf(initial.expiry) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var color by rememberSaveable(initial.id) { mutableStateOf(initial.color) }
    var picking by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val trimmed = code.trim()
    // The code is drawn off the main thread, a moment after the last key: a long code in PDF417 is not made at every letter.
    val checked by produceState<Checked?>(null, trimmed, format) {
        delay(CHECK_DELAY_MS)
        value = withContext(Dispatchers.Default) { Checked(trimmed, format, trimmed.takeIf { it.isNotBlank() && it.length <= Wallet.MAX_CODE }?.let { Wallet.matrix(it, format) }) }
    }
    val settled = checked?.let { it.code == trimmed && it.format == format } == true
    val matrix = checked?.matrix
    val valid = name.isNotBlank() && settled && matrix != null
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
    val sections = prefs.preview.on
    MiniFrame {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetroText(stringResource(if (initial.name.isEmpty() && initial.code.isEmpty()) R.string.wal_new else R.string.notes_edit), size = 34.sp, modifier = Modifier.padding(top = 14.dp))
            // The card as it will look in the list, changing while it is typed.
            if (sections) {
                WalletTile(
                    initial.copy(name = name.trim(), code = trimmed, format = format, kind = kind, expiry = expiry, color = color), today, stacked = false, whole = true, index = 0, prefs = prefs,
                    tag = "wal:preview", onClick = {},
                )
            }
            SectionTitle(stringResource(R.string.wal_sec_card), sections)
            MetroTextField(name, { name = it.take(Wallet.MAX_NAME) }, stringResource(R.string.wallet_name), Modifier.testTag("wal:edit:name"))
            MetroTextField(code, { code = it.take(Wallet.MAX_CODE) }, stringResource(R.string.wal_code), Modifier.testTag("wal:edit:code"))
            MetroButton(stringResource(R.string.wal_from_picture), Modifier.fillMaxWidth().testTag("wal:edit:picture")) { picture.launch("image/*") }
            MetroButton(stringResource(R.string.wal_scan), Modifier.fillMaxWidth().testTag("wal:edit:scan")) { scanning = true }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(CodeFormat.entries) { f -> WalletChip(formatName(f), format == f, "wal:edit:format:${f.name}") { format = f } } }
            if (trimmed.isNotBlank() && settled && matrix == null && name.isNotBlank()) PageNote(stringResource(R.string.wal_format_invalid), Modifier.testTag("wal:edit:invalid"))
            if (trimmed.isNotBlank() && matrix != null) CodeBars(matrix, trimmed, format, Modifier.fillMaxWidth(0.8f))
            SectionTitle(stringResource(R.string.wal_sec_details), sections)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(CardKind.entries) { k -> WalletChip(stringResource(kindName(k)), kind == k, "wal:edit:kind:${k.name}") { kind = k } } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WalletChip(
                    if (expiry == 0L) stringResource(R.string.wal_no_expiry) else LocalDate.ofEpochDay(expiry).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    expiry != 0L, "wal:edit:expiry",
                ) { picking = true }
                if (expiry != 0L) WalletChip("✕", false, "wal:edit:noexpiry", description = stringResource(R.string.wal_expiry_clear)) { expiry = 0L }
            }
            MetroTextField(note, { note = it.take(Wallet.MAX_NOTE) }, stringResource(R.string.cal_notes_hint), Modifier.testTag("wal:edit:note"), singleLine = false)
            SectionTitle(stringResource(R.string.wal_sec_colour), sections)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..7).forEach { index ->
                    val label = if (index == 0) stringResource(R.string.wal_color_phone) else stringResource(R.string.wal_color_n, index)
                    val base = cardColor(index) ?: LocalAccent.current
                    val ring = inkFor(base)
                    Box(
                        Modifier.size(SWATCH).testTag("wal:edit:color:$index")
                            .semantics { role = Role.RadioButton; selected = color == index; contentDescription = label }
                            .metroPress(onClick = { color = index }).background(base)
                            .border(if (color == index) 3.dp else 0.dp, colors.foreground),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The colour of the phone follows the accent the user picks: a ring says it is not one fixed colour.
                        if (index == 0) Canvas(Modifier.size(16.dp)) { drawCircle(ring, style = Stroke(2.dp.toPx()), center = Offset(size.width / 2, size.height / 2), radius = size.minDimension / 2) }
                    }
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("✕", stringResource(R.string.rec_back), onCancel),
                AppBarItem("✓", stringResource(R.string.todo_save)) {
                    val fixed = Wallet.completed(trimmed, format)
                    if (name.isNotBlank() && Wallet.valid(fixed, format)) onSave(initial.copy(name = name.trim(), code = fixed, format = format, kind = kind, expiry = expiry, note = note.trim(), color = color))
                }.icon(Mark.Check),
            ),
        )
    }
    if (scanning) CodeScanDialog(
        onUse = { text, f -> code = text.take(Wallet.MAX_CODE); format = f; scanning = false }, onCancel = { scanning = false },
        secure = prefs.secure.on, extras = prefs.scanExtras.on, motion = prefs.motion.on,
    )
    if (picking) DateTimeDialog(
        (if (expiry == 0L) LocalDate.now().plusMonths(6) else LocalDate.ofEpochDay(expiry)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), dateOnly = true,
        onChoose = { ms -> expiry = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(); picking = false },
        onCancel = { picking = false },
    )
}
