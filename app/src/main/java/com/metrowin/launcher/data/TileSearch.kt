package com.metrowin.launcher.data

import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.TileData

/**
 * Searching what is on Start but not in the app list: the notes, the lists, the countdowns and the names of the sites.
 * A note is found by the words written in it, a list by its items, a site by the words of its address; every kind also answers
 * to a word that says what it is ("note", "lista", "countdown"), in Italian and in English.
 *
 * Only the tiles that open something are searched. A counter counts one more when touched and a clock or the moon only show, so
 * a result that did that would be a surprise; the contacts and the Android settings have their own search.
 */
object TileSearch {
    fun searchable(items: Collection<AppItem>): List<AppItem> = items.filter { it.pinOnly && isSearchable(it) }

    private fun isSearchable(item: AppItem): Boolean = when {
        item.note != null || item.targetDay != 0L -> true
        item.data is TileData.Checklist -> true
        item.data != null -> false
        else -> (item.launch as? Launch.Link)?.uri?.let { it.startsWith("http://") || it.startsWith("https://") } == true
    }

    fun index(items: Collection<AppItem>): SearchIndex<AppItem> =
        SearchIndex(searchable(items).map { SearchEntry(it, it.label, keywords(it)) })

    internal fun keywords(item: AppItem): List<String> = buildList {
        val data = item.data
        when {
            item.note != null -> {
                addAll(NOTE_WORDS)
                add(item.note)
            }
            data is TileData.Checklist -> {
                addAll(LIST_WORDS)
                Checklist.parse(data.raw).forEach { add(it.text) }
            }
            item.targetDay != 0L -> addAll(COUNTDOWN_WORDS)
            else -> {
                val launch = item.launch
                if (launch is Launch.Link && (launch.uri.startsWith("http://") || launch.uri.startsWith("https://"))) {
                    addAll(SITE_WORDS)
                    add(launch.uri.substringAfter("://").substringBefore('/').removePrefix("www."))
                }
            }
        }
    }

    /**
     * The part of a tile that shows why it matched [query]: the line of a note, or the item of a list, that has one of the words;
     * null for a tile that has nothing written on it. Cut at [MAX_SNIPPET] characters.
     */
    fun snippet(item: AppItem, query: String): String? {
        val words = SearchText.words(query)
        val lines: List<String> = when {
            item.note != null -> item.note.lines()
            item.data is TileData.Checklist -> Checklist.parse(item.data.raw).map { it.text }
            else -> return null
        }.map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val hit = lines.firstOrNull { line -> val folded = SearchText.fold(line); words.any { it in folded } } ?: lines.first()
        return if (hit.length <= MAX_SNIPPET) hit else hit.take(MAX_SNIPPET - 1).trimEnd() + "…"
    }

    const val MAX_SNIPPET = 80

    private val NOTE_WORDS = listOf("note", "memo", "nota", "appunto", "appunti")
    private val LIST_WORDS = listOf("list", "checklist", "todo", "lista", "cose da fare")
    private val COUNTDOWN_WORDS = listOf("countdown", "days", "date", "conto alla rovescia", "giorni", "scadenza")
    private val SITE_WORDS = listOf("site", "website", "web", "link", "sito", "pagina")
}
