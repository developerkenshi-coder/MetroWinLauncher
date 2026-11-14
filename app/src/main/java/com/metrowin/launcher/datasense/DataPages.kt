package com.metrowin.launcher.datasense

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.featherRow
import com.metrowin.launcher.ui.components.scrollIndicator
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** What the data page read for one period; kept for a while so that coming back shows it at once while the new reading is made. */
class DataLoad(
    val period: DataPeriod,
    val bars: List<Pair<DataBucket, Pair<Long, Long?>>>,
    val apps: List<AppData>,
    val cycleMobile: Long?,
    val before: Pair<Long, Long?>?,
    val hours: LongArray?,
)

private val dataCache = HashMap<String, DataLoad>()

/** The last reading shown, for the export. */
internal var latestLoad: DataLoad? = null

private enum class NetFilter { All, Wifi, Mobile }

private fun load(context: android.content.Context, reader: UsageReader, period: DataPeriod, billDay: Int, trends: Boolean, hourly: Boolean): DataLoad {
    val now = ZonedDateTime.now()
    val nowMs = now.toInstant().toEpochMilli()
    val monthStart = DataPeriods.start(DataPeriod.ThisMonth, now, billDay).toInstant().toEpochMilli()
    val cycleMobile = reader.device(monthStart, nowMs).second
    val hours = if (!hourly) null else {
        val start = now.toLocalDate().minusDays(6).atStartOfDay(now.zone)
        val cells = (0 until 168).map { i -> start.plusHours(i.toLong()).let { DataBucket("", it.toInstant().toEpochMilli(), it.plusHours(1).toInstant().toEpochMilli()) } }
        reader.perBucket(cells).map { it.first + (it.second ?: 0L) }.toLongArray()
    }
    if (period == DataPeriod.History) {
        // The days Android still has go to the history of the app, which keeps them longer; the page shows the history.
        val recent = DataPeriods.buckets(DataPeriod.Month30, now, billDay)
        DataHistory.recordDays(context, recent.zip(reader.perBucket(recent)).associate { (b, v) -> epochDay(b.from, now.zone) to (v.first to (v.second ?: 0L)) })
        val bars = DataHistory.days(context).takeLast(90).map { (day, wifi, mobile) ->
            val from = LocalDate.ofEpochDay(day).atStartOfDay(now.zone)
            DataBucket(from.dayOfMonth.toString(), from.toInstant().toEpochMilli(), from.plusDays(1).toInstant().toEpochMilli()) to (wifi to mobile as Long?)
        }
        return DataLoad(period, bars, emptyList(), cycleMobile, null, hours)
    }
    val buckets = DataPeriods.buckets(period, now, billDay)
    val bars = buckets.zip(reader.perBucket(buckets))
    val from = DataPeriods.start(period, now, billDay)
    val apps = reader.apps(from.toInstant().toEpochMilli(), nowMs)
    val before = if (!trends) null else {
        val prev = DataMath.previousStart(period, from)
        reader.device(prev.toInstant().toEpochMilli(), prev.toInstant().toEpochMilli() + (nowMs - from.toInstant().toEpochMilli()))
    }
    if (period == DataPeriod.Month30) DataHistory.recordDays(context, bars.associate { (b, v) -> epochDay(b.from, now.zone) to (v.first to (v.second ?: 0L)) })
    return DataLoad(period, bars, apps, cycleMobile, before, hours)
}

