package com.metrowin.launcher.recorder

import com.metrowin.launcher.data.Recordings
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A recording as the list shows it: the file, and what was read from it and from its meta file. */
data class Rec(val file: File, val durationMs: Long, val size: Long, val meta: RecMeta) {
    val name: String get() = file.name
    val stamp: String get() = RecorderFormat.stampOf(file.name)
}

/** The words and numbers of the recorder that need no screen: names, sizes, groups, order, search and the short summary. */
object RecorderFormat {
    private val STAMP = Regex("""(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})""")

    /** "20261026-183005" from the name of a recording, or "" when it has none. */
    fun stampOf(name: String): String = STAMP.find(name)?.value.orEmpty()

    fun timeOf(name: String): LocalDateTime? = STAMP.find(name)?.destructured?.let { (y, mo, d, h, mi, s) ->
        runCatching { LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt()) }.getOrNull()
    }

    /** A date a person reads ("26 Oct 2026, 18:30"), then " · Lesson" when the recording has a name; the stamp as it is for a name without one. */
    fun pretty(name: String, locale: Locale = Locale.getDefault()): String {
        val time = timeOf(name) ?: return Recordings.title(name)
        val date = time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
        val given = Recordings.nameOf(name)
        return date + if (given.isEmpty()) "" else " · $given"
    }

    fun title(name: String, pretty: Boolean): String = if (pretty) pretty(name) else Recordings.title(name)

    /** "412 KB", "3.4 MB". */
    fun size(bytes: Long): String = when {
        bytes < 1024 * 1024 -> "%d KB".format(Locale.ROOT, maxOf(1L, bytes / 1024))
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(Locale.ROOT, bytes / 1048576.0)
        else -> "%.2f GB".format(Locale.ROOT, bytes / 1073741824.0)
    }

    /** The day a recording belongs to, as a stamp of the day ("20261026"), for the groups of the list; "" when the name has no date. */
    fun dayOf(name: String): String = stampOf(name).substringBefore('-')

    fun dayDate(day: String): LocalDate? = runCatching { LocalDate.of(day.substring(0, 4).toInt(), day.substring(4, 6).toInt(), day.substring(6, 8).toInt()) }.getOrNull()

    /** 0 = newest first, 1 = oldest first, 2 = longest first, 3 = by name. */
    fun arrange(recs: List<Rec>, query: String, sort: Int, label: String = ""): List<Rec> {
        val q = query.trim().lowercase()
        val kept = recs.filter { r ->
            (label.isEmpty() || r.meta.label == label) &&
                (q.isEmpty() || Recordings.title(r.name).lowercase().contains(q) || r.meta.transcript.lowercase().contains(q) || r.meta.label.lowercase().contains(q))
        }
        return when (sort) {
            1 -> kept.sortedBy { it.name }
            2 -> kept.sortedByDescending { it.durationMs }
            3 -> kept.sortedBy { Recordings.nameOf(it.name).lowercase().ifEmpty { "￿" + it.name } }
            else -> kept.sortedByDescending { it.name }
        }
    }

    /** A line of what the search found in the transcript around the word, or "" when it is not there. */
    fun snippet(transcript: String, query: String, radius: Int = 28): String {
        val q = query.trim()
        if (q.isEmpty()) return ""
        val at = transcript.indexOf(q, ignoreCase = true)
        if (at < 0) return ""
        val from = (at - radius).coerceAtLeast(0)
        val to = (at + q.length + radius).coerceAtMost(transcript.length)
        return (if (from > 0) "…" else "") + transcript.substring(from, to).replace('\n', ' ') + (if (to < transcript.length) "…" else "")
    }

    /**
     * The few sentences of [text] that carry most of it: each sentence is scored by how often its words (longer than three letters) come in
     * the whole text, and the best [count] are given back in the order they were said. No model, no network; it picks, it does not write.
     */
    fun summary(text: String, count: Int = 3): String {
        val sentences = text.split(Regex("(?<=[.!?…])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.size <= count) return sentences.joinToString(" ")
        fun words(s: String) = Regex("\\p{L}{4,}").findAll(s.lowercase()).map { it.value }.toList()
        val freq = HashMap<String, Int>()
        sentences.forEach { s -> words(s).forEach { freq[it] = (freq[it] ?: 0) + 1 } }
        val best = sentences.withIndex().sortedByDescending { (_, s) -> words(s).let { w -> if (w.isEmpty()) 0.0 else w.sumOf { freq.getValue(it) }.toDouble() / (w.size + 3) } }
            .take(count).map { it.index }.sorted()
        return best.joinToString(" ") { sentences[it] }
    }

    /** The point of a playing position among [marks]: the next marker after [position], or null. */
    fun nextMark(marks: List<Long>, position: Long): Long? = marks.sorted().firstOrNull { it > position + 500 }

    fun previousMark(marks: List<Long>, position: Long): Long? = marks.sorted().lastOrNull { it < position - 1500 }
}
