package com.metrowin.launcher.todo

import android.content.Context

/** A thing to do added from outside the page of the things to do (shared text, an event of the calendar), with the reminder its date asks for. */
object TodoQuick {
    /** False when [text] is empty or the list is full. [note] is kept with the item (cut to what an item holds). */
    fun add(context: Context, text: String, due: Long = 0L, note: String = ""): Boolean {
        val store = TodoStore(context)
        val cur = store.load()
        val added = Todos.add(cur, text, due, now = System.currentTimeMillis(), id = store.reserve(cur))
        if (added.size == cur.size) return false
        val item = TodoReminders.sync(context, added.last().copy(note = note.trim().take(Todos.MAX_NOTE)), null)
        store.save(cur + item)
        return true
    }
}
