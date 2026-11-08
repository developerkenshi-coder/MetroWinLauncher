package com.metrowin.launcher.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.compose.runtime.Immutable
import kotlin.math.abs

/** What the System tile shows: the power the battery moves, its heat, the memory in use, the signal of the Wi-Fi and of the mobile network. Null where the phone does not say. */
@Immutable
class SystemStats(val watts: Float?, val tempC: Float?, val charging: Boolean, val ramPercent: Int, val wifiDbm: Int?, val mobileDbm: Int?) {
    companion object {
        /** Blocking, cheap (the sticky battery broadcast and a few services): call it off the main thread. */
        @Suppress("DEPRECATION")
        fun read(context: Context): SystemStats {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val manager = context.getSystemService(BatteryManager::class.java)
            val microAmps = runCatching { manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull()?.takeIf { it != Long.MIN_VALUE && it != 0L }
            val millivolts = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val memory = DeviceReaders.memory(context)
            val wifi = runCatching { context.getSystemService(WifiManager::class.java).connectionInfo.rssi }.getOrNull()?.takeIf { it > -127 && it < 0 }
            val mobile = if (Build.VERSION.SDK_INT >= 29) {
                runCatching { context.getSystemService(TelephonyManager::class.java).signalStrength?.cellSignalStrengths?.firstOrNull()?.dbm }.getOrNull()
                    ?.takeIf { it < 0 && it > -140 }
            } else null
            return SystemStats(
                watts = if (microAmps != null && millivolts > 0) abs(microAmps) / 1000f * millivolts / 1_000_000f else null,
                tempC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f },
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                ramPercent = if (memory.totalMem > 0) ((memory.totalMem - memory.availMem) * 100 / memory.totalMem).toInt() else 0,
                wifiDbm = wifi,
                mobileDbm = mobile,
            )
        }
    }
}
