package com.metrowin.launcher.notes

import org.json.JSONArray
import org.json.JSONObject

/** A point of a sketch, in the 1000 x 1400 units of the page of a sketch. */
data class Pt(val x: Float, val y: Float)

/** What a stroke of a sketch is: free pen, or a shape for a diagram. */
enum class Tool { Pen, Line, Arrow, Rect, Ellipse, Text }

/**
 * One thing drawn on a sketch. A pen has many [points]; a line, an arrow, a rectangle and an ellipse have two (the corners or the
 * ends); a text has one, where it starts, and its [text].
 */
data class Stroke(val tool: Tool, val color: Int, val width: Float, val points: List<Pt>, val text: String = "")

/** A note: text (with a little markup, see [NoteText]) and, if wanted, a sketch. */
data class Note(
    val id: String,
    val title: String = "",
    val text: String = "",
    val strokes: List<Stroke> = emptyList(),
    /** An index into the colours of the notes (0 is none). */
    val color: Int = 0,
    val pinned: Boolean = false,
    val folder: String = "",
    val created: Long = 0L,
    val updated: Long = 0L,
) {
    val isBlank: Boolean get() = title.isBlank() && text.isBlank() && strokes.isEmpty()
}

object SketchPage {
    const val WIDTH = 1000f
    const val HEIGHT = 1400f
}

/** The notes as JSON text: what is kept (encrypted) on the phone and what a backup holds. Reading never trusts the size of what it reads. */
object NoteCodec {
    const val MAX_NOTES = 5_000
    const val MAX_STROKES = 3_000
    const val MAX_POINTS = 6_000
    const val MAX_TEXT = 200_000

    fun encode(notes: List<Note>): String = JSONObject().put("version", 1).put("notes", JSONArray().also { array -> notes.forEach { array.put(encodeNote(it)) } }).toString()

    fun decode(json: String): List<Note> = runCatching {
        val array = JSONObject(json).getJSONArray("notes")
        (0 until minOf(array.length(), MAX_NOTES)).mapNotNull { runCatching { decodeNote(array.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun encodeNote(note: Note) = JSONObject()
        .put("id", note.id).put("title", note.title).put("text", note.text).put("color", note.color).put("pinned", note.pinned)
        .put("folder", note.folder).put("created", note.created).put("updated", note.updated)
        .put("strokes", JSONArray().also { array -> note.strokes.forEach { array.put(encodeStroke(it)) } })

    private fun encodeStroke(stroke: Stroke) = JSONObject()
        .put("tool", stroke.tool.name).put("color", stroke.color).put("width", stroke.width.toDouble()).put("text", stroke.text)
        // x y x y …, whole units: a page is 1000 wide, so a tenth of a percent is enough.
        .put("p", JSONArray().also { array -> stroke.points.forEach { array.put(Math.round(it.x)); array.put(Math.round(it.y)) } })

    private fun decodeNote(o: JSONObject): Note {
        val strokes = o.optJSONArray("strokes")
        return Note(
            id = o.getString("id").take(64),
            title = o.optString("title").take(MAX_TEXT),
            text = o.optString("text").take(MAX_TEXT),
            strokes = if (strokes == null) emptyList() else (0 until minOf(strokes.length(), MAX_STROKES)).mapNotNull { runCatching { decodeStroke(strokes.getJSONObject(it)) }.getOrNull() },
            color = o.optInt("color").coerceIn(0, 15),
            pinned = o.optBoolean("pinned"),
            folder = o.optString("folder").take(60),
            created = o.optLong("created"),
            updated = o.optLong("updated"),
        )
    }

    private fun decodeStroke(o: JSONObject): Stroke {
        val flat = o.getJSONArray("p")
        val count = minOf(flat.length() / 2, MAX_POINTS)
        return Stroke(
            tool = Tool.valueOf(o.getString("tool")),
            color = o.getInt("color"),
            width = o.optDouble("width", 6.0).toFloat().coerceIn(1f, 80f),
            points = List(count) { Pt(flat.getDouble(2 * it).toFloat(), flat.getDouble(2 * it + 1).toFloat()) },
            text = o.optString("text").take(500),
        )
    }
}
