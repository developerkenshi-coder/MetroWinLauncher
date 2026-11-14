package com.metrowin.launcher.notes

/** What the editor of a note remembers for undo and redo: a place in a list of states of the text, each with where the cursor was. */
class NoteHistory(text: String, cursor: Int = 0, private val limit: Int = 80) {
    data class State(val text: String, val cursor: Int)

    private val states = arrayListOf(State(text, cursor))
    private var at = 0

    /** The text at the step the history is at. */
    val text get() = states[at].text

    val canUndo get() = at > 0
    val canRedo get() = at < states.lastIndex

    /** Keeps [state] as a new step, unless the text is the one already there. A step taken back and then a new text: the steps ahead are forgotten. */
    fun push(state: State) {
        if (state.text == states[at].text) return
        while (states.lastIndex > at) states.removeAt(states.lastIndex)
        states += state
        if (states.size > limit) states.removeAt(0)
        at = states.lastIndex
    }

    /** The step before the one the text is at ([current] is kept first, so what was typed since the last step is not lost), or null at the first. */
    fun undo(current: State): State? {
        push(current)
        if (at == 0) return null
        return states[--at]
    }

    fun redo(): State? = if (canRedo) states[++at] else null
}
