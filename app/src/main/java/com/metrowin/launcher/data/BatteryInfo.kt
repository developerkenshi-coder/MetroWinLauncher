package com.metrowin.launcher.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.Immutable

enum class BatteryState { Discharging, Charging, Full }

/** The charge of the battery, for the tile of Battery Saver. */
@Immutable
data class BatteryInfo(
    val percent: Int,
    val state: BatteryState,
    /** Minutes left to a full battery, known only while charging and only on Android 9 and later. */
    val minutesToFull: Int? = null,
) {
    companion object {
        /** From the values of the system's battery broadcast. [chargeTimeMillis] is -1 when the phone cannot say. */
        fun from(level: Int, scale: Int, status: Int, chargeTimeMillis: Long = -1L): BatteryInfo {
            val percent = if (scale <= 0 || level < 0) 0 else (level * 100 / scale).coerceIn(0, 100)
            val state = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> BatteryState.Charging
                BatteryManager.BATTERY_STATUS_FULL -> BatteryState.Full
                else -> BatteryState.Discharging
            }
            val minutes = if (state == BatteryState.Charging && chargeTimeMillis > 0) {
                ((chargeTimeMillis + 59_999) / 60_000).toInt()
            } else {
                null
            }
            return BatteryInfo(percent, state, minutes)
        }

        /** "35 min", "1 h 05 min": units that read the same in most languages. */
        fun duration(minutes: Int): String {
            val m = minutes.coerceAtLeast(0)
            return if (m < 60) "$m min" else "${m / 60} h ${(m % 60).toString().padStart(2, '0')} min"
        }

        /** The latest reading, from the sticky broadcast: no receiver needed. Null when the phone gives none. */
        fun read(context: Context): BatteryInfo? {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
            return from(intent, context)
        }

        fun from(intent: Intent, context: Context): BatteryInfo {
            val manager = context.getSystemService(BatteryManager::class.java)
            val chargeTime = if (Build.VERSION.SDK_INT >= 28) manager?.computeChargeTimeRemaining() ?: -1L else -1L
            return from(
                level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100),
                status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
                chargeTimeMillis = chargeTime,
            )
        }
    }
}
