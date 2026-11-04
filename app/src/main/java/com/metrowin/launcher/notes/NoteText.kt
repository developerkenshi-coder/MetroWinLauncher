package com.metrowin.launcher.notes

/**
 * The little markup of a note, readable even as plain text: `# title`, `## subtitle`, `- item`, `☐ to do`, `☑ done` and `**bold**`.
 * Pure, so it is tested with words.
 */
object NoteText {
    enum class Kind { Title, Subtitle, Bullet, Unchecked, Checked, Plain }

    /** One line: its kind, what is written after the marker, and its place among the lines of the text. */
    data class Line(val kind: Kind, val body: String, val index: Int)

    fun parse(text: String): List<Line> = text.split('\n').mapIndexed { index, raw ->
        when {
            raw.startsWith("## ") -> Line(Kind.Subtitle, raw.drop(3), index)
            raw.startsWith("# ") -> Line(Kind.Title, raw.drop(2), index)
            raw.startsWith("- ") || raw.startsWith("• ") -> Line(Kind.Bullet, raw.drop(2), index)
            raw.startsWith("☐ ") -> Line(Kind.Unchecked, raw.drop(2), index)
            raw.startsWith("☑ ") -> Line(Kind.Checked, raw.drop(2), index)
            else -> Line(Kind.Plain, raw, index)
        }
    }

    /** The text with the box of line [index] ticked or cleared. A line that is not a box is left as it is. */
    fun toggle(text: String, index: Int): String {
        val lines = text.split('\n').toMutableList()
        val line = lines.getOrNull(index) ?: return text
        lines[index] = when {
            line.startsWith("☐ ") -> "☑ " + line.drop(2)
            line.startsWith("☑ ") -> "☐ " + line.drop(2)
            else -> return text
        }
        return lines.joinToString("\n")
    }

    /** The pieces of a line and whether each is bold: "a **b** c" is a, b (bold), c. An unmatched `**` stays as written. */
    fun spans(line: String): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>()
        var rest = line
        while (true) {
            val open = rest.indexOf("**")
            val close = if (open >= 0) rest.indexOf("**", open + 2) else -1
            if (open < 0 || close < 0) break
            if (open > 0) out += rest.substring(0, open) to false
            if (close > open + 2) out += rest.substring(open + 2, close) to true
            rest = rest.substring(close + 2)
        }
        if (rest.isNotEmpty() || out.isEmpty()) out += rest to false
        return out
    }

    /**
     * What [Note.text] becomes when the marker [marker] is put at the start of the line the cursor is on: the line gets the marker,
     * loses it if it had it already, or changes the one it had.
     */
    fun mark(text: String, cursor: Int, marker: String): Pair<String, Int> {
        val position = cursor.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', position - 1).let { if (position == 0) 0 else it + 1 }
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        val line = text.substring(start, end)
        val old = MARKERS.firstOrNull { line.startsWith(it) } ?: ""
        val replaced = if (old == marker) line.removePrefix(old) else marker + line.removePrefix(old)
        return text.substring(0, start) + replaced + text.substring(end) to (position + replaced.length - line.length).coerceAtLeast(start)
    }

    private val MARKERS = listOf("## ", "# ", "- ", "• ", "☐ ", "☑ ")

    /** Round [selection] of [text] with `**`, or take them off when they are already there. */
    fun bold(text: String, start: Int, end: Int): Pair<String, IntRange> {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        if (a >= 2 && b + 2 <= text.length && text.substring(a - 2, a) == "**" && text.substring(b, b + 2) == "**") {
            return text.removeRange(b, b + 2).removeRange(a - 2, a) to (a - 2 until b - 2)
        }
        return text.substring(0, a) + "**" + text.substring(a, b) + "**" + text.substring(b) to (a + 2 until b + 2)
    }
}
