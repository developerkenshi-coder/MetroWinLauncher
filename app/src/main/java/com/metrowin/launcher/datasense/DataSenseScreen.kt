package com.metrowin.launcher.datasense

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.MemoryStats
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.data.UsageAccess
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit

private fun bytes(value: Long) = StorageInfo.format(value, Locale.getDefault())

private fun hours(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

@Composable
fun DataSenseScreen() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(UsageAccess.granted(context)) }
    // The usage access is given in a screen of Android: when the user comes back it is read again.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = UsageAccess.granted(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val pager = rememberPagerState { 5 }
    val reader = remember { UsageReader(context) }
    val prefs = remember { context.getSharedPreferences("metrowin_datasense", Context.MODE_PRIVATE) }
    var limitMb by remember { mutableLongStateOf(prefs.getLong("limit_mb", 0L)) }
    var billDay by remember { mutableIntStateOf(prefs.getInt("bill_day", 1)) }
    var editingLimit by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    MiniFrame {
        PivotHeader(
            listOf(R.string.ds_data, R.string.ds_storage, R.string.ds_time, R.string.ds_battery, R.string.ds_network).map { stringResource(it) },
            pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
        )
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0) { page ->
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                if (!allowed && page != 3 && page != 4) {
                    PageNote(stringResource(R.string.ds_need_access))
                    MetroButton(stringResource(R.string.recorder_allow), Modifier.padding(top = 14.dp).testTag("ds:allow")) { UsageAccess.openSettings(context) }
                } else when (page) {
                    0 -> DataPage(reader, limitMb, billDay, refresh)
                    1 -> StoragePage(reader, refresh)
                    2 -> TimePage(reader, refresh)
                    3 -> BatteryPage(reader, refresh)
                    else -> NetworkPage(reader, refresh)
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("↻", stringResource(R.string.messages_refresh)) { refresh++ },
                AppBarItem("⚙", stringResource(R.string.ds_limit)) { editingLimit = true },
            ),
        )
    }
    if (editingLimit) {
        var text by remember { mutableStateOf(if (limitMb > 0) (limitMb / 1000.0).toString().removeSuffix(".0") else "") }
        var dayText by remember { mutableStateOf(billDay.toString()) }
        DialogFrame(onCancel = { editingLimit = false }) {
            MetroText(stringResource(R.string.ds_limit_title), size = 20.sp, weight = FontWeight.Normal)
            MetroTextField(text, { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) }, stringResource(R.string.ds_limit_hint), Modifier.testTag("ds:limit"), keyboardType = KeyboardType.Decimal)
            MetroTextField(dayText, { dayText = it.filter(Char::isDigit).take(2) }, stringResource(R.string.ds_billing_day), Modifier.testTag("ds:billday"), keyboardType = KeyboardType.Number)
            MetroButton(stringResource(R.string.todo_save), Modifier.fillMaxWidth().testTag("ds:limit:ok")) {
                billDay = (dayText.toIntOrNull() ?: 1).coerceIn(1, 28)
                prefs.edit { putInt("bill_day", billDay) }
                limitMb = ((text.replace(',', '.').toDoubleOrNull() ?: 0.0) * 1000).toLong().coerceIn(0, 100_000_000)
                prefs.edit { putLong("limit_mb", limitMb) }
                DataLimitWatch.schedule(context)
                editingLimit = false
            }
        }
    }
}

private val WifiColor = Color(0xFF1BA1E2)
private val MobileColor = Color(0xFFF09609)

