package com.metrowin.launcher.calendar

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

private val HOUR = 60.dp
private val GUTTER = 52.dp

/**
 * The day view: the hours of the day down the page, the events as blocks in the hours they take (side by side in columns when they share
 * time), the all-day ones in a strip on top, and for today a line at "now" in the accent colour. The grid opens on the hour before the
 * first event (or before now, or 8 o'clock). Arrows go to the day before and after.
 */
@Composable
fun DayPage(events: List<CalEvent>, day: LocalDate, today: LocalDate, zone: ZoneId, onShift: (Int) -> Unit, onEdit: (CalEvent) -> Unit) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    val context = LocalContext.current
    val blocks = remember(events, day) { DayLayout.blocks(events, day, zone) }
    val allDay = remember(events, day) { DayLayout.allDay(events, day, zone) }
    // "Now" moves on once a minute while the day is today.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(day == today) { while (day == today) { now = System.currentTimeMillis(); delay(60_000) } }
    val nowMin = if (day == today) ((now - day.atStartOfDay(zone).toInstant().toEpochMilli()) / 60_000L).toInt() else -1
    val scroll = rememberScrollState()
    val hourPx = with(LocalDensity.current) { HOUR.roundToPx() }
    LaunchedEffect(day) {
        val focus = blocks.minOfOrNull { it.startMin } ?: nowMin.takeIf { it >= 0 } ?: (8 * 60)
        scroll.scrollTo(((focus / 60 - 1).coerceAtLeast(0)) * hourPx)
    }
    val hourFormat = remember { DateFormat.getTimeFormat(context) }
    val timeOf = { min: Int -> hourFormat.format(Date(day.atStartOfDay(zone).toInstant().toEpochMilli() + min * 60_000L)) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MetroText("‹", size = 34.sp, modifier = Modifier.testTag("cal:day:prev").metroPress(onClick = { onShift(-1) }).padding(horizontal = 8.dp))
            MetroText(
                day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), size = 18.sp, weight = FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 2,
                color = if (day == today) accent else colors.foreground, modifier = Modifier.weight(1f).testTag("cal:day:title"),
            )
            MetroText("›", size = 34.sp, modifier = Modifier.testTag("cal:day:next").metroPress(onClick = { onShift(1) }).padding(horizontal = 8.dp))
        }
        allDay.forEach { e ->
            Box(Modifier.fillMaxWidth().padding(bottom = 2.dp).background(if (e.color != 0) Color(e.color) else accent).metroPress(onClick = { onEdit(e) }).padding(horizontal = 8.dp, vertical = 4.dp).testTag("cal:day:allday")) {
                MetroText(e.title.ifBlank { stringResource(R.string.cal_untitled) }, size = 15.sp, color = Color.White, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).testTag("cal:day:grid")) {
            Box(Modifier.fillMaxWidth().height(HOUR * 24)) {
                // The hours: a label and a thin line each.
                for (h in 0 until 24) {
                    Box(Modifier.offset(y = HOUR * h).fillMaxWidth().height(HOUR)) {
                        MetroText(timeOf(h * 60), size = 12.sp, weight = FontWeight.Normal, color = colors.subtle, maxLines = 1, modifier = Modifier.width(GUTTER).padding(top = 2.dp))
                        Box(Modifier.padding(start = GUTTER).fillMaxWidth().height(1.dp).background(colors.subtle.copy(alpha = 0.3f)))
                    }
                }
                BoxWithConstraints(Modifier.padding(start = GUTTER).fillMaxSize()) {
                    val full = maxWidth
                    blocks.forEach { b ->
                        val width = full / b.columns
                        Box(
                            Modifier.offset(x = width * b.column, y = HOUR * (b.startMin / 60f)).width(width - 2.dp).height(maxOf(HOUR * ((b.endMin - b.startMin) / 60f), 22.dp) - 1.dp)
                                .background(if (b.event.color != 0) Color(b.event.color) else accent).metroPress(onClick = { onEdit(b.event) }).padding(horizontal = 6.dp, vertical = 2.dp).testTag("cal:day:block"),
                        ) {
                            Column {
                                MetroText(b.event.title.ifBlank { stringResource(R.string.cal_untitled) }, size = 15.sp, color = Color.White, maxLines = 2)
                                if (b.endMin - b.startMin >= 45) MetroText(timeOf(b.startMin) + " – " + timeOf(b.endMin.coerceAtMost(DayLayout.MINUTES_IN_DAY - 1)), size = 11.sp, weight = FontWeight.Normal, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                            }
                        }
                    }
                }
                if (nowMin in 0 until DayLayout.MINUTES_IN_DAY) {
                    Box(Modifier.offset(y = HOUR * (nowMin / 60f)).padding(start = GUTTER - 5.dp).fillMaxWidth().testTag("cal:day:now")) {
                        Box(Modifier.size(10.dp).offset(y = (-4).dp).clip(CircleShape).background(accent))
                        Box(Modifier.padding(start = 5.dp).fillMaxWidth().height(2.dp).background(accent))
                    }
                }
            }
        }
    }
}
