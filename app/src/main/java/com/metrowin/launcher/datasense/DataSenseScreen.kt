package com.metrowin.launcher.datasense

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.data.UsageAccess
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.Mark
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import kotlin.math.abs

private enum class Page(val title: Int) {
    Overview(R.string.dsx_overview), Data(R.string.ds_data), Storage(R.string.ds_storage), Time(R.string.ds_time), Battery(R.string.ds_battery), Network(R.string.ds_network)
}

@Composable
fun DataSenseScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs.open(context, Prefs.DATA_SENSE) }
    val options = remember { DsOptions(prefs) }
    CompositionLocalProvider(LocalDsOptions provides options) { DataSenseContent(prefs) }
}

@Composable
private fun DataSenseContent(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    val options = LocalDsOptions.current
    var allowed by remember { mutableStateOf(UsageAccess.granted(context)) }
    // The usage access is given in a screen of Android: when the user comes back it is read again.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = UsageAccess.granted(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val pages = remember(options[Opt.Overview]) { Page.entries.filter { it != Page.Overview || options[Opt.Overview] } }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val reader = remember { UsageReader(context) }
    var limitMb by remember { mutableLongStateOf(prefs.getLong("limit_mb", 0L)) }
    var billDay by remember { mutableIntStateOf(prefs.getInt("bill_day", 1)) }
    var alertPct by remember { mutableIntStateOf(prefs.getInt("alert_pct", DataPeriods.DEFAULT_WARN)) }
    var editingLimit by remember { mutableStateOf(false) }
    var editingOptions by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    // The live readings: a tick every few seconds while the screen is in front and the option is on.
    var tick by remember { mutableIntStateOf(0) }
    val live = options[Opt.Live]
    LaunchedEffect(live) {
        if (live) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { delay(LIVE_MS); tick++ } }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val reduce = LocalReduceMotion.current
    MiniFrame {
        PivotHeader(pages.map { stringResource(it.title) }, pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, pageSpacing = 16.dp) { index ->
            val page = pages[index]
            val active = pager.currentPage == index
            val fade = options[Opt.Chart] && !reduce
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp).graphicsLayer {
                    if (fade) alpha = 1f - 0.6f * abs(pager.currentPage - index + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                },
            ) {
                val liveTick = if (live && active) tick else 0
                if (!allowed && (page == Page.Data || page == Page.Time)) NeedAccess()
                else when (page) {
                    Page.Overview -> OverviewPage(reader, allowed, billDay, refresh + liveTick) { target -> scope.launch { pager.animateScrollToPage(pages.indexOf(target).coerceAtLeast(0)) } }
                    Page.Data -> DataPage(reader, limitMb, billDay, alertPct, refresh)
                    Page.Storage -> StoragePage(reader, allowed, refresh)
                    Page.Time -> TimePage(reader, refresh)
                    Page.Battery -> BatteryPage(reader, refresh + liveTick)
                    Page.Network -> NetworkPage(reader, refresh + liveTick, liveTick)
                }
            }
        }
        AppBar(
            listOf(
                AppBarItem("↻", stringResource(R.string.dsx_refresh)) { refresh++ },
                AppBarItem("⚙", stringResource(R.string.ds_limit)) { editingLimit = true },
            ),
            menu = listOf(
                AppBarItem("", stringResource(R.string.dsx_options)) { editingOptions = true }.icon(Mark.Check),
                AppBarItem("", stringResource(R.string.dsx_export)) { exportCsv(context) }.icon(Mark.Arrow),
                AppBarItem("", stringResource(R.string.dsx_saver)) { openDataSaver(context) }.icon(Mark.Down),
            ),
        )
    }
    if (editingLimit) LimitDialog(prefs, onDone = { limit, day, pct ->
        val changed = limit != limitMb || day != billDay || pct != alertPct
        limitMb = limit; billDay = day; alertPct = pct
        // A new limit, day or share starts the alerts of the cycle again.
        if (changed) DataLimitWatch.resetAlerts(context)
        DataLimitWatch.schedule(context)
        if (limit > 0 && Build.VERSION.SDK_INT >= 33 && !DataLimitWatch.canNotify(context)) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        editingLimit = false
    }, onCancel = { editingLimit = false })
    if (editingOptions) {
        DialogFrame(onCancel = { editingOptions = false }) {
            MetroText(stringResource(R.string.dsx_options), size = 20.sp, weight = FontWeight.Normal)
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Opt.entries.forEach { opt ->
                    MetroToggle(stringResource(opt.label), options[opt], { on ->
                        options.set(opt, on)
                        if (opt == Opt.History && !on) DataHistory.clear(context)
                        if (opt == Opt.History && on) refresh++
                    }, Modifier.testTag("ds:opt:${opt.key}"))
                }
            }
            MetroButton(stringResource(R.string.dsx_close), Modifier.fillMaxWidth()) { editingOptions = false }
        }
    }
}

