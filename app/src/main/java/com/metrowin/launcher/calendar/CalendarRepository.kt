package com.metrowin.launcher.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.TimeZone

/** A calendar of the phone an event can be written in. */
data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int)

/** Reads and writes the events of the calendars of the phone (Calendar provider of Android). Blocking: call it off the main thread. */
class CalendarRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** The calendars that accept new events. */
    fun writableCalendars(): List<CalendarInfo> = runCatching {
        val out = ArrayList<CalendarInfo>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null,
        )?.use { c -> while (c.moveToNext()) out += CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3)) }
        out
    }.getOrDefault(emptyList())

    /** The events that are on at some moment of [from] to [to] (a repeating event once for each time it comes). */
    fun events(from: Long, to: Long): List<CalEvent> = runCatching {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
        val out = ArrayList<CalEvent>()
        resolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.RRULE, CalendarContract.Instances.DESCRIPTION,
            ),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < MAX_EVENTS) {
                out += CalEvent(
                    id = c.getLong(0), calendarId = c.getLong(1), title = c.getString(2).orEmpty(), start = c.getLong(3), end = c.getLong(4),
                    allDay = c.getInt(5) == 1, location = c.getString(6).orEmpty(), calendarName = c.getString(7).orEmpty(), color = c.getInt(8),
                    recurring = !c.getString(9).isNullOrEmpty(), description = c.getString(10).orEmpty(),
                )
            }
        }
        out
    }.getOrDefault(emptyList())

    /** The minutes before an event at which its reminders ring, the smallest first (none when it has none). */
    fun remindersOf(eventId: Long): List<Int> = runCatching {
        val out = ArrayList<Int>()
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders.MINUTES), "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()), null,
        )?.use { while (it.moveToNext()) out += it.getInt(0) }
        out.filter { it >= 0 }.distinct().sorted()
    }.getOrDefault(emptyList())

    private fun values(event: CalEvent) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, event.calendarId)
        put(CalendarContract.Events.TITLE, event.title)
        put(CalendarContract.Events.EVENT_LOCATION, event.location)
        put(CalendarContract.Events.DESCRIPTION, event.description)
        put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
        put(CalendarContract.Events.DTSTART, event.start)
        put(CalendarContract.Events.DTEND, event.end)
        put(CalendarContract.Events.EVENT_TIMEZONE, if (event.allDay) "UTC" else TimeZone.getDefault().id)
    }

    /** Writes a new event; its id, or 0 when it could not be written. */
    fun insert(event: CalEvent): Long = runCatching {
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values(event)) ?: return 0L
        val id = ContentUris.parseId(uri)
        setReminders(id, event.reminders)
        id
    }.getOrDefault(0L)

    /**
     * Writes [event] over the one with its id. For one time of a repeating event ([original] is the event as it was shown, [all] false) only that
     * time is changed: Android keeps it as an exception of the series. With [all] the whole series is changed, and moves by as much as that time did.
     */
    fun update(event: CalEvent, original: CalEvent = event, all: Boolean = true): Boolean = runCatching {
        val body = values(event).apply { remove(CalendarContract.Events.CALENDAR_ID) }
        val seriesUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
        if (original.recurring && !all) {
            body.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, original.start)
            val exception = resolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, event.id), body) ?: return@runCatching false
            setReminders(ContentUris.parseId(exception), event.reminders)
            return@runCatching true
        }
        if (original.recurring) {
            // A repeating event has a length, not an end; its start is the first time of the series, not the one that was opened.
            val first = resolver.query(seriesUri, arrayOf(CalendarContract.Events.DTSTART), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
            body.put(CalendarContract.Events.DTSTART, CalendarLogic.movedSeriesStart(first ?: original.start, original.start, event.start))
            body.putNull(CalendarContract.Events.DTEND)
            body.put(CalendarContract.Events.DURATION, CalendarLogic.duration(event.allDay, event.start, event.end))
        }
        val changed = resolver.update(seriesUri, body, null, null) > 0
        if (changed) setReminders(event.id, event.reminders)
        changed
    }.getOrDefault(false)

    /** Takes [event] out: the one time that was opened when [all] is false and it repeats, else the event with all its times. */
    fun delete(event: CalEvent, all: Boolean = true): Boolean = runCatching {
        if (event.recurring && !all) {
            val cancelled = ContentValues().apply {
                put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, event.start)
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
            }
            resolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, event.id), cancelled) != null
        } else {
            resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id), null, null) > 0
        }
    }.getOrDefault(false)

    private fun setReminders(eventId: Long, minutes: List<Int>) {
        resolver.delete(CalendarContract.Reminders.CONTENT_URI, "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        minutes.distinct().filter { it >= 0 }.forEach { m ->
            resolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, m)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                },
            )
        }
    }

    private companion object {
        const val MAX_EVENTS = 3_000
    }
}
