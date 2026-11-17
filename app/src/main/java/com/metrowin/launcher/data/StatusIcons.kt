package com.metrowin.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.metrowin.launcher.R

/** What the status bar can show, each of which the user can turn off (Settings, Status bar). [id] is what is kept in the settings. */
enum class StatusItem(val id: String, @param:StringRes val label: Int) {
    Notifications("notifications", R.string.statusbar_notifications),
    Airplane("airplane", R.string.statusbar_airplane),
    Hotspot("hotspot", R.string.statusbar_hotspot),
    Vpn("vpn", R.string.statusbar_vpn),
    Location("location", R.string.statusbar_location),
    Bluetooth("bluetooth", R.string.statusbar_bluetooth),
    Alarm("alarm", R.string.statusbar_alarm),
    Sound("sound", R.string.statusbar_sound),
    Dnd("dnd", R.string.statusbar_dnd),
    Saver("saver", R.string.statusbar_saver),
    Signal("signal", R.string.statusbar_signal),
    Network("network", R.string.statusbar_network),
    Roaming("roaming", R.string.statusbar_roaming),
    DataSim("dataSim", R.string.statusbar_data_sim),
    Wifi("wifi", R.string.statusbar_wifi),
    BatteryPercent("batteryPercent", R.string.statusbar_battery_percent),
    Battery("battery", R.string.statusbar_battery),
    Time("time", R.string.statusbar_time);

    companion object {
        /** The ids kept in the settings as one text, [hidden] being what the user turned off. */
        fun hiddenIds(hidden: String): Set<String> = hidden.split(',').filter { it.isNotBlank() }.toSet()

        /** The text to keep when [item] is shown, or not, given what was hidden. */
        fun withShown(hidden: String, item: StatusItem, shown: Boolean): String {
            val ids = hiddenIds(hidden) - item.id
            return (if (shown) ids else ids + item.id).sorted().joinToString(",")
        }
    }
}

/** Whether the ringer is on, vibrating or off. */
enum class Ringer { Normal, Vibrate, Silent }

/** What the phone says now besides its mobile signal (see [CellWatcher]). Wi-Fi is 0 to 4 bars, null while not connected. */
data class StatusReading(
    val wifi: Int? = null,
    val bluetooth: Boolean = false,
    val airplane: Boolean = false,
    val hotspot: Boolean = false,
    val vpn: Boolean = false,
    val location: Boolean = false,
    val alarm: Boolean = false,
    val ringer: Ringer = Ringer.Normal,
    val dnd: Boolean = false,
    val saver: Boolean = false,
)

/**
 * The mobile signal (0 to 4 bars, -1 without a SIM) and the kind of network ("5G", "4G", "3G", "E"...), null when it is not known, for the SIM the phone
 * calls the default one ([slot] is its slot, 0 or 1). [roaming]: that SIM is on a foreign network. [data]: the mobile data goes through it (told only
 * with two SIMs).
 */
data class CellState(
    val level: Int = -1, val label: String? = null, val second: SecondSim? = null,
    val roaming: Boolean = false, val data: Boolean = false, val slot: Int = 0,
) {
    /**
     * What the bar shows as the main SIM when the user chose the SIM of slot [preferred] (1 or 2; 0 leaves the choice to the phone): with two SIMs and
     * the chosen one being the other, the two change places. With one SIM, or a choice that is already the main one, nothing changes.
     */
    fun shown(preferred: Int): CellState {
        val other = second ?: return this
        if (preferred <= 0 || other.slot + 1 != preferred) return this
        return CellState(other.level, other.label, SecondSim(level, label, slot, roaming, data), other.roaming, other.data, other.slot)
    }
}

/** The other SIM, when the phone has two: its signal (0 to 4 bars) and kind of network, shown small under the main ones; its [slot], and whether it roams and carries the data. */
data class SecondSim(val level: Int, val label: String?, val slot: Int = 1, val roaming: Boolean = false, val data: Boolean = false)

