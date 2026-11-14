package com.metrowin.launcher.files

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.fieldTextStyle
import java.io.File
import java.nio.charset.Charset

/**
 * A text file opened to be read and changed. Over a megabyte, or when it cannot be read, it is not opened (a text field would not hold
 * it, and an empty field saved would erase it). It is written back with its own code and line ends, beside and moved over, and leaving with
 * changes not saved asks first. The menu has the wrap, the line numbers, the size of the letters, the code and the line ends.
 */
@Composable
fun TextEditor(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val prefs = remember { FilesPrefs(context) }
    val tooBig = file.length() > 1_000_000L
    val loaded = remember(file) { if (tooBig) null else runCatching { TextFiles.read(file) }.getOrNull() }
    val readable = loaded != null
    var text by remember(file) { mutableStateOf(loaded?.text.orEmpty()) }
    var saved by remember(file) { mutableStateOf(text) }
    var charset by remember(file) { mutableStateOf<Charset>(loaded?.charset ?: Charsets.UTF_8) }
    var crlf by remember(file) { mutableStateOf(loaded?.crlf ?: false) }
    val bom = loaded?.bom ?: false
    var wrap by remember { mutableStateOf(prefs.editorWrap) }
    var numbers by remember { mutableStateOf(prefs.editorNumbers) }
    var fontSize by remember { mutableIntStateOf(prefs.editorSize) }
    var asking by remember { mutableStateOf(false) }
    val dirty = text != saved

    fun leave() { if (dirty) asking = true else onClose() }
    fun save(): Boolean {
        if (!readable) return false
        if (!charset.newEncoder().canEncode(text)) { MetroToast.show(context, R.string.fmx_cannot_encode); return false }
        val ok = TextFiles.writeAtomic(file, TextFiles.encode(text, charset, crlf, bom))
        if (ok) saved = text
        MetroToast.show(context, if (ok) R.string.fm_saved else R.string.fm_save_failed)
        return ok
    }
    androidx.activity.compose.BackHandler { if (asking) asking = false else leave() }

    MiniFrame {
        MetroText(file.name + if (dirty) " •" else "", size = 24.sp, maxLines = 1, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp))
        when {
            tooBig -> PageNote(stringResource(R.string.fm_too_big), Modifier.padding(horizontal = 24.dp))
            !readable -> PageNote(stringResource(R.string.fmx_cannot_read), Modifier.padding(horizontal = 24.dp))
            else -> {
                val style = fieldTextStyle(colors.foreground, fontSize.sp).copy(fontFamily = FontFamily.Monospace, lineHeight = (fontSize * 1.4f).sp)
                val vertical = rememberScrollState()
                Box(Modifier.weight(1f).fillMaxWidth().imePadding().padding(horizontal = 24.dp).verticalScroll(vertical)) {
                    Row {
                        if (numbers && !wrap) MetroText(
                            (1..text.count { it == '\n' } + 1).joinToString("\n"), size = fontSize.sp, weight = FontWeight.Normal, color = colors.subtle,
                            lineHeight = (fontSize * 1.4f).sp, minScale = 1f, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(end = 10.dp),
                        )
                        BasicTextField(
                            value = text, onValueChange = { text = it }, textStyle = style, cursorBrush = SolidColor(LocalAccent.current),
                            modifier = (if (wrap) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()).defaultMinSize(minWidth = 240.dp)).testTag("fm:editor"),
                        )
                    }
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("←", stringResource(R.string.fm_back)) { leave() },
                AppBarItem("✓", stringResource(R.string.fm_save)) { save() },
            ),
            menu = if (!readable) emptyList() else listOf(
                AppBarItem("↩", stringResource(R.string.fmx_editor_wrap)) { wrap = !wrap; prefs.editorWrap = wrap }.marked(wrap),
                AppBarItem("#", stringResource(R.string.fmx_editor_numbers)) { numbers = !numbers; prefs.editorNumbers = numbers }.marked(numbers),
                AppBarItem("A+", stringResource(R.string.fmx_editor_bigger)) { fontSize = (fontSize + 2).coerceAtMost(28); prefs.editorSize = fontSize },
                AppBarItem("A-", stringResource(R.string.fmx_editor_smaller)) { fontSize = (fontSize - 2).coerceAtLeast(10); prefs.editorSize = fontSize },
                AppBarItem("⇄", stringResource(R.string.fmx_editor_charset, charset.displayName())) {
                    if (dirty) MetroToast.show(context, R.string.fmx_editor_save_first)
                    else {
                        val next = TextFiles.CHARSETS[(TextFiles.CHARSETS.indexOf(charset) + 1) % TextFiles.CHARSETS.size]
                        runCatching { TextFiles.read(file, next) }.onSuccess { text = it.text; saved = it.text; charset = next }
                    }
                },
                AppBarItem("¶", stringResource(if (crlf) R.string.fmx_editor_eol_crlf else R.string.fmx_editor_eol_lf)) { crlf = !crlf },
            ),
        )
    }
    if (asking) DialogFrame(onCancel = { asking = false }) {
        MetroText(stringResource(R.string.fmx_unsaved), size = 20.sp, weight = FontWeight.Normal)
        MetroButton(stringResource(R.string.fm_save), Modifier.fillMaxWidth().testTag("fm:editor:save")) { if (save()) onClose() else asking = false }
        MetroButton(stringResource(R.string.fmx_discard), Modifier.fillMaxWidth().testTag("fm:editor:discard")) { onClose() }
        MetroButton(stringResource(R.string.fm_cancel), Modifier.fillMaxWidth()) { asking = false }
    }
}
