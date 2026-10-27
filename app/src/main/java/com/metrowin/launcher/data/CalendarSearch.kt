package com.metrowin.launcher.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import androidx.compose.runtime.Immutable

/** An event of the calendar that the search found. */
@Immutable
data class EventMatch(val eventId: Long, val title: String, val startMillis: Long, val allDay: Boolean)

/** Looks for events in the next months by their title. It needs the calendar permission, and finds nothing without it. */
class CalendarSearch(private val context: Context) {
    /** Blocking, call it off the main thread. The events from now on that match [query], the nearest first. */
    fun search(query: String, now: Long = System.currentTimeMillis(), limit: Int = LIMIT): List<EventMatch> {
        val text = query.trim()
        if (text.length < MIN_QUERY || !LivePermissions.isGranted(context, LivePermissions.calendar)) return emptyList()
        return runCatching {
            val uri = CalendarContract.Instances.CONTENT_SEARCH_URI.buildUpon().also {
                ContentUris.appendId(it, now - DAY_MILLIS)
                ContentUris.appendId(it, now + WINDOW_MILLIS)
            }.appendPath(text).build()
            val found = ArrayList<EventMatch>(limit)
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                ),
                null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (found.size < limit && cursor.moveToNext()) {
                    // What is already over does not count.
                    if (cursor.getLong(3) <= now) continue
                    val title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    found += EventMatch(cursor.getLong(0), title, cursor.getLong(2), cursor.getInt(4) == 1)
                }
            }
            found
        }.getOrDefault(emptyList())
    }

    /** Blocking, call it off the main thread. The next event that has not ended, within [windowMillis]; null without the permission. */
    fun next(now: Long = System.currentTimeMillis(), windowMillis: Long = WEEK_MILLIS): LiveEvent? = upcoming(now, windowMillis, 1).firstOrNull()

    /** Blocking. Up to [limit] events that have not ended, the nearest first, within [windowMillis]; none without the permission. */
    fun upcoming(now: Long = System.currentTimeMillis(), windowMillis: Long = WEEK_MILLIS, limit: Int = UPCOMING): List<LiveEvent> {
        if (!LivePermissions.isGranted(context, LivePermissions.calendar)) return emptyList()
        return runCatching {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, now)
                ContentUris.appendId(it, now + windowMillis)
            }.build()
            val found = ArrayList<LiveEvent>(limit)
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
                null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                while (found.size < limit && cursor.moveToNext()) {
                    if (cursor.getLong(2) <= now) continue
                    found += LiveEvent(cursor.getString(0).orEmpty(), cursor.getLong(1), cursor.getInt(3) == 1)
                }
            }
            found
        }.getOrDefault(emptyList())
    }

    companion object {
        const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val LIMIT = 5
        const val UPCOMING = 4
        private const val MIN_QUERY = 2
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val WINDOW_MILLIS = 183 * DAY_MILLIS

        /** The page of an event in the calendar app. */
        fun uri(eventId: Long): String = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId).toString()
    }
}