private const val LIVE_MS = 3_000L

@Composable
private fun LimitDialog(prefs: android.content.SharedPreferences, onDone: (Long, Int, Int) -> Unit, onCancel: () -> Unit) {
    val options = LocalDsOptions.current
    var text by remember { mutableStateOf(DataMath.limitText(prefs.getLong("limit_mb", 0L))) }
    var dayText by remember { mutableStateOf(prefs.getInt("bill_day", 1).toString()) }
    var pctText by remember { mutableStateOf(prefs.getInt("alert_pct", DataPeriods.DEFAULT_WARN).toString()) }
    var priceText by remember { mutableStateOf(prefs.getFloat("plan_price", 0f).takeIf { it > 0f }?.toString().orEmpty()) }
    var extraText by remember { mutableStateOf(prefs.getFloat("extra_price", 0f).takeIf { it > 0f }?.toString().orEmpty()) }
    var error by remember { mutableStateOf<Int?>(null) }
    DialogFrame(onCancel = onCancel) {
        MetroText(stringResource(R.string.ds_limit_title), size = 20.sp, weight = FontWeight.Normal)
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetroTextField(text, { text = it.filter { c -> c.isLetterOrDigit() || c == '.' || c == ',' || c == ' ' }.take(12) }, stringResource(R.string.dsx_limit_hint), Modifier.testTag("ds:limit"), keyboardType = KeyboardType.Text)
            MetroTextField(dayText, { dayText = it.filter(Char::isDigit).take(2) }, stringResource(R.string.ds_billing_day), Modifier.testTag("ds:billday"), keyboardType = KeyboardType.Number)
            MetroTextField(pctText, { pctText = it.filter(Char::isDigit).take(2) }, stringResource(R.string.dsx_alert_pct_hint), Modifier.testTag("ds:alertpct"), keyboardType = KeyboardType.Number)
            if (options[Opt.Cost]) {
                MetroTextField(priceText, { priceText = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, stringResource(R.string.dsx_plan_price), Modifier.testTag("ds:price"), keyboardType = KeyboardType.Decimal)
                MetroTextField(extraText, { extraText = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, stringResource(R.string.dsx_extra_price), Modifier.testTag("ds:extra"), keyboardType = KeyboardType.Decimal)
            }
        }
        error?.let { PageNote(stringResource(it), Modifier.testTag("ds:error")) }
        MetroButton(stringResource(R.string.dsx_save), Modifier.fillMaxWidth().testTag("ds:limit:ok")) {
            val limit = if (text.isBlank()) 0L else DataMath.parseLimitMb(text)
            val day = dayText.toIntOrNull()
            val pct = pctText.toIntOrNull()
            error = when {
                limit == null -> R.string.dsx_limit_invalid
                day == null || day !in 1..28 -> R.string.dsx_day_invalid
                pct == null || pct !in DataPeriods.MIN_WARN..DataPeriods.MAX_WARN -> R.string.dsx_alert_pct_invalid
                else -> null
            }
            if (error == null && limit != null && day != null && pct != null) {
                prefs.edit {
                    putLong("limit_mb", limit); putInt("bill_day", day); putInt("alert_pct", pct)
                    putFloat("plan_price", priceText.toFloatOrNull() ?: 0f); putFloat("extra_price", extraText.toFloatOrNull() ?: 0f)
                }
                onDone(limit, day, pct)
            }
        }
    }
}

/** The page of a screen that needs the usage access: what it is for, three steps and the button; with a picture instead of only words. */
@Composable
private fun NeedAccess() {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        EmptyArt("!", stringResource(R.string.ds_need_access))
        PageNote(stringResource(R.string.dsx_need_why), Modifier.padding(bottom = 10.dp))
        listOf(R.string.dsx_step1, R.string.dsx_step2, R.string.dsx_step3).forEach { MetroText(stringResource(it), size = 17.sp, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE, modifier = Modifier.padding(vertical = 3.dp)) }
        MetroButton(stringResource(R.string.dsx_allow), Modifier.padding(top = 14.dp).testTag("ds:allow")) { UsageAccess.openSettings(context) }
    }
}

