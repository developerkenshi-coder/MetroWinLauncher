package com.metrowin.launcher.ui.start

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.AppsStats
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.DataUsage
import com.metrowin.launcher.data.MemoryStats
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.TileSize
import com.metrowin.launcher.ui.anim.FlipContainer
import com.metrowin.launcher.ui.components.AppArt
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.util.Locale

/** "7 h 05 min" of the weekly top: hours and minutes, units that read the same in most languages. */
private fun spent(millis: Long): String = BatteryInfo.duration((millis / 60_000).toInt())

/**
 * The Apps tile: how many apps the phone has. Medium turns over to the app used most this week; wide and large show the top five
 * beside the number. Without the usage access the top is a line that says how to get it (a touch opens that screen).
 */
@Composable
internal fun AppsFace(item: AppItem, size: TileSize, width: Dp, height: Dp, stats: AppsStats?, allowed: Boolean, seed: Int) {
    if (stats == null) {
        StaticFace(item, size, width, height)
        return
    }
    val digits = with(androidx.compose.ui.platform.LocalDensity.current) { (height * if (size == TileSize.Small) 0.34f else 0.3f).toSp() }
    if (size == TileSize.Wide || size == TileSize.Large) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = NAME_ROOM), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                    MetroText(stats.installed.toString(), size = digits, maxLines = 1, modifier = Modifier.testTag("apps:count"))
                    MetroText(stringResource(R.string.tile_apps_installed), size = 14.sp, weight = FontWeight.Normal)
                }
                Column(Modifier.weight(0.6f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (stats.top.isEmpty()) {
                        MetroText(
                            stringResource(if (allowed) R.string.tile_apps_nothing else R.string.tile_apps_allow), size = 14.sp, weight = FontWeight.Normal,
                            modifier = Modifier.testTag("apps:hint"),
                        )
                    } else {
                        MetroText(stringResource(R.string.tile_apps_top), size = 13.sp, weight = FontWeight.Normal)
                        stats.top.forEach { top ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("apps:top:${top.item.id}")) {
                                AppArt(top.item, 22.dp)
                                MetroText(top.item.label, size = 15.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                MetroText(spent(top.millis), size = 12.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
            TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
        return
    }
    // The live tile of the smaller sizes: the number, and (medium, when there is one) the app used most this week on the other face.
    val best = stats.top.firstOrNull()
    FlipContainer(if (size == TileSize.Medium && best != null) 2 else 1, seed) { face ->
        Box(Modifier.fillMaxSize()) {
            if (face == 0 || best == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    MetroText(stats.installed.toString(), size = digits, maxLines = 1, modifier = Modifier.testTag("apps:count"))
                    if (size != TileSize.Small) MetroText(stringResource(R.string.tile_apps_installed), size = 14.sp, weight = FontWeight.Normal)
                }
            } else {
                Column(Modifier.align(Alignment.Center).padding(bottom = NAME_ROOM / 2), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppArt(best.item, 40.dp)
                    MetroText(best.item.label, size = 16.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    MetroText(stringResource(R.string.tile_apps_top), size = 12.sp, weight = FontWeight.Normal)
                }
            }
            if (size != TileSize.Small) TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** Where a charge is heading, in percent: empty while it runs down, full while it charges or is full. */
internal fun batteryEnd(state: BatteryState): Int = if (state == BatteryState.Discharging) 0 else 100

/** The minutes the estimate of the phone gives for the way the charge is heading, or null when it gives none. */
internal fun batteryMinutes(battery: BatteryInfo): Int? = if (battery.state == BatteryState.Discharging) battery.minutesLeft else battery.minutesToFull

/**
 * The Battery tile: the charge, and a graph of where it is heading by the estimate Android already keeps (the same one its battery screen
 * shows), so the launcher watches nothing. Not a live tile: it does not turn over. Medium has a small graph, wide and large an extended one.
 */
@Composable
internal fun BatteryGraphFace(item: AppItem, size: TileSize, width: Dp, height: Dp, battery: BatteryInfo?) {
    if (battery == null) {
        StaticFace(item, size, width, height)
        return
    }
    val digits = with(androidx.compose.ui.platform.LocalDensity.current) { (height * if (size == TileSize.Small) 0.34f else 0.26f).toSp() }
    val minutes = batteryMinutes(battery)
    val estimate = minutes?.let {
        stringResource(if (battery.state == BatteryState.Discharging) R.string.battery_time_left else R.string.battery_until_full, BatteryInfo.duration(it))
    }
    Box(Modifier.fillMaxSize()) {
        if (size == TileSize.Small) {
            MetroText("${battery.percent}%", size = digits, maxLines = 1, modifier = Modifier.align(Alignment.Center).testTag("batterygraph:percent"))
        } else {
            val extended = size != TileSize.Medium
            Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = NAME_ROOM)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    MetroText("${battery.percent}%", size = digits, maxLines = 1, modifier = Modifier.testTag("batterygraph:percent"))
                    if (extended) {
                        MetroText(
                            stringResource(
                                when (battery.state) {
                                    BatteryState.Charging -> R.string.battery_charging
                                    BatteryState.Full -> R.string.battery_full
                                    BatteryState.Discharging -> R.string.battery_discharging
                                },
                            ),
                            size = 16.sp, weight = FontWeight.Normal, modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
                        )
                    }
                }
                if (extended && estimate != null) {
                    MetroText(estimate, size = 15.sp, weight = FontWeight.Normal, modifier = Modifier.testTag("batterygraph:estimate"))
                }
                BatteryLine(
                    battery.percent, if (minutes == null) battery.percent else batteryEnd(battery.state), extended,
                    Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp).testTag("batterygraph:graph"),
                )
            }
            TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** The line from the charge now (left) to where it is heading (right), with the area under it; the extended one adds grid lines. */
@Composable
private fun BatteryLine(from: Int, to: Int, extended: Boolean, modifier: Modifier) {
    val ink = LocalMetroColors.current.tileContent
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val y0 = h * (1f - from / 100f)
        val y1 = h * (1f - to / 100f)
        val hair = 1.dp.toPx()
        if (extended) listOf(0.25f, 0.5f, 0.75f).forEach { drawLine(ink.copy(alpha = 0.18f), Offset(0f, h * it), Offset(w, h * it), hair) }
        drawRect(ink.copy(alpha = 0.3f), style = Stroke(hair))
        drawPath(Path().apply { moveTo(0f, h); lineTo(0f, y0); lineTo(w, y1); lineTo(w, h); close() }, ink.copy(alpha = 0.25f))
        drawLine(ink, Offset(0f, y0), Offset(w, y1), 2.dp.toPx())
        drawCircle(ink, 3.dp.toPx(), Offset(0f, y0))
    }
}

/** One line of a table of numbers: the name, and a value for each column (a dash for what the phone would not say). */
@Composable
private fun StatRow(name: String, values: List<String>, tag: String, nameWidth: Dp) {
    Row(Modifier.fillMaxWidth().testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        MetroText(name, size = 15.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.width(nameWidth))
        values.forEach { MetroText(it, size = 18.sp, maxLines = 1, modifier = Modifier.weight(1f)) }
    }
}

/**
 * The Data usage tile: what went over Wi-Fi and over mobile data today; wide and large add the monthly average of each. Without the
 * usage access it says so (a touch opens that screen).
 */
@Composable
internal fun DataUseFace(item: AppItem, size: TileSize, width: Dp, height: Dp, usage: DataUsage?, allowed: Boolean) {
    if (usage == null) {
        NoteLineFace(item, size, width, height, if (allowed) null else stringResource(R.string.tile_usage_allow), "datause:hint")
        return
    }
    val locale = Locale.getDefault()
    fun bytes(value: Long?) = value?.let { StorageInfo.format(it, locale) } ?: "—"
    val wifi = stringResource(R.string.tile_data_wifi)
    val mobile = stringResource(R.string.tile_data_mobile)
    Box(Modifier.fillMaxSize()) {
        if (size == TileSize.Small) {
            MetroText(bytes(usage.wifiToday + (usage.mobileToday ?: 0L)), size = 22.sp, maxLines = 1, modifier = Modifier.align(Alignment.Center).testTag("datause:total"))
        } else {
            val extended = size != TileSize.Medium
            Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = NAME_ROOM), verticalArrangement = Arrangement.Center) {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(NAME_WIDTH))
                    MetroText(stringResource(R.string.tile_data_today), size = 13.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                    if (extended) MetroText(stringResource(R.string.tile_data_month), size = 13.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                }
                StatRow(wifi, listOfNotNull(bytes(usage.wifiToday), if (extended) bytes(usage.wifiMonthly) else null), "datause:wifi", NAME_WIDTH)
                StatRow(mobile, listOfNotNull(bytes(usage.mobileToday), if (extended) bytes(usage.mobileMonthly) else null), "datause:mobile", NAME_WIDTH)
            }
            TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}

private val NAME_WIDTH = 64.dp

/**
 * The Memory tile: what is used and what is free, with a bar; wide and large add what takes the space (apps, photos, videos, music, the
 * rest) as a bar in pieces with each size beside it. The pieces the phone would not say (no usage access, no media access) are left out.
 */
@Composable
internal fun MemoryFace(item: AppItem, size: TileSize, width: Dp, height: Dp, memory: MemoryStats?) {
    if (memory == null) {
        StaticFace(item, size, width, height)
        return
    }
    val locale = Locale.getDefault()
    val storage = memory.storage
    val digits = with(androidx.compose.ui.platform.LocalDensity.current) { (height * 0.2f).toSp() }
    Box(Modifier.fillMaxSize()) {
        if (size == TileSize.Small) {
            MetroText(StorageInfo.format(storage.freeBytes, locale), size = digits, maxLines = 1, modifier = Modifier.align(Alignment.Center).testTag("memoryuse:free"))
        } else if (size == TileSize.Medium) {
            Column(Modifier.align(Alignment.Center).padding(bottom = NAME_ROOM / 2), horizontalAlignment = Alignment.CenterHorizontally) {
                MetroText(
                    StorageInfo.format(memory.used, locale) + " " + stringResource(R.string.tile_mem_used), size = 20.sp, maxLines = 1,
                    modifier = Modifier.testTag("memoryuse:used"),
                )
                BatteryBar(storage.usedPercent, width * 0.6f)
                MetroText(
                    StorageInfo.format(storage.freeBytes, locale) + " " + stringResource(R.string.tile_mem_free), size = 20.sp, maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp).testTag("memoryuse:free"),
                )
            }
            TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        } else {
            val parts = listOf(
                Triple(R.string.tile_mem_apps, memory.apps, 0.95f), Triple(R.string.tile_mem_photos, memory.photos, 0.75f),
                Triple(R.string.tile_mem_videos, memory.videos, 0.6f), Triple(R.string.tile_mem_music, memory.music, 0.45f),
                Triple(R.string.tile_mem_other, memory.other, 0.3f),
            ).filter { it.second != null }
            Column(Modifier.fillMaxSize().padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = NAME_ROOM), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    MetroText(StorageInfo.format(memory.used, locale), size = digits, maxLines = 1, modifier = Modifier.testTag("memoryuse:used"))
                    MetroText(
                        " " + stringResource(R.string.tile_mem_used) + " · " + StorageInfo.format(storage.freeBytes, locale) + " " + stringResource(R.string.tile_mem_free),
                        size = 15.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp).testTag("memoryuse:free"),
                    )
                }
                val ink = LocalMetroColors.current.tileContent
                val total = parts.sumOf { it.second ?: 0L }.coerceAtLeast(1L)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(10.dp).testTag("memoryuse:bar")) {
                    parts.forEach { (_, bytes, alpha) ->
                        val share = (bytes ?: 0L).toFloat() / total
                        if (share > 0f) Box(Modifier.weight(share).fillMaxHeight().background(ink.copy(alpha = alpha)))
                    }
                }
                parts.forEach { (label, bytes, alpha) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp).testTag("memoryuse:part:$label")) {
                        Box(Modifier.size(10.dp).background(LocalMetroColors.current.tileContent.copy(alpha = alpha)))
                        MetroText(stringResource(label), size = 14.sp, weight = FontWeight.Normal, maxLines = 1, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        MetroText(StorageInfo.format(bytes ?: 0L, locale), size = 14.sp, maxLines = 1)
                    }
                }
            }
            TileLabel(item.label, Modifier.align(Alignment.BottomStart))
        }
    }
}
