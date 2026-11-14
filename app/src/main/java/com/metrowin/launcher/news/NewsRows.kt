package com.metrowin.launcher.news

import com.metrowin.launcher.data.NewsItem
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One row of the list of headlines: a day line, a headline, or (on a wide screen) two headlines side by side. */
sealed interface NewsRow {
    class Day(val ago: Long, val at: Long) : NewsRow

    class One(val item: NewsItem, val index: Int) : NewsRow

    class Two(val a: NewsItem, val aIndex: Int, val b: NewsItem, val bIndex: Int) : NewsRow
}

/** How the headlines are laid out in rows. Pure, so it is tested with numbers. */
object NewsRows {
    /** The whole days from the day of [at] to the day of [now] in [zone] (0 today, 1 yesterday), or -1 when the feed gave no date. */
    fun daysAgo(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        if (at <= 0L) return -1L
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(day, Instant.ofEpochMilli(now).atZone(zone).toLocalDate()).coerceAtLeast(0L)
    }

    /**
     * The rows for [items]: the first headline alone (the big one), a line with the day wherever the day changes when [days] is on, and
     * with [wide] the rest in pairs of the same day.
     */
    fun build(items: List<NewsItem>, now: Long, days: Boolean, wide: Boolean, zone: ZoneId = ZoneId.systemDefault()): List<NewsRow> {
        val rows = ArrayList<NewsRow>()
        var pending: Pair<NewsItem, Int>? = null
        var lastDay = Long.MIN_VALUE
        fun flush() { pending?.let { rows += NewsRow.One(it.first, it.second) }; pending = null }
        items.forEachIndexed { i, item ->
            val day = daysAgo(item.publishedAt, now, zone)
            if (days && day >= 0 && day != lastDay) { flush(); rows += NewsRow.Day(day, item.publishedAt) }
            if (day >= 0) lastDay = day
            val waiting = pending
            when {
                !wide || i == 0 -> { flush(); rows += NewsRow.One(item, i) }
                waiting == null -> pending = item to i
                else -> { rows += NewsRow.Two(waiting.first, waiting.second, item, i); pending = null }
            }
        }
        flush()
        return rows
    }

    /** [items] that have [query] in the headline or in the name of who published it, ignoring case; all of them for a blank query. */
    fun search(items: List<NewsItem>, query: String): List<NewsItem> {
        val q = query.trim()
        return if (q.isEmpty()) items else items.filter { it.title.contains(q, ignoreCase = true) || it.source.contains(q, ignoreCase = true) }
    }

    /** The headlines of every source together: newest first, the same headline of two sources once, at most [limit]. A headline whose feed did not say who published it gets the name of the source. */
    fun merge(sources: List<Pair<String, List<NewsItem>>>, limit: Int = 60): List<NewsItem> =
        sources.flatMap { (name, items) -> items.map { if (it.source.isEmpty()) it.copy(source = name) else it } }
            .sortedByDescending { it.publishedAt }
            .distinctBy { it.link }
            .distinctBy { it.title.lowercase().filter(Char::isLetterOrDigit) }
            .take(limit)
}