object StatusIcons {
    /**
     * The label of the kind of network, as the bar of Android would say it: "5G" also for the 5G that rides on 4G (the display info says
     * so), "4G+" for carrier aggregation, "H" and "H+" for HSPA, "3G", "E" for EDGE, "G" for GPRS. Pure, so it is tested.
     * The numbers are those of [TelephonyManager] and [TelephonyDisplayInfo], copied so that this builds and tests without a phone.
     */
    fun networkLabel(type: Int, override: Int = 0): String? = when {
        override == OVERRIDE_NR_NSA || override == OVERRIDE_NR_ADVANCED -> "5G"
        override == OVERRIDE_LTE_CA || override == OVERRIDE_LTE_ADVANCED_PRO -> "4G+"
        else -> when (type) {
            20 -> "5G"
            13 -> "4G"
            19 -> "4G+"
            15 -> "H+"
            8, 9, 10 -> "H"
            3, 5, 6, 12, 14, 17 -> "3G"
            2 -> "E"
            1 -> "G"
            4, 7, 11 -> "1X"
            else -> null
        }
    }

    private const val OVERRIDE_LTE_CA = 1
    private const val OVERRIDE_LTE_ADVANCED_PRO = 2
    private const val OVERRIDE_NR_NSA = 3
    private const val OVERRIDE_NR_ADVANCED = 4

    fun hasPhoneState(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Each reading in its own try: one thing the phone refuses to say never takes the rest of the bar with it. */
    @Suppress("DEPRECATION")
    fun read(context: Context): StatusReading {
        val resolver = context.contentResolver
        val wifi = runCatching {
            val manager = context.applicationContext.getSystemService(WifiManager::class.java)
            val info = manager.connectionInfo
            if (!manager.isWifiEnabled || info == null || info.networkId == -1 || info.rssi <= -127) null
            else if (Build.VERSION.SDK_INT >= 30) manager.calculateSignalLevel(info.rssi).coerceIn(0, 4)
            else WifiManager.calculateSignalLevel(info.rssi, 5)
        }.getOrNull()
        val hotspot = runCatching {
            val manager = context.applicationContext.getSystemService(WifiManager::class.java)
            manager.javaClass.getMethod("isWifiApEnabled").invoke(manager) as? Boolean ?: false
        }.getOrDefault(false)
        val vpn = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.getOrDefault(false)
        val ringer = runCatching {
            when (context.getSystemService(AudioManager::class.java).ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> Ringer.Silent
                AudioManager.RINGER_MODE_VIBRATE -> Ringer.Vibrate
                else -> Ringer.Normal
            }
        }.getOrDefault(Ringer.Normal)
        return StatusReading(
            wifi = wifi,
            bluetooth = runCatching { Settings.Global.getInt(resolver, "bluetooth_on", 0) == 1 }.getOrDefault(false),
            airplane = runCatching { Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }.getOrDefault(false),
            hotspot = hotspot,
            vpn = vpn,
            location = runCatching { LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java)) }.getOrDefault(false),
            alarm = runCatching { context.getSystemService(AlarmManager::class.java).nextAlarmClock != null }.getOrDefault(false),
            ringer = ringer,
            dnd = runCatching {
                context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
                    .let { it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
            }.getOrDefault(false),
            saver = runCatching { context.getSystemService(PowerManager::class.java).isPowerSaveMode }.getOrDefault(false),
        )
    }
}

/**
 * Listens to the mobile signal and the kind of network, which the phone tells as they change (so the bars are the real ones, not the last
 * ones that were read). The kind of network needs the phone state permission; without it the label stays empty and the bars still work.
 */
@Suppress("DEPRECATION")
class CellWatcher(private val context: Context, private val onChange: (CellState) -> Unit) {
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private var state = CellState()
    private var signalCallback: Any? = null
    private var displayCallback: Any? = null
    private var legacy: PhoneStateListener? = null

    private fun update(next: CellState) {
        if (next == state) return
        state = next
        onChange(next)
    }

    private fun levelNow(): Int = runCatching {
        if (telephony != null && telephony.simState == TelephonyManager.SIM_STATE_READY) (if (Build.VERSION.SDK_INT >= 28) telephony.signalStrength?.level ?: 0 else 0) else -1
    }.getOrDefault(-1)

