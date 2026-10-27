package com.metrowin.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** [bars] go from 0 to 4. */
data class WifiNetwork(val name: String, val bars: Int, val secured: Boolean, val connected: Boolean = false)

data class BluetoothPeer(val name: String, val address: String, val connected: Boolean)

/** What Android still wants before a list can show names. */
enum class ConnectionAccess { Ok, NeedsPermission, NeedsLocation }

data class ConnectionPanel(
    val wifiName: String? = null,
    val networks: List<WifiNetwork> = emptyList(),
    val wifiAccess: ConnectionAccess = ConnectionAccess.Ok,
    val devices: List<BluetoothPeer> = emptyList(),
    val bluetoothAccess: ConnectionAccess = ConnectionAccess.Ok,
) {
    val wifiCaption: String? get() = wifiName

    val bluetoothCaption: String? get() = Connections.bluetoothCaption(devices.filter { it.connected }.map { it.name })

    fun captionFor(action: QuickAction): String? = when (action) {
        QuickAction.Wifi -> wifiCaption
        QuickAction.Bluetooth -> bluetoothCaption
        else -> null
    }
}

/** The Bluetooth devices are asked about at once and then once in this many reads of the panel (which come every second). */
const val CONNECTION_DEVICES_EVERY = 5

object Connections {
    /** The signal in dBm as 0 to 4 bars: -100 or less is none, -55 or more is full. */
    fun bars(rssi: Int): Int = (rssi.coerceIn(-100, -55) + 100) * 4 / 45

    /** True when the network asks for a key, from the capabilities a scan reports ("[WPA2-PSK-CCMP][ESS]"). */
    fun isSecured(capabilities: String): Boolean = listOf("WEP", "WPA", "PSK", "SAE", "EAP").any { capabilities.contains(it) }

    /** The name Android gives the connected network: without the quotes it puts around it, and null when it hides it. */
    fun cleanSsid(raw: String?): String? {
        val name = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
        return name.takeIf { it.isNotEmpty() && !it.equals("<unknown ssid>", ignoreCase = true) && it != "0x" }
    }

    /**
     * The networks of a scan as a list: one line per name (the strongest of the access points that share it), the one the
     * phone is on first, then the others by signal. The network the phone is on is there even if the scan missed it
     * (Android limits the scans), with the signal it has now in [connectedBars].
     */
    fun networks(found: List<WifiNetwork>, connected: String?, connectedBars: Int = 0): List<WifiNetwork> =
        found.filter { it.name.isNotBlank() }
            .groupBy { it.name }
            .map { (name, same) -> same.maxBy { it.bars }.copy(connected = name == connected) }
            .let { all ->
                if (connected != null && all.none { it.connected }) all + WifiNetwork(connected, connectedBars, secured = false, connected = true) else all
            }
            .sortedWith(compareByDescending<WifiNetwork> { it.connected }.thenByDescending { it.bars }.thenBy { it.name.lowercase() })

    fun devices(paired: List<BluetoothPeer>, connected: List<BluetoothPeer>): List<BluetoothPeer> {
        val connectedAddresses = connected.map { it.address }.toSet()
        val all = paired.map { it.copy(connected = it.address in connectedAddresses) } +
            connected.filter { c -> paired.none { it.address == c.address } }.map { it.copy(connected = true) }
        return all.distinctBy { it.address }.sortedWith(compareByDescending<BluetoothPeer> { it.connected }.thenBy { it.name.lowercase() })
    }

    fun bluetoothCaption(connectedNames: List<String>): String? {
        val names = connectedNames.filter { it.isNotBlank() }
        return when (names.size) {
            0 -> null
            1 -> names[0]
            else -> "${names[0]} +${names.size - 1}"
        }
    }
}

/** Android shows the names only to an app with the location access (Wi-Fi) or the nearby devices access (Bluetooth, from Android 12). */
class ConnectionController(private val context: Context) {
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private val wifi get() = context.applicationContext.getSystemService(WifiManager::class.java)
    private val bluetooth get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    /** The Bluetooth devices are read only when [devices] is true: Android answers for each profile in turn. */
    suspend fun read(previous: ConnectionPanel, devices: Boolean): ConnectionPanel = previous.copy(
        wifiName = wifiName(),
        networks = networks(),
        wifiAccess = wifiAccess(),
        devices = if (devices) bluetoothDevices() else previous.devices,
        bluetoothAccess = bluetoothAccess(),
    )

