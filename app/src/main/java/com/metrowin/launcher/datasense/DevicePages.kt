package com.metrowin.launcher.datasense

import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatteryHistory
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BatteryPage(reader: UsageReader, refresh: Int) {
    val context = LocalContext.current
    val options = LocalDsOptions.current
    val colors = LocalMetroColors.current
    val p = rememberPalette()
    val reading by produceState<BatteryReading?>(null, refresh) {
        value = withContext(Dispatchers.IO) {
            if (options[Opt.History]) BatteryHistory.record(context, 15 * 60_000L)
            reader.battery()
        }
    }
    val history by produceState<List<com.metrowin.launcher.data.BatterySample>>(emptyList(), refresh, options[Opt.History]) {
        value = if (options[Opt.History]) withContext(Dispatchers.IO) { BatteryHistory.read(context, System.currentTimeMillis() - 24 * 3_600_000L) } else emptyList()
    }
    val b = reading
    if (b == null) {
        if (!options[Opt.Chart]) PageNote(stringResource(R.string.ds_nothing)) else Skeleton(rows = 4)
        return
    }
    val low = b.percent <= 15 && !b.charging
    val level = if (low) p.warn else p.parts[1]
    val health = stringResource(
        when (b.health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> R.string.ds_health_good
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.ds_health_hot
            BatteryManager.BATTERY_HEALTH_DEAD -> R.string.ds_health_dead
            BatteryManager.BATTERY_HEALTH_COLD -> R.string.ds_health_cold
            else -> R.string.ds_unknown
        },
    )
    val plugged = when (b.plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> stringResource(R.string.dsx_ac)
        BatteryManager.BATTERY_PLUGGED_USB -> stringResource(R.string.dsx_usb)
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> stringResource(R.string.dsx_wireless)
        else -> ""
    }
    val rows = listOfNotNull(
        stringResource(R.string.ds_status) to stringResource(if (b.charging) R.string.ds_charging else R.string.ds_discharging) + (if (plugged.isNotEmpty()) " · $plugged" else ""),
        stringResource(R.string.ds_temperature) to "%.1f °C".format(b.celsius),
        stringResource(R.string.ds_voltage) to stringResource(R.string.dsx_mv, b.millivolts),
        stringResource(R.string.ds_health) to health,
        b.technology.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.ds_technology) to it },
        b.microAmps?.let { stringResource(R.string.ds_current) to stringResource(R.string.dsx_ma, (kotlin.math.abs(it) / 1000).toInt()) },
        b.microAmpHours?.let { stringResource(R.string.ds_charge) to stringResource(R.string.dsx_mah, (it / 1000).toInt()) },
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (options[Opt.Chart]) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                Gauge(b.percent / 100f, level, "${b.percent}%", stringResource(R.string.dsx_gauge_battery, b.percent), Modifier.testTag("ds:battery"))
            }
        } else {
            // The big number shrinks when the text of the phone is large, instead of running over.
            MetroText("${b.percent}%", size = 64.sp, maxLines = 1, minScale = 0.5f, modifier = Modifier.testTag("ds:battery"))
            Bar(b.percent / 100f, level, colors.surface, Modifier.padding(bottom = 12.dp), height = 14.dp)
        }
        (if (b.charging) b.minutesToFull?.let { stringResource(R.string.dsx_to_full, com.metrowin.launcher.data.BatteryInfo.duration(it)) } else b.minutesLeft?.let { stringResource(R.string.dsx_left, com.metrowin.launcher.data.BatteryInfo.duration(it)) })
            ?.let { MetroText(it, size = 16.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(bottom = 6.dp)) }
        rows.forEach { (k, v) -> InfoRow(k, v) }
        if (options[Opt.History]) {
            MetroText(stringResource(R.string.dsx_battery_chart), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            if (history.size < 2) PageNote(stringResource(R.string.dsx_battery_wait))
            else Canvas(Modifier.fillMaxWidth().height(90.dp).background(colors.surface)) {
                val start = history.first().time.toFloat()
                val span = (history.last().time - history.first().time).coerceAtLeast(1L).toFloat()
                var previous: androidx.compose.ui.geometry.Offset? = null
                var previousTime = 0L
                history.forEach { s ->
                    val at = Offset(size.width * (s.time - start) / span, size.height * (1f - s.level / 100f))
                    // A gap between readings (phone off, history off) is not drawn as a slope.
                    if (previous != null && s.time - previousTime <= BatteryHistory.GAP) drawLine(if (s.charging) p.parts[1] else p.wifi, previous!!, at, 2.dp.toPx())
                    previous = at
                    previousTime = s.time
                }
            }
        }
    }
}

@Composable
internal fun NetworkPage(reader: UsageReader, refresh: Int, liveTick: Int) {
    val context = LocalContext.current
    val options = LocalDsOptions.current
    val reading by produceState<NetworkReading?>(null, refresh) { value = withContext(Dispatchers.IO) { reader.network() } }
    // The speed measured: what the phone moved since the last look, over the seconds between the two.
    var last by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    val speed by produceState<Pair<Double, Double>?>(null, liveTick, options[Opt.Live]) {
        if (!options[Opt.Live]) { value = null; return@produceState }
        val now = Triple(TrafficStats.getTotalRxBytes(), TrafficStats.getTotalTxBytes(), System.nanoTime())
        val before = last
        last = now
        if (before != null && now.first >= 0 && before.first >= 0) {
            val seconds = (now.third - before.third) / 1e9
            if (seconds > 0.2) value = ((now.first - before.first) * 8 / seconds / 1e6) to ((now.second - before.second) * 8 / seconds / 1e6)
        }
    }
    val saver = remember(refresh) {
        runCatching { context.getSystemService(ConnectivityManager::class.java).restrictBackgroundStatus }.getOrDefault(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED)
    }
    val n = reading
    if (n == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (options[Opt.Chart]) EmptyArt("×", stringResource(R.string.ds_offline), Modifier.testTag("ds:offline"))
            else PageNote(stringResource(R.string.ds_offline), Modifier.testTag("ds:offline"))
        }
        return
    }
    val yes = stringResource(R.string.ds_yes)
    val no = stringResource(R.string.ds_no)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MetroText(transportName(n.transport), size = 48.sp, maxLines = 1, minScale = 0.5f, modifier = Modifier.testTag("ds:transport"))
        listOfNotNull(
            n.operator.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.ds_operator) to it },
            stringResource(R.string.ds_down) to stringResource(R.string.dsx_mbps, n.downKbps / 1000),
            stringResource(R.string.ds_up) to stringResource(R.string.dsx_mbps, n.upKbps / 1000),
            speed?.let { stringResource(R.string.dsx_measured) to "↓ %.1f · ↑ %.1f Mbit/s".format(it.first, it.second) },
            stringResource(R.string.ds_metered) to if (n.metered) yes else no,
            stringResource(R.string.ds_roaming) to if (n.roaming) yes else no,
            stringResource(R.string.ds_vpn) to if (n.vpn) yes else no,
            stringResource(R.string.ds_internet) to if (n.validated) yes else no,
            stringResource(R.string.dsx_saver) to stringResource(
                when (saver) {
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> R.string.dsx_saver_on
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> R.string.dsx_saver_exempt
                    else -> R.string.dsx_saver_off
                },
            ),
        ).forEach { (k, v) -> InfoRow(k, v) }
        MetroButton(stringResource(R.string.dsx_saver), Modifier.padding(top = 10.dp).testTag("ds:saver")) { openDataSaver(context) }
    }
}
