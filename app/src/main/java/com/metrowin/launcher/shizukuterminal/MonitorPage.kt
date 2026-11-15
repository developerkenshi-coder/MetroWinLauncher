package com.metrowin.launcher.shizukuterminal

import com.metrowin.launcher.ui.components.isWide
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatteryDumps
import com.metrowin.launcher.data.BatterySection
import com.metrowin.launcher.data.DeviceMonitor
import com.metrowin.launcher.data.DeviceReaders
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private const val KEEP = 60
private const val TICK_MS = 2_000L

/** What the shell gives once per refresh and the page keeps: lines too slow or too long for every tick. */
private class Extra(val renderRate: String? = null, val radio: List<String> = emptyList(), val bluetooth: List<String> = emptyList())

private fun readExtra(ready: Boolean): Extra {
    if (!ready) return Extra()
    val render = ShizukuShell.shell("dumpsys display | grep -m1 mActiveRenderFrameRate").output.substringAfter('=', "").trim().takeIf { it.isNotEmpty() }?.toFloatOrNull()
    val radio = ShizukuShell.shell(
        "dumpsys telephony.registry | grep -o -E '(rsrp|rsrq|rssnr|ssRsrp|ssRsrq|csiRsrp|mChannelNumber|mNrState|mCellBandwidths|mBands)=[^ ,}]+' | awk '!seen[\$0]++' | head -20",
    ).output.lines().filter { it.contains('=') }
    val bluetooth = ShizukuShell.shell("dumpsys bluetooth_manager | grep -i -E 'codec|battery|connected' | cut -c1-140 | head -30").output.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return Extra(render?.let { String.format(Locale.getDefault(), "%.1f", it) }, radio, bluetooth)
}

/** "Sensors and monitor": the processor, the memory and the heat live, the screen, the Wi-Fi, the SIM and the Bluetooth. */
@Composable
internal fun MonitorPage(state: ShizukuState, refresh: Int, buffers: MonitorBuffers, opts: TerminalOptions) {
    val context = LocalContext.current
    val cpu = buffers.cpu
    val ram = buffers.ram
    val heat = buffers.heat
    val extra by produceState(Extra(), state, refresh) { value = withContext(Dispatchers.IO) { readExtra(state.ready) } }
    val latest by rememberUpdatedState(extra)
    val needs = stringResource(R.string.bd_needs_shizuku)
    val withProcesses by rememberUpdatedState(opts.processes.on)
    val processTitle = stringResource(R.string.tm_processes)
    LaunchedWhileStarted(state to refresh) {
        var before: List<DeviceMonitor.Ticks>? = null
        var round = 0
        var thermal = emptyMap<String, Double>()
        var processes = emptyList<ProcessRow>()
        while (true) {
            val ready = state.ready
            val shell = if (ready) withContext(Dispatchers.IO) { ShizukuShell.shell(DeviceMonitor.SCRIPT, 64 * 1024).output } else ""
            if (ready && round % 2 == 0) thermal = withContext(Dispatchers.IO) { BatteryDumps.temperatures(ShizukuShell.shell("dumpsys thermalservice | grep 'Temperature{'").output) }
            if (ready && withProcesses && round % 2 == 0) processes = withContext(Dispatchers.IO) { parseTop(ShizukuShell.shell("top -b -n 1 -m 12", 64 * 1024).output).take(10) }
            if (!withProcesses) processes = emptyList()
            val parts = shell.split("==")
            val ticks = DeviceMonitor.ticks(parts.getOrElse(0) { "" })
            val usage = before?.takeIf { it.size == ticks.size && ticks.isNotEmpty() }?.let { DeviceMonitor.usage(it, ticks) }.orEmpty()
            before = ticks
            val mem = DeviceMonitor.memInfo(parts.getOrElse(1) { "" })
            val cores = DeviceMonitor.cores(parts.getOrElse(2) { "" })
            usage.firstOrNull()?.let { cpu.add(it); while (cpu.size > KEEP) cpu.removeAt(0) }
            val info = DeviceReaders.memory(context)
            val totalKb = mem["MemTotal"] ?: (info.totalMem / 1024)
            val availKb = mem["MemAvailable"] ?: (info.availMem / 1024)
            if (totalKb > 0) ram.add((totalKb - availKb) * 100f / totalKb).also { while (ram.size > KEEP) ram.removeAt(0) }
            thermal.values.maxOrNull()?.let { heat.add(it.toFloat()); while (heat.size > KEEP) heat.removeAt(0) }
            buffers.sections = withContext(Dispatchers.IO) { build(context, ready, needs, usage, cores, mem, totalKb, availKb, thermal, latest, processes, processTitle) }
            round++
            delay(TICK_MS)
        }
    }
    val wide = isWide()
    val smooth = opts.smooth.on
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (opts.cards.on) item(key = "cards") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Gauge(stringResource(R.string.mo_chart_cpu), cpu.lastOrNull(), 100f, "%", Modifier.weight(1f))
                Gauge(stringResource(R.string.mo_chart_ram), ram.lastOrNull(), 100f, "%", Modifier.weight(1f))
                Gauge(stringResource(R.string.mo_chart_heat), heat.lastOrNull(), 80f, "°C", Modifier.weight(1f))
            }
        }
        item(key = "charts") {
            val cpuChart = @Composable { m: Modifier -> SeriesChart(stringResource(R.string.mo_chart_cpu), cpu.toList(), 0f, 100f, "%", m, smooth) }
            val ramChart = @Composable { m: Modifier -> SeriesChart(stringResource(R.string.mo_chart_ram), ram.toList(), 0f, 100f, "%", m, smooth) }
            val heatChart = @Composable { m: Modifier -> if (heat.size >= 2) SeriesChart(stringResource(R.string.mo_chart_heat), heat.toList(), heat.min() - 1f, heat.max() + 1f, "°C", m, smooth) }
            if (wide) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cpuChart(Modifier.weight(1f)); ramChart(Modifier.weight(1f)); heatChart(Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                cpuChart(Modifier); ramChart(Modifier); heatChart(Modifier)
            }
        }
        infoSections(buffers.sections, "mon", opts.sticky.on)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** A big value in an arc: how full the processor, the memory or the heat is. */
