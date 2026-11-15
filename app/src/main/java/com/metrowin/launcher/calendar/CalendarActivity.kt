package com.metrowin.launcher.calendar

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The calendar of the launcher: the events of the phone as an agenda, a month and a week, and a form to write, change and delete them. */
class CalendarActivity : MiniActivity() {
    @Composable
    override fun Content() = CalendarScreen(initialDay = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(java.time.LocalDate::ofEpochDay))

    companion object {
        /** The day (an epoch day) the calendar opens on: what a touch on the event of an info tile asks for. */
        const val EXTRA_DAY = "day"
    }
}
