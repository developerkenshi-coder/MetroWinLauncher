package com.metrowin.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** How many rows of a looping selector show at once (the one in the middle is the chosen one). */
private const val ROWS = 5
private val ROW_HEIGHT = 46.dp

/** How many times the items are repeated, so that the wheel seems to have no end; it starts in the middle of them. */
private const val LOOPS = 400

/**
 * The selector of Windows Phone: a wheel of [items] that goes round without an end, snaps to a row, and has the chosen one in the middle in
 * the colour of the accent. [selected] moves the wheel when it is changed from outside; a touch on a row brings it to the middle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoopingSelector(items: List<String>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier, tag: String = "selector") {
    val size = items.size.coerceAtLeast(1)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = middleOf(size) + selected.mod(size))
    val currentSelected by rememberUpdatedState(onSelected)
    val colors = LocalMetroColors.current
    val accent = LocalAccent.current
    // When the wheel stops, its middle row is what is chosen.
    LaunchedEffect(state, size) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }
            .filter { (moving, _) -> !moving }
            .map { (_, first) -> first.mod(size) }
            .distinctUntilChanged()
            .collect { currentSelected(it) }
    }
    // Moved from outside (a day that does not exist in the month): the wheel goes to it by the shortest way.
    LaunchedEffect(selected, size) {
        if (state.isScrollInProgress) return@LaunchedEffect
        val now = state.firstVisibleItemIndex.mod(size)
        if (now == selected.mod(size)) return@LaunchedEffect
        var delta = selected.mod(size) - now
        if (delta > size / 2) delta -= size
        if (delta < -size / 2) delta += size
        state.scrollToItem(state.firstVisibleItemIndex + delta)
    }
    LazyColumn(
        modifier.height(ROW_HEIGHT * ROWS).testTag(tag),
        state = state,
        contentPadding = PaddingValues(vertical = ROW_HEIGHT * (ROWS / 2)),
        flingBehavior = rememberSnapFlingBehavior(state),
    ) {
        items(size * LOOPS) { index ->
            val middle = index == state.firstVisibleItemIndex
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .graphicsLayer { alpha = if (middle) 1f else 0.45f },
                contentAlignment = Alignment.Center,
            ) {
                MetroText(
                    items[index.mod(size)], size = if (middle) 28.sp else 22.sp, maxLines = 1,
                    weight = FontWeight.Normal, color = if (middle) accent else colors.foreground,
                )
            }
        }
    }
}

private fun middleOf(size: Int) = size * (LOOPS / 2)

/** The date as three wheels (day, month, year), the way Windows Phone chooses it. */
@Composable
fun MetroDatePicker(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, years: IntRange = 2000..2100, tag: String = "date") {
    val months = remember { DateFormatSymbols(Locale.getDefault()).shortMonths.take(12) }
    val yearList = remember(years) { years.toList() }
    val currentChange by rememberUpdatedState(onChange)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LoopingSelector(
            (1..date.lengthOfMonth()).map { it.toString() }, date.dayOfMonth - 1,
            { currentChange(date.withDayOfMonth(it + 1)) }, Modifier.width(72.dp), "$tag:day",
        )
        LoopingSelector(
            months, date.monthValue - 1,
            { currentChange(DateMath.withMonth(date, it + 1)) }, Modifier.weight(1f), "$tag:month",
        )
        LoopingSelector(
            yearList.map { it.toString() }, (date.year - yearList.first()).coerceIn(0, yearList.lastIndex),
            { currentChange(DateMath.withYear(date, yearList[it])) }, Modifier.width(96.dp), "$tag:year",
        )
    }
}

/** The time as wheels: hours and minutes, and AM or PM when the phone shows 12 hours. */
@Composable
fun MetroTimePicker(time: LocalTime, is24: Boolean, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier, tag: String = "time") {
    val currentChange by rememberUpdatedState(onChange)
    val symbols = remember { DateFormatSymbols(Locale.getDefault()).amPmStrings }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (is24) {
            LoopingSelector((0..23).map { "%02d".format(it) }, time.hour, { currentChange(time.withHour(it)) }, Modifier.weight(1f), "$tag:hour")
        } else {
            val hour12 = TimeMath.hour12(time.hour)
            LoopingSelector(
                (1..12).map { it.toString() }, hour12 - 1,
                { currentChange(time.withHour(TimeMath.hour24(it + 1, time.hour >= 12))) }, Modifier.weight(1f), "$tag:hour",
            )
        }
        LoopingSelector((0..59).map { "%02d".format(it) }, time.minute, { currentChange(time.withMinute(it)) }, Modifier.weight(1f), "$tag:minute")
        if (!is24) {
            LoopingSelector(
                symbols.toList(), if (time.hour >= 12) 1 else 0,
                { currentChange(time.withHour(TimeMath.hour24(TimeMath.hour12(time.hour), it == 1))) }, Modifier.width(88.dp), "$tag:ampm",
            )
        }
    }
}

/** Dates that move to a month or a year: the day goes back to the last one of a month that is shorter. Pure, so it is tested. */
object DateMath {
    fun withMonth(date: LocalDate, month: Int): LocalDate = date.withDayOfMonth(1).withMonth(month).let { it.withDayOfMonth(date.dayOfMonth.coerceAtMost(it.lengthOfMonth())) }

    fun withYear(date: LocalDate, year: Int): LocalDate = date.withDayOfMonth(1).withYear(year).let { it.withDayOfMonth(date.dayOfMonth.coerceAtMost(it.lengthOfMonth())) }
}

/** Hours on a 12-hour clock and back. Pure, so it is tested. */
object TimeMath {
    /** 0 and 12 are 12, 13 is 1: the hour as the clock face of 12 says it. */
    fun hour12(hour24: Int): Int = (hour24 % 12).let { if (it == 0) 12 else it }

    /** The hour of a 12-hour clock, in the afternoon or not, as 0 to 23. */
    fun hour24(hour12: Int, pm: Boolean): Int = (hour12 % 12) + if (pm) 12 else 0
}
