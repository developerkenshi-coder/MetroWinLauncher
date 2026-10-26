package com.metrowin.launcher.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * What the search box does with a sentence that asks for something, in Italian or English: "ricordami di chiamare Anna domani alle
 * 18", "evento cena venerdì alle 20", "nota: comprare il latte", "farmacia vicino a me", "scansiona qr". It is the same thing the
 * voice button leads to, without Cortana: the reminders are the launcher's own, the events go to Google Calendar, the notes to
 * Google Keep, the places to Google Maps and the camera to Google Lens. Pure, so it is tested with sentences.
 */
object Assistant {
    /** The answers for [query], the most likely first (usually one); empty for a sentence that asks for nothing. */
    fun of(query: String, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): List<QuickAnswer> {
        val text = query.trim()
        if (text.length < MIN_LENGTH) return emptyList()
        val folded = WhenParser.fold(text)
        scan(folded)?.let { return listOf(it) }
        reminder(text, folded, now, zone)?.let { return listOf(it) }
        event(text, folded, now, zone)?.let { return listOf(it) }
        note(text, folded)?.let { return listOf(it) }
        nearMe(text, folded)?.let { return listOf(it) }
        return emptyList()
    }

    // --- the camera

    private val SCAN_WORDS = setOf(
        "scan", "scanner", "scansiona", "scansione", "qr", "qr code", "qrcode", "codice qr", "scan qr", "scan qr code", "scan code", "scan text",
        "scan document", "scansiona qr", "scansiona qr code", "scansiona codice", "scansiona codice qr", "scansiona testo", "scansiona documento",
        "leggi qr", "leggi codice", "lens", "google lens", "ocr",
    )

    private fun scan(folded: String): QuickAnswer.Scan? =
        QuickAnswer.Scan.takeIf { folded.trim().replace(Regex("\\s+"), " ") in SCAN_WORDS }

    // --- reminders

    private val REMINDER_PREFIX = Regex(
        """^(?:(?:imposta|impostami|metti|mettimi|crea|creami|aggiungi|segna|fammi|set|add|create|make)\s+(?:un\s+|una\s+|a\s+|me\s+a\s+)?)?""" +
            """(?:ricordami|ricordarmi|ricorda|ricordati|promemoria|avvisami|reminder|remind me|remind|dont forget|don t forget)\b[\s:,\-]*""" +
            """(?:(?:di|che|to|about|that|for|per|quando)\b\s*)?""",
    )

    private fun reminder(text: String, folded: String, now: LocalDateTime, zone: ZoneId): QuickAnswer.Reminder? {
        val prefix = REMINDER_PREFIX.find(folded) ?: return null
        val body = text.substring(prefix.range.last + 1).trim()
        if (body.isEmpty()) return null
        val moment = WhenParser.parse(body, now)
        if (moment == null) return QuickAnswer.Reminder(capitalize(Reminders.clean(body) ?: return null), null)
        val what = Reminders.clean(moment.rest) ?: return null
        return QuickAnswer.Reminder(capitalize(what), moment.at.atZone(zone).toInstant().toEpochMilli())
    }

    // --- calendar events

    private val EVENT_PREFIX = Regex(
        """^(?:(?:aggiungi|crea|nuovo|nuova|metti|inserisci|segna|fissa|add|create|new|schedule|put|set up)\s+(?:un\s+|una\s+|a\s+|an\s+)?)?""" +
            """(?:evento|appuntamento|event|appointment)\b[\s:,\-]*(?:(?:di|per|a|to|for|called|titled|chiamato|intitolato)\b\s*)?""",
    )
    private val CALENDAR_SUFFIX = Regex(
        """^(?:aggiungi|metti|inserisci|segna|crea|add|put|create|schedule)\s+(.+?)\s+(?:al|sul|nel|in|to|on|in the|to the|on the)\s+""" +
            """(?:mio\s+|my\s+)?(?:calendario|agenda|calendar)$""",
    )
    private val CALENDAR_PREFIX = Regex("""^(?:calendario|calendar|agenda)\b[\s:,\-]+""")

