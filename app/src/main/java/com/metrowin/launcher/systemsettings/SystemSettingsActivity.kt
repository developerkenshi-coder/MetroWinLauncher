package com.metrowin.launcher.systemsettings

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import com.metrowin.launcher.shizukuterminal.ShizukuState
import com.metrowin.launcher.ui.mini.MiniActivity

/**
 * The settings of the phone in the look of the launcher: Wi-Fi, mobile network, Bluetooth, date and time, sounds. They need Shizuku to switch what
 * Android keeps to itself; without it this screen hands over to the settings of Android at once, as the Settings tile always did.
 */
class SystemSettingsActivity : MiniActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        // null: still looking. Shizuku hands its connection over a moment after the process starts, so a "no" is only given after a few tries.
        var ready by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(Unit) {
            repeat(12) {
                if (ShizukuState.read(context).ready) {
                    ready = true
                    return@LaunchedEffect
                }
                delay(250)
            }
            ready = false
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            finishAndRemoveTask()
        }
        if (ready == true) SystemSettingsScreen()
    }
}
