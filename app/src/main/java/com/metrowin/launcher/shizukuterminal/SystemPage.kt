package com.metrowin.launcher.shizukuterminal

import android.app.UiModeManager
import android.content.Context
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote

/** One setting of Android with a few values to choose from: what it is now, and the commands that set each value. */
private class Tweak(
    @param:StringRes val label: Int,
    val options: List<Pair<Int, String>>,
    val current: (Context) -> String,
    val set: (String) -> List<Array<String>>,
)

private fun put(table: String, key: String, value: String) = arrayOf("settings", "put", table, key, value)

private val ON_OFF = listOf(R.string.st_on to "1", R.string.st_off to "0")

private val TWEAKS = listOf(
    Tweak(
        R.string.st_t_dark, listOf(R.string.st_t_dark_auto to "0", R.string.st_t_dark_no to "1", R.string.st_t_dark_yes to "2"),
        { it.getSystemService(UiModeManager::class.java).nightMode.toString() },
        { v -> listOf(arrayOf("cmd", "uimode", "night", when (v) { "1" -> "no"; "2" -> "yes"; else -> "auto" })) },
    ),
    Tweak(
        R.string.st_t_animations, listOf("0", "0.5", "1", "1.5", "2").map { R.string.st_x to it },
        { Settings.Global.getFloat(it.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f).toString().removeSuffix(".0") },
        { v -> listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").map { put("global", it, v) } },
    ),
    Tweak(
        R.string.st_t_font, listOf("0.85", "1", "1.15", "1.3", "1.5").map { R.string.st_x to it },
        { Settings.System.getFloat(it.contentResolver, Settings.System.FONT_SCALE, 1f).toString().removeSuffix(".0") },
        { v -> listOf(put("system", "font_scale", v)) },
    ),
    Tweak(
        R.string.st_t_touches, ON_OFF, { Settings.System.getInt(it.contentResolver, "show_touches", 0).toString() },
        { v -> listOf(put("system", "show_touches", v)) },
    ),
    Tweak(
        R.string.st_t_pointer, ON_OFF, { Settings.System.getInt(it.contentResolver, "pointer_location", 0).toString() },
        { v -> listOf(put("system", "pointer_location", v)) },
    ),
    Tweak(
        R.string.st_t_stay_on, ON_OFF, { if (Settings.Global.getInt(it.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0) "1" else "0" },
        { v -> listOf(put("global", "stay_on_while_plugged_in", if (v == "1") "3" else "0")) },
    ),
)

private val ACTIONS = listOf(
    R.string.st_a_lock to arrayOf("input", "keyevent", "KEYCODE_SLEEP"),
    R.string.st_a_shade to arrayOf("cmd", "statusbar", "expand-notifications"),
    R.string.st_a_quick to arrayOf("cmd", "statusbar", "expand-settings"),
)

/** The settings people change with a tool like this, and a few actions: one touch each. */
@Composable
fun SystemPage(state: ShizukuState) {
    val context = LocalContext.current
    var changes by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageNote(stringResource(if (state.ready) R.string.st_sys_hint else R.string.st_pick_locked))
        TWEAKS.forEach { tweak ->
            // Read again after every change.
            val now = remember(changes) { runCatching { tweak.current(context) }.getOrNull() }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetroText(stringResource(tweak.label), size = 20.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tweak.options) { (label, value) ->
                        PageChip(if (label == R.string.st_x) "×$value" else stringResource(label), value == now, "st:tweak:${tweak.label}:$value") {
                            if (state.ready) ShizukuShell.async(tweak.set(value)) { changes++ }
                        }
                    }
                }
            }
        }
        MetroButton(stringResource(R.string.pw_open)) { if (state.ready) context.startActivity(android.content.Intent(context, com.metrowin.launcher.power.PowerActivity::class.java)) }
        ACTIONS.forEach { (label, command) ->
            MetroButton(stringResource(label)) { if (state.ready) ShizukuShell.async(listOf(command)) }
        }
        Column(Modifier.padding(bottom = 16.dp)) {}
    }
}
