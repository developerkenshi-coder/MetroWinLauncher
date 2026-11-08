package com.metrowin.launcher.photos

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One picture of the phone as the Photo hub needs it. [takenAt] is epoch milliseconds; [album] is the folder name MediaStore gives ("" when it gives none). */
data class Photo(val id: Long, val album: String, val takenAt: Long, val favorite: Boolean)

/** The pages of the hub as plain lists, so they can be tested without a phone. Every list is newest first. */
object PhotoLibrary {
    /** The albums by name (the nameless ones last, under ""), each with its pictures. */
    fun albums(photos: List<Photo>): List<Pair<String, List<Photo>>> =
        newestFirst(photos).groupBy { it.album }.toList()
            .sortedWith(compareBy({ it.first.isEmpty() }, { it.first.lowercase() }))

    /** The pictures by the day they were taken in [zone], the latest day first. */
    fun byDay(photos: List<Photo>, zone: ZoneId = ZoneId.systemDefault()): List<Pair<LocalDate, List<Photo>>> =
        newestFirst(photos).groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }.toList()
            .sortedByDescending { it.first }

    fun favorites(photos: List<Photo>): List<Photo> = newestFirst(photos).filter { it.favorite }

    private fun newestFirst(photos: List<Photo>) = photos.sortedByDescending { it.takenAt }
}
