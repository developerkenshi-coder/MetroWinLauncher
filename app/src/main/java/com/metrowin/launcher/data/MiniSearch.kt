package com.metrowin.launcher.data

import androidx.compose.runtime.Immutable
import com.metrowin.launcher.notes.Note
import com.metrowin.launcher.notes.NoteStore
import com.metrowin.launcher.todo.TodoItem
import com.metrowin.launcher.todo.Todos

/** A note the search found: [snippet] is the first line of its text that is not the title. */
@Immutable
data class NoteMatch(val id: String, val title: String, val snippet: String)

/** A thing to do the search found, with the list it is in. */
@Immutable
data class TodoMatch(val id: Long, val text: String, val list: String)

/** What a row of the search opens inside a mini app of the launcher: a link in the form `metrowin://note/<id>` or `metrowin://todo/<id>`. */
object MiniLink {
    private const val SCHEME = "metrowin://"
    const val NOTE = "note"
    const val TODO = "todo"

    /** The start of a link to the conversation with a number (see [sms]). */
    const val SMS = "${SCHEME}sms/"

    fun sms(address: String) = SMS + android.net.Uri.encode(address)

    fun note(id: String) = "$SCHEME$NOTE/$id"
    fun todo(id: Long) = "$SCHEME$TODO/$id"

    /** The kind (`note` or `todo`) and the id of [uri], or null when it is not a link to a mini app. */
    fun parse(uri: String): Pair<String, String>? {
        if (!uri.startsWith(SCHEME)) return null
        val kind = uri.removePrefix(SCHEME).substringBefore('/')
        val id = uri.removePrefix(SCHEME).substringAfter('/', "")
        return if ((kind == NOTE || kind == TODO) && id.isNotEmpty()) kind to id else null
    }
}

/** Looking inside the notes and the things to do of the launcher. Pure, so it is tested. */
object MiniSearch {
    /** Notes with every word of [query]: not the ones in the bin, and not the locked ones, which ask for the lock of the phone to be seen at all. */
    fun notes(all: List<Note>, query: String, limit: Int = LIMIT): List<NoteMatch> =
        NoteStore.search(all.filter { it.deleted == 0L && !it.locked }, query).take(limit).map(::note)

    /** Things still to do that have [query] in the text, the note, a step or the list. */
    fun todos(all: List<TodoItem>, query: String, limit: Int = LIMIT): List<TodoMatch> =
        Todos.search(all.filter { !it.done }, query).take(limit).map { TodoMatch(it.id, it.text, it.list) }

    /** The note changed last, if that was within [withinMillis] of [now]: not blank, not in the bin, not locked. */
    fun recentNote(all: List<Note>, now: Long, withinMillis: Long = RECENT_MILLIS): NoteMatch? =
        all.filter { it.deleted == 0L && !it.locked && !it.isBlank && now - it.updated in 0..withinMillis }
            .maxByOrNull { it.updated }?.let { note(it) }

    private fun note(note: Note): NoteMatch {
        val lines = note.text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        val title = note.title.ifBlank { lines.firstOrNull().orEmpty() }
        return NoteMatch(note.id, title, lines.firstOrNull { it != title }.orEmpty().take(SNIPPET))
    }

    private const val RECENT_MILLIS = 3 * 86_400_000L
    private const val LIMIT = 4
    private const val SNIPPET = 80
}