    /** Wi-Fi names need the location access, and Android 8.1 and later also want the location switched on. */
    fun wifiAccess(): ConnectionAccess = when {
        !granted(Manifest.permission.ACCESS_FINE_LOCATION) -> ConnectionAccess.NeedsPermission
        !runCatching { LocationManagerCompat.isLocationEnabled(context.getSystemService(android.location.LocationManager::class.java)) }.getOrDefault(true) ->
            ConnectionAccess.NeedsLocation
        else -> ConnectionAccess.Ok
    }

    /** From Android 12 the nearby devices access is needed; before, the Bluetooth permission is enough. */
    fun bluetoothAccess(): ConnectionAccess =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_CONNECT)) ConnectionAccess.Ok
        else ConnectionAccess.NeedsPermission

    fun permissionFor(action: QuickAction): String? = when (action) {
        QuickAction.Wifi -> Manifest.permission.ACCESS_FINE_LOCATION
        QuickAction.Bluetooth -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null
        else -> null
    }

    @Suppress("DEPRECATION")
    private fun link(): Pair<String, Int>? = runCatching {
        val manager = wifi
        if (manager == null || !manager.isWifiEnabled || wifiAccess() != ConnectionAccess.Ok) return@runCatching null
        val info = manager.connectionInfo
        val name = if (info == null || info.networkId == -1) null else Connections.cleanSsid(info.ssid)
        name?.let { it to Connections.bars(info.rssi) }
    }.getOrNull()

    fun wifiName(): String? = link()?.first

    /** Asks Android for a new scan; it may refuse (it limits them), and the list then shows the last one it has. */
    @Suppress("DEPRECATION")
    fun scan() {
        if (wifiAccess() == ConnectionAccess.Ok) runCatching { wifi?.startScan() }
    }

    @Suppress("DEPRECATION")
    fun networks(): List<WifiNetwork> {
        if (wifiAccess() != ConnectionAccess.Ok) return emptyList()
        val found = runCatching {
            wifi?.scanResults.orEmpty().map { WifiNetwork(it.SSID.orEmpty(), Connections.bars(it.level), Connections.isSecured(it.capabilities.orEmpty())) }
        }.getOrDefault(emptyList())
        val link = link()
        return Connections.networks(found, link?.first, link?.second ?: 0)
    }

    @SuppressLint("MissingPermission")
    suspend fun bluetoothDevices(): List<BluetoothPeer> {
        val adapter = bluetooth ?: return emptyList()
        if (bluetoothAccess() != ConnectionAccess.Ok) return emptyList()
        val paired = runCatching { adapter.bondedDevices.orEmpty().map(::peer) }.getOrDefault(emptyList())
        val connected = if (adapter.isEnabled) {
            PROFILES.flatMap { connectedOn(it) }.map { peer(it).copy(connected = true) }
        } else {
            emptyList()
        }
        return Connections.devices(paired, connected)
    }

    @SuppressLint("MissingPermission")
    private fun peer(device: BluetoothDevice): BluetoothPeer {
        val name = runCatching {
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) device.alias else null) ?: device.name
        }.getOrNull()
        return BluetoothPeer(name?.takeIf { it.isNotBlank() } ?: device.address.orEmpty(), device.address.orEmpty(), connected = false)
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectedOn(profile: Int): List<BluetoothDevice> {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
        if (profile == BluetoothProfile.GATT) return runCatching { manager.getConnectedDevices(BluetoothProfile.GATT) }.getOrDefault(emptyList())
        val adapter = manager.adapter ?: return emptyList()
        var proxy: BluetoothProfile? = null
        val devices = withTimeoutOrNull(PROXY_WAIT_MS) {
            suspendCancellableCoroutine<List<BluetoothDevice>> { found ->
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(type: Int, service: BluetoothProfile) {
                        proxy = service
                        if (found.isActive) found.resume(runCatching { service.connectedDevices }.getOrDefault(emptyList()))
                    }

                    override fun onServiceDisconnected(type: Int) {}
                }
                if (!runCatching { adapter.getProfileProxy(context, listener, profile) }.getOrDefault(false)) found.resume(emptyList())
            }
        }
        proxy?.let { runCatching { adapter.closeProfileProxy(profile, it) } }
        return devices.orEmpty()
    }

    private companion object {
        const val PROXY_WAIT_MS = 1_500L

        /** Audio (music, calls), hearing aids (Android 10) and low energy (watches, trackers). */
        val PROFILES: List<Int> = buildList {
            add(BluetoothProfile.A2DP)
            add(BluetoothProfile.HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(BluetoothProfile.HEARING_AID)
            add(BluetoothProfile.GATT)
        }
    }
}