/** The tiles: battery, network, free space and the mobile data of the cycle; a touch goes to the page of each. */
@Composable
private fun OverviewPage(reader: UsageReader, allowed: Boolean, billDay: Int, refresh: Int, go: (Page) -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val battery by produceState<BatteryReading?>(null, refresh) { value = withContext(Dispatchers.IO) { reader.battery() } }
    val network by produceState<NetworkReading?>(null, refresh) { value = withContext(Dispatchers.IO) { reader.network() } }
    val free by produceState<StorageInfo?>(null, refresh) { value = withContext(Dispatchers.IO) { StorageInfo.read() } }
    val mobile by produceState<Long?>(null, refresh, allowed) {
        value = if (!allowed) null else withContext(Dispatchers.IO) {
            val now = ZonedDateTime.now()
            reader.device(DataPeriods.cycleStart(now, billDay).toInstant().toEpochMilli(), now.toInstant().toEpochMilli()).second
        }
    }
    val tiles = listOf(
        Triple(Page.Battery, stringResource(R.string.ds_battery), battery?.let { "${it.percent}%" } ?: "—"),
        Triple(Page.Network, stringResource(R.string.ds_network), network?.let { transportName(it.transport) } ?: stringResource(R.string.ds_offline)),
        Triple(Page.Storage, stringResource(R.string.ds_storage), free?.let { bytes(it.freeBytes) } ?: "—"),
        Triple(Page.Data, stringResource(R.string.ds_mobile), mobile?.let { bytes(it) } ?: "—"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (page, title, value) ->
                    Box(Modifier.weight(1f).aspectRatio(1f).background(accent).metroPress(onClick = { go(page) }).padding(10.dp).testTag("ds:tile:${page.name}")) {
                        MetroText(title, size = 15.sp, weight = FontWeight.Normal, color = colors.tileContent, modifier = Modifier.align(Alignment.TopStart))
                        MetroText(value, size = 30.sp, color = colors.tileContent, maxLines = 1, minScale = 0.5f, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }
            }
        }
    }
}

private fun exportCsv(context: android.content.Context) {
    val load = latestLoad ?: return
    val text = DataMath.csv(load.bars.map { Triple(it.first, it.second.first, it.second.second) }, load.apps)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, "Data Sense")
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The data saver of Android: its settings for this app, or the page of the data use when the phone has no such screen. */
internal fun openDataSaver(context: android.content.Context) {
    val own = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(own) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@Composable
internal fun transportName(transport: Transport): String = stringResource(
    when (transport) {
        Transport.WiFi -> R.string.dsx_wifi
        Transport.Mobile -> R.string.ds_mobile
        Transport.Ethernet -> R.string.dsx_ethernet
        Transport.Bluetooth -> R.string.dsx_bluetooth
        Transport.Unknown -> R.string.ds_unknown
    },
)

@Composable
internal fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) stringResource(R.string.dsx_hours_min, minutes / 60, minutes % 60) else stringResource(R.string.dsx_min, minutes)
}

@Composable
internal fun InfoRow(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        MetroText(key, size = 18.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(1f))
        MetroText(value, size = 20.sp, maxLines = 1)
    }
}
