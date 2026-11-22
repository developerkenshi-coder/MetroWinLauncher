package com.metrowin.launcher.data

import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId

/** The photos of this day in the years before: same month and day, an earlier year. */
object PhotoMemories {
    /** How many years back the memories are looked for. */
    const val MAX_YEARS = 30

    /**
     * The spans of time (epoch milliseconds, both ends in) that hold this day in each of the years before, the latest year first. With [days] the span
     * takes that many days either side. 29 February is a memory of 28 February in a year that has none, and the 29th of a leap year is one of the
     * 28th as well, so that no photo of the leap day is lost.
     */
    fun ranges(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault(), days: Int = 0, years: Int = MAX_YEARS): List<LongRange> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return (1..years).map { back ->
            val year = today.year - back
            val day = today.withYear(year)
            // The 28th of February of a leap year also takes the 29th: the leap day has no day of its own in the years that follow.
            val extra = if (today.monthValue == 2 && today.dayOfMonth == 28 && !Year.isLeap(today.year.toLong()) && Year.isLeap(year.toLong())) 1L else 0L
            val from = day.minusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = day.plusDays(days.toLong() + extra).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            from..to
        }
    }

    fun isMemory(takenMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault(), days: Int = 0): Boolean {
        if (takenMillis <= 0L) return false
        val taken = Instant.ofEpochMilli(takenMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        if (taken.year >= today.year) return false
        return ranges(nowMillis, zone, days, today.year - taken.year).last().contains(takenMillis)
    }

    /** The day a photo taken at [takenMillis] is of, in [zone] (for the caption). */
    fun dateOf(takenMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(takenMillis).atZone(zone).toLocalDate()
}

/** The rules a query of the photos follows to leave out what an info tile does not want. Pure, so it is tested. */
object PhotoFilter {
    class Selection(val text: String, val args: Array<String>)

    /**
     * The conditions of a query of the media store: only the photos of [album] (its bucket id), only the favourites (Android 11 and later), without the
     * screenshots (Android 10 and later know the folder each one is in) and without the folders in [excluded] (bucket ids).
     */
    fun selection(album: String?, favorites: Boolean, noShots: Boolean, excluded: Set<String>, sdk: Int): Selection {
        val conditions = ArrayList<String>()
        val args = ArrayList<String>()
        if (album != null) {
            conditions += "bucket_id = ?"
            args += album
        }
        if (favorites && sdk >= 30) conditions += "is_favorite = 1"
        if (noShots) {
            conditions += "bucket_display_name NOT LIKE ?"
            args += "%creenshot%"
            if (sdk >= 29) {
                conditions += "relative_path NOT LIKE ?"
                args += "%creenshot%"
            }
        }
        val others = excluded.filter { it.isNotEmpty() && it != album }
        if (others.isNotEmpty()) {
            conditions += "bucket_id NOT IN (${others.joinToString(",") { "?" }})"
            args += others
        }
        return Selection(conditions.joinToString(" AND "), args.toTypedArray())
    }
}