    private fun event(text: String, folded: String, now: LocalDateTime, zone: ZoneId): QuickAnswer.CalendarEvent? {
        val body = EVENT_PREFIX.find(folded)?.let { text.substring(it.range.last + 1) }
            ?: CALENDAR_SUFFIX.find(folded)?.let { match -> match.groups[1]?.range?.let { text.substring(it) } }
            ?: CALENDAR_PREFIX.find(folded)?.let { text.substring(it.range.last + 1) }
            ?: return null
        val clean = body.trim()
        if (clean.isEmpty()) return null
        val moment = WhenParser.parse(clean, now)
        if (moment == null) return QuickAnswer.CalendarEvent(capitalize(Reminders.clean(clean) ?: return null), null, false)
        val title = Reminders.clean(moment.rest).orEmpty()
        return if (moment.hasTime) {
            QuickAnswer.CalendarEvent(capitalize(title), moment.at.atZone(zone).toInstant().toEpochMilli(), false)
        } else {
            // A day with no hour is an event of the whole day; the calendar counts those from midnight, UTC.
            val midnight = moment.at.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            QuickAnswer.CalendarEvent(capitalize(title), midnight, true)
        }
    }

    // --- notes

    private val NOTE_PREFIX = Regex(
        """^(?:(?:crea|scrivi|aggiungi|nuova|prendi|fai|take|make|add|new|write|create)\s+(?:una\s+|un\s+|a\s+|an\s+)?)?""" +
            """(?:nota|note|appunto|appunta|annota|memo)\b(?:\s+(?:to self|per me))?[\s:,\-]*(?:(?:che|di|that|to)\b\s*)?""",
    )

    private fun note(text: String, folded: String): QuickAnswer.Note? {
        val prefix = NOTE_PREFIX.find(folded) ?: return null
        val body = Reminders.clean(text.substring(prefix.range.last + 1)) ?: return null
        // One lone word after "note" is more likely an app being looked for than a note being written.
        if (!body.contains(' ') && !folded.contains(':')) return null
        return QuickAnswer.Note(capitalize(body))
    }

    // --- places near the user

    private val NEAR_AFTER = Regex(
        """^(.+?)\s+(?:vicino a me|vicino|qui vicino|qui intorno|nei dintorni|intorno a me|attorno a me|nelle vicinanze|piu vicin[oa]|near me|nearby|""" +
            """around me|close to me|near here|around here|closest|nearest)$""",
    )
    private val NEAR_BEFORE = Regex(
        """^(?:vicino a me|qui vicino|nei dintorni|near me|nearby|closest|nearest|il piu vicino|la piu vicina|piu vicino|piu vicina)\s+(.+)$""",
    )
    private val WHERE_IS = Regex("""^(?:dov e|dove|where is|where s)\s+(.+?)\s+(?:piu vicin[oa]|closest|nearest|vicin[oa] a me|near me)$""")
    private val ARTICLES = Regex("""^(?:il|lo|la|l|i|gli|le|un|uno|una|the|a|an)\s+""", RegexOption.IGNORE_CASE)

    private fun nearMe(text: String, folded: String): QuickAnswer.NearMe? {
        val match = WHERE_IS.find(folded) ?: NEAR_AFTER.find(folded) ?: NEAR_BEFORE.find(folded) ?: return null
        // The words as they were typed, with their accents: the folded ones are only for finding the phrase.
        val what = ARTICLES.replace(text.substring(match.groups[1]?.range ?: return null).trim(), "").trim()
        return if (what.length in 2..MAX_NEAR) QuickAnswer.NearMe(what) else null
    }

    private fun capitalize(text: String): String = text.replaceFirstChar { it.uppercase() }

    private const val MIN_LENGTH = 3
    private const val MAX_NEAR = 40
}
