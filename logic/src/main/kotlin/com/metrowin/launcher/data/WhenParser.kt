package com.metrowin.launcher.data

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * A moment said in words, in Italian or English: "domani alle 18", "tra 20 minuti", "venerdì sera", "next monday at 9",
 * "il 15 marzo", "at 6:30 pm". The words that said it are taken out of the text, which keeps its letters as they were typed.
 * Pure, so it is tested with sentences.
 */
object WhenParser {
    /** The moment ([at]), whether the sentence said the time of the day ([hasTime]), and what is left of the sentence ([rest]). */
    class Result(val at: LocalDateTime, val rest: String, val hasTime: Boolean)

    private class Hit(val range: IntRange, val groupValues: List<String>)

    private enum class Part(val hour: Int) { Morning(9), Afternoon(15), Evening(20), Night(22) }

    private val MONTHS = mapOf(
        "gennaio" to 1, "febbraio" to 2, "marzo" to 3, "aprile" to 4, "maggio" to 5, "giugno" to 6, "luglio" to 7, "agosto" to 8,
        "settembre" to 9, "ottobre" to 10, "novembre" to 11, "dicembre" to 12,
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6, "july" to 7, "august" to 8,
        "september" to 9, "october" to 10, "november" to 11, "december" to 12,
    )

    /** Short names, only taken after a day number ("15 dic"): "set" and "mar" would be words of their own. */
    private val MONTHS_SHORT = mapOf(
        "gen" to 1, "feb" to 2, "apr" to 4, "mag" to 5, "giu" to 6, "lug" to 7, "ago" to 8, "ott" to 10, "nov" to 11, "dic" to 12,
        "jan" to 1, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "dec" to 12,
    )

    private val WEEKDAYS = mapOf(
        "lunedi" to DayOfWeek.MONDAY, "martedi" to DayOfWeek.TUESDAY, "mercoledi" to DayOfWeek.WEDNESDAY, "giovedi" to DayOfWeek.THURSDAY,
        "venerdi" to DayOfWeek.FRIDAY, "sabato" to DayOfWeek.SATURDAY, "domenica" to DayOfWeek.SUNDAY,
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )

    private const val UNIT = """(?:minut[oi]|minutes?|min|ore|ora|hours?|hrs?|h|giorn[oi]|days?|settiman[ae]|weeks?|mes[ei]|months?)"""
    private val DURATION_NUMBER = Regex("""\b(?:tra|fra|in)\s+(\d+(?:[.,]\d+)?)\s*($UNIT)\b""")
    private val DURATION_ONE = Regex("""\b(?:tra|fra|in)\s+(?:un|una|an|a|one)\s+($UNIT)\b""")
    private val DURATION_HALF = Regex("""\b(?:tra|fra|in)\s+(?:mezz\s*ora|mezza\s*ora|mezzora|half an hour|half hour)\b""")
    private val DURATION_QUARTER = Regex("""\b(?:tra|fra)\s+un\s+quarto\s+d\s*ora\b|\bin\s+a\s+quarter\s+(?:of\s+an\s+)?hour\b""")

