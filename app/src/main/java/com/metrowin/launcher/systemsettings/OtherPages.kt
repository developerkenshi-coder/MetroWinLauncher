package com.metrowin.launcher.systemsettings

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.nfc.NfcAdapter
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.DeviceReaders
import com.metrowin.launcher.data.ScreenTimeouts
import com.metrowin.launcher.data.mobileDataOn
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroSlider
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.mini.DateTimeDialog
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.settings.ToggleSetting
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.delay
import java.util.TimeZone

private fun word(on: Boolean) = if (on) "enable" else "disable"

private fun flag(on: Boolean) = if (on) "1" else "0"

private fun Context.global(key: String, default: Int) = Settings.Global.getInt(contentResolver, key, default)

private fun Context.system(key: String, default: Int) = Settings.System.getInt(contentResolver, key, default)

private fun globalUri(key: String) = Settings.Global.getUriFor(key)

private fun systemUri(key: String) = Settings.System.getUriFor(key)

/** The title of a block of a page; only with the refined lists on. */
@Composable
private fun Header(text: String) {
    if (LocalOpts.current[Option.Looks]) MetroText(text, size = 22.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun hint(res: Int): String? = if (LocalOpts.current[Option.Looks]) stringResource(res) else null

@Composable
internal fun CellularPage() {
    val context = LocalContext.current
    val data = watched(uris = listOf(globalUri("mobile_data"))) { mobileDataOn(context) }
    // The roaming key is the one of the data subscription when the maker keeps it per subscription, the plain AOSP one otherwise.
    val key = remember {
        val sub = SubscriptionManager.getDefaultDataSubscriptionId()
        SettingsLogic.roamingKey(sub, context.global("data_roaming$sub", Int.MIN_VALUE) != Int.MIN_VALUE)
    }
    val roaming = watched(uris = listOf(globalUri(key))) { context.global(key, 0) == 1 }
    ToggleSetting(label = stringResource(R.string.qa_mobile_data), checked = data, onChange = { exec(context, "svc", "data", word(it)) }, hint = hint(R.string.ssx_data_hint), tag = "ss:data")
    ToggleSetting(
        label = stringResource(R.string.mo_roaming), checked = roaming, onChange = { exec(context, "settings", "put", "global", key, flag(it)) },
        hint = stringResource(R.string.ss_roaming_hint), tag = "ss:roaming",
    )
    Header(stringResource(R.string.ssx_h_info))
    watched(fallbackMs = 5_000) { DeviceReaders.sim(context) }.forEach { (label, value) -> InfoLine(label, value) }
}

@Composable
internal fun BluetoothPage() {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }
    val on = watched(actions = listOf(BluetoothAdapter.ACTION_STATE_CHANGED)) { adapter?.isEnabled == true }
    ToggleSetting(
        label = stringResource(R.string.qa_bluetooth), checked = on, onChange = { exec(context, "svc", "bluetooth", word(it)) }, enabled = adapter != null,
        hint = hint(R.string.ssx_bt_hint), tag = "ss:bluetooth",
    )
    // The state row is the switch above; the rest are the paired devices.
    val state = stringResource(R.string.mo_bt_state)
    Header(stringResource(R.string.ssx_h_info))
    watched(actions = listOf(BluetoothAdapter.ACTION_STATE_CHANGED), fallbackMs = 5_000) { DeviceReaders.bluetooth(context) }.filter { it.first != state }.forEach { (label, value) -> InfoLine(label, value) }
    MetroButton(stringResource(R.string.ss_pair)) { runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }
}

@Composable
internal fun DateTimePage() {
    val context = LocalContext.current
    val autoTime = watched(uris = listOf(globalUri("auto_time"))) { context.global("auto_time", 1) == 1 }
    val autoZone = watched(uris = listOf(globalUri("auto_time_zone"))) { context.global("auto_time_zone", 1) == 1 }
    val is24 = watched(uris = listOf(systemUri("time_12_24"))) { DateFormat.is24HourFormat(context) }
    // A zone changed by the system (or by this app) is read from the system again, not kept from the first look.
    val zone = watched(actions = listOf(Intent.ACTION_TIMEZONE_CHANGED)) { TimeZone.setDefault(null); TimeZone.getDefault().id }
    var query by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    ToggleSetting(label = stringResource(R.string.ss_auto_time), checked = autoTime, onChange = { exec(context, "settings", "put", "global", "auto_time", flag(it)) }, tag = "ss:autotime")
    ToggleSetting(label = stringResource(R.string.ss_auto_zone), checked = autoZone, onChange = { exec(context, "settings", "put", "global", "auto_time_zone", flag(it)) }, tag = "ss:autozone")
    ToggleSetting(label = stringResource(R.string.ss_24h), checked = is24, onChange = { exec(context, "settings", "put", "system", "time_12_24", if (it) "24" else "12") }, tag = "ss:24h")
    if (LocalOpts.current[Option.ManualTime]) {
        if (autoTime) PageNote(stringResource(R.string.ssx_manual_needs))
        else MetroButton(stringResource(R.string.ssx_set_time), Modifier.fillMaxWidth()) { picking = true }
    }
    if (picking) DateTimeDialog(System.currentTimeMillis(), onChoose = { exec(context, "cmd", "alarm", "set-time", it.toString()); picking = false }, onCancel = { picking = false })
    MetroText(stringResource(R.string.ss_zone, SettingsLogic.zoneLabel(zone, System.currentTimeMillis())), size = 20.sp)
    if (!autoZone) {
        MetroTextField(query, { query = it }, stringResource(R.string.st_search), Modifier.fillMaxWidth())
        val now = System.currentTimeMillis()
        val matches = remember(query) { SettingsLogic.zoneMatches(TimeZone.getAvailableIDs().toList(), query) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(matches) { id -> PageChip(SettingsLogic.zoneLabel(id, now), id == zone, "ss:zone:$id") { exec(context, "cmd", "alarm", "set-timezone", id) } }
        }
    }
}

private val STREAMS = listOf(
    R.string.ss_music to AudioManager.STREAM_MUSIC, R.string.ss_ring to AudioManager.STREAM_RING, R.string.ss_notification to AudioManager.STREAM_NOTIFICATION,
    R.string.ss_alarm to AudioManager.STREAM_ALARM, R.string.ss_call to AudioManager.STREAM_VOICE_CALL,
)

private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"

@Composable
internal fun SoundsPage() {
    val context = LocalContext.current
    val opts = LocalOpts.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    // The hardware keys change the volume too: one reading for all the streams, when Android says so, and the sliders follow it.
    val volumes = watched(actions = listOf(VOLUME_CHANGED, AudioManager.RINGER_MODE_CHANGED_ACTION), uris = listOf(Settings.System.CONTENT_URI), fallbackMs = 5_000) {
        STREAMS.map { audio.getStreamVolume(it.second) }
    }
    if (opts[Option.SoundExtras]) RingerControls(audio)
    val wide = LocalConfiguration.current.screenWidthDp >= 600 && opts[Option.Looks]
    val rows: List<@Composable (Modifier) -> Unit> = STREAMS.mapIndexed { i, (label, stream) -> { modifier -> VolumeRow(audio, label, stream, volumes[i], modifier) } }
    if (wide) rows.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) { pair.forEach { it(Modifier.weight(1f)) } }
    } else rows.forEach { it(Modifier) }
}

