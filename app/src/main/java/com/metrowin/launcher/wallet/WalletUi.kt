package com.metrowin.launcher.wallet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.ColorMath
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalHighContrast
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import com.metrowin.launcher.ui.theme.toArgbInt
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** The sizes of the wallet, in one place. */
internal val CARD_HEIGHT = 120.dp
internal val CARD_STRIP = 60.dp
internal val TILE_HEIGHT = 72.dp
internal val SCAN_FRAME = 260.dp
internal val SWATCH = 40.dp
internal const val SEARCH_LIMIT = 60

internal fun cardColor(index: Int): Color? = MetroAccents.getOrNull(index - 1)

/** The accent colour (as its number, 1 up) nearest to [rgb]: for a pass that asks for a colour of its own. */
internal fun nearestAccent(rgb: Int): Int {
    fun dist(c: Color) = listOf(c.red * 255 - (rgb shr 16 and 255), c.green * 255 - (rgb shr 8 and 255), c.blue * 255 - (rgb and 255)).sumOf { (it * it).toDouble() }
    return MetroAccents.indices.minBy { dist(MetroAccents[it]) } + 1
}

internal fun kindName(kind: CardKind) = when (kind) {
    CardKind.Loyalty -> R.string.wal_kind_loyalty
    CardKind.Ticket -> R.string.wal_kind_ticket
    CardKind.Other -> R.string.wal_kind_other
}

@Composable
internal fun formatName(format: CodeFormat): String = stringResource(
    when (format) {
        CodeFormat.Code128 -> R.string.wal_fmt_code128
        CodeFormat.Ean13 -> R.string.wal_fmt_ean13
        CodeFormat.Code39 -> R.string.wal_fmt_code39
        CodeFormat.Qr -> R.string.wal_fmt_qr
        CodeFormat.Aztec -> R.string.wal_fmt_aztec
        CodeFormat.Pdf417 -> R.string.wal_fmt_pdf417
        CodeFormat.Ean8 -> R.string.wal_fmt_ean8
        CodeFormat.UpcA -> R.string.wal_fmt_upca
        CodeFormat.UpcE -> R.string.wal_fmt_upce
        CodeFormat.Itf -> R.string.wal_fmt_itf
        CodeFormat.Codabar -> R.string.wal_fmt_codabar
        CodeFormat.DataMatrix -> R.string.wal_fmt_datamatrix
    },
)

/** The ink on a card of colour [background]: white, or near black when the card is light (white on yellow or lime cannot be read). */
@Composable
internal fun inkFor(background: Color): Color =
    if (LocalHighContrast.current) ColorMath.inkOn(background) else if (ColorMath.luminance(background.toArgbInt()) > 0.55f) Color(0xFF111111) else Color.White

/** The activity behind [this], however many wrappers are around it. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** No screenshot and no picture in the recent apps while [on]; one place for the whole wallet screen, so two pages that overlap in a transition cannot undo each other. */
@Composable
internal fun KeepSecure(on: Boolean) {
    val context = LocalContext.current
    DisposableEffect(on) {
        val window = context.findActivity()?.window
        if (on) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (on) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** For a full-screen dialog: the window reaches into the cutout, and is kept out of screenshots when [secure]. */
@Composable
internal fun DialogWindowSetup(secure: Boolean) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(window, secure) {
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
            }
            if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }
}

/** Today, as an epoch day, kept true: it changes at midnight and when the app comes back after the night. */
@Composable
internal fun rememberToday(): Long {
    var today by remember { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now().toEpochDay() }
    LaunchedEffect(today) {
        val now = LocalDateTime.now()
        delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 1_000)
        today = LocalDate.now().toEpochDay()
    }
    return today
}

/** A chip with a clear mark when it is chosen: filled, and with a line round it, so it can be told from the others without the colour. */
@Composable
internal fun WalletChip(text: String, selected: Boolean, tag: String, modifier: Modifier = Modifier, description: String? = null, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    Box(
        modifier.testTag(tag)
            .semantics { role = Role.RadioButton; this.selected = selected; if (description != null) contentDescription = description }
            .metroPress(onClick = onClick).litFill(selected)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.foreground else colors.subtle.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(text, size = 16.sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) onAccent() else colors.foreground)
    }
}

/** A card and its code, drawn: the picture of the empty wallet. */
@Composable
internal fun CardGlyph(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Canvas(modifier) {
        val stroke = 3.dp.toPx()
        drawRoundRect(accent, Offset(stroke, size.height * 0.15f), Size(size.width - 2 * stroke, size.height * 0.7f), CornerRadius(6.dp.toPx()), Stroke(stroke))
        val top = size.height * 0.4f
        val bottom = size.height * 0.7f
        var x = size.width * 0.22f
        listOf(2f, 1f, 3f, 1f, 2f, 1f, 3f).forEach { w ->
            drawRect(accent, Offset(x, top), Size(w * 1.6.dp.toPx(), bottom - top))
            x += w * 1.6.dp.toPx() + 2.5.dp.toPx()
        }
    }
}

/** The passphrase of a backup: asked twice when a backup is being made (a slip would lock it for good), once when one is being opened. */
@Composable
internal fun PassphraseDialog(twice: Boolean, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val mismatch = twice && second.isNotEmpty() && first != second
    DialogFrame(onCancel, title = stringResource(R.string.wal_pass_title)) {
        MetroTextField(first, { first = it.take(128) }, stringResource(R.string.wal_pass_hint), Modifier.testTag("wal:pass"), password = true)
        if (twice) {
            MetroTextField(second, { second = it.take(128) }, stringResource(R.string.wal_pass_again), Modifier.testTag("wal:pass:again"), password = true)
            PageNote(stringResource(if (mismatch) R.string.wal_pass_mismatch else R.string.wal_pass_warning))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetroButton(stringResource(android.R.string.ok), Modifier.weight(1f).testTag("wal:pass:ok")) { if (first.isNotEmpty() && (!twice || first == second)) onDone(first) }
            MetroButton(stringResource(android.R.string.cancel), Modifier.weight(1f), onClick = onCancel)
        }
    }
}

@Composable
internal fun SectionTitle(title: String, on: Boolean) {
    if (!on) return
    val colors = LocalMetroColors.current
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(colors.subtle.copy(alpha = 0.4f)))
    MetroText(title, size = 14.sp, weight = FontWeight.SemiBold, color = colors.subtle)
}
