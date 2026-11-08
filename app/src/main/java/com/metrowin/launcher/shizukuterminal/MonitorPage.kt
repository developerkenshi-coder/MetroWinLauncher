package com.metrowin.launcher.shizukuterminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
fun MonitorPage(state: ShizukuState, refresh: Int) {
    val context = LocalContext.current
    val cpu = remember { mutableStateListOf<Float>() }
    val ram = remember { mutableStateListOf<Float>() }
    val heat = remember { mutableStateListOf<Float>() }
    var sections by remember { mutableStateOf(emptyList<BatterySection>()) }
    val extra by produceState(Extra(), state, refresh) { value = withContext(Dispatchers.IO) { readExtra(state.ready) } }
    val latest by rememberUpdatedState(extra)
    val needs = stringResource(R.string.bd_needs_shizuku)
    LaunchedWhileStarted(state to refresh) {
        var before: List<DeviceMonitor.Ticks>? = null
        var round = 0
        var thermal = emptyMap<String, Double>()
        while (true) {
            val ready = state.ready
            val shell = if (ready) withContext(Dispatchers.IO) { ShizukuShell.shell(DeviceMonitor.SCRIPT, 64 * 1024).output } else ""
            if (ready && round % 2 == 0) thermal = withContext(Dispatchers.IO) { BatteryDumps.temperatures(ShizukuShell.shell("dumpsys thermalservice | grep 'Temperature{'").output) }
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
            sections = withContext(Dispatchers.IO) { build(context, ready, needs, usage, cores, mem, totalKb, availKb, thermal, latest) }
            round++
            delay(TICK_MS)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(key = "charts") {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                SeriesChart(stringResource(R.string.mo_chart_cpu), cpu.toList(), 0f, 100f, "%")
                SeriesChart(stringResource(R.string.mo_chart_ram), ram.toList(), 0f, 100f, "%")
                if (heat.size >= 2) SeriesChart(stringResource(R.string.mo_chart_heat), heat.toList(), heat.min() - 1f, heat.max() + 1f, "°C")
            }
        }
        infoSections(sections, "mon")
        item { androidx.compose.foundation.layout.Row(Modifier.padding(bottom = 24.dp)) {} }
    }
}

private fun pct(value: Float) = String.format(Locale.getDefault(), "%.0f %%", value)

private fun mb(kb: Long) = "${kb / 1024} MB"

private fun ghz(khz: Long?) = khz?.let { String.format(Locale.getDefault(), "%.2f GHz", it / 1_000_000f) } ?: "-"

private fun build(
    context: android.content.Context, ready: Boolean, needs: String, usage: List<Float>, cores: List<DeviceMonitor.Core>,
    mem: Map<String, Long>, totalKb: Long, availKb: Long, thermal: Map<String, Double>, extra: Extra,
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
        mem["Buffers"]?.let { add("Buffers" to mb(it)) }
        mem["Shmem"]?.let { add("Shmem" to mb(it)) }
        if (swapTotal > 0) add(s(R.string.mo_swap) to "${mb(swapTotal - (mem["SwapFree"] ?: 0))} / ${mb(swapTotal)}")
    }
    val thermalRows = if (!ready) listOf(s(R.string.mo_thermal) to needs) else thermal.map { (name, value) -> name to String.format(Locale.getDefault(), "%.1f °C", value) }
    val radioRows = extra.radio.map { line -> line.substringBefore('=') to line.substringAfter('=') }
    return listOf(
        BatterySection(s(R.string.mo_cpu), cpuRows),
        BatterySection(s(R.string.mo_ram), ramRows),
        BatterySection(s(R.string.mo_thermal), thermalRows),
        BatterySection(s(R.string.mo_display), DeviceReaders.display(context, extra.renderRate)),
        BatterySection(s(R.string.mo_wifi), DeviceReaders.wifi(context)),
        BatterySection(s(R.string.mo_sim), DeviceReaders.sim(context) + radioRows),
        BatterySection(s(R.string.mo_bluetooth), DeviceReaders.bluetooth(context) + extra.bluetooth.map { "" to it }),
    )
}
