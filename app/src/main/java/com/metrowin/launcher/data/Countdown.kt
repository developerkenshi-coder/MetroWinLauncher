package com.metrowin.launcher.data

import java.time.LocalDate

/** The days between today and the date a countdown tile counts to. Pure, so it is tested with dates. */
object Countdown {
    /** Days from [today] to [targetDay] (an epoch day): positive before the date, 0 on it, negative after it. */
    fun daysLeft(targetDay: Long, today: LocalDate): Long = targetDay - today.toEpochDay()

    /** A date to start from when a countdown is made: a week from [today]. */
    fun defaultDay(today: LocalDate): Long = today.plusWeeks(1).toEpochDay()

    fun date(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    /** [date] moved by one day, month or year ([field]), [delta] times; a month or a year keeps the day when there is one. */
    fun step(date: LocalDate, field: Field, delta: Int): LocalDate = when (field) {
        Field.Day -> date.plusDays(delta.toLong())
        Field.Month -> date.plusMonths(delta.toLong())
        Field.Year -> date.plusYears(delta.toLong())
    }

    enum class Field { Day, Month, Year }
}
