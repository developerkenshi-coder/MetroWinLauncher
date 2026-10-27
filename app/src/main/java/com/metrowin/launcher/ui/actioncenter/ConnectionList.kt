package com.metrowin.launcher.ui.actioncenter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BluetoothPeer
import com.metrowin.launcher.data.ConnectionAccess
import com.metrowin.launcher.data.ConnectionPanel
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.WifiNetwork
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors

/** The longest the list of networks or devices gets: the system screen of the button has the rest. */
private const val MAX_ROWS = 8

/**
 * What a long press on the Wi-Fi or Bluetooth button opens under the buttons: the networks around you, or the devices that are
 * connected and those that are paired but not connected. A touch on a line opens the settings of Android, where it is switched
 * on; a touch on the heading closes the list.
 *
 * [on] says whether Wi-Fi or Bluetooth is switched on. [onAllow] asks for what Android wants before it tells the names.
 */
@Composable
fun ConnectionList(
    action: QuickAction,
    panel: ConnectionPanel,
    on: Boolean?,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMetroColors.current
    val wifi = action == QuickAction.Wifi
    val access = if (wifi) panel.wifiAccess else panel.bluetoothAccess
    Column(modifier.fillMaxWidth().background(colors.surface).padding(vertical = 4.dp).testTag("connections:${action.id}")) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).metroPress(onClick = onClose).padding(vertical = 8.dp).testTag("connections:close"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(action.glyph, 22.dp, color = LocalAccent.current)
                Spacer(Modifier.width(10.dp))
                MetroText(stringResource(if (wifi) R.string.ac_wifi_networks else R.string.ac_bluetooth_devices), size = 20.sp)
            }
            LinkButton(onOpenSettings, "connections:settings") {
                MetroText(stringResource(R.string.ac_conn_settings), size = 15.sp, weight = FontWeight.Normal, color = LocalAccent.current)
            }
        }
        when {
            // Wi-Fi that is off has nothing to list; Bluetooth that is off still has the devices it is paired with.
            wifi && on == false -> Note(stringResource(R.string.ac_wifi_off))
            access != ConnectionAccess.Ok -> {
                Note(stringResource(accessNote(action, access)))
                MetroButton(
                    stringResource(if (access == ConnectionAccess.NeedsLocation) R.string.ac_conn_location else R.string.ac_conn_allow),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("connections:allow"),
                    onClick = onAllow,
                )
            }
            wifi -> {
                if (panel.networks.isEmpty()) Note(stringResource(R.string.ac_wifi_none))
                panel.networks.take(MAX_ROWS).forEach { NetworkRow(it, onOpenSettings) }
            }
            else -> {
                if (on == false) Note(stringResource(R.string.ac_bluetooth_off))
                else if (panel.devices.isEmpty()) Note(stringResource(R.string.ac_bluetooth_none))
                panel.devices.take(MAX_ROWS).forEach { DeviceRow(it, onOpenSettings) }
            }
        }
    }
}

private fun accessNote(action: QuickAction, access: ConnectionAccess): Int = when {
    access == ConnectionAccess.NeedsLocation -> R.string.ac_wifi_needs_location
    action == QuickAction.Wifi -> R.string.ac_wifi_needs_permission
    else -> R.string.ac_bluetooth_needs_permission
}

@Composable
private fun LinkButton(onClick: () -> Unit, tag: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.metroPress(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp).testTag(tag)) { content() }
}

@Composable
private fun Note(text: String) {
    MetroText(
        text, size = 15.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, lineHeight = 21.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun NetworkRow(network: WifiNetwork, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val subtitle = when {
        network.connected -> R.string.ac_conn_connected
        network.secured -> R.string.ac_wifi_secured
        else -> R.string.ac_wifi_open
    }
    Row(
        Modifier.fillMaxWidth().metroPress(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp).testTag("network:${network.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SignalBars(network.bars, if (network.connected) accent else colors.foreground, colors.inactive)
        Column(Modifier.weight(1f)) {
            MetroText(network.name, size = 17.sp, weight = FontWeight.Normal, color = if (network.connected) accent else colors.foreground, maxLines = 1)
            MetroText(stringResource(subtitle), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
        }
    }
}

@Composable
private fun DeviceRow(device: BluetoothPeer, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().metroPress(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp).testTag("device:${device.address}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphIcon(Glyph.Bluetooth, 22.dp, color = if (device.connected) accent else colors.subtle)
        Column(Modifier.weight(1f)) {
            MetroText(device.name, size = 17.sp, weight = FontWeight.Normal, color = if (device.connected) accent else colors.foreground, maxLines = 1)
            MetroText(
                stringResource(if (device.connected) R.string.ac_conn_connected else R.string.ac_bluetooth_paired),
                size = 13.sp, weight = FontWeight.Normal, color = colors.subtle,
            )
        }
    }
}

/** Four bars that grow, [bars] of them in [color] and the rest in [dim]. */
@Composable
private fun SignalBars(bars: Int, color: Color, dim: Color) {
    Canvas(Modifier.size(22.dp)) {
        // Four bars and the three gaps between them, all the same width.
        val unit = size.width / 7f
        repeat(4) { i ->
            val height = size.height * (i + 1) / 4f
            drawRect(
                color = if (i < bars) color else dim,
                topLeft = Offset(i * 2 * unit, size.height - height),
                size = Size(unit, height),
            )
        }
    }
}
