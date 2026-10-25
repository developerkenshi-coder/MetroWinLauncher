package com.metrowin.launcher.data

import androidx.compose.runtime.Immutable
import java.util.Locale

/** What the search box can answer or do for what is typed, besides finding apps. */
@Immutable
sealed interface QuickAnswer {
    /** "12*3+4 = 40": touching it copies the result. */
    data class Calculation(val expression: String, val result: String) : QuickAnswer

    /** "5 km = 3.10686 mi": touching it copies the result. */
    data class Conversion(val text: String, val result: String) : QuickAnswer

    data class Call(val number: String) : QuickAnswer
    data class Message(val number: String) : QuickAnswer
    data class Link(val url: String, val shown: String) : QuickAnswer
    data class Email(val address: String) : QuickAnswer

    /** A timer of [seconds], started in the clock app. */
    data class Timer(val seconds: Int) : QuickAnswer

    /** An alarm at [hour]:[minute] (0 to 23, 0 to 59), set in the clock app. */
    data class Alarm(val hour: Int, val minute: Int) : QuickAnswer
}

/**
 * Everything the search box understands by itself, with no network: sums, unit conversions, phone numbers, links, email
 * addresses, timers and alarms. Pure, so it is tested with words. Voice commands share the timer and alarm parsing.
 */
