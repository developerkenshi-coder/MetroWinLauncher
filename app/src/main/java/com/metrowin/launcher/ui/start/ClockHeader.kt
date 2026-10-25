package com.metrowin.launcher.ui.start

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.metrowin.launcher.ui.anim.LaunchedWhileStarted
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.metrowin.launcher.ui.anim.metroPress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ClockHours
import com.metrowin.launcher.data.NextAlarm
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale

/** The time in large light type with the date under it, above the tiles. Redrawn when the minute changes. */
@Composable
fun ClockHeader(
    modifier: Modifier = Modifier,
    /** A touch on the time and on the date; null makes that part plain text. */
    onTimeClick: (() -> Unit)? = null,
    onDateClick: (() -> Unit)? = null,
    hours: ClockHours = ClockHours.System,
    /** The next alarm under the date, and the number of the week next to it. */
    showAlarm: Boolean = false,
    showWeek: Boolean = false,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedWhileStarted(Unit) {
        while (true) {
            val current = LocalDateTime.now()
            now = current
            // Wake up just after the next minute starts.
            delay((60 - current.second) * 1_000L - current.nano / 1_000_000 + 50)
        }
    }
    val locale = Locale.getDefault()
    val is24 = hours.is24(DateFormat.is24HourFormat(context))
    // The next alarm is read again at every minute: it is one call, and it changes when the user sets or cancels one.
    val alarm = remember(now, showAlarm) { if (showAlarm) NextAlarm.describe(NextAlarm.read(context), System.currentTimeMillis()) else null }
    Column(modifier.padding(bottom = 14.dp, start = 2.dp)) {
        Row(
            Modifier.then(if (onTimeClick != null) Modifier.testTag("clock:time").metroPress(onClick = onTimeClick) else Modifier),
            verticalAlignment = Alignment.Bottom,
        ) {
            MetroText(ClockFormat.time(now.toLocalTime(), is24, locale), size = 52.sp)
            val meridiem = ClockFormat.meridiem(now.toLocalTime(), is24, locale)
            if (meridiem.isNotEmpty()) {
                MetroText(meridiem, size = 18.sp, weight = FontWeight.Normal, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
            }
        }
        MetroText(
            ClockFormat.date(now.toLocalDate(), locale) + if (showWeek) " · " + stringResource(R.string.clock_week, ClockFormat.week(now.toLocalDate(), locale)) else "",
            size = 18.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
            modifier = if (onDateClick != null) Modifier.testTag("clock:date").metroPress(onClick = onDateClick) else Modifier,
        )
        if (alarm != null) {
            Row(Modifier.padding(top = 2.dp).testTag("clock:alarm"), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.Alarm, 16.dp, color = LocalMetroColors.current.subtle)
                MetroText(
                    ClockFormat.time(alarm.time, is24, locale) + ClockFormat.meridiem(alarm.time, is24, locale).let { if (it.isEmpty()) "" else " $it" } +
                        when (val day = alarm.day) {
                            NextAlarm.Day.Today -> ""
                            NextAlarm.Day.Tomorrow -> " · " + stringResource(R.string.live_tomorrow)
                            is NextAlarm.Day.Weekday -> " · " + day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
                        },
                    size = 16.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}
