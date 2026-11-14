package com.metrowin.launcher.todo

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The things to do as text for other apps: Markdown, todo.txt and CSV out, Markdown and todo.txt in. Pure, so it is tested. */
object TodoFormats {
    enum class Format(val extension: String, val mime: String) { Markdown("md", "text/markdown"), TodoTxt("txt", "text/plain"), Csv("csv", "text/csv") }

    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val day = DateTimeFormatter.ISO_LOCAL_DATE

    fun export(items: List<TodoItem>, format: Format, zone: ZoneId = ZoneId.systemDefault()): String = when (format) {
        Format.Markdown -> markdown(items, zone)
        Format.TodoTxt -> todoTxt(items, zone)
        Format.Csv -> csv(items, zone)
    }

    private fun at(millis: Long, zone: ZoneId) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    private fun markdown(items: List<TodoItem>, zone: ZoneId): String = buildString {
        // The main list first, with no heading; then every named list under its own.
        items.groupBy { it.list }.toList().sortedBy { it.first.isNotEmpty() }.forEach { (list, rows) ->
            if (list.isNotEmpty()) append("\n## ").append(list).append('\n')
            rows.forEach { item ->
                append("- [").append(if (item.done) 'x' else ' ').append("] ").append(item.text)
                if (item.due != 0L) append(" [due: ").append(dateTime.format(at(item.due, zone))).append(']')
                if (item.starred) append(" [star]")
                if (item.repeat != Repeat.None) append(" [repeat: ").append(item.repeat.name.lowercase()).append(']')
                append('\n')
                if (item.note.isNotEmpty()) item.note.lines().forEach { append("  > ").append(it).append('\n') }
                item.subs.forEach { append("  - [").append(if (it.done) 'x' else ' ').append("] ").append(it.text).append('\n') }
            }
        }
    }.trimStart('\n')

    /** todo.txt: "(A)" is a star, "x" a done one; the list is "+name" (spaces become "_") and the date "due:" with the hour as "at:". */
    private fun todoTxt(items: List<TodoItem>, zone: ZoneId): String = buildString {
        items.forEach { item ->
            if (item.done) append("x ")
            else if (item.starred) append("(A) ")
            (item.created.takeIf { it != 0L } ?: item.completedAt).takeIf { it != 0L }?.let { append(day.format(at(it, zone))).append(' ') }
            append(item.text)
            if (item.list.isNotEmpty()) append(" +").append(item.list.replace(' ', '_'))
            if (item.due != 0L) {
                val t = at(item.due, zone)
                append(" due:").append(day.format(t)).append(" at:").append(t.toLocalTime().toString())
            }
            append('\n')
        }
    }

