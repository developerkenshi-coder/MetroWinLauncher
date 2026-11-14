package com.metrowin.launcher.notes

/**
 * The little markup of a note, readable even as plain text: `# title`, `## subtitle`, `- item`, `1. item`, `☐ to do`, `☑ done` and `**bold**`.
 * Pure, so it is tested with words.
 */
object NoteText {
    enum class Kind { Title, Subtitle, Bullet, Numbered, Unchecked, Checked, Plain }

    /** One line: its kind, what is written after the marker, and its place among the lines of the text. [number] is the one of a numbered item. */
    data class Line(val kind: Kind, val body: String, val index: Int, val number: Int = 0)

    private val numbered = Regex("^(\\d{1,4})\\. ")

    fun parse(text: String): List<Line> = text.split('\n').mapIndexed { index, raw ->
        when {
            raw.startsWith("## ") -> Line(Kind.Subtitle, raw.drop(3), index)
            raw.startsWith("# ") -> Line(Kind.Title, raw.drop(2), index)
            raw.startsWith("- ") || raw.startsWith("• ") -> Line(Kind.Bullet, raw.drop(2), index)
            numbered.find(raw) != null -> numbered.find(raw)!!.let { Line(Kind.Numbered, raw.substring(it.value.length), index, it.groupValues[1].toInt()) }
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

    /**
     * The text when Enter was just pressed ([new] is [old] with one new line at [cursor]) at the end of a list item: the next line starts with the same
     * marker (a box is a clear box, a number is the next one), and Enter on an item with nothing in it ends the list. Null when it is not that.
     */
    fun continueList(old: String, new: String, cursor: Int): Pair<String, Int>? {
        if (new.length != old.length + 1 || cursor < 1 || cursor > new.length || new[cursor - 1] != '\n' || new.removeRange(cursor - 1, cursor) != old) return null
        val start = new.lastIndexOf('\n', cursor - 2) + 1
        val line = new.substring(start, cursor - 1)
        val marker = when {
            line.startsWith("- ") -> "- "
            line.startsWith("• ") -> "• "
            line.startsWith("☐ ") || line.startsWith("☑ ") -> "☐ "
            else -> numbered.find(line)?.let { (it.groupValues[1].toInt() + 1).toString() + ". " } ?: return null
        }
        val own = if (line.startsWith("☑ ")) "☐ " else if (numbered.find(line) != null) numbered.find(line)!!.value else marker
        // An item with nothing after its marker: the marker goes and the list is over.
        if (line.length == own.length) return new.removeRange(start, cursor) to start
        return new.substring(0, cursor) + marker + new.substring(cursor) to cursor + marker.length
    }

    /** The places of the bold pieces of [text] as ranges of the text, `**` included: the ones [spans] would find, for a field that shows them as they are typed. */
    fun boldRanges(text: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        var from = 0
        while (true) {
            val open = text.indexOf("**", from)
            val close = if (open >= 0) text.indexOf("**", open + 2) else -1
            if (open < 0 || close < 0) break
            // A `**` can not span lines: the markup is read one line at a time.
            if (text.substring(open, close).contains('\n')) { from = open + 2; continue }
            if (close > open + 2) out += open..close + 1
            from = close + 2
        }
        return out
    }
}
