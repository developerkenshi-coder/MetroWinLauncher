package com.metrowin.launcher.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroProgressBar
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** The things of the file manager that can be switched on and off; what each does is in its line. */
@Composable
fun OptionsDialog(isOn: (Opt) -> Boolean, onToggle: (Opt) -> Unit, onClose: () -> Unit) {
    DialogFrame(onCancel = onClose) {
        MetroText(stringResource(R.string.fmx_options), size = 22.sp)
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            Opt.entries.forEach { opt -> MenuRow(stringResource(opt.label), "fm:opt:${opt.name}", checked = isOn(opt)) { onToggle(opt) } }
        }
        MetroButton(stringResource(R.string.fmx_close), Modifier.fillMaxWidth(), onClose)
    }
}

/** Renaming many files: a template, a word to replace, a number to start from, and what each would be called. */
@Composable
fun BulkRenameDialog(files: List<File>, onApply: (List<RenamePlan>) -> Unit, onCancel: () -> Unit) {
    val colors = LocalMetroColors.current
    var template by remember { mutableStateOf("{name}") }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("1") }
    val plan = remember(template, find, replace, start) { FileViews.renamePlan(files, template, find, replace, start.toIntOrNull()?.coerceAtLeast(0) ?: 1) }
    val ready = plan.count { it.ok && it.newName != it.file.name }
    DialogFrame(onCancel = onCancel) {
        MetroText(stringResource(R.string.fmx_bulk_rename), size = 20.sp, weight = FontWeight.Normal)
        MetroTextField(template, { template = it.take(120) }, stringResource(R.string.fmx_bulk_template), Modifier.testTag("fm:bulk:template"))
        PageNote(stringResource(R.string.fmx_bulk_hint))
        MetroTextField(find, { find = it.take(60) }, stringResource(R.string.fmx_bulk_find), Modifier.testTag("fm:bulk:find"))
        MetroTextField(replace, { replace = it.take(60) }, stringResource(R.string.fmx_bulk_replace), Modifier.testTag("fm:bulk:replace"))
        MetroTextField(start, { start = it.filter(Char::isDigit).take(6) }, stringResource(R.string.fmx_bulk_start), Modifier.testTag("fm:bulk:start"))
        Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
            plan.forEach { p ->
                MetroText(
                    p.file.name + "  →  " + p.newName, size = 14.sp, weight = FontWeight.Normal, maxLines = 2, minScale = 1f,
                    color = if (p.ok) colors.foreground else LocalAccent.current.copy(alpha = 0.6f),
                )
            }
        }
        if (plan.any { !it.ok }) PageNote(stringResource(R.string.fmx_bulk_refused))
        MetroButton(stringResource(R.string.fm_rename), Modifier.fillMaxWidth().testTag("fm:bulk:ok")) { if (ready > 0) onApply(plan) else onCancel() }
    }
}

/** What an archive holds, listed; a touch on a file extracts that one beside the archive. */
@Composable
fun ArchiveListDialog(file: File, onExtractOne: (String) -> Unit, onClose: () -> Unit) {
    val colors = LocalMetroColors.current
    val items by produceState<List<FileOps.ArchiveItem>?>(null, file) { value = withContext(Dispatchers.IO) { FileOps.listArchive(file).orEmpty() } }
    DialogFrame(onCancel = onClose) {
        MetroText(file.name, size = 20.sp, weight = FontWeight.Normal, maxLines = 2)
        when {
            items == null -> MetroProgressBar(Modifier.padding(vertical = 6.dp))
            items!!.isEmpty() -> PageNote(stringResource(R.string.fmx_archive_unreadable))
            else -> {
                PageNote(stringResource(R.string.fmx_archive_tap))
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()).testTag("fm:archive:list")) {
                    items!!.forEach { item ->
                        Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).let { if (item.isDir) it else it.metroPress(onClick = { onExtractOne(item.name) }) }.padding(vertical = 4.dp)) {
                            MetroText(item.name, size = 16.sp, maxLines = 2, minScale = 1f, color = if (item.isDir) colors.subtle else colors.foreground)
                            if (!item.isDir) MetroText(StorageInfo.format(item.size, Locale.getDefault()), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle)
                        }
                    }
                }
            }
        }
        MetroButton(stringResource(R.string.fmx_close), Modifier.fillMaxWidth(), onClose)
    }
}

