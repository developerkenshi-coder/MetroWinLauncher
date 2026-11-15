package com.metrowin.launcher.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

/** What the info tiles ask of the phone about where it is: the Wi-Fi it is on, a Bluetooth audio device (a car, headphones). */
object InfoDevice {
    /** The name of the Wi-Fi network the phone is on, without quotes; empty when it is on none, or Android hides the name (it needs the position permission). */
    fun wifiName(context: Context): String = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return ""
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ""
        val info = if (Build.VERSION.SDK_INT >= 29) {
            capabilities.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
        }
        clean(info?.ssid)
    }.getOrDefault("")

    /** The name as written by Android: with quotes, or the word it uses for a name it does not give. */
    fun clean(raw: String?): String = raw.orEmpty().trim().trim('"').takeIf { it.isNotEmpty() && it != "<unknown ssid>" }.orEmpty()

    /** A Bluetooth audio device is connected as an output now (no permission needed). */
    fun bluetoothAudio(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(AudioManager::class.java)
        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }.getOrDefault(false)
}
