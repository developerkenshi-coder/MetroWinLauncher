package com.metrowin.launcher.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ClockFormat
import com.metrowin.launcher.data.LockText
import com.metrowin.launcher.data.NearPlace
import com.metrowin.launcher.data.TodayAction
import com.metrowin.launcher.data.TodayInfo
import com.metrowin.launcher.model.Glyph
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.anim.turnstile
import com.metrowin.launcher.ui.components.GlyphIcon
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.LocalStyle
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.panel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The card above the search box: the day, the weather, the next event of the calendar, the reminders that come and the next
 * alarm, then four places to look for around the user. Every line opens what it talks about ([onAction]); a line that has
 * nothing to tell (no event, no alarm) is not there. It is drawn from what the launcher already holds, so showing it costs nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayCard(
    info: TodayInfo,
    index: Int,
    listState: LazyListState,
    openedAt: Long,
    exiting: Boolean,
    onAction: (TodayAction) -> Unit,
) {
    val colors = LocalMetroColors.current
    val reduced = LocalReduceMotion.current
    val away = rememberRowAway(index, listState, openedAt, exiting)
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val today = Instant.ofEpochMilli(info.nowMillis).atZone(zone).toLocalDate()
    val date = remember(today, locale) { ClockFormat.date(today, locale) }
    val shape = if (LocalStyle.current.isClassic) RectangleShape else RoundedCornerShape(16.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("today")
            .graphicsLayer { turnstile(away.value, reduced) }
            .panel(shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The caption keeps the room of its word; the weather has what is left, and says less of itself when that is not much.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            MetroText(
                stringResource(R.string.today_title), size = 13.sp, weight = FontWeight.Normal, color = LocalAccent.current, maxLines = 1,
                modifier = Modifier.padding(end = 10.dp),
            )
            info.weather?.let { weather ->
                Row(
                    Modifier.weight(1f, fill = false).testTag("today:weather").metroPress(enabled = !exiting, onClick = { onAction(TodayAction.Weather) }).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlyphIcon(Glyph.Weather, 22.dp)
                    MetroText(
                        "${weather.temperature}° · " + stringResource(weather.condition.label), size = 15.sp, weight = FontWeight.Normal, maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        MetroText(
            date, size = 26.sp, maxLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("today:date").metroPress(enabled = !exiting, onClick = { onAction(TodayAction.Calendar) }),
        )

        info.event?.let { event ->
            TodayLine(Glyph.Calendar, LockText.event(event, info.nowMillis, zone, is24, locale), "today:event", exiting) { onAction(TodayAction.Calendar) }
        }
        info.reminders.forEach { reminder ->
            val at = LockText.alarm(reminder.at, info.nowMillis, zone, is24, locale)
            TodayLine(Glyph.Bell, "$at · ${reminder.text}", "today:reminder:${reminder.id}", exiting) { onAction(TodayAction.Reminders) }
        }
        if (info.missed > 0) {
            TodayLine(
                Glyph.Bell, pluralStringResource(R.plurals.today_missed, info.missed, info.missed), "today:missed", exiting,
                accent = true,
            ) { onAction(TodayAction.Reminders) }
        }
        info.alarmMillis?.let { alarm ->
            TodayLine(
                Glyph.Alarm, stringResource(R.string.today_alarm, LockText.alarm(alarm, info.nowMillis, zone, is24, locale)), "today:alarm", exiting,
            ) { onAction(TodayAction.Clock) }
        }

        MetroText(stringResource(R.string.today_near), size = 13.sp, weight = FontWeight.Normal, color = colors.subtle)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NearPlace.entries.forEach { place ->
                val label = stringResource(place.label)
                Box(
                    Modifier
                        .testTag("today:near:${place.name}")
                        .metroPress(enabled = !exiting, onClick = { onAction(TodayAction.Near(label)) })
                        .litFill(false)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MetroText(label, size = 16.sp, weight = FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

/** One line of the card: a small glyph, what it says, and what a touch on it opens. */
@Composable
private fun TodayLine(glyph: Glyph, text: String, tag: String, exiting: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    val colors = LocalMetroColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .metroPress(enabled = !exiting, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphIcon(glyph, 22.dp, color = if (accent) LocalAccent.current else colors.subtle)
        MetroText(text, size = 17.sp, weight = FontWeight.Normal, maxLines = 3, modifier = Modifier.weight(1f))
    }
}
