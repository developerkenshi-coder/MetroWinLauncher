package com.metrowin.launcher.shizukuterminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private val STARTERS = listOf(
    "dumpsys battery", "getprop ro.build.version.release", "pm list packages -3", "settings list global", "wm size", "wm density",
    "df -h /data", "uptime", "dumpsys deviceidle whitelist", "cmd package list packages -d",
)

private const val SAVED = "saved"
private const val MAX_OUTPUT = 60_000

/** A command line with the rights of the shell, and the commands kept by the user (the starting ones can be removed too). */
@Composable
fun TerminalPage(state: ShizukuState) {
    val context = LocalContext.current
    val prefs = remember { Prefs.open(context, Prefs.SHIZUKU) }
    var saved by remember { mutableStateOf(prefs.getString(SAVED, null)?.lines()?.filter { it.isNotBlank() } ?: STARTERS) }
    var line by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val stop = remember { AtomicBoolean(false) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    LaunchedEffect(output) { scroll.scrollTo(scroll.maxValue) }
    fun keep(list: List<String>) {
        saved = list
        prefs.edit { putString(SAVED, list.joinToString("\n")) }
    }
    fun run() {
        val command = line.trim()
        if (command.isEmpty() || busy || !state.ready) return
        busy = true
        stop.set(false)
        scope.launch {
            val result = withContext(Dispatchers.IO) { ShizukuShell.shell(command, cancel = stop) }
            output = (output + "$ $command\n${result.output}\n[${result.code}]\n\n").takeLast(MAX_OUTPUT)
            busy = false
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Always shown: a command typed here can wipe data or break the system, and it skips the protected list of the apps page.
        Box(Modifier.fillMaxWidth().background(LocalAccent.current).padding(10.dp)) {
            MetroText(stringResource(R.string.st_term_warning), size = 14.sp, weight = FontWeight.SemiBold, color = Color.White, maxLines = Int.MAX_VALUE, lineHeight = 19.sp)
        }
        PageNote(stringResource(if (state.ready) R.string.st_term_hint else R.string.st_pick_locked))
        Box(Modifier.weight(1f).fillMaxWidth().background(LocalMetroColors.current.surface).padding(10.dp).verticalScroll(scroll)) {
            BasicText(output, style = TextStyle(color = LocalMetroColors.current.foreground, fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(saved) { command -> PageChip(command, selected = command == line.trim(), tag = "st:cmd") { line = command } }
        }
        MetroTextField(line, { line = it }, stringResource(R.string.st_term_placeholder), Modifier.fillMaxWidth(), onSearch = ::run)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroButton(stringResource(if (busy) R.string.st_stop else R.string.st_run), Modifier.weight(1f)) { if (busy) stop.set(true) else run() }
            MetroButton(stringResource(R.string.st_clear), Modifier.weight(1f)) { output = "" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            MetroButton(stringResource(R.string.st_save), Modifier.weight(1f)) { line.trim().takeIf { it.isNotEmpty() && it !in saved }?.let { keep(saved + it) } }
            MetroButton(stringResource(R.string.st_remove), Modifier.weight(1f)) { keep(saved - line.trim()) }
        }
    }
}
