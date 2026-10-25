package com.metrowin.launcher.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Questions about dates the search box answers: "tra 30 giorni", "in 2 weeks", "45 days ago", "giorni al 25 dicembre",
 * "days until christmas". Pure, with today passed in, so it is tested with dates.
 */
object DateAnswers {
    private enum class Step { Day, Week, Month, Year }

    private val steps = mapOf(
        "giorno" to Step.Day, "giorni" to Step.Day, "day" to Step.Day, "days" to Step.Day,
        "settimana" to Step.Week, "settimane" to Step.Week, "week" to Step.Week, "weeks" to Step.Week,
        "mese" to Step.Month, "mesi" to Step.Month, "month" to Step.Month, "months" to Step.Month,
        "anno" to Step.Year, "anni" to Step.Year, "year" to Step.Year, "years" to Step.Year,
    )

    private val AHEAD = Regex("""^(?:(?:tra|fra|in) (\d{1,4}) (\p{L}+)|(\d{1,4}) (\p{L}+) (?:da oggi|da adesso|from today|from now))$""")
    private val AGO = Regex("""^(\d{1,4}) (\p{L}+) (?:fa|ago)$""")
    private val UNTIL = Regex("""^(?:(?:quanti )?giorni(?: mancano)?(?: a| al| alla| allo| fino a| fino al| fino alla)?|(?:how many )?days(?: left)?(?: until the| to the| till the| until| to| till| before)?) (.+)$""")

    /** The answer for [text] (already folded by `VoiceCommands.normalize`), or null. */
    fun of(text: String, today: LocalDate, locale: Locale = Locale.getDefault()): QuickAnswer? {
        AHEAD.matchEntire(text)?.let { m ->
            val (n, word) = if (m.groupValues[1].isNotEmpty()) m.groupValues[1] to m.groupValues[2] else m.groupValues[3] to m.groupValues[4]
            return shifted(today, n.toLong(), steps[word] ?: return null)
        }
        AGO.matchEntire(text)?.let { m -> return shifted(today, -m.groupValues[1].toLong(), steps[m.groupValues[2]] ?: return null) }
        UNTIL.matchEntire(text)?.let { m ->
            val target = parseDate(m.groupValues[1], today, locale) ?: return null
            return QuickAnswer.DaysUntil(target, ChronoUnit.DAYS.between(today, target))
        }
        return null
    }

    private fun shifted(today: LocalDate, amount: Long, step: Step): QuickAnswer.DateOffset? {
        val date = runCatching {
            when (step) {
                Step.Day -> today.plusDays(amount)
                Step.Week -> today.plusWeeks(amount)
                Step.Month -> today.plusMonths(amount)
                Step.Year -> today.plusYears(amount)
            }
        }.getOrNull() ?: return null
        if (date.year !in YEARS) return null
        return QuickAnswer.DateOffset(date, ChronoUnit.DAYS.between(today, date))
    }

    private val YEARS = 1900..2200

    private val months: Map<String, Int> = buildMap {
        listOf(
            listOf("gennaio", "january", "jan", "gen"), listOf("febbraio", "february", "feb"), listOf("marzo", "march", "mar"),
            listOf("aprile", "april", "apr"), listOf("maggio", "may", "mag"), listOf("giugno", "june", "jun", "giu"),
            listOf("luglio", "july", "jul", "lug"), listOf("agosto", "august", "aug", "ago"),
            listOf("settembre", "september", "sep", "sept", "set"), listOf("ottobre", "october", "oct", "ott"),
            listOf("novembre", "november", "nov"), listOf("dicembre", "december", "dec", "dic"),
        ).forEachIndexed { index, words -> words.forEach { put(it, index + 1) } }
    }

    private val FILLERS = setOf("il", "the", "di", "of", "del", "dell", "d")
    private val ORDINAL = Regex("""^(\d{1,2})(?:st|nd|rd|th|o|°)$""")

    /**
     * A date in [text] (folded and with single spaces): "25 dicembre", "december 25 2027", "25 12", "25 12 2026", "2026 12 25",
     * "natale", "pasqua", "domani". Without a year it is the next such day, today included.
     */
    fun parseDate(text: String, today: LocalDate, locale: Locale = Locale.getDefault()): LocalDate? {
        val tokens = text.split(' ').filter { it.isNotEmpty() && it !in FILLERS }.map { ORDINAL.matchEntire(it)?.groupValues?.get(1) ?: it }
        if (tokens.isEmpty()) return null
        named(tokens.joinToString(" "), today)?.let { return it }
        val numbers = tokens.map { it.toIntOrNull() }
        val month = tokens.firstNotNullOfOrNull { months[it] }
        return when {
            // "25 dicembre", "dicembre 25", with a year or without.
            month != null -> {
                val others = tokens.indices.filter { months[tokens[it]] == null }.map { numbers[it] ?: return null }
                when (others.size) {
                    1 -> next(month, others[0], today)
                    2 -> {
                        val (a, b) = others
                        // The year is the one with four digits, wherever it stands.
                        if (a > 31) onDay(a, month, b) else onDay(b, month, a)
                    }
                    else -> null
                }
            }
            numbers.any { it == null } -> null
            numbers.size == 2 -> {
                val (a, b) = numbers.map { it!! }
                if (monthFirst(locale) && a <= 12) next(a, b, today) else next(b, a, today)
            }
            numbers.size == 3 -> {
                val (a, b, c) = numbers.map { it!! }
                when {
                    a > 31 -> onDay(a, b, c) // 2026 12 25
                    monthFirst(locale) && a <= 12 -> onDay(c, a, b)
                    else -> onDay(c, b, a)
                }
            }
            else -> null
        }
    }

    private fun monthFirst(locale: Locale) = locale.country == "US"

    private fun onDay(year: Int, month: Int, day: Int): LocalDate? =
        if (year in YEARS) runCatching { LocalDate.of(year, month, day) }.getOrNull() else null

    /** The next [day] of [month] on or after [today]; 29 February waits for a leap year. */
    private fun next(month: Int, day: Int, today: LocalDate): LocalDate? {
        for (year in today.year..today.year + 8) {
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: continue
            if (date >= today) return date
        }
        return null
    }

    private fun named(text: String, today: LocalDate): LocalDate? = when (text) {
        "oggi", "today" -> today
        "domani", "tomorrow" -> today.plusDays(1)
        "ieri", "yesterday" -> today.minusDays(1)
        "natale", "christmas", "christmas day" -> next(12, 25, today)
        "capodanno", "new year", "new years day", "new year s day" -> next(1, 1, today)
        "epifania", "befana", "epiphany" -> next(1, 6, today)
        "san valentino", "valentine s day", "valentines day", "valentine" -> next(2, 14, today)
        "ferragosto" -> next(8, 15, today)
        "halloween" -> next(10, 31, today)
        "pasqua", "easter" -> easterOnOrAfter(today, 0)
        "pasquetta", "easter monday" -> easterOnOrAfter(today, 1)
        else -> null
    }

    private fun easterOnOrAfter(today: LocalDate, afterDays: Long): LocalDate {
        val thisYear = easter(today.year).plusDays(afterDays)
        return if (thisYear >= today) thisYear else easter(today.year + 1).plusDays(afterDays)
    }

    /** Easter Sunday of [year] (the Gregorian computus of Meeus, Jones and Butcher). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
