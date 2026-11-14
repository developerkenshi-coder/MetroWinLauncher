package com.metrowin.launcher.photos

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * One picture of the phone as the Photo hub needs it. [takenAt] is epoch milliseconds; [album] is the folder name MediaStore gives ("" when it
 * gives none); [volume] says which storage holds it (two cards may have a "Camera" each); [modified] (seconds) tells a thumbnail that the picture changed.
 */
data class Photo(
    val id: Long,
    val album: String,
    val takenAt: Long,
    val favorite: Boolean,
    val volume: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val bytes: Long = 0,
    val mime: String = "",
    val modified: Long = 0,
) {
    /** Width over height, 1 when the size is not known. */
    val ratio: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

/** A folder of pictures; [key] tells two folders of the same name on different storages apart. */
data class Album(val key: String, val name: String, val volume: String, val photos: List<Photo>)

/** A titled run of pictures (a day, a month, a year). */
data class Section(val key: String, val date: LocalDate, val photos: List<Photo>)

/** The pages of the hub as plain lists, so they can be tested without a phone. Every list is newest first. */
object PhotoLibrary {
    private val cameraNames = setOf("camera", "100andro", "dcim")

    /** The albums by storage and name (the nameless ones last), each with its pictures; with [cameraFirst] the camera folder leads. */
    fun albums(photos: List<Photo>, cameraFirst: Boolean = false): List<Album> =
        newestFirst(photos).groupBy { it.volume to it.album }.map { (k, list) -> Album("${k.first}/${k.second}", k.second, k.first, list) }
            .sortedWith(
                compareBy<Album>({ cameraFirst && it.name.lowercase() !in cameraNames }, { it.name.isEmpty() }, { it.name.lowercase() }, { it.volume }),
            )

    /** The pictures by the day they were taken in [zone], the latest day first. */
    fun byDay(photos: List<Photo>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, List<Photo>>> =
        newestFirst(photos).groupBy { dayOf(it, zone) }.toList().sortedByDescending { it.first }

    /** The same by [granularity]: every picture of a month (or year) in one section, dated on its first day. */
    fun sections(photos: List<Photo>, granularity: Granularity, zone: ZoneId = ZoneId.systemDefault()): List<Section> =
        newestFirst(photos).groupBy { granularity.start(dayOf(it, zone)) }.toList().sortedByDescending { it.first }
            .map { (date, list) -> Section("${granularity.name}:$date", date, list) }

    fun favorites(photos: List<Photo>): List<Photo> = newestFirst(photos).filter { it.favorite }

    /** The pictures taken on the day of [today] in earlier years, the nearest year first. */
    fun memories(photos: List<Photo>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Pair<Int, List<Photo>>> =
        newestFirst(photos).filter { val d = dayOf(it, zone); d.year < today.year && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth }
            .groupBy { today.year - dayOf(it, zone).year }.toList().sortedBy { it.first }

    /**
     * The pictures whose album, month name, year or ISO date (2026-11-08) contains every word of [query]; [monthName] gives the name of a month.
     * Searching by place needs the GPS of the pictures, which needs one more permission: not done.
     */
    fun search(photos: List<Photo>, query: String, monthName: (Int) -> String, zone: ZoneId = ZoneId.systemDefault()): List<Photo> {
        val words = query.lowercase().split(' ', ',').filter { it.isNotBlank() }
        if (words.isEmpty()) return photos
        return photos.filter { photo ->
            val day = dayOf(photo, zone)
            val text = "${photo.album} ${monthName(day.monthValue)} ${day.year} $day ${day.dayOfMonth}".lowercase()
            words.all { it in text }
        }
    }

    /** Groups of pictures that look like the same file: same size in bytes, same dimensions and same moment; metadata only, nothing is opened. */
    fun duplicates(photos: List<Photo>): List<List<Photo>> =
        photos.filter { it.bytes > 0 && it.width > 0 }.groupBy { Triple(it.bytes, it.width to it.height, it.takenAt) }.values.filter { it.size > 1 }
            .map { newestFirst(it) }.sortedByDescending { it.first().takenAt }

    fun dayOf(photo: Photo, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(photo.takenAt).atZone(zone).toLocalDate()

    private fun newestFirst(photos: List<Photo>) = photos.sortedByDescending { it.takenAt }
}

/** How much time one heading of the date page covers. */
enum class Granularity {
    Day, Month, Year;

    fun start(day: LocalDate): LocalDate = when (this) {
        Day -> day
        Month -> YearMonth.from(day).atDay(1)
        Year -> day.withDayOfYear(1)
    }

    fun next() = entries[(ordinal + 1) % entries.size]
}

/** One row of a grid: the pictures of it and the width each takes as a fraction of the row (they add up to 1 less the gaps). */
data class GridRow(val first: Int, val fractions: List<Float>, val height: Float)

/** How the pictures are laid in rows: squares in columns, or by their own proportions (every row as wide as the screen, as tall as it needs). */
object PhotoRows {
    /** Rows for pictures with the given [ratios], in a row of [width] px with [gap] px between them. [targetHeight] is the wanted height of a row; [square] gives squares of that side. */
    fun layout(ratios: List<Float>, width: Float, targetHeight: Float, gap: Float, square: Boolean): List<GridRow> {
        if (ratios.isEmpty() || width <= 0f || targetHeight <= 0f) return emptyList()
        val rows = ArrayList<GridRow>()
        if (square) {
            val columns = maxOf(1, ((width + gap) / (targetHeight + gap)).toInt())
            val side = (width - gap * (columns - 1)) / columns
            for (start in ratios.indices step columns) {
                val n = minOf(columns, ratios.size - start)
                rows += GridRow(start, List(n) { side / width }, side)
            }
            return rows
        }
        var start = 0
        while (start < ratios.size) {
            var sum = 0f
            var end = start
            // Add pictures until, at the wanted height, they would fill the row.
            while (end < ratios.size) {
                sum += ratios[end].coerceIn(0.4f, 2.5f)
                end++
                if (sum * targetHeight + gap * (end - start - 1) >= width) break
            }
            val n = end - start
            val full = sum * targetHeight + gap * (n - 1) >= width
            val height = if (full) (width - gap * (n - 1)) / sum else targetHeight
            rows += GridRow(start, (start until end).map { ratios[it].coerceIn(0.4f, 2.5f) * height / width }, height)
            start = end
        }
        return rows
    }
}
