package com.metrowin.launcher.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.metrowin.launcher.ui.anim.metroPress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.ColorMath
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.onAccent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** What every page of the calendar shares: the day that is today, the zone, the clock in use, the switches. */
class CalendarEnv(
    val options: CalendarOptions,
    val today: LocalDate,
    val zone: ZoneId,
    val first: DayOfWeek,
    val is24: Boolean,
    /** One time format for all the rows (made once, not for each row). */
    val timeFormat: java.text.DateFormat,
    val calendars: List<CalendarInfo>,
) {
    fun time(ms: Long): String = timeFormat.format(Date(ms))
}

val LocalCal = compositionLocalOf<CalendarEnv> { error("no calendar env") }

/** The colour an event is drawn with: its own, else the one chosen for its calendar, else the one of the calendar, else the accent; dimmed at night if the switch is on. */
@Composable
fun eventFill(e: CalEvent): Color {
    val env = LocalCal.current
    val accent = LocalAccent.current
    fun argb(c: Int) = Color(c or 0xFF000000.toInt())
    val base = when {
        e.ownColor != 0 -> argb(e.ownColor)
        env.options.colors[e.calendarId] != null -> argb(env.options.colors.getValue(e.calendarId))
        e.color != 0 -> argb(e.color)
        else -> accent
    }
    return if (env.options[Option.DimColors]) lerp(base, LocalMetroColors.current.background, 0.35f) else base
}

/** The text colour that reads on [fill] (white or black, whichever has more contrast). */
@Composable
fun eventInk(fill: Color): Color = if (fill == LocalAccent.current) onAccent() else ColorMath.inkOn(fill)

enum class CalIcon(val path: String) {
    Place("M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"),
    Bell("M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"),
    Repeat("M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"),
    Video("M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"),
    People("M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"),
}

/** A small filled icon of the set above (24 x 24 grid). Decoration: it says nothing to TalkBack, the text beside it does. */
@Composable
fun CalIconView(icon: CalIcon, size: Dp = 14.dp, color: Color = LocalMetroColors.current.subtle, modifier: Modifier = Modifier) {
    val path = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) { drawPath(path, color, style = Fill) }
    }
}

/** An empty page: a drawn calendar and a line that says what to do. [locked] draws a padlock sign for the state without the permission. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, locked: Boolean = false, content: @Composable () -> Unit = {}) {
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(72.dp).clearAndSetSemantics { }) {
            val w = size.width
            drawRect(colors.subtle, Offset(w * 0.08f, w * 0.18f), Size(w * 0.84f, w * 0.74f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.05f))
            drawRect(accent, Offset(w * 0.08f, w * 0.18f), Size(w * 0.84f, w * 0.2f))
            for (i in 0 until 3) for (j in 0 until 3) {
                val dot = if (locked) colors.inactive else if (i == 1 && j == 1) accent else colors.inactive
                drawRoundRect(dot, Offset(w * (0.2f + 0.22f * i), w * (0.46f + 0.14f * j)), Size(w * 0.14f, w * 0.09f), CornerRadius(w * 0.02f))
            }
        }
        MetroText(text, size = 16.sp, color = colors.subtle, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp))
        content()
    }
}

/** A "‹" or "›" arrow that says what it does to TalkBack (the glyph alone is read as a shape). */
@Composable
fun NavArrow(symbol: String, description: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier.testTag(tag).metroPress(onClick = onClick).semantics { contentDescription = description }.padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { MetroText(symbol, size = 34.sp, modifier = Modifier.clearAndSetSemantics { }) }
}
