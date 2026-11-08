package com.metrowin.launcher.systemsettings

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.widget.Toast
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.DeviceReaders
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.data.WifiShell
import com.metrowin.launcher.data.mobileDataOn
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroSlider
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.PivotHeader
import com.metrowin.launcher.ui.mini.MiniFrame
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.settings.ToggleSetting
import com.metrowin.launcher.ui.start.DialogFrame
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

private fun run(vararg command: String) = ShizukuShell.async(listOf(arrayOf(*command)))

private fun switchWord(on: Boolean) = if (on) "enable" else "disable"

/** Re-reads [read] every two seconds while the page is on screen (not with the app in the background): what Android reports a moment after a switch was thrown. */
@Composable
private fun <T> watched(everyMs: Long = 2_000, read: () -> T): T {
    var value by remember { mutableStateOf(read()) }
    LaunchedWhileStarted(Unit) {
        while (true) {
            delay(everyMs)
            value = read()
        }
    }
    return value
}

@Composable
fun SystemSettingsScreen() {
    val context = LocalContext.current
    val pager = rememberPagerState { 5 }
    var refresh by remember { mutableIntStateOf(0) }
    MiniFrame {
        PivotHeader(
            listOf(stringResource(R.string.qa_wifi), stringResource(R.string.ss_cellular), stringResource(R.string.qa_bluetooth), stringResource(R.string.ss_datetime), stringResource(R.string.ss_sounds))
                .map { it.lowercase() },
            pager, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp),
        )
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (page) {
                    0 -> WifiPage(refresh)
                    1 -> CellularPage()
                    2 -> BluetoothPage()
                    3 -> DateTimePage()
                    else -> SoundsPage()
                }
                Column(Modifier.padding(bottom = 16.dp)) {}
            }
        }
        AppBar(
            listOf(
                AppBarItem("↻", stringResource(R.string.messages_refresh)) { refresh++ },
                AppBarItem("⚙", stringResource(R.string.ss_android)) { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } },
            ),
        )
    }
}

@Composable
private fun WifiPage(refresh: Int) {
    val context = LocalContext.current
    val wifi = remember { context.getSystemService(WifiManager::class.java) }
    val on = watched { wifi.isWifiEnabled }
    var message by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<WifiShell.Net?>(null) }
    var forget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var password by remember { mutableStateOf("") }
    var changes by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    ToggleSetting(label = stringResource(R.string.qa_wifi), checked = on, onChange = { run("svc", "wifi", switchWord(it)); changes++ }, tag = "ss:wifi")
    val nets by produceState(emptyList<WifiShell.Net>(), on, refresh, changes) {
        value = if (on) withContext(Dispatchers.IO) { WifiShell.scan(ShizukuShell.shell("cmd wifi start-scan; sleep 2; cmd wifi list-scan-results").output) } else emptyList()
    }
    val saved by produceState(emptyList<Pair<Int, String>>(), on, refresh, changes) {
        value = if (on) withContext(Dispatchers.IO) { WifiShell.saved(ShizukuShell.shell("cmd wifi list-networks").output) } else emptyList()
    }
    if (message.isNotEmpty()) PageNote(message)
    if (on) {
        MetroText(stringResource(R.string.ss_networks), size = 22.sp)
        if (nets.isEmpty()) PageNote(stringResource(R.string.ss_scanning))
        nets.forEach { net ->
            Column(Modifier.fillMaxWidth().metroPress(onClick = { chosen = net; password = "" })) {
                MetroText(net.ssid, size = 20.sp, maxLines = 1)
                MetroText(
                    "${net.dbm} dBm · ${String.format(java.util.Locale.getDefault(), "%.1f", net.mhz / 1000f)} GHz · ${stringResource(if (net.secured) R.string.ss_secured else R.string.ss_open)}",
                    size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
                )
            }
        }
        if (saved.isNotEmpty()) {
            MetroText(stringResource(R.string.ss_saved), size = 22.sp, modifier = Modifier.padding(top = 10.dp))
            saved.forEach { net ->
                Column(Modifier.fillMaxWidth().metroPress(onClick = { forget = net })) { MetroText(net.second, size = 20.sp, maxLines = 1) }
            }
        }
    }
    chosen?.let { net ->
        DialogFrame(onCancel = { chosen = null }) {
            MetroText(net.ssid, size = 22.sp, maxLines = 2)
            if (net.secured) MetroTextField(password, { password = it }, stringResource(R.string.ss_password), Modifier.fillMaxWidth(), password = true)
            MetroButton(stringResource(R.string.ss_connect), Modifier.fillMaxWidth()) {
                if (net.secured && password.length < 8) {
                    Toast.makeText(context, R.string.ss_password_short, Toast.LENGTH_SHORT).show()
                    return@MetroButton
                }
                chosen = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { ShizukuShell.run(WifiShell.connect(net, password)) }
                    // The raw output of `cmd wifi` is for the shell, not for the user: the page says what to try.
                    message = if (result.ok) context.getString(R.string.st_done) else context.getString(R.string.ss_connect_failed, net.ssid)
                    changes++
                }
            }
        }
    }
    forget?.let { net ->
        DialogFrame(onCancel = { forget = null }) {
            MetroText(net.second, size = 22.sp, maxLines = 2)
            PageNote(stringResource(R.string.ss_forget_hint))
            MetroButton(stringResource(R.string.ss_forget), Modifier.fillMaxWidth()) {
                run("cmd", "wifi", "forget-network", net.first.toString())
                forget = null
                changes++
            }
        }
    }
}

