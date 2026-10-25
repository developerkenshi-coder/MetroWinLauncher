package com.metrowin.launcher.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** How the Start clock writes the time and the date. Pure, so it can be tested in any language. */
object ClockFormat {
    /** "14:05" on a 24 hour clock, "2:05" on a 12 hour one (the AM or PM goes in [meridiem]). */
    fun time(time: LocalTime, is24Hour: Boolean, locale: Locale): String =
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm", locale).format(time)

    /** "PM" on a 12 hour clock, empty on a 24 hour one. */
    fun meridiem(time: LocalTime, is24Hour: Boolean, locale: Locale): String =
        if (is24Hour) "" else DateTimeFormatter.ofPattern("a", locale).format(time)

    /** The number of the week of [date] in the calendar of [locale] (weeks start on Monday in Italy, on Sunday in the US). */
    fun week(date: LocalDate, locale: Locale): Int = date.get(WeekFields.of(locale).weekOfWeekBasedYear())

    /** The weekday, then the day and the month in the order of the language, without the year: "giovedì 5 marzo". */
    fun date(date: LocalDate, locale: Locale): String {
        val long = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.LONG, null, IsoChronology.INSTANCE, locale)
        val withoutYear = long
            .replace(Regex("[\\s,./-]*y+[\\s年]*(?=$|[^y])"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trimEnd(',', '.', '/', '-')
        return DateTimeFormatter.ofPattern("EEEE $withoutYear", locale).format(date)
    }
}
