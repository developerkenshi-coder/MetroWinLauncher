package com.metrowin.launcher.data

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import android.view.Display
import com.metrowin.launcher.R
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/** Rows about the screen, the Wi-Fi, the SIM and the Bluetooth, read from Android itself (no shell). */
object DeviceReaders {
    private fun Context.s(id: Int, vararg args: Any) = getString(id, *args)

    private fun f(value: Float, decimals: Int = 1) = String.format(Locale.getDefault(), "%.${decimals}f", value)

    fun display(context: Context, renderRate: String?): List<Pair<String, String>> {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
        val metrics = context.resources.displayMetrics
        val mode = display.mode
        val rows = ArrayList<Pair<String, String>>()
        rows += context.s(R.string.mo_current_mode) to "${mode.physicalWidth} × ${mode.physicalHeight} @ ${f(mode.refreshRate)} Hz"
        renderRate?.let { rows += context.s(R.string.mo_render_rate) to "$it Hz" }
        display.supportedModes.sortedWith(compareBy({ -it.physicalWidth }, { -it.refreshRate })).forEach {
            rows += context.s(R.string.mo_modes) to "${it.physicalWidth} × ${it.physicalHeight} @ ${f(it.refreshRate)} Hz"
        }
        rows += context.s(R.string.mo_density) to "${metrics.densityDpi} dpi (${f(metrics.xdpi, 0)} × ${f(metrics.ydpi, 0)})"
        if (metrics.xdpi > 0 && metrics.ydpi > 0) {
            val w = metrics.widthPixels / metrics.xdpi
            val h = metrics.heightPixels / metrics.ydpi
            rows += context.s(R.string.mo_size) to "${f(sqrt(w * w + h * h))} in"
        }
        rows += context.s(R.string.mo_rotation) to "${display.rotation * 90}°"
        @Suppress("DEPRECATION")
        runCatching { display.hdrCapabilities?.supportedHdrTypes?.toList().orEmpty() }.getOrDefault(emptyList()).takeIf { it.isNotEmpty() }?.let { types ->
            rows += context.s(R.string.mo_hdr) to types.joinToString(", ") { hdrName(it) }
        }
        rows += context.s(R.string.mo_wide) to context.s(if (display.isWideColorGamut) R.string.ds_yes else R.string.ds_no)
        return rows
    }