@Composable
private fun CellularPage() {
    val context = LocalContext.current
    val data = watched { mobileDataOn(context) }
    // shortcut: roaming has no public reader or writer, so the AOSP global key is all there is (a maker that keeps it elsewhere shows it off); upgrade if a public API appears.
    val roaming = watched { Settings.Global.getInt(context.contentResolver, "data_roaming", 0) == 1 }
    ToggleSetting(label = stringResource(R.string.qa_mobile_data), checked = data, onChange = { run("svc", "data", switchWord(it)) }, tag = "ss:data")
    ToggleSetting(
        label = stringResource(R.string.mo_roaming), checked = roaming, onChange = { run("settings", "put", "global", "data_roaming", if (it) "1" else "0") },
        hint = stringResource(R.string.ss_roaming_hint), tag = "ss:roaming",
    )
    DeviceReaders.sim(context).forEach { (label, value) -> InfoLine(label, value) }
}

@Composable
private fun BluetoothPage() {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    val on = watched { adapter?.isEnabled == true }
    ToggleSetting(label = stringResource(R.string.qa_bluetooth), checked = on, onChange = { run("svc", "bluetooth", switchWord(it)) }, enabled = adapter != null, tag = "ss:bluetooth")
    DeviceReaders.bluetooth(context).drop(1).forEach { (label, value) -> InfoLine(label, value) }
    MetroButton(stringResource(R.string.ss_pair)) { runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }
}

@Composable
private fun DateTimePage() {
    val context = LocalContext.current
    val autoTime = watched { Settings.Global.getInt(context.contentResolver, "auto_time", 1) == 1 }
    val autoZone = watched { Settings.Global.getInt(context.contentResolver, "auto_time_zone", 1) == 1 }
    val is24 = watched { DateFormat.is24HourFormat(context) }
    var zone by remember { mutableStateOf(TimeZone.getDefault().id) }
    var query by remember { mutableStateOf("") }
    ToggleSetting(label = stringResource(R.string.ss_auto_time), checked = autoTime, onChange = { run("settings", "put", "global", "auto_time", if (it) "1" else "0") }, tag = "ss:autotime")
    ToggleSetting(label = stringResource(R.string.ss_auto_zone), checked = autoZone, onChange = { run("settings", "put", "global", "auto_time_zone", if (it) "1" else "0") }, tag = "ss:autozone")
    ToggleSetting(label = stringResource(R.string.ss_24h), checked = is24, onChange = { run("settings", "put", "system", "time_12_24", if (it) "24" else "12") }, tag = "ss:24h")
    MetroText(stringResource(R.string.ss_zone, zone), size = 20.sp)
    if (!autoZone) {
        MetroTextField(query, { query = it }, stringResource(R.string.st_search), Modifier.fillMaxWidth())
        val matches = remember(query) { if (query.length < 2) emptyList() else TimeZone.getAvailableIDs().filter { it.contains(query.trim(), true) }.take(12) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(matches) { id ->
                PageChip(id, id == zone, "ss:zone:$id") {
                    run("cmd", "alarm", "set-timezone", id)
                    zone = id
                }
            }
        }
    }
}

private val STREAMS = listOf(
    R.string.ss_music to AudioManager.STREAM_MUSIC, R.string.ss_ring to AudioManager.STREAM_RING, R.string.ss_notification to AudioManager.STREAM_NOTIFICATION,
    R.string.ss_alarm to AudioManager.STREAM_ALARM, R.string.ss_call to AudioManager.STREAM_VOICE_CALL,
)

@Composable
private fun SoundsPage() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    STREAMS.forEach { (label, stream) ->
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        // The hardware keys change the volume too: it is read again while the page shows, and the slider follows what the system says.
        val actual = watched(500) { audio.getStreamVolume(stream) }
        var level by remember(actual) { mutableIntStateOf(actual) }
        MetroText("${stringResource(label)}  $level / $max", size = 20.sp)
        MetroSlider(
            value = level / max.toFloat(),
            onChange = { fraction ->
                val next = (fraction * max).toInt().coerceIn(0, max)
                // The ringer volumes refuse a change while Do not disturb holds them: the slider then stays where it was.
                if (runCatching { audio.setStreamVolume(stream, next, 0) }.isSuccess) level = next
            },
            description = stringResource(label),
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetroText(label, size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(0.5f))
        MetroText(value, size = 16.sp, weight = FontWeight.Normal, modifier = Modifier.weight(0.5f))
    }
}
