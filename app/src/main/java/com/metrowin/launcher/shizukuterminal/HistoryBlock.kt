package com.metrowin.launcher.shizukuterminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatteryHistory
import com.metrowin.launcher.data.BatterySample
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.settings.ToggleSetting
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private class Range(val label: Int, val ms: Long)

private val RANGES = listOf(Range(R.string.tm_24h, 24L * 3_600_000), Range(R.string.bh_7d, 7L * 86_400_000), Range(R.string.bh_30d, 30L * 86_400_000))

/** A point of a chart: when, the value, and whether the phone was charging (the line is lit then). */
internal class Point(val time: Long, val value: Float, val charging: Boolean)

internal fun one(value: Float) = String.format(Locale.getDefault(), "%.1f", value)

/** The history of the battery: what the readings say, three charts, and the switch of the recording. */
@Composable
internal fun HistoryBlock(refresh: Int, beat: Int, opts: TerminalOptions) {
    val context = LocalContext.current
    var range by remember { mutableIntStateOf(0) }
    var on by remember { mutableStateOf(BatteryHistory.enabled(context)) }
    var cleared by remember { mutableIntStateOf(0) }
    // A reading whenever the doctor opens (and the job of Android planned), unless the last one is only a couple of minutes old.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (BatteryHistory.enabled(context)) {
                BatteryHistory.schedule(context)
                BatteryHistory.record(context, 120_000)
            }
        }
    }
    // What was read for each range stays here: going back to a range shows it at once, and the new reading replaces it a moment later.
    val kept = remember { HashMap<Int, List<BatterySample>>() }
    val samples by produceState(kept[range].orEmpty(), range, beat / 6, refresh, cleared, on) {
        if (kept[range] != null) value = kept.getValue(range)
        value = withContext(Dispatchers.IO) { BatteryHistory.read(context, System.currentTimeMillis() - RANGES[range].ms) }
        kept[range] = value
    }
    val now = System.currentTimeMillis()
    val from = now - RANGES[range].ms
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetroText(stringResource(R.string.bh_title), size = 24.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RANGES.indices.toList()) { i ->
                PageChip(stringResource(RANGES[i].label), i == range, "st:range:$i") { range = i }
            }
        }
        if (samples.size < 2) {
            PageNote(stringResource(if (on) R.string.bh_empty else R.string.bh_off))
        } else {
            val s = BatteryHistory.summary(samples)
            s.drainPerHour?.let { KeyValueRow(stringResource(R.string.bh_drain), "${one(it)} %/h") }
            s.hoursOnFull?.let { KeyValueRow(stringResource(R.string.bh_autonomy), "${one(it)} h") }
            s.chargePerHour?.let { KeyValueRow(stringResource(R.string.bh_charge_rate), "${one(it)} %/h") }
            s.peakWatts?.let { KeyValueRow(stringResource(R.string.bh_peak_power), "${one(it)} W") }
            KeyValueRow(stringResource(R.string.bh_charges), s.charges.toString())
            s.tempMax?.let { KeyValueRow(stringResource(R.string.bh_temp_max), "${one(it)} °C") }
            s.tempAvg?.let { KeyValueRow(stringResource(R.string.bh_temp_avg), "${one(it)} °C") }
            s.capacityMah?.let { KeyValueRow(stringResource(R.string.bh_capacity), "${it.toInt()} mAh") }
            KeyValueRow(stringResource(R.string.bh_samples), samples.size.toString())
            val temps = samples.map { it.tenths / 10f }
            val capacity = BatteryHistory.capacityByDay(samples)
            val wide = LocalConfiguration.current.screenWidthDp >= 600
            val level = @Composable { m: Modifier ->
                Chart(stringResource(R.string.bh_chart_level), samples.map { Point(it.time, it.level.toFloat(), it.charging) }, from, now, 0f, 100f, "%", BatteryHistory.GAP, m)
            }
            val temp = @Composable { m: Modifier ->
                Chart(
                    stringResource(R.string.bh_chart_temp), samples.map { Point(it.time, it.tenths / 10f, it.charging) }, from, now,
                    temps.min() - 1f, temps.max() + 1f, "°C", BatteryHistory.GAP, m,
                )
            }
            val cap = @Composable { m: Modifier ->
                val values = capacity.map { it.second }
                Chart(
                    stringResource(R.string.bh_chart_capacity), capacity.map { Point(it.first, it.second, false) }, from, now,
                    values.min() * 0.98f - 1f, values.max() * 1.02f + 1f, "mAh", 3 * 86_400_000L, m,
                )
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    level(Modifier.weight(1f))
                    temp(Modifier.weight(1f))
                    if (capacity.size >= 2) cap(Modifier.weight(1f))
                }
            } else {
                level(Modifier)
                temp(Modifier)
                if (capacity.size >= 2) cap(Modifier)
            }
        }
        ToggleSetting(
            label = stringResource(R.string.bh_recording), checked = on,
            onChange = { value -> BatteryHistory.setEnabled(context, value); on = value },
            hint = stringResource(R.string.bh_recording_hint), tag = "st:history",
        )
        MetroButton(stringResource(R.string.bh_clear)) { BatteryHistory.clear(context); cleared++ }
    }
}

