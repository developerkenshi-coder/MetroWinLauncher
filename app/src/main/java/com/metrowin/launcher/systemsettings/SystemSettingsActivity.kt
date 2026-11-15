package com.metrowin.launcher.systemsettings

import com.metrowin.launcher.ui.actioncenter.MetroToast
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.metrowin.launcher.R
import com.metrowin.launcher.shizukuterminal.ShizukuState
import com.metrowin.launcher.ui.mini.MiniActivity
import kotlinx.coroutines.delay

/** Why Shizuku cannot help yet, in a line for the user. */
@StringRes
private fun ShizukuState.reason(): Int = when (this) {
    ShizukuState.Missing -> R.string.ssx_shizuku_missing
    ShizukuState.Stopped -> R.string.ssx_shizuku_stopped
    ShizukuState.NotAllowed -> R.string.ssx_shizuku_denied
    ShizukuState.Ready -> R.string.st_done
}

/**
 * The settings of the phone in the look of the launcher: Wi-Fi, mobile network, Bluetooth, date and time, sounds. They need Shizuku to switch what
 * Android keeps to itself; without it this screen says why and hands over to the settings of Android, as the Settings tile always did
 * (or, with the option on, shows what public APIs can do).
 */
class SystemSettingsActivity : MiniActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        // null: still looking. Shizuku hands its connection over a moment after the process starts, so a "no" is only given after a few tries.
        var ready by remember { mutableStateOf<Boolean?>(null) }
        var attempt by remember { mutableIntStateOf(0) }
        LaunchedEffect(attempt) {
            ready = null
            repeat(12) {
                if (ShizukuState.read(context).ready) {
                    ready = true
                    return@LaunchedEffect
                }
                delay(250)
            }
            val state = ShizukuState.read(context)
            if (state.ready) {
                ready = true
                return@LaunchedEffect
            }
            if (SettingsPrefs.on(context, Option.Reduced)) {
                ready = false
                return@LaunchedEffect
            }
            // The screen goes away at once, so the plain Android toast carries the reason over to the settings of Android.
            MetroToast.show(applicationContext, state.reason())
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            // shortcut: finishAndRemoveTask (not finish) on purpose: finish() would bring the launcher in front of the settings of Android; upgrade if a hand-over without that exists.
            finishAndRemoveTask()
        }
        when (ready) {
            true -> SystemSettingsScreen()
            false -> SystemSettingsScreen(reduced = true, onRetry = { attempt++ })
            null -> SettingsSplash()
        }
    }
}
