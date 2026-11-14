package com.metrowin.launcher.mail

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.metrowin.launcher.R
import com.metrowin.launcher.data.ClockFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** How a date of the list is said: the hour for today, "Yesterday", the weekday for the last days, the date after that. */
enum class MailDay { Today, Yesterday, Weekday, Date }

object MailDates {
    /** Which way of saying [millis] fits, as seen on [now]. */
    fun day(millis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): MailDay {
        val days = java.time.temporal.ChronoUnit.DAYS.between(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        return when {
            days == 0L || days < 0 -> MailDay.Today
            days == 1L -> MailDay.Yesterday
            days < 7 -> MailDay.Weekday
            else -> MailDay.Date
        }
    }

    fun weekday(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
}

/** "18:00", "Yesterday", "Mon", "26 Oct": the moment of a message as short as the list needs it, in the language and the clock of the phone. */
@Composable
fun mailWhen(millis: Long, now: Long = System.currentTimeMillis()): String {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val yesterday = stringResource(R.string.mail_yesterday)
    return when (MailDates.day(millis, now, zone)) {
        MailDay.Today -> {
            val is24 = DateFormat.is24HourFormat(context)
            val meridiem = ClockFormat.meridiem(at.toLocalTime(), is24, locale)
            ClockFormat.time(at.toLocalTime(), is24, locale) + if (meridiem.isEmpty()) "" else " $meridiem"
        }
        MailDay.Yesterday -> yesterday
        MailDay.Weekday -> MailDates.weekday(at.toLocalDate(), locale)
        MailDay.Date -> ClockFormat.date(at.toLocalDate(), locale)
    }
}
