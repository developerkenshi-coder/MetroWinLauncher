package com.metrowin.launcher.calendar

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrowin.launcher.R

/** The switches of the calendar, in the "options" of its "…" menu. What touches contacts or writes by a gesture starts off. */
enum class Option(val key: String, val default: Boolean, @param:StringRes val label: Int) {
    WeekGrid("week_grid", true, R.string.cal_opt_week_grid),
    WeekNumbers("week_numbers", true, R.string.cal_opt_week_numbers),
    RowDetails("row_details", true, R.string.cal_opt_row_details),
    NowLabel("now_label", true, R.string.cal_opt_now_label),
    TapToCreate("tap_to_create", true, R.string.cal_opt_tap_create),
    YearView("year_view", true, R.string.cal_opt_year),
    SplitWide("split_wide", true, R.string.cal_opt_split),
    Attendees("attendees", true, R.string.cal_opt_attendees),
    OpenDay("open_day", true, R.string.cal_opt_open_day),
    RepeatNew("repeat_new", true, R.string.cal_opt_repeat),
    SwipeDays("swipe_days", false, R.string.cal_opt_swipe_days),
    HideDeclined("hide_declined", false, R.string.cal_opt_hide_declined),
    Birthdays("birthdays", false, R.string.cal_opt_birthdays),
    DragEvents("drag_events", false, R.string.cal_opt_drag),
    Heatmap("heatmap", false, R.string.cal_opt_heatmap),
    ByCalendar("by_calendar", false, R.string.cal_opt_by_calendar),
    LargeText("large_text", false, R.string.cal_opt_large_text),
    DimColors("dim_colors", false, R.string.cal_opt_dim),
}

/** What the calendar remembers for itself (its own preferences file): the switches, the calendars hidden, the colours given to calendars, the second time zone. */
class CalendarOptions(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("calendar_options", Context.MODE_PRIVATE)
    private val flags = mutableStateMapOf<String, Boolean>().apply { Option.entries.forEach { put(it.key, prefs.getBoolean(it.key, it.default)) } }

    /** The calendars the user chose not to see. Only the screens hide them: the calendars of the phone are not touched. */
    var hidden by mutableStateOf(prefs.getStringSet("hidden", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet())
        private set

    /** A colour chosen for a whole calendar (it does not change the calendar of the phone). */
    var colors by mutableStateOf(prefs.getStringSet("colors", emptySet()).orEmpty().mapNotNull { s -> s.split(':').takeIf { it.size == 2 }?.let { (a, b) -> a.toLongOrNull()?.let { id -> b.toIntOrNull()?.let { id to it } } } }.toMap())
        private set

    /** The id of a second time zone shown beside the hours of the day view, blank for none. */
    var secondZone by mutableStateOf(prefs.getString("second_zone", "").orEmpty())
        private set

    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false))
        private set

    operator fun get(option: Option): Boolean = flags[option.key] ?: option.default

    fun set(option: Option, on: Boolean) { flags[option.key] = on; prefs.edit().putBoolean(option.key, on).apply() }

    fun toggleHidden(id: Long) { hidden = if (id in hidden) hidden - id else hidden + id; prefs.edit().putStringSet("hidden", hidden.map { it.toString() }.toSet()).apply() }

    fun setColor(id: Long, color: Int?) {
        colors = if (color == null) colors - id else colors + (id to color)
        prefs.edit().putStringSet("colors", colors.map { "${it.key}:${it.value}" }.toSet()).apply()
    }

    fun setSecondZone(id: String) { secondZone = id; prefs.edit().putString("second_zone", id).apply() }

    fun finishOnboarding() { onboarded = true; prefs.edit().putBoolean("onboarded", true).apply() }
}