@Composable
private fun Gauge(title: String, value: Float?, max: Float, unit: String, modifier: Modifier) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val shown = value?.let { String.format(Locale.getDefault(), "%.0f %s", it, unit) } ?: "–"
    Box(modifier.background(colors.surface).padding(vertical = 10.dp).semantics(mergeDescendants = true) { contentDescription = "$title $shown" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
            val inset = 4.dp.toPx()
            val box = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
            drawArc(colors.inactive, 135f, 270f, false, androidx.compose.ui.geometry.Offset(inset, inset), box, style = stroke)
            if (value != null) drawArc(accent, 135f, 270f * (value / max).coerceIn(0f, 1f), false, androidx.compose.ui.geometry.Offset(inset, inset), box, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MetroText(shown, size = 18.sp, weight = FontWeight.SemiBold)
            MetroText(title, size = 11.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1)
        }
    }
}

private fun pct(value: Float) = String.format(Locale.getDefault(), "%.0f %%", value)

private fun mb(kb: Long) = "${kb / 1024} MB"

private fun ghz(khz: Long?) = khz?.let { String.format(Locale.getDefault(), "%.2f GHz", it / 1_000_000f) } ?: "-"

private fun build(
    context: android.content.Context, ready: Boolean, needs: String, usage: List<Float>, cores: List<DeviceMonitor.Core>,
    mem: Map<String, Long>, totalKb: Long, availKb: Long, thermal: Map<String, Double>, extra: Extra,
    processes: List<ProcessRow>, processTitle: String,
): List<BatterySection> {
    fun s(id: Int) = context.getString(id)
    val cpuRows = if (!ready) listOf(s(R.string.mo_cpu) to needs) else buildList {
        usage.firstOrNull()?.let { add(s(R.string.mo_cpu_load) to pct(it)) }
        cores.forEachIndexed { i, core ->
            val load = usage.getOrNull(i + 1)?.let { pct(it) } ?: "-"
            add(core.name to if (!core.online) s(R.string.mo_core_off) else "$load · ${ghz(core.cur)} (${ghz(core.min)} – ${ghz(core.max)}) · ${core.governor}")
        }
    }
    val swapTotal = mem["SwapTotal"] ?: 0
    val ramRows = buildList {
        add(s(R.string.mo_ram_total) to mb(totalKb))
        add(s(R.string.mo_ram_used) to "${mb(totalKb - availKb)} (${pct((totalKb - availKb) * 100f / totalKb.coerceAtLeast(1))})")
        add(s(R.string.mo_ram_free) to mb(availKb))
        mem["Cached"]?.let { add(s(R.string.mo_cached) to mb(it)) }
        mem["Buffers"]?.let { add(s(R.string.tm_buffers) to mb(it)) }
        mem["Shmem"]?.let { add(s(R.string.tm_shmem) to mb(it)) }
        if (swapTotal > 0) add(s(R.string.mo_swap) to "${mb(swapTotal - (mem["SwapFree"] ?: 0))} / ${mb(swapTotal)}")
    }
    val thermalRows = if (!ready) listOf(s(R.string.mo_thermal) to needs) else thermal.map { (name, value) -> name to String.format(Locale.getDefault(), "%.1f °C", value) }
    val radioRows = extra.radio.map { line -> line.substringBefore('=') to line.substringAfter('=') }
    return listOf(
        BatterySection(s(R.string.mo_cpu), cpuRows),
        BatterySection(s(R.string.mo_ram), ramRows),
        BatterySection(s(R.string.mo_thermal), thermalRows),
        BatterySection(processTitle, processes.map { it.name to "${pct(it.cpu)} · ${pct(it.mem)} · ${it.pid}" }),
        BatterySection(s(R.string.mo_display), DeviceReaders.display(context, extra.renderRate)),
        BatterySection(s(R.string.mo_wifi), DeviceReaders.wifi(context)),
        BatterySection(s(R.string.mo_sim), DeviceReaders.sim(context) + radioRows),
        BatterySection(s(R.string.mo_bluetooth), DeviceReaders.bluetooth(context) + extra.bluetooth.map { "" to it }),
    )
}
