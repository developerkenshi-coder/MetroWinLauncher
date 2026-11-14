package com.metrowin.launcher.shizukuterminal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SAVED = "saved"
private const val MAX_OUTPUT = 60_000

/** The colours of the Terminal: the ones of the look in use, or one of the themes of its own. */
internal class Palette(val bg: Color, val fg: Color, val err: Color, val ok: Color, val warn: Color, val prompt: Color)

@Composable
internal fun palette(theme: Int): Palette {
    val c = LocalMetroColors.current
    val black = Color.Black
    return when (theme) {
        1 -> Palette(black, Color(0xFF3DDC6B), Color(0xFFFF6B6B), Color(0xFF9BFFB5), Color(0xFFFFD166), Color(0xFF9BFFB5))
        2 -> Palette(black, Color(0xFFFFB000), Color(0xFFFF6B6B), Color(0xFF7CE38B), Color(0xFFFFE08A), Color(0xFFFFD27A))
        3 -> Palette(black, Color.White, Color(0xFFFF8080), Color(0xFF80FF80), Color(0xFFFFFF00), Color.White)
        else -> Palette(
            c.surface, c.foreground, if (c.dark) Color(0xFFFF6B6B) else Color(0xFFB3261E), if (c.dark) Color(0xFF5BD98B) else Color(0xFF1B7F3B),
            if (c.dark) Color(0xFFFFD166) else Color(0xFF8A5A00), LocalAccent.current,
        )
    }
}

/** A thin bar that runs along while a command runs (a still one when the user turned motion off). */
@Composable
private fun BusyBar(color: Color) {
    val still = LocalReduceMotion.current
    val x by rememberInfiniteTransition(label = "busy").animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "x")
    Canvas(Modifier.fillMaxWidth().height(3.dp)) {
        val w = size.width * 0.3f
        if (still) drawRect(color, size = size) else drawRect(color, Offset(((size.width + w) * x - w).coerceAtLeast(0f), 0f), Size(minOf(w, size.width), size.height))
    }
}

/** The words for how a command ended; null for a plain exit code. */
@Composable
private fun endingText(timeoutSeconds: Int): (Int) -> String? {
    val none = stringResource(R.string.tm_end_noshizuku)
    val slow = stringResource(R.string.tm_end_timeout, timeoutSeconds)
    val stopped = stringResource(R.string.tm_end_stopped)
    return { code -> when (Ending.of(code)) { Ending.NoShizuku -> none; Ending.Timeout -> slow; Ending.Stopped -> stopped; else -> null } }
}

private fun render(block: Block, p: Palette, colored: Boolean, ending: (Int) -> String?): AnnotatedString = buildAnnotatedString {
    if (block.collapsed) return@buildAnnotatedString
    fun line(text: String, color: Color) = withStyle(SpanStyle(color = color)) { append(text); append('\n') }
    if (block.out.isNotEmpty()) block.out.lines().forEach { l ->
        line(l, if (!colored) p.fg else when (logLevel(l)) { 'E', 'F' -> p.err; 'W' -> p.warn; else -> p.fg })
    }
    if (block.err.isNotEmpty()) block.err.lines().forEach { line(it, if (colored) p.err else p.fg) }
    block.code?.let { code ->
        val say = ending(code)
        withStyle(SpanStyle(color = if (!colored) p.fg else if (code == 0) p.ok else p.err, fontWeight = FontWeight.SemiBold)) { append(say ?: "[$code]") }
        append("\n")
    }
}

/** The text a [Block] shows: while it runs, what has come so far. */
private fun Block.finish(result: ShizukuShell.Result) {
    out = result.stdout.takeLast(MAX_OUTPUT)
    err = result.error.takeLast(MAX_OUTPUT)
    code = result.code
}