private fun epochDay(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

@Composable
internal fun DataPage(reader: UsageReader, limitMb: Long, billDay: Int, alertPct: Int, refresh: Int) {
    val context = LocalContext.current
    val options = LocalDsOptions.current
    var period by remember { mutableStateOf(DataPeriod.Week) }
    var open by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NetFilter.All) }
    val trends = options[Opt.Trends]
    val extra = options[Opt.Extra]
    val key = "${period.name}:$billDay"
    val data by produceState(dataCache[key], key, refresh, trends, extra) {
        // What was read before is shown while the new reading is made.
        value = dataCache[key]
        value = withContext(Dispatchers.IO) { load(context, reader, period, billDay, trends, extra) }.also { dataCache[key] = it; latestLoad = it }
    }
    val wide = options[Opt.Wide] && LocalConfiguration.current.screenWidthDp >= 600
    val periods = DataPeriod.entries.filter { it != DataPeriod.History || options[Opt.History] }
    val chips: @Composable () -> Unit = {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            items(periods) { p ->
                PageChip(
                    stringResource(
                        when (p) {
                            DataPeriod.Today -> R.string.ds_today
                            DataPeriod.Week -> R.string.ds_7days
                            DataPeriod.Month30 -> R.string.ds_30days
                            DataPeriod.ThisMonth -> if (billDay == 1) R.string.ds_this_month else R.string.ds_cycle
                            DataPeriod.History -> R.string.dsx_history
                        },
                    ),
                    period == p, "ds:period:${p.name}",
                ) { period = p; open = null }
            }
        }
    }
    val loaded = data
    if (loaded == null) {
        chips()
        Skeleton(rows = 4, chart = true)
        return
    }
    val total = loaded.bars.sumOf { it.second.first + (it.second.second ?: 0L) }
    val summary: @Composable () -> Unit = { DataSummary(loaded, limitMb, alertPct, billDay) }
    val state = rememberLazyListState()
    if (wide) {
        Column(Modifier.fillMaxSize()) {
            chips()
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { summary() }
                LazyColumn(Modifier.weight(1f).testTag("ds:data").scrollIndicator(state), state) {
                    appSection(loaded, total, open, { open = if (open == it) null else it }, query, { query = it }, filter, { filter = it })
                }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().testTag("ds:data").scrollIndicator(state), state) {
            item { chips(); summary() }
            appSection(loaded, total, open, { open = if (open == it) null else it }, query, { query = it }, filter, { filter = it })
        }
    }
}