    fun start() {
        val manager = telephony ?: return
        update(withSims(state.copy(level = levelNow()), readSims()))
        val executor = ContextCompat.getMainExecutor(context)
        if (Build.VERSION.SDK_INT >= 31) {
            val signal = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = update(state.copy(level = levelOf(signalStrength)))
            }
            if (runCatching { manager.registerTelephonyCallback(executor, signal) }.isSuccess) signalCallback = signal
            if (StatusIcons.hasPhoneState(context)) {
                val display = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) =
                        update(state.copy(label = StatusIcons.networkLabel(telephonyDisplayInfo.networkType, telephonyDisplayInfo.overrideNetworkType)))
                }
                if (runCatching { manager.registerTelephonyCallback(executor, display) }.isSuccess) displayCallback = display
            }
        } else {
            val listener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = update(state.copy(level = levelOf(signalStrength)))
            }
            if (runCatching { manager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) }.isSuccess) legacy = listener
        }
    }

    private fun levelOf(strength: SignalStrength): Int =
        if (telephony != null && telephony.simState == TelephonyManager.SIM_STATE_READY) strength.level else -1

    /** Where the phone cannot tell the display info (before Android 12), the kind of network is asked for now and then. */
    @SuppressLint("MissingPermission") // hasPhoneState checked on the next line
    fun poll() {
        val manager = telephony ?: return
        val sims = readSims()
        if (displayCallback != null || !StatusIcons.hasPhoneState(context)) {
            update(withSims(state, sims))
            return
        }
        val label = runCatching { StatusIcons.networkLabel(manager.dataNetworkType) }.getOrNull()
        update(withSims(CellState(levelNow().takeIf { signalCallback == null && legacy == null } ?: state.level, label), sims))
    }

    /** What [readSims] found, written into [base]. */
    private fun withSims(base: CellState, sims: Sims) = base.copy(second = sims.second, slot = sims.slot, roaming = sims.roaming, data = sims.data)

    private class Sims(val second: SecondSim?, val slot: Int, val roaming: Boolean, val data: Boolean)

    /**
     * The SIM that is not the default one, read now (the signal level of the phone and the kind of network of its data connection), with the slot of
     * the default one and which of them roams and carries the data. No second SIM with one SIM or without the phone permission.
     * shortcut: polled with the rest of the bar, and without the 5G that rides on 4G (that needs a callback for each SIM); add the callbacks if it is missed.
     */
    @SuppressLint("MissingPermission") // hasPhoneState checked first
    private fun readSims(): Sims {
        val roaming = runCatching { telephony?.isNetworkRoaming == true }.getOrDefault(false)
        return runCatching {
            if (!StatusIcons.hasPhoneState(context)) return@runCatching Sims(null, 0, roaming, false)
            val subs = context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
            if (subs.size < 2) return@runCatching Sims(null, 0, roaming, false)
            val main = SubscriptionManager.getDefaultSubscriptionId()
            val data = SubscriptionManager.getDefaultDataSubscriptionId()
            val other = subs.sortedBy { it.simSlotIndex }.firstOrNull { it.subscriptionId != main } ?: return@runCatching Sims(null, 0, roaming, false)
            val slot = subs.firstOrNull { it.subscriptionId == main }?.simSlotIndex ?: 0
            val sim = telephony?.createForSubscriptionId(other.subscriptionId)?.takeIf { it.simState == TelephonyManager.SIM_STATE_READY }
                ?: return@runCatching Sims(null, slot, roaming, false)
            val second = SecondSim(
                (if (Build.VERSION.SDK_INT >= 28) sim.signalStrength?.level else null) ?: 0, StatusIcons.networkLabel(sim.dataNetworkType),
                other.simSlotIndex, sim.isNetworkRoaming, data == other.subscriptionId,
            )
            Sims(second, slot, roaming, data == main)
        }.getOrDefault(Sims(null, 0, roaming, false))
    }

    fun stop() {
        val manager = telephony ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            (signalCallback as? TelephonyCallback)?.let { runCatching { manager.unregisterTelephonyCallback(it) } }
            (displayCallback as? TelephonyCallback)?.let { runCatching { manager.unregisterTelephonyCallback(it) } }
        } else {
            legacy?.let { runCatching { manager.listen(it, PhoneStateListener.LISTEN_NONE) } }
        }
        signalCallback = null
        displayCallback = null
        legacy = null
    }
}