object QuickAnswers {
    /** The answers for [query], the most likely first (usually one); empty for a word that is just a name. */
    fun of(query: String, locale: Locale = Locale.getDefault()): List<QuickAnswer> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        timerOrAlarm(VoiceCommands.normalize(text))?.let { return listOf(it) }
        Calculator.evaluate(text)?.let { return listOf(QuickAnswer.Calculation(text, Calculator.format(it, locale))) }
        UnitConverter.convert(text, locale)?.let { return listOf(QuickAnswer.Conversion(it.text, it.result)) }
        email(text)?.let { return listOf(it) }
        phone(text)?.let { return listOf(QuickAnswer.Call(it), QuickAnswer.Message(it)) }
        link(text)?.let { return listOf(it) }
        return emptyList()
    }

    // --- phone numbers, links, email

    private val PHONE = Regex("""^\+?[\d\s().-]{6,20}$""")
    private val EMAIL = Regex("""^[\w.+-]+@[\w-]+(\.[\w-]+)*\.\p{L}{2,}$""")
    private val LINK = Regex("""^(?:https?://)?(?:www\.)?[\p{L}\d-]+(?:\.[\p{L}\d-]+)*\.\p{L}{2,}(?::\d+)?(?:[/?#]\S*)?$""", RegexOption.IGNORE_CASE)

    /** A phone number as typed: at least six digits and nothing but the signs numbers are written with. */
    fun phone(text: String): String? {
        val trimmed = text.trim()
        if (!PHONE.matches(trimmed) || trimmed.count { it.isDigit() } < 6) return null
        return PhoneCalls.clean(trimmed).takeIf { it.isNotEmpty() }
    }

    fun email(text: String): QuickAnswer.Email? = text.trim().takeIf { EMAIL.matches(it) }?.let { QuickAnswer.Email(it) }

    /** An address of a web page: with or without `https://` and `www.`; a bare file name like "notes.txt" is not one. */
    fun link(text: String): QuickAnswer.Link? {
        val trimmed = text.trim()
        if (' ' in trimmed || !LINK.matches(trimmed)) return null
        val host = trimmed.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore('?').substringBefore('#')
        val tld = host.substringAfterLast('.').lowercase(Locale.ROOT)
        val explicit = trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) || trimmed.startsWith("www.", true)
        // "dr.house" and "notes.txt" are not addresses: without http or www the ending must be a real domain ending.
        if (!explicit && tld !in KNOWN_ENDINGS) return null
        val url = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
        return QuickAnswer.Link(url, trimmed)
    }

    /** The domain endings a bare "name.ending" is taken for an address with: the common ones and the big countries'. */
    private val KNOWN_ENDINGS = setOf(
        "com", "org", "net", "edu", "gov", "info", "io", "app", "dev", "co", "me", "tv", "ai", "xyz", "online", "site", "store", "tech",
        "news", "blog", "shop", "cloud", "it", "de", "fr", "es", "uk", "eu", "ch", "at", "nl", "be", "pl", "ru", "jp", "cn", "in", "us",
        "ca", "au", "br", "pt", "se", "no", "dk", "fi", "gr", "ie", "cz", "tr", "mx", "ar", "kr", "nz",
    )

    // --- timers and alarms

    private val timerWords = setOf("timer", "countdown", "cronometro")
    private val alarmWords = setOf("sveglia", "svegliami", "svegliare", "alarm", "wake")
    private val durationUnit = Regex("""(\d+(?:\.\d+)?)\s*(ore|ora|hours?|hrs?|h|minuti|minuto|minutes?|mins?|m|secondi|secondo|seconds?|secs?|s)(?![\p{L}\d])""")
    private val hoursAndMinutes = Regex("""(?<![\d.])(\d+)\s*h\s*(\d{1,2})(?![\p{L}\d])""")
    private val bareNumber = Regex("""^\D*(\d+)\D*$""")

    /**
     * A timer or an alarm in [normalized] (text already folded by `VoiceCommands.normalize`: lower case, no accents, words
     * separated by single spaces, so "7:30" is "7 30"), or null. "timer 10 minuti", "imposta un timer di un ora e 30",
     * "set a timer for 90 seconds", "sveglia alle 7 e 30", "alarm at 6 45 pm", "wake me at 6".
     */
    fun timerOrAlarm(normalized: String): QuickAnswer? {
        val words = normalized.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val timerAt = words.indexOfFirst { it in timerWords }
        val alarmAt = words.indexOfFirst { it in alarmWords }
        return when {
            timerAt >= 0 && (alarmAt < 0 || timerAt < alarmAt) -> timer(words.drop(timerAt + 1).joinToString(" "))
            alarmAt >= 0 -> alarm(words.drop(alarmAt + 1), words.take(alarmAt))
            else -> null
        }
    }

    private val oneUnit = Regex("""(?<![\p{L}\d])(?:un|una|an|a|one)\s+(ora|hour|minuto|minute|secondo|second)(?![\p{L}\d])""")

    private fun timer(rest: String): QuickAnswer.Timer? {
        var seconds = 0.0
        // "un ora", "an hour": one of them.
        var remaining = oneUnit.replace(rest) { "1 ${it.groupValues[1]}" }
        // "mezz'ora", "half an hour"
        for (phrase in listOf("mezz ora", "mezza ora", "mezzora", "half an hour", "half hour")) {
            if (phrase in remaining) {
                seconds += 30 * 60
                remaining = remaining.replace(phrase, " ")
            }
        }
        hoursAndMinutes.findAll(remaining).toList().forEach { m ->
            seconds += m.groupValues[1].toInt() * 3600.0 + m.groupValues[2].toInt() * 60.0
        }
        remaining = hoursAndMinutes.replace(remaining, " ")
        val parts = durationUnit.findAll(remaining).toList()
        parts.forEach { m ->
            val n = m.groupValues[1].toDouble()
            seconds += when (m.groupValues[2]) {
                "ore", "ora", "hour", "hours", "hr", "hrs", "h" -> n * 3600
                "minuti", "minuto", "minute", "minutes", "min", "mins", "m" -> n * 60
                else -> n
            }
        }
        // "un ora e 30": the unit of the last number is understood to be the one below.
        if (parts.size == 1 && parts[0].groupValues[2] in setOf("ore", "ora", "hour", "hours", "hr", "hrs", "h")) {
            Regex("""(?:e|and)\s+(\d{1,2})\s*$""").find(remaining)?.let { seconds += it.groupValues[1].toInt() * 60.0 }
        }
        if (seconds == 0.0 && parts.isEmpty()) {
            // "timer 10": minutes.
            bareNumber.matchEntire(remaining.trim())?.let { seconds = it.groupValues[1].toDouble() * 60 }
        }
        val total = seconds.toInt()
        return if (total in 1..MAX_TIMER_SECONDS) QuickAnswer.Timer(total) else null
    }

    private val numberWord = Regex("""^\d{1,2}$""")
    private val skipped = setOf("alle", "ore", "le", "at", "for", "per", "a", "the", "to", "all", "l", "me", "mi", "up", "tu", "ti", "alle")

    private fun alarm(after: List<String>, before: List<String>): QuickAnswer.Alarm? {
        val tokens = after.filter { it !in skipped }
        val hourToken = tokens.firstOrNull() ?: return null
        if (!numberWord.matches(hourToken)) return null
        var hour = hourToken.toInt()
        var minute = 0
        val second = tokens.getOrNull(1)
        val third = tokens.getOrNull(2)
        // "7 e 75" is not a time.
        if ((second == "e" || second == "and") && third != null && numberWord.matches(third) && third.toInt() >= 60) return null
        when {
            second != null && numberWord.matches(second) && second.toInt() < 60 -> minute = second.toInt()
            (second == "e" || second == "and") && third != null && numberWord.matches(third) && third.toInt() < 60 -> minute = third.toInt()
            (second == "e" || second == "and") && third in setOf("mezza", "mezzo", "half") -> minute = 30
            (second == "e" || second == "and") && third in setOf("un", "quarto", "quarter") -> minute = 15
            (second == "meno") && third != null && numberWord.matches(third) && third.toInt() in 1..59 -> { hour -= 1; minute = 60 - third.toInt() }
        }
        val all = before + tokens
        val pm = all.any { it == "pm" || it == "sera" || it == "pomeriggio" || it == "stasera" }
        val am = all.any { it == "am" || it == "mattina" || it == "mattino" || it == "notte" }
        if (pm && hour in 1..11) hour += 12
        if (am && hour == 12) hour = 0
        if (hour < 0) hour += 24
        return if (hour in 0..23 && minute in 0..59) QuickAnswer.Alarm(hour, minute) else null
    }

    private const val MAX_TIMER_SECONDS = 24 * 3600
}