    private fun csv(items: List<TodoItem>, zone: ZoneId): String = buildString {
        append("text,done,due,starred,list,note\n")
        fun cell(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        items.forEach {
            append(cell(it.text)).append(',').append(it.done).append(',').append(if (it.due == 0L) "" else dateTime.format(at(it.due, zone))).append(',')
                .append(it.starred).append(',').append(cell(it.list)).append(',').append(cell(it.note)).append('\n')
        }
    }

    // --- in

    private val mdLine = Regex("""^(\s*)[-*+]\s+\[([ xX])]\s+(.*)$""")
    private val mdTag = Regex("""\s*\[(due|repeat): ?([^\]]*)]|\s*\[star]""")

    /**
     * The things to do in [text], as items with id 0 (the caller gives them ids): Markdown check lists or todo.txt, whichever it is.
     * A line that is neither is skipped; the limits of text and list are the usual ones.
     */
    fun parse(text: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<TodoItem> {
        val lines = text.lines()
        return if (lines.any { mdLine.matches(it) }) parseMarkdown(lines, now, zone) else parseTodoTxt(lines, now, zone)
    }

    private fun parseMarkdown(lines: List<String>, now: Long, zone: ZoneId): List<TodoItem> {
        val out = mutableListOf<TodoItem>()
        var list = ""
        for (line in lines) {
            if (line.startsWith("#")) { list = Todos.cleanList(line.trimStart('#')); continue }
            val m = mdLine.matchEntire(line)
            val trimmed = line.trim()
            if (m != null && m.groupValues[1].isEmpty()) {
                var body = m.groupValues[3]
                var due = 0L
                var star = false
                var repeat = Repeat.None
                mdTag.findAll(body).forEach { t ->
                    when {
                        t.value.trim() == "[star]" -> star = true
                        t.groupValues[1] == "due" -> due = parseDateTime(t.groupValues[2].trim(), zone)
                        t.groupValues[1] == "repeat" -> repeat = Repeat.entries.firstOrNull { it.name.equals(t.groupValues[2].trim(), true) } ?: Repeat.None
                    }
                }
                body = mdTag.replace(body, "")
                val clean = Todos.clean(body) ?: continue
                out += TodoItem(0, clean, m.groupValues[2] != " ", due, star, list, created = now, completedAt = if (m.groupValues[2] != " ") now else 0L, repeat = repeat)
            } else if (m != null && out.isNotEmpty()) {
                val last = out.last()
                val sub = m.groupValues[3].trim().take(Todos.MAX_SUB_TEXT)
                if (sub.isNotEmpty() && last.subs.size < Todos.MAX_SUBS) out[out.lastIndex] = last.copy(subs = last.subs + Sub(sub, m.groupValues[2] != " "))
            } else if (trimmed.startsWith(">") && out.isNotEmpty()) {
                val last = out.last()
                out[out.lastIndex] = last.copy(note = Todos.cleanNote(if (last.note.isEmpty()) trimmed.removePrefix(">").trim() else last.note + "\n" + trimmed.removePrefix(">").trim()))
            }
        }
        return out
    }

    private fun parseTodoTxt(lines: List<String>, now: Long, zone: ZoneId): List<TodoItem> = lines.mapNotNull { raw ->
        var words = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return@mapNotNull null
        val done = words.first() == "x"
        if (done) words = words.drop(1)
        val star = words.firstOrNull()?.let { it.length == 3 && it[0] == '(' && it[2] == ')' && it[1] in 'A'..'Z' } == true
        if (star) words = words.drop(1)
        // Up to two leading dates (completion, creation) are not part of the text.
        while (words.isNotEmpty() && Regex("""\d{4}-\d{2}-\d{2}""").matches(words.first())) words = words.drop(1)
        var list = ""
        var dueDay: LocalDate? = null
        var dueTime: LocalTime? = null
        val text = words.filter { w ->
            when {
                w.startsWith("+") && w.length > 1 -> { if (list.isEmpty()) list = Todos.cleanList(w.drop(1).replace('_', ' ')); false }
                w.startsWith("due:") -> { dueDay = runCatching { LocalDate.parse(w.drop(4)) }.getOrNull(); false }
                w.startsWith("at:") -> { dueTime = runCatching { LocalTime.parse(w.drop(3)) }.getOrNull(); false }
                else -> true
            }
        }.joinToString(" ")
        val clean = Todos.clean(text) ?: return@mapNotNull null
        val due = dueDay?.let { it.atTime(dueTime ?: LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli() } ?: 0L
        TodoItem(0, clean, done, due, star, list, created = now, completedAt = if (done) now else 0L)
    }

    private fun parseDateTime(text: String, zone: ZoneId): Long = runCatching {
        LocalDateTime.parse(text, dateTime).atZone(zone).toInstant().toEpochMilli()
    }.getOrElse {
        runCatching { LocalDate.parse(text).atTime(9, 0).atZone(zone).toInstant().toEpochMilli() }.getOrDefault(0L)
    }

    /** [items] with the ones in [drafts] added (ids given, the ones already there with the same text, list and state skipped, the limit kept). */
    fun merge(items: List<TodoItem>, drafts: List<TodoItem>, firstId: Long): List<TodoItem> {
        var id = maxOf(firstId, Todos.nextId(items))
        val known = items.map { Triple(it.text.lowercase(), it.list, it.done) }.toMutableSet()
        val added = mutableListOf<TodoItem>()
        for (d in drafts) {
            if (items.size + added.size >= Todos.MAX_ITEMS) break
            if (!known.add(Triple(d.text.lowercase(), d.list, d.done))) continue
            added += d.copy(id = id++)
        }
        return items + added
    }
}

/**
 * The time and the day a phrase says: "buy milk tomorrow at 6 pm" is "buy milk" for tomorrow 18:00. The words of the six languages of the
 * launcher; a number is read as an hour only after "at" (or its word) or with a colon, so "2 apples" stays whole.
 */
object SpokenDate {
    class Result(val text: String, val due: Long)

    private val dayWords = mapOf(
        0 to listOf("today", "oggi", "heute", "hoy", "hoje", "aujourd'hui", "aujourd’hui"),
        1 to listOf("tomorrow", "domani", "morgen", "mañana", "manana", "amanhã", "amanha", "demain"),
        2 to listOf("day after tomorrow", "dopodomani", "übermorgen", "pasado mañana", "depois de amanhã", "après-demain", "apres-demain"),
    )
    private val clock = Regex(
        """(?:\b(?:at|alle|ore|à|um|a las|a la|às|as)\s+)(\d{1,2})(?:\s*[:.h]\s*(\d{2}))?(?:\s*(am|pm))?\b|\b(\d{1,2})\s*[:.h]\s*(\d{2})(?:\s*(am|pm))?\b|\b(\d{1,2})\s*(am|pm)\b""",
        RegexOption.IGNORE_CASE,
    )
    private const val MORNING = 9

    fun parse(phrase: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Result {
        var text = phrase.trim()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var plus: Int? = null
        // The longest words first, so "day after tomorrow" is not read as "tomorrow".
        dayWords.entries.sortedByDescending { it.key }.forEach { (n, words) ->
            if (plus != null) return@forEach
            words.sortedByDescending { it.length }.forEach { w ->
                if (plus == null) Regex("(?<![\\p{L}])" + Regex.escape(w) + "(?![\\p{L}])", RegexOption.IGNORE_CASE).find(text)?.let { text = text.removeRange(it.range); plus = n }
            }
        }
        var time: LocalTime? = null
        clock.find(text)?.let { m ->
            val g = m.groupValues
            val hour = (g[1].ifEmpty { g[4].ifEmpty { g[7] } }).toInt()
            val minute = (g[2].ifEmpty { g[5] }).ifEmpty { "0" }.toInt()
            val meridiem = g[3].ifEmpty { g[6].ifEmpty { g[8] } }.lowercase()
            val h = when { meridiem == "pm" && hour < 12 -> hour + 12; meridiem == "am" && hour == 12 -> 0; else -> hour }
            if (h in 0..23 && minute in 0..59) { time = LocalTime.of(h, minute); text = text.removeRange(m.range) }
        }
        val cleaned = Todos.clean(text.replace(Regex("\\s+(?=[,.;])"), "")) ?: return Result(Todos.clean(phrase).orEmpty(), 0L)
        if (plus == null && time == null) return Result(cleaned, 0L)
        val date = today.plusDays((plus ?: 0).toLong())
        var at = date.atTime(time ?: LocalTime.of(MORNING, 0)).atZone(zone).toInstant().toEpochMilli()
        // An hour alone that has gone by today is meant for tomorrow.
        if (plus == null && at <= now) at = date.plusDays(1).atTime(time ?: LocalTime.of(MORNING, 0)).atZone(zone).toInstant().toEpochMilli()
        return Result(cleaned, at)
    }
}
