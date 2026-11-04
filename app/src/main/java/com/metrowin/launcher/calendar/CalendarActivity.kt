package com.metrowin.launcher.calendar

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The calendar of the launcher: the events of the phone as an agenda, a month and a week, and a form to write, change and delete them. */
class CalendarActivity : MiniActivity() {
    @Composable
    override fun Content() = CalendarScreen()
}