@Composable
private fun VolumeRow(audio: AudioManager, label: Int, stream: Int, actual: Int, modifier: Modifier) {
    val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
    var level by remember { mutableIntStateOf(actual) }
    var touched by remember { mutableLongStateOf(0L) }
    var before by remember { mutableIntStateOf(max / 2) }
    // The reading that comes while a finger drags would pull the slider back; it is taken only a moment after the last touch.
    LaunchedEffect(actual) {
        val wait = touched + 800 - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        level = actual
    }
    fun apply(next: Int) {
        touched = System.currentTimeMillis()
        // The ringer volumes refuse a change while Do not disturb holds them: the slider then stays where it was.
        if (runCatching { audio.setStreamVolume(stream, next, 0) }.isSuccess) level = next
    }
    Column(modifier) {
        MetroText("${stringResource(label)}  $level / $max", size = 20.sp)
        if (LocalOpts.current[Option.SoundExtras]) {
            PageChip(stringResource(if (level == 0) R.string.ssx_unmute else R.string.ssx_mute), level == 0, "ss:mute:$stream") {
                if (level > 0) { before = level; apply(0) } else apply(before.coerceAtLeast(1))
            }
        }
        MetroSlider(value = level / max.toFloat(), onChange = { apply((it * max).toInt().coerceIn(0, max)) }, description = stringResource(label))
    }
}

/** The ringer mode and Do not disturb. Silent needs the access of Android to Do not disturb: without it the change is refused and said. */
@Composable
private fun RingerControls(audio: AudioManager) {
    val context = LocalContext.current
    val mode = watched(actions = listOf(AudioManager.RINGER_MODE_CHANGED_ACTION)) { audio.ringerMode }
    val zen = watched(uris = listOf(globalUri("zen_mode"))) { context.global("zen_mode", 0) != 0 }
    MetroText(stringResource(R.string.ssx_ringer), size = 22.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.ssx_ringer_normal to AudioManager.RINGER_MODE_NORMAL, R.string.ssx_ringer_vibrate to AudioManager.RINGER_MODE_VIBRATE, R.string.ssx_ringer_silent to AudioManager.RINGER_MODE_SILENT)
            .forEach { (label, m) ->
                PageChip(stringResource(label), mode == m, "ss:ringer:$m") {
                    if (runCatching { audio.ringerMode = m }.isFailure) com.metrowin.launcher.ui.actioncenter.MetroToast.show(context, R.string.ssx_failed)
                }
            }
    }
    ToggleSetting(label = stringResource(R.string.ss_quiet), checked = zen, onChange = { exec(context, "cmd", "notification", "set_dnd", if (it) "priority" else "off") }, tag = "ss:dnd")
}