/** A command line with the rights of the shell, and the commands kept by the user (the starting ones can be removed too). */
@Composable
internal fun TerminalPage(state: ShizukuState, session: TerminalSession, opts: TerminalOptions) {
    val context = LocalContext.current
    val prefs = remember { Prefs.open(context, Prefs.SHIZUKU) }
    var saved by remember { mutableStateOf(SavedCommands.decode(prefs.getString(SAVED, null))) }
    val tab = session.tab
    var pending by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val p = palette(opts.theme)
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val compact = opts.compact.on || keyboard
    val ending = endingText((opts.timeoutMs / 1000).toInt())
    val total = tab.blocks.sumOf { it.out.length + it.err.length }
    LaunchedEffect(tab, total, tab.blocks.size) { scroll.scrollTo(scroll.maxValue) }
    fun keep(list: List<String>) {
        saved = list
        prefs.edit { putString(SAVED, SavedCommands.encode(list)) }
    }
    val blockedText = stringResource(R.string.tm_blocked_readonly)
    fun exec(command: String) {
        val block = Block(command)
        tab.blocks.add(block)
        session.add(command, opts.keepHistory.on)
        tab.cursor = -1
        tab.busy = true
        tab.stop.set(false)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ShizukuShell.shell(command, cancel = tab.stop, timeoutMs = opts.timeoutMs, onOutput = { block.out = it.takeLast(MAX_OUTPUT) })
            }
            block.finish(result)
            tab.busy = false
            tab.trim(MAX_OUTPUT)
        }
    }
    fun submit() {
        val command = tab.line.trim()
        if (command.isEmpty() || tab.busy || !state.ready) return
        when {
            opts.readOnly.on && !Command.readOnly(command) -> tab.blocks.add(Block(command).also { it.err = blockedText })
            Command.destructive(command) -> pending = command
            else -> exec(command)
        }
    }
    val danger = Command.destructive(tab.line)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Shown whenever the keyboard is not in the way: a command typed here can wipe data or break the system, and it skips the protected list of the apps page.
        if (!keyboard) Box(Modifier.fillMaxWidth().background(LocalAccent.current).padding(10.dp)) {
            MetroText(stringResource(R.string.st_term_warning), size = 14.sp, weight = FontWeight.SemiBold, color = onAccent(), maxLines = Int.MAX_VALUE, lineHeight = 19.sp)
        }
        if (!compact) PageNote(stringResource(if (state.ready) R.string.st_term_hint else R.string.st_pick_locked))
        if (opts.tabs.on) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(session.tabs.size) { i ->
                PageChip(session.tabs[i].name, i == session.current, "st:tab:$i") { session.current = i }
            }
            item { PageChip("+", false, "st:tab:add") { if (session.tabs.size < 6) { session.tabs.add(Tab((session.tabs.size + 1).toString())); session.current = session.tabs.lastIndex } } }
            if (session.tabs.size > 1) item {
                PageChip("×", false, "st:tab:close") { session.tabs.removeAt(session.current); session.current = session.current.coerceAtMost(session.tabs.lastIndex) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(p.bg).padding(10.dp).verticalScroll(scroll)) {
            if (tab.blocks.isEmpty()) {
                BasicText(stringResource(R.string.tm_empty), style = TextStyle(color = p.fg.copy(alpha = 0.6f), fontSize = opts.fontSize.sp, fontFamily = FontFamily.Monospace))
            }
            SelectionContainer {
                Column {
                    tab.blocks.forEach { block ->
                        val head = (if (block.collapsed) "▸ $ " else "▾ $ ") + block.command
                        BasicText(
                            head,
                            Modifier.fillMaxWidth().clickable { block.collapsed = !block.collapsed },
                            style = TextStyle(color = p.prompt, fontSize = opts.fontSize.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, lineHeight = (opts.fontSize + 4).sp),
                        )
                        BasicText(
                            render(block, p, opts.colors.on, ending),
                            style = TextStyle(color = p.fg, fontSize = opts.fontSize.sp, fontFamily = FontFamily.Monospace, lineHeight = (opts.fontSize + 4).sp),
                        )
                    }
                }
            }
        }
        if (tab.busy) BusyBar(p.prompt)
        if (!compact) {
            val shown = suggestions(saved, tab.line)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown) { command -> PageChip(command, selected = command == tab.line.trim(), tag = "st:cmd") { tab.line = command } }
            }
        }
        if (opts.quickKeys.on) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { PageChip("↑", false, "st:key:up", Modifier.semantics { contentDescription = "history up" }) { session.browse(-1) } }
            item { PageChip("↓", false, "st:key:down", Modifier.semantics { contentDescription = "history down" }) { session.browse(1) } }
            items(listOf("|", "/", "~", "-", ">", "&", "*", "$", "\"", ";")) { key -> PageChip(key, false, "st:key:$key") { tab.line += key } }
        }
        MetroTextField(tab.line, { tab.line = it; tab.cursor = -1 }, stringResource(R.string.st_term_placeholder), Modifier.fillMaxWidth(), onSearch = ::submit)
        if (danger) MetroText(stringResource(R.string.tm_danger), size = 14.sp, weight = FontWeight.SemiBold, color = p.err)
        if (opts.readOnly.on) PageNote(stringResource(R.string.tm_readonly_on))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = if (compact) 8.dp else 0.dp)) {
            MetroButton(if (tab.busy) "■ " + stringResource(R.string.st_stop) else "▶ " + stringResource(R.string.st_run), Modifier.weight(1f)) { if (tab.busy) tab.stop.set(true) else submit() }
            MetroButton("✕ " + stringResource(R.string.st_clear), Modifier.weight(1f)) { tab.blocks.clear() }
        }
        if (!compact) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            MetroButton("★ " + stringResource(R.string.st_save), Modifier.weight(1f)) { tab.line.trim().takeIf { it.isNotEmpty() && it !in saved }?.let { keep(saved + it) } }
            MetroButton("− " + stringResource(R.string.st_remove), Modifier.weight(1f)) { keep(saved - tab.line.trim()) }
        }
    }
    pending?.let { command ->
        DialogFrame(onCancel = { pending = null }, title = stringResource(R.string.tm_confirm_title)) {
            BasicText(command, style = TextStyle(color = LocalMetroColors.current.foreground, fontSize = 15.sp, fontFamily = FontFamily.Monospace))
            MetroText(stringResource(R.string.tm_confirm_body), size = 16.sp, weight = FontWeight.SemiBold, color = palette(0).err)
            MetroButton(stringResource(R.string.tm_run_anyway), Modifier.fillMaxWidth()) { pending = null; exec(command) }
            MetroButton(stringResource(R.string.tm_cancel), Modifier.fillMaxWidth()) { pending = null }
        }
    }
    if (session.historyOpen) HistoryDialog(session) { tab.line = it; session.historyOpen = false }
}

/** The commands run so far, with a search; a touch brings one back to the line. */
@Composable
private fun HistoryDialog(session: TerminalSession, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = session.history.reversed().filter { query.isBlank() || it.contains(query.trim(), true) }
    DialogFrame(onCancel = { session.historyOpen = false }, title = stringResource(R.string.tm_history)) {
        MetroTextField(query, { query = it }, stringResource(R.string.st_search), Modifier.fillMaxWidth())
        if (shown.isEmpty()) PageNote(stringResource(R.string.tm_history_empty))
        LazyColumn(Modifier.fillMaxWidth().height(280.dp)) {
            items(shown) { command ->
                BasicText(
                    command, Modifier.fillMaxWidth().clickable { onPick(command) }.padding(vertical = 10.dp),
                    style = TextStyle(color = LocalMetroColors.current.foreground, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
                )
            }
        }
        MetroButton(stringResource(R.string.tm_history_clear), Modifier.fillMaxWidth()) { session.clearHistory() }
    }
}
