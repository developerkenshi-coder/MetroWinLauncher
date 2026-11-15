package com.metrowin.launcher.systemsettings

import android.net.wifi.WifiManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.data.WifiShell
import com.metrowin.launcher.ui.actioncenter.MetroToast
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroProgressBar
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.settings.ToggleSetting
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the last look at the Wi-Fi showed: the networks around, the ones the phone knows, the one it is on. */
private class WifiSnapshot(val nets: List<WifiShell.Net>, val saved: List<Pair<Int, String>>, val connected: String?, val unreadable: Boolean)

private val EMPTY = WifiSnapshot(emptyList(), emptyList(), null, false)

@Composable
internal fun WifiPage(refresh: Int) {
    val context = LocalContext.current
    val opts = LocalOpts.current
    val looks = opts[Option.Looks]
    val wifi = remember { context.getSystemService(WifiManager::class.java) }
    val on = watched(actions = listOf(WifiManager.WIFI_STATE_CHANGED_ACTION)) { wifi.isWifiEnabled }
    var message by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<WifiShell.Net?>(null) }
    var forget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var hidden by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val tick by Nudge.tick.collectAsState()
    val scope = rememberCoroutineScope()
    // A note about a connection is gone after a few seconds: it must not stay as if it were about the next one.
    LaunchedEffect(message) { if (message.isNotEmpty()) { delay(5_000); message = "" } }
    ToggleSetting(
        label = stringResource(R.string.qa_wifi), checked = on, onChange = { exec(context, "svc", "wifi", if (it) "enable" else "disable") },
        hint = if (looks) stringResource(R.string.ssx_wifi_hint) else null, tag = "ss:wifi",
    )
    val snap by produceState(EMPTY, on, refresh, tick) {
        if (!on) { value = EMPTY; return@produceState }
        scanning = true
        try {
            val known = withContext(Dispatchers.IO) { WifiShell.saved(ShizukuShell.shell("cmd wifi list-networks").output) to SettingsLogic.connectedSsid(ShizukuShell.shell("cmd wifi status").output) }
            value = WifiSnapshot(value.nets, known.first, known.second, false)
            // The wait is a coroutine delay, not a `sleep` in the shell: leaving the page ends it.
            withContext(Dispatchers.IO) { ShizukuShell.shell("cmd wifi start-scan") }
            delay(2_000)
            val out = withContext(Dispatchers.IO) { ShizukuShell.shell("cmd wifi list-scan-results").output }
            val nets = WifiShell.scan(out)
            value = WifiSnapshot(nets, known.first, known.second, nets.isEmpty() && out.lines().count { it.isNotBlank() } > 1)
        } finally {
            scanning = false
        }
    }
    if (message.isNotEmpty()) PageNote(message)
    if (!on) PageNote(stringResource(R.string.ssx_wifi_off))
    if (on) {
        MetroText(stringResource(R.string.ss_networks), size = 22.sp)
        if (scanning) MetroProgressBar(Modifier.padding(vertical = 4.dp))
        if (snap.nets.isEmpty() && !scanning) PageNote(stringResource(if (snap.unreadable) R.string.ssx_scan_unreadable else R.string.ssx_scan_empty))
        if (snap.nets.isEmpty() && scanning) PageNote(stringResource(R.string.ss_scanning))
        snap.nets.forEach { net ->
            val isOn = net.ssid == snap.connected
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).metroPress(onClick = { chosen = net; password = ""; show = false }),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    MetroText(net.ssid, size = 20.sp, maxLines = 1, color = if (isOn) LocalAccent.current else LocalMetroColors.current.foreground)
                    val known = if (snap.saved.any { it.second == net.ssid }) " · ${stringResource(R.string.ssx_known)}" else ""
                    val state = if (isOn) " · ${stringResource(R.string.ssx_connected)}" else ""
                    MetroText(
                        "${net.dbm} dBm · ${String.format(java.util.Locale.getDefault(), "%.1f", net.mhz / 1000f)} GHz · ${stringResource(if (net.secured) R.string.ss_secured else R.string.ss_open)}$known$state",
                        size = 14.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, maxLines = 2,
                    )
                }
                if (looks) SignalBars(SettingsLogic.signalBars(net.dbm))
            }
        }
        if (opts[Option.HiddenWifi]) PageChip(stringResource(R.string.ssx_hidden_join), false, "ss:hidden", Modifier.padding(top = 6.dp)) { hidden = true; password = ""; show = false }
        if (snap.saved.isNotEmpty()) {
            MetroText(stringResource(R.string.ss_saved), size = 22.sp, modifier = Modifier.padding(top = 10.dp))
            snap.saved.forEach { net ->
                Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).metroPress(onClick = { forget = net }), verticalArrangement = Arrangement.Center) { MetroText(net.second, size = 20.sp, maxLines = 1) }
            }
        }
    }
    chosen?.let { net ->
        DialogFrame(onCancel = { chosen = null }) {
            MetroText(net.ssid, size = 22.sp, maxLines = 2)
            if (net.secured) PasswordField(password, { password = it }, show) { show = it }
            MetroButton(stringResource(R.string.ss_connect), Modifier.fillMaxWidth()) {
                if (!SettingsLogic.passwordOk(net.secured, password)) {
                    MetroToast.show(context, R.string.ss_password_short)
                    return@MetroButton
                }
                chosen = null
                scope.launch { message = connect(context, WifiShell.connect(net, password), net.ssid) }
            }
        }
    }
    if (hidden) HiddenDialog(onCancel = { hidden = false }) { ssid, secured, sae, pw ->
        hidden = false
        scope.launch { message = connect(context, WifiShell.connect(WifiShell.Net(ssid, 0, 0, secured, sae), pw) + "-h", ssid) }
    }
    forget?.let { net ->
        DialogFrame(onCancel = { forget = null }) {
            MetroText(net.second, size = 22.sp, maxLines = 2)
            PageNote(stringResource(R.string.ss_forget_hint))
            MetroButton(stringResource(R.string.ss_forget), Modifier.fillMaxWidth()) {
                exec(context, "cmd", "wifi", "forget-network", net.first.toString())
                forget = null
            }
        }
    }
}

