package com.metrowin.launcher.data

/** Typing on does not shuffle what is on screen: what was there stays where it was, what is new comes after. */
object StableOrder {
    /** True when [query] is [previous] with more typed after it (nothing before, nothing taken away). */
    fun extends(previous: String, query: String): Boolean {
        val a = SearchText.fold(previous).trim()
        val b = SearchText.fold(query).trim()
        return a.isNotEmpty() && b.length > a.length && b.startsWith(a)
    }

    /** [next] with the results that were already shown for [previousQuery] first, in their order, then the new ones in theirs. Unchanged when [query] does not extend [previousQuery]. */
    fun <T> keep(previousQuery: String, previous: List<T>, query: String, next: List<T>): List<T> {
        if (previous.isEmpty() || !extends(previousQuery, query)) return next
        val inNext = next.toHashSet()
        val kept = previous.filter { it in inNext }
        val keptSet = kept.toHashSet()
        return kept + next.filter { it !in keptSet }
    }
}