    @Suppress("DEPRECATION")
    private fun hdrName(type: Int) = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
        else -> "#$type"
    }

    @Suppress("DEPRECATION")
    fun wifi(context: Context): List<Pair<String, String>> {
        val wifi = context.getSystemService(WifiManager::class.java) ?: return emptyList()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.allNetworks.firstOrNull { connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        val info = wifiInfo(context, wifi)
        if (network == null || info == null) {
            return listOf(context.s(R.string.mo_wifi_state) to context.s(if (wifi.isWifiEnabled) R.string.mo_not_connected else R.string.mo_wifi_off))
        }
        val rows = ArrayList<Pair<String, String>>()
        info.ssid?.takeIf { it != "<unknown ssid>" }?.let { rows += "SSID" to it.trim('"') }
        info.bssid?.takeIf { it != "02:00:00:00:00:00" }?.let { rows += "BSSID" to it }
        rows += context.s(R.string.mo_signal) to "${info.rssi} dBm"
        if (info.linkSpeed > 0) {
            val detail = if (Build.VERSION.SDK_INT >= 29) " (↑ ${info.txLinkSpeedMbps} · ↓ ${info.rxLinkSpeedMbps})" else ""
            rows += context.s(R.string.mo_link) to "${info.linkSpeed} Mbps$detail"
        }
        if (info.frequency > 0) rows += context.s(R.string.mo_band) to "${f(info.frequency / 1000f, 3)} GHz · ${context.s(R.string.mo_channel)} ${channel(info.frequency)}"
        if (Build.VERSION.SDK_INT >= 30) rows += context.s(R.string.mo_standard) to standard(info.wifiStandard)
        connectivity.getLinkProperties(network)?.let { link ->
            link.linkAddresses.forEach { rows += "IP" to (it.address.hostAddress ?: "") }
            link.routes.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress?.let { rows += "Gateway" to it }
            link.dnsServers.forEach { rows += "DNS" to (it.hostAddress ?: "") }
            link.domains?.takeIf { it.isNotBlank() }?.let { rows += "Domains" to it }
            if (Build.VERSION.SDK_INT >= 29 && link.mtu > 0) rows += "MTU" to link.mtu.toString()
        }
        return rows
    }

    /**
     * The Wi-Fi link as Android hands it to an app. From Android 12 `connectionInfo` is deprecated and hides the name and address of
     * the network unless the callback that delivers them asks for the location info (which still needs the location permission).
     * Blocking, up to [WIFI_WAIT_MS]: call it off the main thread. When the callback gives nothing, the old call answers.
     */
    @Suppress("DEPRECATION")
    private fun wifiInfo(context: Context, wifi: WifiManager): WifiInfo? {
        if (Build.VERSION.SDK_INT >= 31) {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val latch = CountDownLatch(1)
            var found: WifiInfo? = null
            val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    found = capabilities.transportInfo as? WifiInfo
                    latch.countDown()
                }
            }
            runCatching {
                connectivity.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
                latch.await(WIFI_WAIT_MS, TimeUnit.MILLISECONDS)
            }.logged("deviceReaders.wifiCallback")
            runCatching { connectivity.unregisterNetworkCallback(callback) }
            found?.let { return it }
        }
        return runCatching { wifi.connectionInfo }.getOrNull()
    }

    private const val WIFI_WAIT_MS = 800L

    /** The Wi-Fi channel of a frequency in MHz. */
    fun channel(mhz: Int): Int = when {
        mhz == 2484 -> 14
        mhz in 2400..2483 -> (mhz - 2407) / 5
        mhz in 5000..5895 -> (mhz - 5000) / 5
        mhz >= 5955 -> (mhz - 5950) / 5
        else -> 0
    }

    private fun standard(value: Int) = when (value) {
        1 -> "802.11a/b/g"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> "Wi-Fi 6 (802.11ax)"
        7 -> "Wi-Fi 7 (802.11be)"
        else -> "-"
    }

    @SuppressLint("MissingPermission") // dataNetworkType is read only when hasPhoneState says so
    @Suppress("DEPRECATION")
    fun sim(context: Context): List<Pair<String, String>> {
        val phone = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        val rows = ArrayList<Pair<String, String>>()
        phone.networkOperatorName?.takeIf { it.isNotBlank() }?.let { rows += context.s(R.string.mo_operator) to it }
        phone.simOperatorName?.takeIf { it.isNotBlank() }?.let { rows += context.s(R.string.mo_sim_operator) to it }
        rows += context.s(R.string.mo_sim_state) to when (phone.simState) {
            TelephonyManager.SIM_STATE_READY -> context.s(R.string.mo_sim_ready)
            TelephonyManager.SIM_STATE_ABSENT -> context.s(R.string.mo_sim_absent)
            else -> context.s(R.string.ds_unknown)
        }
        if (StatusIcons.hasPhoneState(context)) runCatching { networkType(phone.dataNetworkType) }.getOrNull()?.let { rows += context.s(R.string.mo_net_type) to it }
        rows += context.s(R.string.mo_roaming) to context.s(if (phone.isNetworkRoaming) R.string.ds_yes else R.string.ds_no)
        if (Build.VERSION.SDK_INT >= 29) runCatching { phone.signalStrength?.cellSignalStrengths }.getOrNull()?.forEach { cell ->
            rows += cell.javaClass.simpleName.removePrefix("CellSignalStrength") to "${cell.dbm} dBm · ${context.s(R.string.mo_level)} ${cell.level}/${CellSignalStrength.SIGNAL_STRENGTH_GREAT}"
        }
        return rows
    }

    @Suppress("DEPRECATION")
    private fun networkType(type: Int) = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
        TelephonyManager.NETWORK_TYPE_LTE -> "4G (LTE)"
        TelephonyManager.NETWORK_TYPE_NR -> "5G (NR)"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> null
        else -> "#$type"
    }

    @SuppressLint("MissingPermission") // `allowed` below checks BLUETOOTH_CONNECT
    fun bluetooth(context: Context): List<Pair<String, String>> {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return listOf(context.s(R.string.mo_bt_state) to context.s(R.string.mo_bt_none))
        val rows = ArrayList<Pair<String, String>>()
        rows += context.s(R.string.mo_bt_state) to context.s(if (adapter.isEnabled) R.string.toggle_on else R.string.toggle_off)
        val allowed = Build.VERSION.SDK_INT < 31 || LivePermissions.isGranted(context, android.Manifest.permission.BLUETOOTH_CONNECT)
        if (allowed && adapter.isEnabled) {
            runCatching { adapter.bondedDevices }.getOrNull()?.forEach { rows += context.s(R.string.mo_bt_bonded) to (it.name ?: it.address) }
        }
        return rows
    }

    /** The memory as Android reports it to an app: total, free, and whether it is short. */
    fun memory(context: Context): ActivityManager.MemoryInfo = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
}