/**
 * Runs the connect command and says how it went, in the words of the app: what `cmd wifi` prints is for the shell, not for the user.
 * shortcut: `cmd wifi` only takes the password as an argument (no stdin, no file), so it shows to the shell user in `ps`; apps cannot see it (Android hides other users' processes).
 */
private suspend fun connect(context: android.content.Context, command: Array<String>, ssid: String): String {
    SettingsPrefs.log(context, command)
    val result = withContext(Dispatchers.IO) { ShizukuShell.run(command) }
    Nudge.soon()
    return if (result.ok) context.getString(R.string.st_done) else context.getString(R.string.ss_connect_failed, ssid)
}

@Composable
private fun PasswordField(password: String, onChange: (String) -> Unit, show: Boolean, onShow: (Boolean) -> Unit) {
    MetroTextField(password, onChange, stringResource(R.string.ss_password), Modifier.fillMaxWidth(), password = !show)
    PageChip(stringResource(R.string.ssx_show_password), show, "ss:show") { onShow(!show) }
}

@Composable
private fun HiddenDialog(onCancel: () -> Unit, onJoin: (ssid: String, secured: Boolean, sae: Boolean, password: String) -> Unit) {
    val context = LocalContext.current
    var ssid by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(1) } // 0 open, 1 WPA2, 2 WPA3
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    DialogFrame(onCancel = onCancel) {
        MetroText(stringResource(R.string.ssx_hidden_join), size = 22.sp, maxLines = 2)
        MetroTextField(ssid, { ssid = it }, stringResource(R.string.ssx_ssid), Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.ss_open to 0, R.string.ssx_wpa2 to 1, R.string.ssx_wpa3 to 2).forEach { (label, k) -> PageChip(stringResource(label), kind == k, "ss:kind:$k") { kind = k } }
        }
        if (kind != 0) PasswordField(password, { password = it }, show) { show = it }
        MetroButton(stringResource(R.string.ss_connect), Modifier.fillMaxWidth()) {
            if (ssid.isBlank()) return@MetroButton
            if (!SettingsLogic.passwordOk(kind != 0, password)) {
                MetroToast.show(context, R.string.ss_password_short)
                return@MetroButton
            }
            onJoin(ssid.trim(), kind != 0, kind == 2, password)
        }
    }
}

@Composable
private fun SignalBars(level: Int) {
    val colors = LocalMetroColors.current
    Canvas(Modifier.size(24.dp, 18.dp)) {
        val w = size.width / 7f
        repeat(4) { i ->
            val h = size.height * (i + 1) / 4f
            drawRect(if (i < level) colors.foreground else colors.inactive, Offset(i * 2 * w, size.height - h), Size(w, h))
        }
    }
}