@Composable
private fun DataSummary(load: DataLoad, limitMb: Long, alertPct: Int, billDay: Int) {
    val context = LocalContext.current
    val options = LocalDsOptions.current
    val colors = LocalMetroColors.current
    val p = rememberPalette()
    val wide = options[Opt.Wide] && LocalConfiguration.current.screenWidthDp >= 600
    val wifi = load.bars.sumOf { it.second.first }
    val mobile = load.bars.sumOf { it.second.second ?: 0L }
    val total = wifi + mobile
    val trends = options[Opt.Trends]
    Column {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                MetroText(stringResource(R.string.dsx_wifi), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
                MetroText(bytes(wifi), size = 34.sp, maxLines = 1, minScale = 0.6f, modifier = Modifier.testTag("ds:wifi"))
            }
            Column(Modifier.weight(1f)) {
                MetroText(stringResource(R.string.ds_mobile), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
                MetroText(bytes(mobile), size = 34.sp, maxLines = 1, minScale = 0.6f, modifier = Modifier.testTag("ds:mobile"))
            }
        }
        if (trends) load.before?.let { Trend(DataMath.trendPercent(total, it.first + (it.second ?: 0L))) }
        val share = DataPeriods.share(wifi, total)
        Bar(share, p.wifi, p.mobile, Modifier.padding(top = 10.dp, bottom = 6.dp), hatchRest = true, description = stringResource(R.string.dsx_share_desc, (share * 100).toInt(), 100 - (share * 100).toInt()))
        Legend(p, Modifier.padding(bottom = 8.dp))
        val rich = options[Opt.Chart]
        Chart(load.bars.map { ChartBar(it.first.label, it.second.first, it.second.second ?: 0L) }, if (wide) 150.dp else 110.dp, p, rich, Modifier.testTag("ds:chart"))
        if (Build.VERSION.SDK_INT < 29) PageNote(stringResource(R.string.dsx_no_mobile_old), Modifier.padding(top = 8.dp))
        val hotspot = load.apps.firstOrNull { it.tethering }?.mobile ?: 0L
        if (hotspot > 0) PageNote(stringResource(R.string.dsx_hotspot_included, bytes(hotspot)), Modifier.padding(top = 8.dp))
        if (trends && load.period != DataPeriod.Today) {
            val days = load.bars.map { (it.second.second ?: 0L) }
            DataMath.unusualFactor(days.lastOrNull() ?: 0L, days.dropLast(1))?.let {
                PageNote(stringResource(R.string.dsx_unusual, bytes(days.last()), it), Modifier.padding(top = 8.dp).testTag("ds:unusual"))
            }
        }
        if (limitMb > 0 && load.cycleMobile != null) LimitBlock(load.cycleMobile, limitMb, alertPct, billDay, p)
        if (trends) load.before?.let { before ->
            val prior = before.first + (before.second ?: 0L)
            val top = maxOf(total, prior, 1L)
            MetroText(stringResource(R.string.dsx_compare), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            MetroText(stringResource(R.string.dsx_compare_now, bytes(total)), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
            Bar(total.toFloat() / top, p.wifi, colors.surface, Modifier.padding(bottom = 4.dp), height = 10.dp)
            MetroText(stringResource(R.string.dsx_compare_before, bytes(prior)), size = 14.sp, weight = FontWeight.Normal, color = colors.subtle)
            Bar(prior.toFloat() / top, colors.subtle, colors.surface, height = 10.dp)
        }
        if (options[Opt.Extra]) ExtraCharts(load, p)
        MetroText(stringResource(R.string.ds_apps), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    }
}

@Composable
private fun LimitBlock(used: Long, limitMb: Long, alertPct: Int, billDay: Int, p: DsPalette) {
    val context = LocalContext.current
    val options = LocalDsOptions.current
    val colors = LocalMetroColors.current
    val limit = limitMb * 1_000_000L
    val share = DataPeriods.share(used, limit)
    val warn = share >= alertPct / 100f
    MetroText(
        stringResource(R.string.ds_limit_used, bytes(used), bytes(limit)), size = 15.sp, weight = FontWeight.Normal,
        color = if (share >= 1f) LocalAccent.current else colors.subtle, modifier = Modifier.padding(top = 12.dp).testTag("ds:limit:text"),
    )
    Bar(share, if (warn) p.warn else p.mobile, colors.surface, Modifier.padding(top = 4.dp), height = 18.dp, description = stringResource(R.string.dsx_limit_desc, (share * 100).toInt()))
    if (!DataLimitWatch.canNotify(context)) PageNote(stringResource(R.string.dsx_notif_needed), Modifier.padding(top = 6.dp))
    if (options[Opt.Trends]) {
        val now = ZonedDateTime.now()
        val start = DataPeriods.cycleStart(now, billDay)
        val end = DataPeriods.cycleEnd(now, billDay)
        val projected = DataMath.projection(used, now.toInstant().toEpochMilli() - start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli())
        PageNote(
            stringResource(R.string.dsx_projection, bytes(projected)) + if (projected > limit) " " + stringResource(R.string.dsx_projection_over) else "",
            Modifier.padding(top = 6.dp).testTag("ds:projection"),
        )
    }
    if (options[Opt.Cost]) {
        val extra = Prefs.open(context, Prefs.DATA_SENSE).getFloat("extra_price", 0f).toDouble()
        val cost = DataMath.overageCost(used, limit, extra)
        if (cost > 0) PageNote(stringResource(R.string.dsx_extra_cost, NumberFormat.getCurrencyInstance().format(cost)), Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ExtraCharts(load: DataLoad, p: DsPalette) {
    val colors = LocalMetroColors.current
    if (load.bars.size >= 7) {
        MetroText(stringResource(R.string.dsx_calendar), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        val first = Instant.ofEpochMilli(load.bars.first().first.from).atZone(ZoneId.systemDefault()).toLocalDate()
        CalendarGrid(load.bars.map { (b, v) -> b.label.toInt() to v.first + (v.second ?: 0L) }, first.dayOfWeek.value - 1, p)
    }
    load.hours?.let { hours ->
        val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
        MetroText(stringResource(R.string.dsx_hours), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        HourHeat(hours, p, stringResource(R.string.dsx_hours_desc, peak % 24))
    }
    if (load.apps.size >= 2) {
        MetroText(stringResource(R.string.dsx_flow), size = 16.sp, weight = FontWeight.SemiBold, color = colors.subtle, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        FlowChart(load.apps, p, stringResource(R.string.dsx_flow_desc, load.apps.first().label))
    }
}

private fun LazyListScope.appSection(
    load: DataLoad, total: Long, open: Int?, toggle: (Int) -> Unit, query: String, setQuery: (String) -> Unit, filter: NetFilter, setFilter: (NetFilter) -> Unit,
) {
    item {
        val options = LocalDsOptions.current
        if (options[Opt.Tools] && load.apps.isNotEmpty()) {
            Column(Modifier.padding(bottom = 6.dp)) {
                MetroTextField(query, setQuery, stringResource(R.string.dsx_search), Modifier.testTag("ds:search"))
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageChip(stringResource(R.string.dsx_all), filter == NetFilter.All, "ds:filter:all") { setFilter(NetFilter.All) }
                    PageChip(stringResource(R.string.dsx_wifi), filter == NetFilter.Wifi, "ds:filter:wifi") { setFilter(NetFilter.Wifi) }
                    PageChip(stringResource(R.string.ds_mobile), filter == NetFilter.Mobile, "ds:filter:mobile") { setFilter(NetFilter.Mobile) }
                }
            }
        }
        if (load.apps.isEmpty() && load.period != DataPeriod.History) PageNote(stringResource(R.string.ds_nothing))
    }
    val shown = load.apps.asSequence()
        .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        .map { it to when (filter) { NetFilter.All -> it.total; NetFilter.Wifi -> it.wifi; NetFilter.Mobile -> it.mobile } }
        .filter { it.second > 0 }.sortedByDescending { it.second }.toList()
    itemsIndexed(shown, key = { _, row -> row.first.uid }) { index, (app, value) ->
        AppDataRow(app, value, shown.first().second, total, open == app.uid, { toggle(app.uid) }, index)
    }
}

@Composable
private fun AppDataRow(app: AppData, value: Long, top: Long, total: Long, open: Boolean, toggle: () -> Unit, index: Int) {
    val options = LocalDsOptions.current
    val colors = LocalMetroColors.current
    val p = rememberPalette()
    val compact = options[Opt.Compact]
    val heavy = options[Opt.Trends] && DataPeriods.share(app.total, total) >= 0.2f && index < 3
    Column(Modifier.fillMaxWidth().featherRow(index).testTag("ds:app").metroPress(onClick = toggle).padding(vertical = if (compact) 2.dp else 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (options[Opt.Icons]) { AppIcon(app.packageName, if (compact) 22.dp else 28.dp); androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp)) }
            MetroText(app.label, size = if (compact) 16.sp else 19.sp, weight = if (heavy) FontWeight.SemiBold else FontWeight.Light, color = if (heavy) LocalAccent.current else colors.foreground, maxLines = 1, modifier = Modifier.weight(1f))
            MetroText(bytes(value), size = if (compact) 15.sp else 17.sp, weight = FontWeight.Normal, color = colors.subtle)
        }
        if (!compact) Bar(DataPeriods.share(value, top), p.wifi, colors.surface, Modifier.padding(top = 3.dp), height = 6.dp)
        if (open) {
            MetroText(
                stringResource(R.string.ds_app_detail, bytes(app.wifi), bytes(app.mobile), bytes(app.foreground), bytes(app.background), (DataPeriods.share(app.total, total) * 100).toInt()),
                size = 14.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun TimePage(reader: UsageReader, refresh: Int) {
    val options = LocalDsOptions.current
    val colors = LocalMetroColors.current
    val p = rememberPalette()
    var days by remember { mutableStateOf(1) }
    val list by produceState<List<AppTime>?>(null, days, refresh, options[Opt.HideLauncher]) {
        value = withContext(Dispatchers.IO) {
            val now = ZonedDateTime.now()
            val from = now.toLocalDate().minusDays(days - 1L).atStartOfDay(now.zone).toInstant().toEpochMilli()
            val leaveOut = if (options[Opt.HideLauncher]) setOfNotNull(reader.homePackage()) else emptySet()
            reader.screenTime(from, now.toInstant().toEpochMilli(), leaveOut = leaveOut)
        }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        items(listOf(1, 7, 30)) { d ->
            PageChip(stringResource(when (d) { 1 -> R.string.ds_today; 7 -> R.string.ds_7days; else -> R.string.ds_30days }), days == d, "ds:time:$d") { days = d }
        }
    }
    val rows = list
    if (rows == null) { Skeleton(rows = 5); return }
    MetroText(duration(rows.sumOf { it.millis }), size = 36.sp, maxLines = 1, minScale = 0.6f, modifier = Modifier.testTag("ds:time:total"))
    MetroText(stringResource(R.string.ds_screen_time), size = 15.sp, weight = FontWeight.Normal, color = colors.subtle)
    PageNote(stringResource(R.string.dsx_time_note), Modifier.padding(bottom = 8.dp))
    val state = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().testTag("ds:time").scrollIndicator(state), state) {
        itemsIndexed(rows, key = { _, row -> row.packageName }) { index, app ->
            val compact = options[Opt.Compact]
            Column(Modifier.fillMaxWidth().featherRow(index).padding(vertical = if (compact) 2.dp else 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (options[Opt.Icons]) { AppIcon(app.packageName, if (compact) 22.dp else 28.dp); androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp)) }
                    MetroText(app.label, size = if (compact) 16.sp else 19.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    MetroText(duration(app.millis), size = if (compact) 15.sp else 17.sp, weight = FontWeight.Normal, color = colors.subtle)
                }
                if (!compact) {
                    Bar(DataPeriods.share(app.millis, rows.first().millis), p.wifi, colors.surface, Modifier.padding(top = 3.dp), height = 6.dp)
                    MetroText(
                        stringResource(R.string.ds_last_used, android.text.format.DateUtils.getRelativeTimeSpanString(app.lastUsed, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()),
                        size = 12.sp, weight = FontWeight.Normal, color = colors.subtle,
                    )
                }
            }
        }
    }
}
