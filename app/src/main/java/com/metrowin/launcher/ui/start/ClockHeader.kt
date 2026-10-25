package com.metrowin.launcher.ui.start

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalMetroColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.util.Locale

/** The time in large light type with the date under it, above the tiles. Redrawn when the minute changes. */
@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            val current = LocalDateTime.now()
            value = current
            // Wake up just after the next minute starts.
            delay((60 - current.second) * 1_000L - current.nano / 1_000_000 + 50)
        }
    }
    val locale = Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(context)
    Column(modifier.padding(bottom = 14.dp, start = 2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            MetroText(ClockFormat.time(now.toLocalTime(), is24, locale), size = 52.sp)
            val meridiem = ClockFormat.meridiem(now.toLocalTime(), is24, locale)
            if (meridiem.isNotEmpty()) {
                MetroText(meridiem, size = 18.sp, weight = FontWeight.Normal, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
            }
        }
        MetroText(
            ClockFormat.date(now.toLocalDate(), locale),
            size = 18.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle,
        )
    }
}
