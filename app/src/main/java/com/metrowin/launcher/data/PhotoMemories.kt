package com.metrowin.launcher.data

import java.time.Instant
import java.time.ZoneId

/** The photos of this day in the years before: same month and day, an earlier year. */
object PhotoMemories {
    fun isMemory(takenMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (takenMillis <= 0L) return false
        val taken = Instant.ofEpochMilli(takenMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return taken.year < today.year && taken.monthValue == today.monthValue && taken.dayOfMonth == today.dayOfMonth
    }
}