/**
 * A line chart with a grid, the lowest and highest value at its side, and a reading where it is touched (the point nearest to the finger, with its time).
 * With [smoothed] the line is a curve through the points, in one colour (the series of the Monitor); the battery keeps its lit stretches.
 */
@Composable
internal fun Chart(
    title: String, points: List<Point>, from: Long, to: Long, lo: Float, hi: Float, unit: String, gap: Long,
    modifier: Modifier = Modifier, smoothed: Boolean = false, stamp: ((Point) -> String)? = null,
) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val low = points.minOf { it.value }
    val high = points.maxOf { it.value }
    var touched by remember(points.size) { mutableStateOf<Int?>(null) }
    val picked = touched?.let { points.getOrNull(it) }
    val summary = "$title ${one(low)} – ${one(high)} $unit"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetroText(title, size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
            MetroText(
                if (picked != null) "${stamp?.invoke(picked) ?: defaultStamp(picked, from)}${one(picked.value)} $unit" else "${one(low)} – ${one(high)} $unit",
                size = 14.sp, weight = FontWeight.Normal, color = if (picked != null) accent else colors.subtle,
            )
        }
        Box(Modifier.fillMaxWidth().height(110.dp).background(colors.surface).semantics { contentDescription = summary }) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(points.size) {
                    detectTapGestures { tap -> val i = nearestIndexByTime(points, tap.x, size.width.toFloat(), from, to); touched = if (touched == i) null else i }
                },
            ) {
                val pad = 6.dp.toPx()
                fun at(p: Point) = Offset(
                    pad + (p.time - from).coerceAtLeast(0L).toFloat() / (to - from).coerceAtLeast(1L) * (size.width - 2 * pad),
                    size.height - pad - (p.value - lo) / (hi - lo).coerceAtLeast(0.001f) * (size.height - 2 * pad),
                )
                // The grid: the top, the middle and the bottom of the range.
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = pad + f * (size.height - 2 * pad)
                    drawLine(colors.inactive, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                if (smoothed && points.size > 2) {
                    val path = Path()
                    val xy = points.map(::at)
                    path.moveTo(xy[0].x, xy[0].y)
                    for (i in 1 until xy.size - 1) path.quadraticTo(xy[i].x, xy[i].y, (xy[i].x + xy[i + 1].x) / 2, (xy[i].y + xy[i + 1].y) / 2)
                    path.lineTo(xy.last().x, xy.last().y)
                    drawPath(path, colors.foreground, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                } else {
                    points.zipWithNext().forEach { (a, b) ->
                        if (b.time - a.time <= gap) drawLine(if (b.charging) accent else colors.foreground, at(a), at(b), strokeWidth = 3.dp.toPx())
                    }
                    if (points.size < 40) points.forEach { drawCircle(if (it.charging) accent else colors.foreground, 3.dp.toPx(), at(it)) }
                }
                picked?.let { p ->
                    val o = at(p)
                    drawLine(accent, Offset(o.x, 0f), Offset(o.x, size.height), strokeWidth = 1.dp.toPx())
                    drawCircle(accent, 5.dp.toPx(), o)
                }
            }
            MetroText(one(hi), size = 11.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp))
            MetroText(one(lo), size = 11.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp))
        }
    }
}

/** The time of a reading of the battery ("12:30 · "), or nothing for a series that has no clock. */
private fun defaultStamp(p: Point, from: Long): String =
    if (from > 1_000_000_000_000L) android.text.format.DateFormat.format("d/M HH:mm", p.time).toString() + " · " else ""

/** The point whose time is nearest to the one under the finger. */
private fun nearestIndexByTime(points: List<Point>, x: Float, width: Float, from: Long, to: Long): Int {
    val t = from + (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f) * (to - from)
    return points.indices.minByOrNull { Math.abs(points[it].time - t) } ?: 0
}

/** A chart of a series read a moment apart (the monitor): the oldest value on the left, the newest on the right; a touch says how many seconds ago. */
@Composable
internal fun SeriesChart(title: String, values: List<Float>, lo: Float, hi: Float, unit: String, modifier: Modifier = Modifier, smoothed: Boolean = false, stepSeconds: Int = 2) {
    if (values.size < 2) return
    val last = values.size - 1
    Chart(
        title, values.mapIndexed { i, v -> Point(i.toLong(), v, false) }, 0L, last.toLong(), lo, hi, unit, Long.MAX_VALUE, modifier, smoothed,
        stamp = { p -> "-${(last - p.time.toInt()) * stepSeconds} s · " },
    )
}
