package com.metrowin.launcher.shizukuterminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
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

private class Range(val label: Int?, val text: String, val ms: Long)

private val RANGES = listOf(Range(null, "24 h", 24L * 3_600_000), Range(R.string.bh_7d, "", 7L * 86_400_000), Range(R.string.bh_30d, "", 30L * 86_400_000))

/** A point of a chart: when, the value, and whether the phone was charging (the line is lit then). */
internal class Point(val time: Long, val value: Float, val charging: Boolean)

internal fun one(value: Float) = String.format(Locale.getDefault(), "%.1f", value)

/** The history of the battery: what the readings say, three charts, and the switch of the recording. */
@Composable
fun HistoryBlock(refresh: Int, beat: Int) {
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
    val samples by produceState(emptyList<BatterySample>(), range, beat / 6, refresh, cleared, on) {
        value = withContext(Dispatchers.IO) { BatteryHistory.read(context, System.currentTimeMillis() - RANGES[range].ms) }
    }
    val now = System.currentTimeMillis()
    val from = now - RANGES[range].ms
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetroText(stringResource(R.string.bh_title), size = 24.sp, color = LocalAccent.current, modifier = Modifier.padding(top = 10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RANGES.indices.toList()) { i ->
                PageChip(RANGES[i].label?.let { stringResource(it) } ?: RANGES[i].text, i == range, "st:range:$i") { range = i }
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
            Chart(stringResource(R.string.bh_chart_level), samples.map { Point(it.time, it.level.toFloat(), it.charging) }, from, now, 0f, 100f, "%", BatteryHistory.GAP)
            val temps = samples.map { it.tenths / 10f }
            Chart(
                stringResource(R.string.bh_chart_temp), samples.map { Point(it.time, it.tenths / 10f, it.charging) }, from, now,
                temps.min() - 1f, temps.max() + 1f, "°C", BatteryHistory.GAP,
            )
            val capacity = BatteryHistory.capacityByDay(samples)
            if (capacity.size >= 2) {
                val values = capacity.map { it.second }
                Chart(
                    stringResource(R.string.bh_chart_capacity), capacity.map { Point(it.first, it.second, false) }, from, now,
                    values.min() * 0.98f - 1f, values.max() * 1.02f + 1f, "mAh", 3 * 86_400_000L,
                )
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

@Composable
internal fun Chart(title: String, points: List<Point>, from: Long, to: Long, lo: Float, hi: Float, unit: String, gap: Long) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val low = points.minOf { it.value }
    val high = points.maxOf { it.value }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetroText(title, size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
            MetroText("${one(low)} – ${one(high)} $unit", size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
        }
        Canvas(Modifier.fillMaxWidth().height(110.dp).background(colors.surface)) {
            val pad = 6.dp.toPx()
            fun at(p: Point) = Offset(
                pad + (p.time - from).coerceAtLeast(0L).toFloat() / (to - from).coerceAtLeast(1L) * (size.width - 2 * pad),
                size.height - pad - (p.value - lo) / (hi - lo).coerceAtLeast(0.001f) * (size.height - 2 * pad),
            )
            points.zipWithNext().forEach { (a, b) ->
                if (b.time - a.time <= gap) drawLine(if (b.charging) accent else colors.foreground, at(a), at(b), strokeWidth = 3.dp.toPx())
            }
            if (points.size < 40) points.forEach { drawCircle(if (it.charging) accent else colors.foreground, 3.dp.toPx(), at(it)) }
        }
    }
}

/** A chart of a series read a moment apart (the monitor): the oldest value on the left, the newest on the right. */
@Composable
internal fun SeriesChart(title: String, values: List<Float>, lo: Float, hi: Float, unit: String) {
    if (values.size < 2) return
    Chart(title, values.mapIndexed { i, v -> Point(i.toLong(), v, false) }, 0L, (values.size - 1).toLong(), lo, hi, unit, Long.MAX_VALUE)
}