@Composable
private fun DataPage(reader: UsageReader, limitMb: Long, billDay: Int, refresh: Int) {
    var period by remember { mutableStateOf(DataPeriod.Week) }
    var open by remember { mutableStateOf<Int?>(null) }
    val colors = LocalMetroColors.current
    val now = remember(period, refresh) { ZonedDateTime.now() }
    val data by produceState<Triple<List<Pair<DataBucket, Pair<Long, Long?>>>, List<AppData>, Long?>?>(null, period, refresh, billDay) {
        value = withContext(Dispatchers.IO) {
            val buckets = DataPeriods.buckets(period, now, billDay).map { it to reader.device(it.from, it.to) }
            val from = DataPeriods.start(period, now, billDay).toInstant().toEpochMilli()
            val monthStart = DataPeriods.start(DataPeriod.ThisMonth, now, billDay).toInstant().toEpochMilli()
            Triple(buckets, reader.apps(from, now.toInstant().toEpochMilli()), reader.device(monthStart, now.toInstant().toEpochMilli()).second)
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        items(DataPeriod.entries) { p ->
            PageChip(
                stringResource(
                    when (p) {
                        DataPeriod.Today -> R.string.ds_today
                        DataPeriod.Week -> R.string.ds_7days
                        DataPeriod.Month30 -> R.string.ds_30days
                        DataPeriod.ThisMonth -> if (billDay == 1) R.string.ds_this_month else R.string.ds_cycle
                    },
                ),
                period == p, "ds:period:${p.name}",
            ) { period = p; open = null }
        }
    }
    val loaded = data
    if (loaded == null) {
        PageNote(stringResource(R.string.rec_working))
        return
    }
    val (buckets, apps, mobileMonth) = loaded
    val wifi = buckets.sumOf { it.second.first }
    val mobile = buckets.sumOf { it.second.second ?: 0L }
    val total = wifi + mobile
    LazyColumn(Modifier.fillMaxSize().testTag("ds:data")) {
        item {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    MetroText("Wi-Fi", size = 15.sp, weight = FontWeight.Normal, color = WifiColor)
                    MetroText(bytes(wifi), size = 34.sp, maxLines = 1, modifier = Modifier.testTag("ds:wifi"))
                }
                Column(Modifier.weight(1f)) {
                    MetroText(stringResource(R.string.ds_mobile), size = 15.sp, weight = FontWeight.Normal, color = MobileColor)
                    MetroText(bytes(mobile), size = 34.sp, maxLines = 1, modifier = Modifier.testTag("ds:mobile"))
                }
            }
            Bar(DataPeriods.share(wifi, total), WifiColor, MobileColor, Modifier.padding(vertical = 10.dp))
            Chart(buckets.map { it.first.label to it.second }, Modifier.fillMaxWidth().height(110.dp).testTag("ds:chart"))
            if (limitMb > 0 && mobileMonth != null) {
                val used = mobileMonth
                val share = DataPeriods.share(used, limitMb * 1000_000L)
                MetroText(stringResource(R.string.ds_limit_used, bytes(used), bytes(limitMb * 1000_000L)), size = 15.sp, weight = FontWeight.Normal, color = if (share >= 1f) LocalAccent.current else colors.subtle, modifier = Modifier.padding(top = 12.dp).testTag("ds:limit:text"))
                Bar(share, if (share >= 0.9f) Color(0xFFE51400) else MobileColor, colors.surface, Modifier.padding(top = 4.dp))
            }
            MetroText(stringResource(R.string.ds_apps), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            if (apps.isEmpty()) PageNote(stringResource(R.string.ds_nothing))
        }
        items(apps, key = { it.uid }) { app ->
            Column(Modifier.fillMaxWidth().testTag("ds:app").metroPress(onClick = { open = if (open == app.uid) null else app.uid }).padding(vertical = 5.dp)) {
                Row {
                    MetroText(app.label, size = 19.sp, modifier = Modifier.weight(1f))
                    MetroText(bytes(app.total), size = 17.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
                Bar(DataPeriods.share(app.total, apps.first().total), WifiColor, colors.surface, Modifier.padding(top = 3.dp), height = 4)
                if (open == app.uid) {
                    MetroText(
                        stringResource(R.string.ds_app_detail, bytes(app.wifi), bytes(app.mobile), bytes(app.foreground), bytes(app.background), (DataPeriods.share(app.total, total) * 100).toInt()),
                        size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** A bar of two parts: [first] of its width in [firstColor], the rest in [restColor]. */
@Composable
private fun Bar(first: Float, firstColor: Color, restColor: Color, modifier: Modifier = Modifier, height: Int = 12) {
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        drawRect(restColor, Offset.Zero, size)
        drawRect(firstColor, Offset.Zero, Size(size.width * first, size.height))
    }
}

/** Bars side by side, each stacked: Wi-Fi below and mobile above. */
@Composable
private fun Chart(values: List<Pair<String, Pair<Long, Long?>>>, modifier: Modifier) {
    val colors = LocalMetroColors.current
    val top = (values.maxOfOrNull { it.second.first + (it.second.second ?: 0L) } ?: 0L).coerceAtLeast(1L)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f).background(colors.surface)) {
            val slot = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { i, (_, pair) ->
                val wifiH = size.height * pair.first / top
                val mobileH = size.height * (pair.second ?: 0L) / top
                drawRect(WifiColor, Offset(i * slot + slot * 0.15f, size.height - wifiH), Size(slot * 0.7f, wifiH))
                drawRect(MobileColor, Offset(i * slot + slot * 0.15f, size.height - wifiH - mobileH), Size(slot * 0.7f, mobileH))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val step = maxOf(1, values.size / 8)
            values.forEachIndexed { i, (label, _) -> Box(Modifier.weight(1f)) { if (i % step == 0) MetroText(label, size = 10.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1, minScale = 1f) } }
        }
    }
}

@Composable
private fun StoragePage(reader: UsageReader, refresh: Int) {
    val context = LocalContext.current
    val colors = LocalMetroColors.current
    val memory by produceState<MemoryStats?>(null, refresh) { value = withContext(Dispatchers.IO) { MemoryStats.read(context) } }
    val sizes by produceState<List<AppSize>?>(null, refresh) { value = withContext(Dispatchers.IO) { reader.appSizes() } }
    val m = memory
    if (m == null) { PageNote(stringResource(R.string.ds_nothing)); return }
    // A kind the phone would not measure (no permission) is shown as a dash, not as nothing.
    val parts = listOf(
        Triple(R.string.ds_apps, m.apps, Color(0xFF1BA1E2)), Triple(R.string.ds_photos, m.photos, Color(0xFFA4C400)),
        Triple(R.string.ds_videos, m.videos, Color(0xFFF472D0)), Triple(R.string.ds_music, m.music, Color(0xFFF09609)),
        Triple(R.string.ds_other, m.other, Color(0xFF8A8A8A)),
    )
    LazyColumn(Modifier.fillMaxSize().testTag("ds:storage")) {
        item {
            MetroText(bytes(m.used), size = 40.sp, maxLines = 1, modifier = Modifier.testTag("ds:used"))
            MetroText(stringResource(R.string.ds_of_total, bytes(m.storage.totalBytes), bytes(m.storage.freeBytes)), size = 16.sp, weight = FontWeight.Normal, color = colors.subtle)
            Canvas(Modifier.fillMaxWidth().height(22.dp).padding(vertical = 6.dp).background(colors.surface)) {
                var x = 0f
                parts.forEach { (_, size, color) ->
                    val w = this.size.width * DataPeriods.share(size ?: 0L, m.storage.totalBytes)
                    drawRect(color, Offset(x, 0f), Size(w, this.size.height))
                    x += w
                }
            }
            parts.forEach { (name, size, color) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(14.dp).background(color).padding(horizontal = 7.dp))
                    MetroText(stringResource(name), size = 18.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    MetroText(size?.let { bytes(it) } ?: "—", size = 18.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
            }
            val cache = sizes?.sumOf { it.cache } ?: 0L
            if (cache > 0) MetroText(stringResource(R.string.ds_cache, bytes(cache)), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(top = 8.dp))
            MetroText(stringResource(R.string.ds_biggest), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        }
        items(sizes.orEmpty(), key = { it.packageName }) { app ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row {
                    MetroText(app.label, size = 19.sp, modifier = Modifier.weight(1f))
                    MetroText(bytes(app.total), size = 17.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
                MetroText(stringResource(R.string.ds_app_sizes, bytes(app.app), bytes(app.data), bytes(app.cache)), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
            }
        }
    }
}

@Composable
private fun TimePage(reader: UsageReader, refresh: Int) {
    val colors = LocalMetroColors.current
    var days by remember { mutableIntStateOf(1) }
    val list by produceState<List<AppTime>?>(null, days, refresh) {
        value = withContext(Dispatchers.IO) {
            val now = ZonedDateTime.now()
            val from = now.toLocalDate().minusDays(days - 1L).atStartOfDay(now.zone).toInstant().toEpochMilli()
            reader.screenTime(from, now.toInstant().toEpochMilli())
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        PageChip(stringResource(R.string.ds_today), days == 1, "ds:time:1") { days = 1 }
        PageChip(stringResource(R.string.ds_7days), days == 7, "ds:time:7") { days = 7 }
        PageChip(stringResource(R.string.ds_30days), days == 30, "ds:time:30") { days = 30 }
    }
    val rows = list
    if (rows == null) { PageNote(stringResource(R.string.rec_working)); return }
    MetroText(hours(rows.sumOf { it.millis }), size = 36.sp, maxLines = 1, modifier = Modifier.testTag("ds:time:total"))
    MetroText(stringResource(R.string.ds_screen_time), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle, modifier = Modifier.padding(bottom = 8.dp))
    LazyColumn(Modifier.fillMaxSize().testTag("ds:time")) {
        items(rows, key = { it.packageName }) { app ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row {
                    MetroText(app.label, size = 19.sp, modifier = Modifier.weight(1f))
                    MetroText(hours(app.millis), size = 17.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
                Bar(DataPeriods.share(app.millis, rows.first().millis), WifiColor, colors.surface, Modifier.padding(top = 3.dp), height = 4)
                MetroText(
                    stringResource(R.string.ds_last_used, android.text.format.DateUtils.getRelativeTimeSpanString(app.lastUsed, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()),
                    size = 12.sp, weight = FontWeight.Normal, color = colors.subtle,
                )
            }
        }
    }
}

@Composable
private fun BatteryPage(reader: UsageReader, refresh: Int) {
    val colors = LocalMetroColors.current
    val reading by produceState<BatteryReading?>(null, refresh) { value = withContext(Dispatchers.IO) { reader.battery() } }
    val b = reading ?: return PageNote(stringResource(R.string.ds_nothing))
    MetroText("${b.percent}%", size = 64.sp, maxLines = 1, modifier = Modifier.testTag("ds:battery"))
    Bar(b.percent / 100f, if (b.percent <= 15 && !b.charging) Color(0xFFE51400) else Color(0xFFA4C400), colors.surface, Modifier.padding(bottom = 12.dp), height = 14)
    val health = stringResource(
        when (b.health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> R.string.ds_health_good
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.ds_health_hot
            BatteryManager.BATTERY_HEALTH_DEAD -> R.string.ds_health_dead
            BatteryManager.BATTERY_HEALTH_COLD -> R.string.ds_health_cold
            else -> R.string.ds_unknown
        },
    )
    val rows = listOfNotNull(
        stringResource(R.string.ds_status) to stringResource(if (b.charging) R.string.ds_charging else R.string.ds_discharging) + (if (b.plugged.isNotEmpty()) " · ${b.plugged}" else ""),
        stringResource(R.string.ds_temperature) to "%.1f °C".format(b.celsius),
        stringResource(R.string.ds_voltage) to "${b.millivolts} mV",
        stringResource(R.string.ds_health) to health,
        b.technology.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.ds_technology) to it },
        b.microAmps?.let { stringResource(R.string.ds_current) to "${kotlin.math.abs(it) / 1000} mA" },
        b.microAmpHours?.let { stringResource(R.string.ds_charge) to "${it / 1000} mAh" },
    )
    rows.forEach { (k, v) -> InfoRow(k, v) }
}

@Composable
private fun NetworkPage(reader: UsageReader, refresh: Int) {
    val reading by produceState<NetworkReading?>(null, refresh) { value = withContext(Dispatchers.IO) { reader.network() } }
    val n = reading ?: return PageNote(stringResource(R.string.ds_offline), Modifier.testTag("ds:offline"))
    MetroText(n.transport, size = 48.sp, maxLines = 1, modifier = Modifier.testTag("ds:transport"))
    val yes = stringResource(R.string.ds_yes)
    val no = stringResource(R.string.ds_no)
    listOfNotNull(
        n.operator.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.ds_operator) to it },
        stringResource(R.string.ds_down) to "${n.downKbps / 1000} Mb/s",
        stringResource(R.string.ds_up) to "${n.upKbps / 1000} Mb/s",
        stringResource(R.string.ds_metered) to if (n.metered) yes else no,
        stringResource(R.string.ds_roaming) to if (n.roaming) yes else no,
        stringResource(R.string.ds_vpn) to if (n.vpn) yes else no,
        stringResource(R.string.ds_internet) to if (n.validated) yes else no,
    ).forEach { (k, v) -> InfoRow(k, v) }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        MetroText(key, size = 18.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(1f))
        MetroText(value, size = 20.sp, maxLines = 1)
    }
}
