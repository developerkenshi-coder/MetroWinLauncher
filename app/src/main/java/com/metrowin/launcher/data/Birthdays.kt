package com.metrowin.launcher.data

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/** A birthday from the contacts: the day and the month, and the year when the contact has it. */
@Immutable
data class Birthday(val name: String, val month: Int, val day: Int, val year: Int? = null)

/** The next birthday of each contact, and how far away it is. Pure, so it is tested with dates. */
object Birthdays {
    /** One birthday as it comes next from [today]: [daysAway] is 0 on the day itself; [turning] is the age, when the year is known. */
    @Immutable
    data class Next(val name: String, val date: LocalDate, val daysAway: Int, val turning: Int?)

    /** "1990-03-05" or "--03-05" (no year) as a birthday of [name]; null for anything else, including a day that does not exist. */
    fun parse(name: String, value: String?): Birthday? {
        val text = value?.trim() ?: return null
        val match = FORMAT.matchEntire(text) ?: return null
        val year = match.groupValues[1].takeIf { it.isNotEmpty() }?.toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        // 29 February is a valid birthday; any other impossible date is not.
        if (month !in 1..12 || day < 1 || day > java.time.Month.of(month).maxLength()) return null
        return Birthday(name, month, day, year?.takeIf { it in 1900..3000 })
    }

    /** The day of the next birthday on or after [today]; 29 February is celebrated on the 28th in years without it. */
    fun nextDate(birthday: Birthday, today: LocalDate): LocalDate {
        fun inYear(year: Int): LocalDate {
            val feb29 = birthday.month == 2 && birthday.day == 29 && !java.time.Year.isLeap(year.toLong())
            return LocalDate.of(year, birthday.month, if (feb29) 28 else birthday.day)
        }
        val thisYear = inYear(today.year)
        return if (thisYear >= today) thisYear else inYear(today.year + 1)
    }

    /** The birthdays that come soonest first, at most [limit]; equal days in alphabetical order. */
    fun upcoming(birthdays: List<Birthday>, today: LocalDate, limit: Int = 5): List<Next> =
        birthdays
            .map { b ->
                val date = nextDate(b, today)
                Next(b.name, date, (date.toEpochDay() - today.toEpochDay()).toInt(), b.year?.let { date.year - it }?.takeIf { it > 0 })
            }
            .sortedWith(compareBy<Next> { it.daysAway }.thenBy { AppSections.fold(it.name) })
            .take(limit)

    private val FORMAT = Regex("""^(?:(\d{4})-|--)(\d{2})-(\d{2})$""")
}
