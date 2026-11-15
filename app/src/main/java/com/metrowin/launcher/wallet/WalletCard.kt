package com.metrowin.launcher.wallet

import com.metrowin.launcher.ui.mini.miniPageSide
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.zxing.common.BitMatrix
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LightColors
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** [code] drawn as [format] in black on white, with the quiet space on every side that scanners want. */
@Composable
fun CodeImage(code: String, format: CodeFormat, modifier: Modifier = Modifier) {
    val matrix = remember(code, format) { Wallet.matrix(code, format) }
    CodeBars(matrix, code, format, modifier)
}

/** The same from a [matrix] that is ready (the editor makes it off the main thread). */
@Composable
internal fun CodeBars(matrix: BitMatrix?, code: String, format: CodeFormat, modifier: Modifier = Modifier) {
    val ratio = matrix?.let { if (format.twoDimensional) it.width.toFloat() / it.height else 3.4f } ?: 1f
    val description = stringResource(R.string.wal_code_desc, formatName(format), code.take(DESCRIBED))
    Canvas(modifier.semantics { contentDescription = description }.background(Color.White).aspectRatio(if (format.twoDimensional) ratio.coerceIn(0.5f, 4f) else 3.4f).testTag("wal:code")) {
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

private const val DESCRIBED = 40

/** The code as big as the screen allows on white, for a scanner that is held up to the phone; a touch closes it. */
@Composable
private fun CodeFullscreen(card: Card, secure: Boolean, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogWindowSetup(secure)
        BackHandler { onClose() }
        Box(Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) { detectTapGestures { onClose() } }.testTag("wal:fullscreen"), contentAlignment = Alignment.Center) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CodeImage(card.code, card.format, Modifier.fillMaxWidth())
                MetroText(card.code, size = 22.sp, weight = FontWeight.Normal, color = Color.Black)
                MetroText(stringResource(R.string.wal_fullscreen_hint), size = 14.sp, weight = FontWeight.Normal, color = LightColors.subtle)
            }
        }
    }
}

@Composable
internal fun CardView(
    card: Card, prefs: WalletPrefs, today: Long,
    onEdit: () -> Unit, onDelete: () -> Unit, onBack: () -> Unit, onFavorite: () -> Unit, onExport: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    var confirm by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    // The screen is as bright as it goes while a code is shown: a scanner reads it better.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val before = window?.attributes?.screenBrightness
        window?.let { it.attributes = it.attributes.also { a -> a.screenBrightness = 1f } }
        onDispose { window?.let { it.attributes = it.attributes.also { a -> a.screenBrightness = before ?: -1f } } }
    }
    BackHandler { if (confirm) confirm = false else onBack() }
    // The code comes in with a short swell; not at all when the animations are off.
    val still = LocalReduceMotion.current
    val opened = remember { Animatable(if (prefs.motion.on && !still) 0f else 1f) }
    LaunchedEffect(Unit) { opened.animateTo(1f, tween(260)) }
    MiniFrame {
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= WIDE
            val panel: @Composable (Modifier) -> Unit = { m ->
                Column(
                    m.widthIn(max = 420.dp).fillMaxWidth().graphicsLayer { alpha = opened.value; scaleX = 0.94f + 0.06f * opened.value; scaleY = 0.94f + 0.06f * opened.value }
                        .background(Color.White).metroPress(onClick = { full = true }).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CodeImage(card.code, card.format, Modifier.fillMaxWidth())
                    MetroText(card.code, size = 18.sp, weight = FontWeight.Normal, color = Color.Black, modifier = Modifier.padding(top = 8.dp))
                }
            }
            val details: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    if (card.expiry != 0L) {
                        val state = Wallet.expiryState(card, today)
                        val flagged = prefs.expiry.on && state != ExpiryState.Valid || state == ExpiryState.Expired
                        val date = LocalDate.ofEpochDay(card.expiry).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
                        MetroText(
                            stringResource(R.string.wal_expires, date), size = 17.sp, weight = if (flagged) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (flagged) LocalAccent.current else colors.subtle, modifier = Modifier.padding(top = 12.dp),
                        )
                        if (prefs.expiry.on && state == ExpiryState.Soon) {
                            val left = (card.expiry - today).toInt()
                            MetroText(pluralStringResource(R.plurals.wal_days_left, left, left), size = 15.sp, weight = FontWeight.Normal, color = LocalAccent.current)
                        }
                    }
                    if (card.note.isNotBlank()) MetroText(card.note, size = 17.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 8.dp))
                    if (prefs.history.on && card.uses > 0) {
                        val last = Instant.ofEpochMilli(card.lastUsed).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        MetroText(pluralStringResource(R.plurals.wal_used_times, card.uses, card.uses, last), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).miniPageSide()) {
                MetroText(card.name, size = 32.sp, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp).testTag("wal:name"))
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { panel(Modifier) }
                        details(Modifier.weight(1f))
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { panel(Modifier) }
                    details(Modifier)
                }
            }
        }
        // The question is above the bar, always in view, and not at the end of a long page.
        if (confirm) MetroButton(stringResource(R.string.notes_delete_confirm), Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).testTag("wal:delete:confirm")) { onDelete() }
        AppBar(
            listOf(
                AppBarItem("←", stringResource(R.string.rec_back), onBack),
                AppBarItem("✎", stringResource(R.string.notes_edit), onEdit),
                AppBarItem("✕", stringResource(R.string.todo_delete)) { confirm = true },
            ),
            menu = listOf(
                AppBarItem(if (card.favorite) "★" else "☆", stringResource(R.string.wal_favourite), onFavorite),
                AppBarItem("⇪", stringResource(R.string.wal_export_card), onExport),
            ),
        )
    }
    if (full) CodeFullscreen(card, prefs.secure.on) { full = false }
}

private val WIDE = 600.dp
