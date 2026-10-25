package com.metrowin.launcher.data

/**
 * A list of things to do, kept as text so that it fits in a shortcut: one line for each item, "[x] " in front of the ones
 * done and "[ ] " in front of the others. Pure, so the rules are tested without a screen.
 */
object Checklist {
    data class Item(val text: String, val done: Boolean)

    const val MAX_ITEMS = 40
    const val MAX_TEXT = 60

    fun parse(raw: String): List<Item> = raw.lines().mapNotNull { line ->
        val done = line.startsWith("[x] ")
        val text = when {
            done -> line.removePrefix("[x] ")
            line.startsWith("[ ] ") -> line.removePrefix("[ ] ")
            else -> line
        }.trim()
        text.takeIf { it.isNotEmpty() }?.let { Item(it.take(MAX_TEXT), done) }
    }.take(MAX_ITEMS)

    fun serialize(items: List<Item>): String = items.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text }

    /** The list with [text] added at the end (blank text and a full list change nothing). */
    fun add(items: List<Item>, text: String): List<Item> {
        val clean = text.trim().replace('\n', ' ').take(MAX_TEXT)
        return if (clean.isEmpty() || items.size >= MAX_ITEMS) items else items + Item(clean, false)
    }

    fun toggle(items: List<Item>, index: Int): List<Item> =
        items.mapIndexed { i, item -> if (i == index) item.copy(done = !item.done) else item }

    fun remove(items: List<Item>, index: Int): List<Item> = items.filterIndexed { i, _ -> i != index }

    fun clearDone(items: List<Item>): List<Item> = items.filterNot { it.done }

    /** How many are done and how many there are. */
    fun progress(items: List<Item>): Pair<Int, Int> = items.count { it.done } to items.size

    /** The first few items still to do, for the face of the tile. */
    fun pending(items: List<Item>, limit: Int): List<String> = items.filterNot { it.done }.take(limit).map { it.text }
}
