package com.metrowin.launcher.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** An event read from an iCalendar file, not yet written to a calendar of the phone. [end] is exclusive, as in [CalEvent]. */
data class IcsEvent(
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String = "",
    val description: String = "",
    /** The repeat rule as written in the file (checked: only letters, digits and `=;,-+:`), or blank. */
    val rule: String = "",
    val reminders: List<Int> = emptyList(),
)

/** Writes and reads iCalendar (.ics) text. Pure, so it is tested. A file from outside is untrusted: sizes and counts are limited. */
object Ics {
    const val MAX_EVENTS = 2_000
    const val MAX_CHARS = 5_000_000
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val DAY = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** The [events] as a calendar file. A repeating event is written once for each time it comes (without its rule): shortcut: whole series are not exported, upgrade when a series must survive the trip. */
    fun write(events: List<CalEvent>, now: Long = System.currentTimeMillis()): String {
        val out = StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//MetroWin Launcher//Calendar//EN\r\nCALSCALE:GREGORIAN\r\n")
        val stamp = STAMP.format(Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC))
        for (e in events) {
            line(out, "BEGIN:VEVENT")
            line(out, "UID:${e.id}-${e.start}@metrowin")
            line(out, "DTSTAMP:$stamp")
            if (e.allDay) {
                line(out, "DTSTART;VALUE=DATE:" + DAY.format(Instant.ofEpochMilli(e.start).atZone(ZoneOffset.UTC)))
                line(out, "DTEND;VALUE=DATE:" + DAY.format(Instant.ofEpochMilli(e.end).atZone(ZoneOffset.UTC)))
            } else {
                line(out, "DTSTART:" + STAMP.format(Instant.ofEpochMilli(e.start).atZone(ZoneOffset.UTC)))
                line(out, "DTEND:" + STAMP.format(Instant.ofEpochMilli(e.end).atZone(ZoneOffset.UTC)))
            }
            line(out, "SUMMARY:" + escape(e.title))
            if (e.location.isNotBlank()) line(out, "LOCATION:" + escape(e.location))
            if (e.description.isNotBlank()) line(out, "DESCRIPTION:" + escape(e.description))
            for (m in e.reminders) {
                line(out, "BEGIN:VALARM"); line(out, "ACTION:DISPLAY"); line(out, "DESCRIPTION:" + escape(e.title)); line(out, "TRIGGER:-PT${m}M"); line(out, "END:VALARM")
            }
            line(out, "END:VEVENT")
        }
        line(out, "END:VCALENDAR")
        return out.toString()
    }

    private fun escape(text: String) = text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")

    /** One line, folded as the format asks (a continuation starts with a space). */
    private fun line(out: StringBuilder, text: String) {
        var rest = text
        var first = true
        while (rest.length > 70) {
            var cut = 70
            if (Character.isHighSurrogate(rest[cut - 1])) cut--
            out.append(if (first) "" else " ").append(rest, 0, cut).append("\r\n")
            rest = rest.substring(cut); first = false
        }
        out.append(if (first) "" else " ").append(rest).append("\r\n")
    }

    /** The text of [input], at most [MAX_CHARS] bytes of it: a file from outside is never read whole. */
    fun readBounded(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8_192)
        while (out.size() < MAX_CHARS) {
            val n = input.read(chunk, 0, minOf(chunk.size, MAX_CHARS - out.size()))
            if (n < 0) break
            out.write(chunk, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** The events of the calendar file [text], at most [MAX_EVENTS]; whatever is not understood is left out. [zone] is where a time without a zone is. */
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<IcsEvent> {
        val lines = text.take(MAX_CHARS).replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "").split('\n')
        val out = ArrayList<IcsEvent>()
        var props: HashMap<String, Pair<Map<String, String>, String>>? = null
        var alarms = ArrayList<Int>()
        var inAlarm = false
        var trigger: String? = null
        for (raw in lines) {
            val colon = raw.indexOf(':')
            if (colon <= 0) continue
            val head = raw.substring(0, colon).split(';')
            val name = head[0].uppercase()
            val params = head.drop(1).mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it).uppercase() to p.substring(it + 1).trim('"') } }.toMap()
            val value = raw.substring(colon + 1)
            when {
                name == "BEGIN" && value.equals("VEVENT", true) -> { props = HashMap(); alarms = ArrayList() }
                name == "BEGIN" && value.equals("VALARM", true) && props != null -> { inAlarm = true; trigger = null }
                name == "END" && value.equals("VALARM", true) -> { inAlarm = false; trigger?.let { reminderOf(it) }?.let { alarms += it } }
                name == "END" && value.equals("VEVENT", true) -> {
                    props?.let { p -> build(p, alarms, zone)?.let { if (out.size < MAX_EVENTS) out += it } }
                    props = null
                }
                inAlarm -> if (name == "TRIGGER") trigger = value
                props != null && name !in props -> props[name] = params to value
            }
        }
        return out
    }

    private fun build(p: Map<String, Pair<Map<String, String>, String>>, alarms: List<Int>, zone: ZoneId): IcsEvent? {
        if (p["STATUS"]?.second.equals("CANCELLED", true)) return null
        val (sp, sv) = p["DTSTART"] ?: return null
        val allDay = sp["VALUE"].equals("DATE", true) || sv.length == 8
        val start = moment(sv, sp, zone) ?: return null
        val end = p["DTEND"]?.let { (ep, ev) -> moment(ev, ep, zone) }
            ?: p["DURATION"]?.second?.let { durationMs(it) }?.let { start + it }
            ?: (start + if (allDay) 86_400_000L else CalendarDefaults.DURATION_MS)
        val rule = p["RRULE"]?.second?.trim().orEmpty().takeIf { it.startsWith("FREQ=", true) && it.length <= 200 && it.all { c -> c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "=;,-+:" } }.orEmpty()
        return IcsEvent(
            title = unescape(p["SUMMARY"]?.second.orEmpty()).take(200), start = start, end = maxOf(end, start + if (allDay) 86_400_000L else CalendarDefaults.MIN_EVENT_MS),
            allDay = allDay, location = unescape(p["LOCATION"]?.second.orEmpty()).take(200), description = unescape(p["DESCRIPTION"]?.second.orEmpty()).take(2_000),
            rule = rule.uppercase(), reminders = alarms.distinct().sorted(),
        )
    }

    private fun moment(value: String, params: Map<String, String>, zone: ZoneId): Long? = runCatching {
        if (params["VALUE"].equals("DATE", true) || value.length == 8) LocalDate.parse(value.take(8), DAY).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else {
            val local = LocalDateTime.parse(value.removeSuffix("Z").take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            val where = if (value.endsWith("Z")) ZoneOffset.UTC else params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
            local.atZone(where).toInstant().toEpochMilli()
        }
    }.getOrNull()

    /** "PT1H30M", "P1D", "P2W": a length in milliseconds. */
    fun durationMs(text: String): Long? {
        val m = Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$", RegexOption.IGNORE_CASE).find(text.trim()) ?: return null
        val g = m.groupValues
        fun n(i: Int) = g[i].toLongOrNull() ?: 0L
        val ms = ((n(2) * 7 + n(3)) * 86_400L + n(4) * 3_600L + n(5) * 60L + n(6)) * 1000L
        return if (g[1] == "-") -ms else ms
    }

    /** A reminder TRIGGER before the start ("-PT15M") as minutes; null for any other kind. */
    private fun reminderOf(trigger: String): Int? = durationMs(trigger)?.takeIf { it <= 0 }?.let { (-it / 60_000L).toInt() }?.takeIf { it <= CalendarDefaults.MAX_REMINDER_MIN }

    private fun unescape(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                out.append(when (val n = text[i + 1]) { 'n', 'N' -> '\n'; else -> n }); i += 2
            } else { out.append(c); i++ }
        }
        return out.toString()
    }
}
