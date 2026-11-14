package com.metrowin.launcher.datasense

import android.content.Intent
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.MemoryStats
import com.metrowin.launcher.data.UsageAccess
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** The sizes of the apps, kept for five minutes: one call to Android for each app is slow, and the list changes little. */
private var sizesCache: Pair<Long, List<AppSize>>? = null
private const val SIZES_KEEP_MS = 5 * 60_000L

private fun openAppInfo(context: android.content.Context, packageName: String) {
    val page = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(page) }
}

/** The screen of Android that frees space: the one of the storage manager, or the page of the storage settings. */
private fun openStorageSettings(context: android.content.Context) {
    val manage = Intent(StorageManager.ACTION_MANAGE_STORAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(manage) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun openFile(context: android.content.Context, file: BigFile) {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, context.contentResolver.getType(file.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(view) }
}

@Composable
internal fun StoragePage(reader: UsageReader, allowed: Boolean, refresh: Int) {
    val context = LocalContext.current
    val options = LocalDsOptions.current
    val memory by produceState<MemoryStats?>(null, refresh, allowed) {
        value = withContext(Dispatchers.IO) { MemoryStats.read(context).also { m -> m?.let { DataHistory.recordFree(context, it.storage.freeBytes) } } }
    }
    val sizes by produceState<List<AppSize>?>(sizesCache?.second, refresh, allowed) {
        if (!allowed) { value = emptyList(); return@produceState }
        val kept = sizesCache
        if (kept != null && System.currentTimeMillis() - kept.first < SIZES_KEEP_MS && refresh == 0) { value = kept.second; return@produceState }
        value = withContext(Dispatchers.IO) {
            reader.appSizes(active = { isActive }).also { if (it.isNotEmpty()) sizesCache = System.currentTimeMillis() to it }
        }
    }
    val files by produceState<Pair<List<BigFile>, List<List<BigFile>>>?>(null, refresh, options[Opt.BigFiles]) {
        value = if (options[Opt.BigFiles]) withContext(Dispatchers.IO) { StorageScan.read(context) } else null
    }
    val m = memory
    if (m == null) { Skeleton(rows = 5, chart = true); return }
    val wide = options[Opt.Wide] && LocalConfiguration.current.screenWidthDp >= 600
    val state = rememberLazyListState()
    val summary: @Composable () -> Unit = { StorageSummary(m, sizes.orEmpty(), allowed, files) }
    if (wide) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { summary() }
            LazyColumn(Modifier.weight(1f).testTag("ds:storage").scrollIndicator(state), state) { sizeRows(sizes.orEmpty()) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().testTag("ds:storage").scrollIndicator(state), state) {
            item { summary() }
            sizeRows(sizes.orEmpty())
        }
    }
}

@Composable
private fun StorageSummary(m: MemoryStats, sizes: List<AppSize>, allowed: Boolean, files: Pair<List<BigFile>, List<List<BigFile>>>?) {
    val context = LocalContext.current
    val options = LocalDsOptions.current
    val colors = LocalMetroColors.current
    val p = rememberPalette()
    // A kind the phone would not measure (no permission) is shown as a dash, not as nothing.
    val parts = listOf(
        Triple(R.string.ds_apps, m.apps, p.parts[0]), Triple(R.string.ds_photos, m.photos, p.parts[1]),
        Triple(R.string.ds_videos, m.videos, p.parts[2]), Triple(R.string.ds_music, m.music, p.parts[3]),
        Triple(R.string.ds_other, m.other, p.parts[4]),
    )
    Column {
        MetroText(bytes(m.used), size = 40.sp, maxLines = 1, minScale = 0.6f, modifier = Modifier.testTag("ds:used"))
        MetroText(stringResource(R.string.ds_of_total, bytes(m.storage.totalBytes), bytes(m.storage.freeBytes)), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2)
        if (options[Opt.Chart]) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                Donut(
                    parts.map { (_, size, color) -> DataPeriods.share(size ?: 0L, m.storage.totalBytes) to color }, "${m.storage.usedPercent}%",
                    stringResource(R.string.dsx_donut_desc, m.storage.usedPercent),
                )
            }
        } else {
            Canvas(Modifier.fillMaxWidth().height(22.dp).padding(vertical = 6.dp).background(colors.surface)) {
                var x = 0f
                parts.forEach { (_, size, color) ->
                    val w = this.size.width * DataPeriods.share(size ?: 0L, m.storage.totalBytes)
                    drawRect(color, Offset(x, 0f), Size(w, this.size.height))
                    x += w
                }
            }
        }
        parts.forEach { (name, size, color) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(color))
                MetroText(stringResource(name), size = 18.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                MetroText(size?.let { bytes(it) } ?: "—", size = 18.sp, weight = FontWeight.Normal, color = colors.subtle)
            }
        }
        val cache = sizes.sumOf { it.cache }
        if (cache > 0) MetroText(stringResource(R.string.ds_cache, bytes(cache)), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 8.dp))
        if (!allowed) {
            PageNote(stringResource(R.string.dsx_sizes_need), Modifier.padding(top = 8.dp))
            MetroButton(stringResource(R.string.dsx_allow), Modifier.padding(top = 8.dp).testTag("ds:allow")) { UsageAccess.openSettings(context) }
        }
        if (options[Opt.Cleanup]) {
            MetroButton(stringResource(R.string.dsx_free_up), Modifier.padding(top = 12.dp).testTag("ds:cleanup")) { openStorageSettings(context) }
            if (sizes.isNotEmpty()) PageNote(stringResource(R.string.dsx_app_info_hint), Modifier.padding(top = 6.dp))
        }
        if (options[Opt.History]) FreeTrend(p)
        if (options[Opt.Chart] && sizes.size >= 3) {
            MetroText(stringResource(R.string.dsx_treemap), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            val top = sizes.take(12)
            Treemap(top.mapIndexed { i, s -> Triple(s.label, s.total, p.parts[i % 4]) }, onClick = { openAppInfo(context, top[it].packageName) })
        }
        if (options[Opt.BigFiles]) BigFiles(files)
        MetroText(stringResource(R.string.ds_biggest), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    }
}

