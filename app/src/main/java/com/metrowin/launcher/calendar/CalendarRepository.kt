package com.metrowin.launcher.calendar

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.TimeZone

/** A calendar of the phone. [writable] is false for the ones that only show events (subscribed, shared for reading). */
data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int, val writable: Boolean = true)

/** A person invited to an event. [status] is [CalendarContract.Attendees]'s: 1 accepted, 2 declined, 4 maybe. */
data class Attendee(val name: String, val email: String, val status: Int)

/** Why a write did not go through, for the message that tells the user. */
enum class Failure { None, Permission, ReadOnly, Other }

/** Reads and writes the events of the calendars of the phone (Calendar provider of Android). Blocking: call it off the main thread. */
class CalendarRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** Why the last write failed ([Failure.None] after one that worked). */
    @Volatile var failure: Failure = Failure.None
        private set

    private fun <T> guarded(fallback: T, block: () -> T): T {
        failure = Failure.None
        return try {
            block()
        } catch (e: SecurityException) {
            failure = Failure.Permission; fallback
        } catch (e: Exception) {
            failure = Failure.Other; fallback
        }
    }

    /** All the calendars of the phone, with whether each accepts new events. */
    fun calendars(): List<CalendarInfo> = runCatching {
        val out = ArrayList<CalendarInfo>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) out += CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3), c.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) }
        out
    }.getOrDefault(emptyList())

    /** The calendars that accept new events. */
    fun writableCalendars(): List<CalendarInfo> = calendars().filter { it.writable }

    private val instanceColumns = arrayOf(
        CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.RRULE, CalendarContract.Instances.DESCRIPTION,
        CalendarContract.Instances.CALENDAR_ACCESS_LEVEL, CalendarContract.Instances.STATUS, CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        CalendarContract.Instances.EVENT_TIMEZONE, CalendarContract.Instances.EVENT_COLOR,
    )

    private fun read(uri: android.net.Uri): List<CalEvent> {
        val out = ArrayList<CalEvent>()
        resolver.query(uri, instanceColumns, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && out.size < CalendarDefaults.MAX_EVENTS) {
                out += CalEvent(
                    id = c.getLong(0), calendarId = c.getLong(1), title = c.getString(2).orEmpty(), start = c.getLong(3), end = c.getLong(4),
                    allDay = c.getInt(5) == 1, location = c.getString(6).orEmpty(), calendarName = c.getString(7).orEmpty(), color = c.getInt(8),
                    recurring = !c.getString(9).isNullOrEmpty(), description = c.getString(10).orEmpty(),
                    readOnly = c.getInt(11) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR, status = c.getInt(12), selfStatus = c.getInt(13),
                    timeZone = c.getString(14).orEmpty(), ownColor = c.getInt(15), rule = c.getString(9).orEmpty(),
                )
            }
        }
        return out
    }

    /** The events that are on at some moment of [from] to [to] (a repeating event once for each time it comes). */
    fun events(from: Long, to: Long): List<CalEvent> = runCatching {
        read(CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build())
    }.getOrDefault(emptyList())

    /** The events of [from] to [to] that have [query] in the title, the place or the notes (the provider looks for it). */
    fun search(query: String, from: Long, to: Long): List<CalEvent> = runCatching {
        read(CalendarContract.Instances.CONTENT_SEARCH_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to); it.appendPath(query) }.build())
    }.getOrDefault(emptyList())

    /** The minutes before an event at which its reminders ring, the smallest first (none when it has none). Null when they could not be read. */
    fun remindersOf(eventId: Long): List<Int>? = runCatching {
        val out = ArrayList<Int>()
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders.MINUTES), "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()), null,
        )?.use { while (it.moveToNext()) out += it.getInt(0) } ?: return null
        out.filter { it >= 0 }.distinct().sorted()
    }.getOrNull()

    /** The people invited to an event. */
    fun attendeesOf(eventId: Long): List<Attendee> = runCatching {
        val out = ArrayList<Attendee>()
        resolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(CalendarContract.Attendees.ATTENDEE_NAME, CalendarContract.Attendees.ATTENDEE_EMAIL, CalendarContract.Attendees.ATTENDEE_STATUS),
            "${CalendarContract.Attendees.EVENT_ID} = ?", arrayOf(eventId.toString()), null,
        )?.use { c ->
            while (c.moveToNext() && out.size < 100) out += Attendee(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getInt(2))
        }
        out
    }.getOrDefault(emptyList())

    /** The answer of the user to an invitation, written on the row of the user among the guests ([email] is the account of the calendar); the calendar app of the account sends it. False when the user is not a guest. */
    fun answer(eventId: Long, email: String, status: Int): Boolean = guarded(false) {
        resolver.update(
            CalendarContract.Attendees.CONTENT_URI, ContentValues().apply { put(CalendarContract.Attendees.ATTENDEE_STATUS, status) },
            "${CalendarContract.Attendees.EVENT_ID} = ? AND ${CalendarContract.Attendees.ATTENDEE_EMAIL} = ?", arrayOf(eventId.toString(), email),
        ) > 0
    }

    internal fun values(event: CalEvent, rule: String? = null) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, event.calendarId)
        put(CalendarContract.Events.TITLE, event.title)
        put(CalendarContract.Events.EVENT_LOCATION, event.location)
        put(CalendarContract.Events.DESCRIPTION, event.description)
        put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
        put(CalendarContract.Events.DTSTART, event.start)
        put(CalendarContract.Events.EVENT_TIMEZONE, if (event.allDay) "UTC" else event.timeZone.ifBlank { TimeZone.getDefault().id })
        if (event.ownColor != 0) put(CalendarContract.Events.EVENT_COLOR, event.ownColor) else putNull(CalendarContract.Events.EVENT_COLOR)
        if (rule.isNullOrBlank()) put(CalendarContract.Events.DTEND, event.end)
        else {
            // A repeating event has a length and no end.
            put(CalendarContract.Events.RRULE, rule)
            put(CalendarContract.Events.DURATION, CalendarLogic.duration(event.allDay, event.start, event.end))
            putNull(CalendarContract.Events.DTEND)
        }
    }

    /** The operations that set the reminders of the event the operation [index] of the batch wrote, or of [eventId]: all go in one batch, so they are done together or not at all. */
    private fun reminderOps(minutes: List<Int>, eventId: Long?, index: Int): List<ContentProviderOperation> =
        minutes.distinct().filter { it in 0..CalendarDefaults.MAX_REMINDER_MIN }.map { m ->
            ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI).apply {
                if (eventId != null) withValue(CalendarContract.Reminders.EVENT_ID, eventId) else withValueBackReference(CalendarContract.Reminders.EVENT_ID, index)
                withValue(CalendarContract.Reminders.MINUTES, m)
                withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }.build()
        }

    private fun run(ops: List<ContentProviderOperation>) = resolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(ops))

    /** Writes a new event (repeating with [rule]); its id, or 0 when it could not be written ([failure] says why). */
    fun insert(event: CalEvent, rule: String? = null): Long = guarded(0L) {
        if (writableCalendars().none { it.id == event.calendarId }) { failure = Failure.ReadOnly; return@guarded 0L }
        write(event, rule)
    }

    private fun write(event: CalEvent, rule: String?): Long {
        val ops = listOf(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(values(event, rule)).build()) + reminderOps(event.reminders, null, 0)
        return run(ops).firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: 0L
    }

    /** Writes the events read from a file into [calendarId]; how many were written (one that fails does not stop the others). */
    fun insertAll(events: List<IcsEvent>, calendarId: Long): Int = guarded(0) {
        if (writableCalendars().none { it.id == calendarId }) { failure = Failure.ReadOnly; return@guarded 0 }
        events.count { e -> runCatching { write(CalEvent(0, calendarId, e.title, e.start, e.end, e.allDay, e.location, e.description, reminders = e.reminders), e.rule.ifBlank { null }) }.getOrDefault(0L) != 0L }
    }

    /**
     * Writes [event] over the one with its id. For one time of a repeating event ([original] is the event as it was shown, [all] false) only that
     * time is changed: Android keeps it as an exception of the series. With [all] the whole series is changed, and moves by as much as that time did.
     */
    fun update(event: CalEvent, original: CalEvent = event, all: Boolean = true): Boolean = guarded(false) {
        if (original.readOnly) { failure = Failure.ReadOnly; return@guarded false }
        val body = values(event).apply { remove(CalendarContract.Events.CALENDAR_ID) }
        val seriesUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
        if (original.recurring && !all) {
            body.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, original.start)
            val ops = listOf(ContentProviderOperation.newInsert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, event.id)).withValues(body).build()) + reminderOps(event.reminders, null, 0)
            return@guarded run(ops).firstOrNull()?.uri != null
        }
        if (original.recurring) {
            // A repeating event has a length, not an end; its start is the first time of the series, not the one that was opened.
            val first = resolver.query(seriesUri, arrayOf(CalendarContract.Events.DTSTART), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
            body.put(CalendarContract.Events.DTSTART, CalendarLogic.movedSeriesStart(first ?: original.start, original.start, event.start))
            body.putNull(CalendarContract.Events.DTEND)
            body.put(CalendarContract.Events.DURATION, CalendarLogic.duration(event.allDay, event.start, event.end))
        }
        val ops = listOf(
            ContentProviderOperation.newUpdate(seriesUri).withValues(body).build(),
            ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI).withSelection("${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(event.id.toString())).build(),
        ) + reminderOps(event.reminders, event.id, 0)
        (run(ops).firstOrNull()?.count ?: 0) > 0
    }

    /** Takes [event] out: the one time that was opened when [all] is false and it repeats, else the event with all its times. */
    fun delete(event: CalEvent, all: Boolean = true): Boolean = guarded(false) {
        if (event.readOnly) { failure = Failure.ReadOnly; return@guarded false }
        if (event.recurring && !all) {
            val cancelled = ContentValues().apply {
                put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, event.start)
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
            }
            resolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, event.id), cancelled) != null
        } else {
            resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id), null, null) > 0
        }
    }
}
