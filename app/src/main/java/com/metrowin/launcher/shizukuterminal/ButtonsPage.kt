package com.metrowin.launcher.shizukuterminal

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import com.metrowin.launcher.data.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.ShizukuButtons
import com.metrowin.launcher.data.ShizukuRadio
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.settings.ToggleSetting
import com.metrowin.launcher.ui.theme.LocalAccent

/** "Button permissions": how to turn Shizuku on, and which buttons of the Action Center work on the spot. */
@Composable
fun ButtonsPage(state: ShizukuState) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(ShizukuButtons.chosen(context)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MetroText(
            stringResource(
                when (state) {
                    ShizukuState.Ready -> R.string.st_state_ready
                    ShizukuState.NotAllowed -> R.string.st_state_not_allowed
                    ShizukuState.Stopped -> R.string.st_state_stopped
                    ShizukuState.Missing -> R.string.st_state_missing
                },
            ),
            size = 24.sp, color = if (state.ready) LocalAccent.current else com.metrowin.launcher.ui.theme.LocalMetroColors.current.foreground,
        )
        if (!state.ready) {
            MetroText(stringResource(R.string.st_how_title), size = 20.sp)
            listOf(R.string.st_how_1, R.string.st_how_2, R.string.st_how_3, R.string.st_how_4, R.string.st_how_5, R.string.st_how_6).forEachIndexed { i, step ->
                PageNote("${i + 1}. ${stringResource(step)}")
            }
            when (state) {
                ShizukuState.Missing -> MetroButton(stringResource(R.string.st_download)) { open(context, Intent(Intent.ACTION_VIEW, "https://shizuku.rikka.app/download/".toUri())) }
                ShizukuState.Stopped -> MetroButton(stringResource(R.string.st_open_shizuku)) {
                    context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { open(context, it) }
                }
                ShizukuState.NotAllowed -> MetroButton(stringResource(R.string.ac_shizuku_grant)) { ShizukuRadio.requestPermission() }
                ShizukuState.Ready -> Unit
            }
        }
        // The test runs a command for real: if it does not say "shell", the buttons will not work either, and the line above says why.
        var testResult by remember { mutableStateOf("") }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        MetroButton(stringResource(R.string.st_test)) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { ShizukuShell.shell("id") }
                testResult = "${com.metrowin.launcher.data.ShizukuRadio.diagnosis()}\n[${result.code}] ${result.output.take(160)}"
            }
        }
        if (testResult.isNotEmpty()) PageNote(testResult)
        MetroText(stringResource(R.string.st_pick_title), size = 20.sp, modifier = Modifier.padding(top = 8.dp))
        PageNote(stringResource(if (state.ready) R.string.st_pick_hint else R.string.st_pick_locked))
        if (state.ready) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetroButton(stringResource(R.string.st_all_on)) { ShizukuButtons.setAll(context, true); chosen = ShizukuButtons.chosen(context) }
                MetroButton(stringResource(R.string.st_all_off)) { ShizukuButtons.setAll(context, false); chosen = ShizukuButtons.chosen(context) }
            }
        }
        var watch by remember { mutableStateOf(com.metrowin.launcher.data.ShizukuWatch.enabled(context)) }
        ToggleSetting(
            label = stringResource(R.string.sw_toggle), checked = watch,
            onChange = { on -> com.metrowin.launcher.data.ShizukuWatch.setEnabled(context, on); watch = on },
            hint = stringResource(R.string.sw_toggle_hint), tag = "st:watch",
        )
        ShizukuButtons.direct.forEach { action ->
            ToggleSetting(
                label = stringResource(action.label), checked = action.id in chosen,
                onChange = { on -> ShizukuButtons.set(context, action, on); chosen = ShizukuButtons.chosen(context) },
                hint = stringResource(hint(action)), enabled = state.ready, tag = "st:button:${action.id}",
            )
        }
        ShizukuButtons.none.forEach { action ->
            Column {
                MetroText(stringResource(action.label), size = 22.sp)
                PageNote(stringResource(if (action == QuickAction.Vpn) R.string.st_no_vpn else R.string.st_no_hotspot))
            }
        }
        Column(Modifier.padding(bottom = 16.dp)) {}
    }
}

private fun open(context: android.content.Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@StringRes
private fun hint(action: QuickAction): Int = when (action) {
    QuickAction.Wifi -> R.string.st_h_wifi
    QuickAction.Bluetooth -> R.string.st_h_bluetooth
    QuickAction.Airplane -> R.string.st_h_airplane
    QuickAction.MobileData -> R.string.st_h_data
    QuickAction.Location -> R.string.st_h_location
    QuickAction.QuietHours, QuickAction.QuietTimed -> R.string.st_h_quiet
    QuickAction.Rotation -> R.string.st_h_rotation
    QuickAction.AutoBrightness -> R.string.st_h_autobrightness
    QuickAction.ScreenTimeout -> R.string.st_h_timeout
    QuickAction.Brightness -> R.string.st_h_brightness
    QuickAction.Recents -> R.string.st_h_recents
    else -> R.string.st_h_screenshot
}
