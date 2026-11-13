package com.metrowin.launcher.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset

/** The birthdays of the contacts, as all-day entries that can be read but not changed. Needs the permission to read the contacts. */
object Birthdays {
    /** "1984-03-09" or "--03-09" (no year) as the month and day, or null when it is neither. A 29 February is kept. */
    fun parse(value: String): MonthDay? = runCatching {
        val v = value.trim()
        when {
            v.startsWith("--") -> MonthDay.of(v.substring(2, 4).toInt(), v.substring(5, 7).toInt())
            v.length >= 10 && v[4] == '-' -> MonthDay.of(v.substring(5, 7).toInt(), v.substring(8, 10).toInt())
            else -> null
        }
    }.getOrNull()

    /** The entries for the days of [range]: one for each birthday that falls in it, a 29 February on the 28th in the years that have no 29th. */
    fun entries(contacts: List<Pair<Long, Pair<String, MonthDay>>>, range: ClosedRange<LocalDate>, label: String): List<CalEvent> {
        val out = ArrayList<CalEvent>()
        for ((id, named) in contacts) {
            val (name, md) = named
            for (year in range.start.year..range.endInclusive.year) {
                val day = md.atYear(year).let { if (md.monthValue == 2 && md.dayOfMonth == 29 && !java.time.Year.isLeap(year.toLong())) LocalDate.of(year, 2, 28) else it }
                if (day < range.start || day > range.endInclusive) continue
                val start = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                out += CalEvent(-(id * 10_000 + year % 10_000), 0, name, start, start + 86_400_000L, true, calendarName = label, readOnly = true, birthday = true)
            }
        }
        return out
    }

    /** Blocking. Empty without the permission. */
    fun read(context: Context, range: ClosedRange<LocalDate>, label: String): List<CalEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val found = ArrayList<Pair<Long, Pair<String, MonthDay>>>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()), null,
            )?.use { c ->
                while (c.moveToNext() && found.size < 5_000) {
                    val md = parse(c.getString(2).orEmpty()) ?: continue
                    found += c.getLong(0) to (c.getString(1).orEmpty() to md)
                }
            }
        }
        return entries(found, range, label)
    }
}
