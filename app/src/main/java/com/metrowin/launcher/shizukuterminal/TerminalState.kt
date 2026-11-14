package com.metrowin.launcher.shizukuterminal

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BatterySection
import com.metrowin.launcher.data.Prefs
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

/** What the pages of the Terminal keep while the user swipes to another one (the pager lets go of a page that is not next to the one in view). */

/** One command run, with what it wrote. */
internal class Block(val command: String) {
    var out by mutableStateOf("")
    var err by mutableStateOf("")
    var code by mutableStateOf<Int?>(null)
    var collapsed by mutableStateOf(false)
}

/** One terminal: its output, the line being typed, and whether a command runs. */
internal class Tab(val name: String) {
    val blocks = mutableStateListOf<Block>()
    var line by mutableStateOf("")
    var busy by mutableStateOf(false)
    val stop = AtomicBoolean(false)

    /** Where the arrows are in the history (-1: not browsing it). */
    var cursor by mutableIntStateOf(-1)

    fun text(): String = blocks.joinToString("\n") { b ->
        "$ ${b.command}\n" + listOf(b.out, b.err).filter { it.isNotEmpty() }.joinToString("\n").let { if (it.isEmpty()) "" else it + "\n" } + (b.code?.let { "[$it]\n" } ?: "")
    }

    /** The oldest output goes first when there is too much to show. */
    fun trim(limit: Int) {
        while (blocks.size > 1 && blocks.sumOf { it.out.length + it.err.length + it.command.length } > limit) blocks.removeAt(0)
    }
}

/** The terminals, the commands run so far (newest last) and the windows that belong to the page. */
internal class TerminalSession(private val prefs: SharedPreferences, restore: Boolean) {
    val tabs = mutableStateListOf(Tab("1"))
    var current by mutableIntStateOf(0)
    var historyOpen by mutableStateOf(false)
    val history = mutableStateListOf<String>().apply {
        if (restore) prefs.getString(HISTORY, null)?.let { text -> runCatching { JSONArray(text) }.getOrNull()?.let { a -> addAll((0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }) } }
    }
    val tab get() = tabs[current.coerceIn(0, tabs.lastIndex)]

    fun add(command: String, keep: Boolean) {
        history.remove(command)
        history.add(command)
        while (history.size > MAX_HISTORY) history.removeAt(0)
        if (keep) save()
    }

    fun clearHistory() {
        history.clear()
        prefs.edit { remove(HISTORY) }
    }

    fun save() = prefs.edit { putString(HISTORY, JSONArray(history.toList()).toString()) }

    /** The arrows: [step] -1 goes to an older command, +1 to a newer one (past the newest, the line is empty again). */
    fun browse(step: Int) {
        val t = tab
        if (history.isEmpty()) return
        val next = when {
            t.cursor == -1 && step < 0 -> history.lastIndex
            t.cursor == -1 -> return
            else -> t.cursor + step
        }
        if (next > history.lastIndex) { t.cursor = -1; t.line = ""; return }
        t.cursor = next.coerceAtLeast(0)
        t.line = history[t.cursor]
    }

    companion object {
        private const val HISTORY = "terminal_history"
        const val MAX_HISTORY = 200
    }
}

/** What the Monitor has drawn so far: kept here, not in the page, so a swipe away and back does not empty the charts. */
internal class MonitorBuffers {
    val cpu = mutableStateListOf<Float>()
    val ram = mutableStateListOf<Float>()
    val heat = mutableStateListOf<Float>()
    var sections by mutableStateOf(emptyList<BatterySection>())
}

/** The filter and the search of the Apps page. */
internal class AppsUi {
    var filter by mutableStateOf(AppFilter.Apps)
    var query by mutableStateOf("")
}

/** One switch of the options window. */
internal class Option(private val prefs: SharedPreferences, val key: String, default: Boolean, @param:StringRes val label: Int, @param:StringRes val hint: Int) {
    var on by mutableStateOf(prefs.getBoolean(key, default))
        private set

    fun set(value: Boolean) {
        on = value
        prefs.edit { putBoolean(key, value) }
    }
}

/**
 * The choices of the user for this app, kept in the file of the Terminal. What touches privacy, battery or permissions is off until the user turns it on;
 * what is only look is on.
 */
internal class TerminalOptions(context: Context) {
    private val prefs = Prefs.open(context, Prefs.SHIZUKU)

    private fun option(key: String, default: Boolean, label: Int, hint: Int) = Option(prefs, "opt_$key", default, label, hint)

    val readOnly = option("readonly", false, R.string.tm_o_readonly, R.string.tm_o_readonly_h)
    val lock = option("lock", false, R.string.tm_o_lock, R.string.tm_o_lock_h)
    val longRun = option("long", false, R.string.tm_o_long, R.string.tm_o_long_h)
    val keepHistory = option("keep_history", false, R.string.tm_o_keep_history, R.string.tm_o_keep_history_h)
    val processes = option("processes", false, R.string.tm_o_processes, R.string.tm_o_processes_h)
    val advanced = option("advanced", false, R.string.tm_o_advanced, R.string.tm_o_advanced_h)
    val tabs = option("tabs", false, R.string.tm_o_tabs, R.string.tm_o_tabs_h)
    val compact = option("compact", false, R.string.tm_o_compact, R.string.tm_o_compact_h)
    val colors = option("colors", true, R.string.tm_o_colors, R.string.tm_o_colors_h)
    val cards = option("cards", true, R.string.tm_o_cards, R.string.tm_o_cards_h)
    val smooth = option("smooth", true, R.string.tm_o_smooth, R.string.tm_o_smooth_h)
    val sticky = option("sticky", true, R.string.tm_o_sticky, R.string.tm_o_sticky_h)
    val icons = option("icons", true, R.string.tm_o_icons, R.string.tm_o_icons_h)
    val indicator = option("indicator", true, R.string.tm_o_indicator, R.string.tm_o_indicator_h)
    val quickKeys = option("quickkeys", true, R.string.tm_o_quickkeys, R.string.tm_o_quickkeys_h)

    /** In the order the window shows them: what protects first. */
    val all = listOf(readOnly, lock, longRun, keepHistory, processes, advanced, tabs, compact, colors, cards, smooth, sticky, icons, indicator, quickKeys)

    var theme by mutableIntStateOf(prefs.getInt("opt_theme", 0).coerceIn(0, THEMES - 1))
        private set
    var fontSize by mutableIntStateOf(prefs.getInt("opt_font", 13).coerceIn(FONTS.first(), FONTS.last()))
        private set

    fun chooseTheme(value: Int) { theme = value; prefs.edit { putInt("opt_theme", value) } }

    fun chooseFont(value: Int) { fontSize = value; prefs.edit { putInt("opt_font", value) } }

    /** The seconds a command of the Terminal may run. */
    val timeoutMs get() = if (longRun.on) 300_000L else 20_000L

    companion object {
        const val THEMES = 4
        val FONTS = listOf(11, 13, 15, 18)
    }
}
