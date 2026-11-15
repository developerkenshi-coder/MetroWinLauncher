package com.metrowin.launcher.shizukuterminal

import android.app.UiModeManager
import android.content.Context
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroSlider
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.pages.PageChip
import com.metrowin.launcher.ui.pages.PageNote

/** One setting of Android with a few values to choose from: what it is now, and the commands that set each value. */
private class Tweak(
    @param:StringRes val label: Int,
    val options: List<Pair<Int, String>>,
    val current: (Context) -> String,
    val set: (String) -> List<Array<String>>,
    val range: SliderRange? = null,
    /** A change that can leave the screen hard to read asks first. */
    val confirm: Boolean = false,
)

private fun put(table: String, key: String, value: String) = arrayOf("settings", "put", table, key, value)

/** The names of the settings of Android this page changes. */
private object Keys {
    const val FONT = "font_scale"
    const val SHOW_TOUCHES = "show_touches"
    const val POINTER = "pointer_location"
    const val STAY_ON = "stay_on_while_plugged_in"
    val ANIMATIONS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
}

/** A setting chosen on a slider: the range, the step, and what "default" is. */
private class SliderRange(val min: Float, val max: Float, val step: Float, val default: Float)

private fun number(v: Float) = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }

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
        { v -> Keys.ANIMATIONS.map { put("global", it, v) } },
        SliderRange(0f, 2f, 0.1f, 1f),
    ),
    Tweak(
        R.string.st_t_font, listOf("0.85", "1", "1.15", "1.3", "1.5").map { R.string.st_x to it },
        { Settings.System.getFloat(it.contentResolver, Settings.System.FONT_SCALE, 1f).toString().removeSuffix(".0") },
        { v -> listOf(put("system", Keys.FONT, v)) },
        SliderRange(0.85f, 1.5f, 0.05f, 1f), confirm = true,
    ),
    Tweak(
        R.string.st_t_touches, ON_OFF, { Settings.System.getInt(it.contentResolver, Keys.SHOW_TOUCHES, 0).toString() },
        { v -> listOf(put("system", Keys.SHOW_TOUCHES, v)) },
    ),
    Tweak(
        R.string.st_t_pointer, ON_OFF, { Settings.System.getInt(it.contentResolver, Keys.POINTER, 0).toString() },
        { v -> listOf(put("system", Keys.POINTER, v)) },
    ),
    Tweak(
        R.string.st_t_stay_on, ON_OFF, { if (Settings.Global.getInt(it.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0) "1" else "0" },
        { v -> listOf(put("global", Keys.STAY_ON, if (v == "1") "3" else "0")) },
    ),
)

private val ACTIONS = listOf(
    R.string.st_a_lock to arrayOf("input", "keyevent", "KEYCODE_SLEEP"),
    R.string.st_a_shade to arrayOf("cmd", "statusbar", "expand-notifications"),
    R.string.st_a_quick to arrayOf("cmd", "statusbar", "expand-settings"),
)

/** The settings people change with a tool like this, and a few actions: one touch each, and what came of it said under the title. */
@Composable
internal fun SystemPage(state: ShizukuState, opts: TerminalOptions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var changes by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf<Pair<String, List<Array<String>>>?>(null) }
    val done = stringResource(R.string.st_done)
    val noShizuku = stringResource(R.string.tm_end_noshizuku)
    val failed = stringResource(R.string.tm_failed)
    val locked = !state.ready || opts.readOnly.on
    fun apply(label: String, commands: List<Array<String>>) {
        if (locked) return
        scope.launch {
            val results = withContext(Dispatchers.IO) { commands.map { ShizukuShell.run(it) } }
            val bad = results.firstOrNull { !it.ok }
            message = "$label: ${when { bad == null -> done; bad.code == -1 -> noShizuku; else -> bad.output.lineSequence().firstOrNull().orEmpty().ifEmpty { failed } }}"
            changes++
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageNote(stringResource(if (state.ready) R.string.st_sys_hint else R.string.st_pick_locked))
        if (opts.readOnly.on) PageNote(stringResource(R.string.tm_readonly_on))
        MetroTextField(query, { query = it }, stringResource(R.string.st_search), Modifier.fillMaxWidth())
        if (message.isNotEmpty()) PageNote(message)
        TWEAKS.forEach { tweak ->
            val title = stringResource(tweak.label)
            if (query.isNotBlank() && !title.contains(query.trim(), true)) return@forEach
            // Read again after every change.
            val now = remember(changes) { runCatching { tweak.current(context) }.getOrNull() }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetroText(title, size = 20.sp)
                val range = tweak.range
                if (range != null) {
                    // A slider with what the value looks like before it is applied.
                    var draft by remember(changes) { mutableFloatStateOf(now?.toFloatOrNull() ?: range.default) }
                    MetroSlider((draft - range.min) / (range.max - range.min), { f -> draft = (Math.round((range.min + f * (range.max - range.min)) / range.step) * range.step).coerceIn(range.min, range.max) }, title)
                    MetroText("×${number(draft)}", size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle)
                    if (tweak.confirm) MetroText(stringResource(R.string.tm_font_sample), size = (18 * draft).sp, weight = FontWeight.Normal)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val commands = tweak.set(number(draft))
                        MetroButton(stringResource(R.string.tm_apply), Modifier.weight(1f)) { if (tweak.confirm) asking = title to commands else apply(title, commands) }
                        MetroButton(stringResource(R.string.tm_default), Modifier.weight(1f)) { apply(title, tweak.set(number(range.default))) }
                    }
                } else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tweak.options) { (label, value) ->
                        PageChip(if (label == R.string.st_x) "×$value" else stringResource(label), value == now, "st:tweak:${tweak.label}:$value") { apply(title, tweak.set(value)) }
                    }
                }
            }
        }
        MetroButton(stringResource(R.string.pw_open)) { if (state.ready) context.startActivity(android.content.Intent(context, com.metrowin.launcher.power.PowerActivity::class.java)) }
        ACTIONS.forEach { (label, command) ->
            val name = stringResource(label)
            MetroButton(name) { apply(name, listOf(command)) }
        }
        Spacer(Modifier.height(16.dp))
    }
    asking?.let { (title, commands) ->
        DialogFrame(onCancel = { asking = null }, title = title) {
            MetroText(stringResource(R.string.tm_font_confirm), size = 16.sp, weight = FontWeight.SemiBold, color = palette(0).err)
            MetroButton(stringResource(R.string.tm_apply), Modifier.fillMaxWidth()) { asking = null; apply(title, commands) }
            MetroButton(stringResource(R.string.tm_cancel), Modifier.fillMaxWidth()) { asking = null }
        }
    }
}