/** What each thing in the folder takes, the biggest first, as bars; a touch on a folder goes into it. */
@Composable
fun AnalyzerDialog(dir: File, showHidden: Boolean, onGo: (File) -> Unit, onClose: () -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val sizes by produceState<List<Pair<File, Long>>?>(null, dir) { value = withContext(Dispatchers.IO) { FileViews.folderSizes(dir, showHidden) { isActive } } }
    DialogFrame(onCancel = onClose) {
        MetroText(stringResource(R.string.fmx_analyzer), size = 20.sp, weight = FontWeight.Normal)
        MetroText(dir.name.ifEmpty { "/" }, size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
        val list = sizes
        when {
            list == null -> { MetroProgressBar(Modifier.padding(vertical = 6.dp)); PageNote(stringResource(R.string.fm_working)) }
            list.isEmpty() -> PageNote(stringResource(R.string.fm_folder_empty))
            else -> {
                val total = list.sumOf { it.second }.coerceAtLeast(1L)
                val biggest = list.first().second.coerceAtLeast(1L)
                MetroText(stringResource(R.string.fm_size) + ": " + StorageInfo.format(total, Locale.getDefault()), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()).testTag("fm:analyzer:list")) {
                    list.take(40).forEach { (file, bytes) ->
                        Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).let { if (file.isDirectory) it.metroPress(onClick = { onGo(file) }) else it }.padding(vertical = 4.dp)) {
                            Row {
                                MetroText(file.name, size = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                MetroText(StorageInfo.format(bytes, Locale.getDefault()) + "  " + (bytes * 100 / total) + "%", size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(6.dp).background(colors.surface)) {
                                Box(Modifier.fillMaxWidth(bytes.toFloat() / biggest).height(6.dp).background(accent))
                            }
                        }
                    }
                }
            }
        }
        MetroButton(stringResource(R.string.fmx_close), Modifier.fillMaxWidth(), onClose)
    }
}

/** A look at a file inside its properties: a picture, the first page of a pdf, or the first lines of a text. */
@Composable
fun FilePreview(file: File) {
    val colors = LocalMetroColors.current
    when {
        FilesThumbs.isPicture(file) -> FilesThumbs.load(file, side = 512)?.let {
            Image(it, null, Modifier.fillMaxWidth().heightIn(max = 220.dp), contentScale = ContentScale.Fit)
        }
        FilesThumbs.isPdf(file) -> FilesThumbs.page(file)?.let {
            Image(it, null, Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Fit)
        }
        FileOps.isText(file) -> {
            val head by produceState("", file) {
                value = withContext(Dispatchers.IO) {
                    runCatching { file.inputStream().use { i -> val b = ByteArray(2048); val n = i.read(b); if (n <= 0) "" else TextFiles.decode(b.copyOf(n)).text.lines().take(8).joinToString("\n") } }.getOrDefault("")
                }
            }
            if (head.isNotEmpty()) MetroText(head, size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 8, minScale = 1f, modifier = Modifier.fillMaxWidth().background(colors.surface).padding(8.dp))
        }
    }
}

/** A picture as big as the screen, to pinch and drag. */
@Composable
fun ImageViewer(file: File, onClose: () -> Unit) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file) { value = withContext(Dispatchers.IO) { FilesThumbs.large(file) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    androidx.activity.compose.BackHandler { onClose() }
    MiniFrame {
        MetroText(file.name, size = 18.sp, maxLines = 1, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().testTag("fm:viewer"), contentAlignment = Alignment.Center) {
            val bitmap = image
            if (bitmap == null) MetroProgressBar(Modifier.padding(24.dp))
            else Image(
                bitmap, file.name,
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                contentScale = ContentScale.Fit,
            )
        }
        com.metrowin.launcher.ui.components.AppBar(listOf(com.metrowin.launcher.ui.components.AppBarItem("←", stringResource(R.string.fm_back)) { onClose() }))
    }
}