/** The free space as one line over the days the history has. */
@Composable
private fun FreeTrend(p: DsPalette) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val points = DataHistory.free(context)
    if (points.size < 2) { PageNote(stringResource(R.string.dsx_free_trend_wait), Modifier.padding(top = 12.dp)); return }
    MetroText(stringResource(R.string.dsx_free_trend), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
    val low = points.minOf { it.second }
    val high = points.maxOf { it.second }
    Canvas(Modifier.fillMaxWidth().height(70.dp).background(colors.surface)) {
        val dx = size.width / (points.size - 1)
        val span = (high - low).coerceAtLeast(1L).toFloat()
        var previous: Offset? = null
        points.forEachIndexed { i, (_, free) ->
            val at = Offset(i * dx, size.height - 6.dp.toPx() - (size.height - 12.dp.toPx()) * (free - low) / span)
            previous?.let { drawLine(p.wifi, it, at, 2.dp.toPx()) }
            previous = at
        }
    }
    MetroText(stringResource(R.string.dsx_free_range, bytes(low), bytes(high)), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
}

@Composable
private fun BigFiles(files: Pair<List<BigFile>, List<List<BigFile>>>?) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    MetroText(stringResource(R.string.dsx_big_files), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    if (files == null) { Skeleton(rows = 3); return }
    if (files.first.isEmpty()) { PageNote(stringResource(R.string.dsx_big_none)); return }
    files.first.forEach { f -> FileRow(f.name, bytes(f.size)) { openFile(context, f) } }
    if (files.second.isNotEmpty()) {
        MetroText(stringResource(R.string.dsx_duplicates), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        files.second.forEach { group -> FileRow("${group.first().name} ×${group.size}", bytes(group.first().size * group.size)) { openFile(context, group.first()) } }
    }
}

@Composable
private fun FileRow(name: String, size: String, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    Row(Modifier.fillMaxWidth().metroPress(onClick = onClick).padding(vertical = 4.dp)) {
        MetroText(name, size = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
        MetroText(size, size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun LazyListScope.sizeRows(sizes: List<AppSize>) {
    itemsIndexed(sizes, key = { _, s -> s.packageName }) { index, app ->
        val context = LocalContext.current
        val options = LocalDsOptions.current
        val colors = LocalMetroColors.current
        val compact = options[Opt.Compact]
        Column(
            Modifier.fillMaxWidth().featherRow(index).then(if (options[Opt.Cleanup]) Modifier.metroPress(onClick = { openAppInfo(context, app.packageName) }) else Modifier)
                .padding(vertical = if (compact) 2.dp else 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (options[Opt.Icons]) { AppIcon(app.packageName, if (compact) 22.dp else 28.dp); Spacer(Modifier.padding(start = 10.dp)) }
                MetroText(app.label, size = if (compact) 16.sp else 19.sp, maxLines = 1, modifier = Modifier.weight(1f))
                MetroText(bytes(app.total), size = if (compact) 15.sp else 17.sp, weight = FontWeight.Normal, color = colors.subtle)
            }
            if (!compact) MetroText(stringResource(R.string.ds_app_sizes, bytes(app.app), bytes(app.data), bytes(app.cache)), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 2)
        }
    }
}
