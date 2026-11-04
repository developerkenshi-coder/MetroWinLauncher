package com.metrowin.launcher.ui.mini

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroDatePicker
import com.metrowin.launcher.ui.components.MetroTimePicker
import com.metrowin.launcher.ui.start.DialogFrame
import androidx.compose.foundation.layout.fillMaxWidth
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A date, and a time unless [dateOnly], as wheels; [onChoose] gets the moment in the time zone of the phone (epoch milliseconds). */
@Composable
fun DateTimeDialog(initial: Long, dateOnly: Boolean = false, onChoose: (Long) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(initial).atZone(zone)
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime().withSecond(0).withNano(0)) }
    DialogFrame(onCancel = onCancel) {
        MetroDatePicker(date, { date = it }, years = (LocalDate.now().year - 5)..(LocalDate.now().year + 15), tag = "dt:date")
        if (!dateOnly) MetroTimePicker(time, DateFormat.is24HourFormat(context), { time = it }, tag = "dt:time")
        MetroButton(stringResource(R.string.todo_save), Modifier.fillMaxWidth().testTag("dt:ok")) {
            onChoose(LocalDateTime.of(date, if (dateOnly) java.time.LocalTime.MIDNIGHT else time).atZone(zone).toInstant().toEpochMilli())
        }
    }
}