@Composable
internal fun DisplayPage() {
    val context = LocalContext.current
    val brightness = watched(uris = listOf(systemUri("screen_brightness"))) { context.system("screen_brightness", 128) }
    val adaptive = watched(uris = listOf(systemUri("screen_brightness_mode"))) { context.system("screen_brightness_mode", 0) == 1 }
    val timeout = watched(uris = listOf(systemUri("screen_off_timeout"))) { context.system("screen_off_timeout", 30_000) }
    val rotate = watched(uris = listOf(systemUri("accelerometer_rotation"))) { context.system("accelerometer_rotation", 1) == 1 }
    val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    var level by remember { mutableIntStateOf(brightness) }
    var touched by remember { mutableLongStateOf(0L) }
    var request by remember { mutableIntStateOf(-1) }
    LaunchedEffect(brightness) { if (System.currentTimeMillis() - touched > 800) level = brightness }
    // One command per pause of the finger, not one per pixel of the drag (each is a process of the shell); the value the system itself moves (adaptive brightness) is never written back.
    LaunchedEffect(request) {
        if (request < 0) return@LaunchedEffect
        delay(250)
        exec(context, "settings", "put", "system", "screen_brightness", request.toString())
    }
    MetroText(stringResource(R.string.ssx_brightness, level * 100 / 255), size = 20.sp)
    MetroSlider(value = level / 255f, onChange = { touched = System.currentTimeMillis(); level = (it * 255).toInt().coerceIn(1, 255); request = level }, description = stringResource(R.string.ssx_brightness, level * 100 / 255))
    ToggleSetting(label = stringResource(R.string.ssx_auto_brightness), checked = adaptive, onChange = { exec(context, "settings", "put", "system", "screen_brightness_mode", flag(it)) }, tag = "ss:adaptive")
    MetroText(stringResource(R.string.ssx_timeout), size = 22.sp, modifier = Modifier.padding(top = 10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ScreenTimeouts.STEPS) { ms ->
            val (n, seconds) = ScreenTimeouts.parts(ms)
            PageChip(stringResource(if (seconds) R.string.ssx_seconds else R.string.ssx_minutes, n), ms == timeout, "ss:timeout:$ms") { exec(context, "settings", "put", "system", "screen_off_timeout", ms.toString()) }
        }
    }
    ToggleSetting(label = stringResource(R.string.ssx_rotate), checked = rotate, onChange = { exec(context, "settings", "put", "system", "accelerometer_rotation", flag(it)) }, tag = "ss:rotate")
    ToggleSetting(label = stringResource(R.string.ssx_dark), checked = dark, onChange = { exec(context, "cmd", "uimode", "night", if (it) "yes" else "no") }, tag = "ss:dark")
}

@Composable
internal fun SystemPage() {
    val context = LocalContext.current
    val airplane = watched(uris = listOf(globalUri("airplane_mode_on"))) { context.global("airplane_mode_on", 0) == 1 }
    val nfc = remember { NfcAdapter.getDefaultAdapter(context) }
    val nfcOn = watched(actions = listOf(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) { nfc?.isEnabled == true }
    val location = watched(actions = listOf(android.location.LocationManager.MODE_CHANGED_ACTION)) { context.getSystemService(android.location.LocationManager::class.java).isLocationEnabled }
    val saver = watched(actions = listOf(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)) { context.getSystemService(PowerManager::class.java).isPowerSaveMode }
    ToggleSetting(
        label = stringResource(R.string.ss_airplane), checked = airplane, onChange = { exec(context, "cmd", "connectivity", "airplane-mode", word(it)) },
        hint = stringResource(R.string.ssx_airplane_hint), tag = "ss:airplane",
    )
    ToggleSetting(label = stringResource(R.string.ss_nfc), checked = nfcOn, onChange = { exec(context, "svc", "nfc", word(it)) }, enabled = nfc != null, tag = "ss:nfc")
    ToggleSetting(label = stringResource(R.string.ss_location), checked = location, onChange = { exec(context, "cmd", "location", "set-location-enabled", it.toString()) }, tag = "ss:location")
    ToggleSetting(label = stringResource(R.string.ssx_saver), checked = saver, onChange = { exec(context, "settings", "put", "global", "low_power", flag(it)) }, tag = "ss:saver")
}

/** What the phone says about its networks, read only (the Wi-Fi reader waits for a callback, so it stays out of the main thread): the page of the reduced mode (no Shizuku). */
@Composable
internal fun InfoPage() {
    val context = LocalContext.current
    val rows = watched(fallbackMs = 5_000) { DeviceReaders.sim(context) + DeviceReaders.bluetooth(context) }
    rows.forEach { (label, value) -> InfoLine(label, value) }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetroText(label, size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(0.5f))
        // A long value (a device name, an address) goes on to the next line instead of being cut.
        MetroText(value, size = 16.sp, weight = FontWeight.Normal, maxLines = Int.MAX_VALUE, modifier = Modifier.weight(0.5f))
    }
}