    private val MONTH_NAMES = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")
    private val MONTH_ALL = (MONTHS.keys + MONTHS_SHORT.keys).sortedByDescending { it.length }.joinToString("|")
    private val ISO_DATE = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val SLASH_DATE = Regex("""(?:\b(?:il|on|the)\s+)?\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")
    private val DOT_DATE = Regex("""\b(?:il|on)\s+(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?\b""")
    private val DAY_MONTH = Regex("""(?:\b(?:il|on|the)\s+)?\b(\d{1,2})(?:st|nd|rd|th|o)?\s+(?:di\s+|of\s+)?($MONTH_ALL)\b(?:\s+(\d{4}))?""")
    private val MONTH_DAY = Regex("""\b($MONTH_NAMES)\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4}))?""")
    private val DAY_NUMBER = Regex("""\b(?:il|on the|the)\s+(\d{1,2})\b(?![:.]\d)(?!\s*(?:am|pm))""")

    private val DAY_AFTER = Regex("""\b(?:dopodomani|day after tomorrow)\b""")
    private val TOMORROW = Regex("""\b(?:domani|tomorrow)\b""")
    private val TODAY = Regex("""\b(?:oggi|today)\b""")
    private val TONIGHT = Regex("""\b(stasera|stanotte|stamattina|stamani|stamane|tonight|this (?:morning|afternoon|evening))\b""")
    private val WEEKDAY = Regex("""\b(?:(prossim[oa]|next|quest[oa]|this|di|on)\s+)?(${WEEKDAYS.keys.sortedByDescending { it.length }.joinToString("|")})\b""")
    private val PART = Regex("""\b(?:(?:di|del|nel|nella|in the|at|la|in)\s+)?(mattina|mattino|pomeriggio|sera|notte|morning|afternoon|evening|night)\b""")

    private val MIDDAY = Regex("""\b(?:mezzogiorno|noon)\b""")
    private val MIDNIGHT = Regex("""\b(?:mezzanotte|midnight)\b""")
    private val TIME_AND = Regex("""\b(?:alle ore|alle|all|ore|at|@)\s*(\d{1,2})\s+e\s+(mezza|mezzo|un quarto|quarto|\d{1,2})\b""")
    private val TIME_LESS = Regex("""\b(?:alle ore|alle|all|ore|at|@)\s*(\d{1,2})\s+meno\s+(un quarto|quarto|\d{1,2})\b""")
    private val TIME_PREFIXED = Regex("""\b(?:alle ore|alle|all|ore|at|@)\s*(\d{1,2})(?:\s*[:.h]\s*(\d{2}))?\s*(a\.m\.|p\.m\.|am|pm)?(?!\d|:\d)""")
    private val TIME_COLON = Regex("""\b(\d{1,2}):(\d{2})\s*(a\.m\.|p\.m\.|am|pm)?(?!\d)""")
    private val TIME_MERIDIEM = Regex("""\b(\d{1,2})\s*(a\.m\.|p\.m\.|am|pm)(?![\p{L}\d])""")

    /** The moment in [text] counted from [now], or null when [text] says none. */
    fun parse(text: String, now: LocalDateTime): Result? {
        val folded = fold(text)
        val work = StringBuilder(folded)
        val removed = ArrayList<IntRange>()
        // The groups are copied at once: a match reads its text from [work], which is blanked as soon as it is taken.
        fun find(regex: Regex): Hit? = regex.find(work)?.let { Hit(it.range, it.groupValues.toList()) }
        fun take(hit: Hit?): Hit? {
            if (hit != null) {
                removed += hit.range
                for (i in hit.range) work.setCharAt(i, ' ')
            }
            return hit
        }

        val today = now.toLocalDate()

        // A length of time from now: minutes and hours are a moment on their own, days and more keep the time of the day.
        var afterDays = 0L
        var date: LocalDate? = null
        var weekdayToday = false
        val minutesAhead: Long? = run {
            take(find(DURATION_NUMBER))?.let { m ->
                val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@run null
                return@run durationOf(amount, m.groupValues[2]) { afterDays = it }
            }
            take(find(DURATION_ONE))?.let { m -> return@run durationOf(1.0, m.groupValues[1]) { afterDays = it } }
            take(find(DURATION_HALF))?.let { return@run 30L }
            take(find(DURATION_QUARTER))?.let { return@run 15L }
            null
        }
        if (minutesAhead != null) {
            return Result(now.plusMinutes(minutesAhead).withSecond(0).withNano(0), cleanRest(text, removed), hasTime = true)
        }
        if (afterDays > 0) date = today.plusDays(afterDays)

        // The date.
        if (date == null) {
            take(find(ISO_DATE))?.let { m -> date = safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        }
        if (date == null) {
            (take(find(SLASH_DATE)) ?: take(find(DOT_DATE)))?.let { m ->
                date = dayMonth(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], today)
            }
        }
        if (date == null) {
            take(find(DAY_MONTH))?.let { m ->
                val month = MONTHS[m.groupValues[2]] ?: MONTHS_SHORT[m.groupValues[2]]
                if (month != null) date = dayMonth(m.groupValues[1].toInt(), month, m.groupValues[3], today)
            }
        }
        if (date == null) {
            take(find(MONTH_DAY))?.let { m -> date = dayMonth(m.groupValues[2].toInt(), MONTHS.getValue(m.groupValues[1]), m.groupValues[3], today) }
        }
        var part: Part? = null
        if (date == null) {
            when {
                take(find(DAY_AFTER)) != null -> date = today.plusDays(2)
                take(find(TOMORROW)) != null -> date = today.plusDays(1)
                take(find(TODAY)) != null -> date = today
                else -> take(find(TONIGHT))?.let { m ->
                    date = today
                    part = partOfToday(m.groupValues[1])
                }
            }
        }
        if (date == null) {
            take(find(DAY_NUMBER))?.let { m -> date = nextDayOfMonth(m.groupValues[1].toInt(), today) }
        }
        if (date == null) {
            take(find(WEEKDAY))?.let { m ->
                val target = WEEKDAYS.getValue(m.groupValues[2])
                val next = m.groupValues[1].let { it.startsWith("prossim") || it == "next" }
                var delta = (target.value - today.dayOfWeek.value + 7) % 7
                if (delta == 0) {
                    if (next) delta = 7 else weekdayToday = true
                }
                date = today.plusDays(delta.toLong())
            }
        }
        take(find(PART))?.let { m ->
            part = when (m.groupValues[1]) {
                "mattina", "mattino", "morning" -> Part.Morning
                "pomeriggio", "afternoon" -> Part.Afternoon
                "sera", "evening" -> Part.Evening
                else -> Part.Night
            }
        }

        // The time of the day.
        var hour: Int? = null
        var minute = 0
        var pm: Boolean? = null
        fun meridiem(text: String): Boolean? = when (text.replace(".", "")) {
            "pm" -> true
            "am" -> false
            else -> null
        }
        when {
            take(find(MIDDAY)) != null -> hour = 12
            take(find(MIDNIGHT)) != null -> hour = 0
            else -> {
                val m = take(find(TIME_AND))
                if (m != null) {
                    hour = m.groupValues[1].toInt()
                    minute = when (val word = m.groupValues[2]) {
                        "mezza", "mezzo" -> 30
                        "un quarto", "quarto" -> 15
                        else -> word.toInt()
                    }
                } else {
                    val less = take(find(TIME_LESS))
                    if (less != null) {
                        hour = less.groupValues[1].toInt() - 1
                        minute = 60 - (if (less.groupValues[2].endsWith("quarto")) 15 else less.groupValues[2].toInt())
                        if (hour < 0) hour += 24
                    } else {
                        val t = take(find(TIME_PREFIXED)) ?: take(find(TIME_COLON)) ?: take(find(TIME_MERIDIEM))
                        if (t != null) {
                            hour = t.groupValues[1].toInt()
                            minute = t.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() && it[0].isDigit() }?.toInt() ?: 0
                            pm = meridiem(t.groupValues.last())
                        }
                    }
                }
            }
        }
        if (hour != null && (hour !in 0..24 || minute !in 0..59)) return null
        if (hour == 24) hour = 0

        if (date == null && hour == null && part == null) return null

        // am and pm: said, or understood from the part of the day.
        var h = hour
        if (h != null) {
            if (pm != null) {
                if (pm == true && h in 1..11) h += 12
                if (pm == false && h == 12) h = 0
            } else {
                val partOfDay = part
                if (partOfDay != null && h in 1..11) {
                    when (partOfDay) {
                        Part.Afternoon, Part.Evening -> h += 12
                        Part.Night -> if (h >= 6) h += 12
                        Part.Morning -> Unit
                    }
                } else if (partOfDay == Part.Night && h == 12) {
                    h = 0
                }
            }
        }
        val partDefault = part?.hour
        val time = when {
            h != null -> LocalTime.of(h, minute)
            partDefault != null -> LocalTime.of(partDefault, 0)
            else -> LocalTime.of(DEFAULT_HOUR, 0)
        }
        val hasTime = h != null || part != null

        val day = date
        val at: LocalDateTime = if (day == null) {
            // Only a time: today when it is still ahead, otherwise tomorrow. "alle 6" at 4 pm is the evening.
            var candidate = today.atTime(time)
            val vague = hour != null && hour in 1..11 && pm == null && part == null
            if (candidate <= now && vague) {
                val evening = today.atTime(time.plusHours(12))
                if (evening > now) candidate = evening
            }
            if (candidate <= now) candidate = candidate.plusDays(1)
            candidate
        } else {
            var candidate = day.atTime(time)
            if (weekdayToday && candidate <= now) candidate = candidate.plusDays(7)
            // A day without a time that is today and already past its usual hour: an hour from now.
            if (!hasTime && day == today && candidate <= now) candidate = now.plusHours(1).withSecond(0).withNano(0)
            candidate
        }
        return Result(at, cleanRest(text, removed), hasTime)
    }

    private fun durationOf(amount: Double, unit: String, days: (Long) -> Unit): Long? {
        return when {
            unit.startsWith("min") -> amount.toLong().takeIf { it in 1..MAX_MINUTES }
            unit.startsWith("or") || unit.startsWith("hour") || unit.startsWith("hr") || unit == "h" ->
                (amount * 60).toLong().takeIf { it in 1..MAX_MINUTES }
            unit.startsWith("giorn") || unit.startsWith("day") -> {
                days(amount.toLong().coerceIn(1L, 366L))
                null
            }
            unit.startsWith("settiman") || unit.startsWith("week") -> {
                days((amount * 7).toLong().coerceIn(1L, 366L))
                null
            }
            unit.startsWith("mes") || unit.startsWith("month") -> {
                days((amount * 30).toLong().coerceIn(1L, 366L))
                null
            }
            else -> null
        }
    }

    private fun partOfToday(word: String): Part = when (word) {
        "stasera", "tonight", "this evening" -> Part.Evening
        "stanotte" -> Part.Night
        "this afternoon" -> Part.Afternoon
        else -> Part.Morning
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? = runCatching { LocalDate.of(year, month, day) }.getOrNull()

    /** A day and a month with a year when one was said; otherwise this year, or the next when it is already past. */
    private fun dayMonth(day: Int, month: Int, yearText: String, today: LocalDate): LocalDate? {
        if (yearText.isNotEmpty()) {
            val year = yearText.toInt().let { if (it < 100) 2000 + it else it }
            return safeDate(year, month, day)
        }
        val thisYear = safeDate(today.year, month, day) ?: return null
        return if (thisYear < today) safeDate(today.year + 1, month, day) else thisYear
    }

    /** "il 15": the next day with that number, this month when it has not passed. */
    private fun nextDayOfMonth(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..31) return null
        var month = today.withDayOfMonth(1)
        repeat(13) {
            if (day <= month.lengthOfMonth()) {
                val candidate = month.withDayOfMonth(day)
                if (candidate >= today) return candidate
            }
            month = month.plusMonths(1)
        }
        return null
    }

    /** [text] with the [removed] places taken out, the spaces closed up and the words that joined them left off the ends. */
    private fun cleanRest(text: String, removed: List<IntRange>): String {
        // Places may overlap (a match swallows the blanks an earlier one left), so they are marked first and taken out together.
        val gone = BooleanArray(text.length)
        removed.forEach { range -> for (i in range) if (i in gone.indices) gone[i] = true }
        val builder = StringBuilder(text.length)
        text.forEachIndexed { i, c -> builder.append(if (gone[i]) ' ' else c) }
        val words = builder.toString().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        fun joiner(word: String) = word.trim(',', ';', ':', '-', '–', '.').lowercase(Locale.ROOT).let { it.isEmpty() || it in CONNECTORS }
        while (words.isNotEmpty() && joiner(words.first())) words.removeAt(0)
        while (words.isNotEmpty() && joiner(words.last())) words.removeAt(words.lastIndex)
        return words.joinToString(" ")
    }

    private val CONNECTORS = setOf(
        "di", "che", "to", "a", "per", "il", "lo", "la", "l", "alle", "all", "at", "on", "the", "of", "e", "and", "entro", "by", "for", "in", "da", "ore",
    )

    /** Same letters in the same places, lower case and with the accents off, so a match in it points into [text]. */
    fun fold(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text) {
            val letter = when (c) {
                '’', '‘', 'ʼ', '\'', '`', '´' -> ' '
                else -> Normalizer.normalize(c.toString(), Normalizer.Form.NFD).firstOrNull() ?: c
            }
            out.append(letter.lowercaseChar())
        }
        return out.toString()
    }

    private const val DEFAULT_HOUR = 9
    private const val MAX_MINUTES = 7 * 24 * 60L
}
