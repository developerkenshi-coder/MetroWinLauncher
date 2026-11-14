package com.metrowin.launcher

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.metrowin.launcher.calendar.CalEvent
import com.metrowin.launcher.calendar.CalendarRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On a device: the repository writes, changes and deletes events in a throwaway LOCAL calendar that the test makes and removes.
 * It never touches the calendars of an account (the ones that sync): everything is read and written by the id of the local one.
 */
@RunWith(AndroidJUnit4::class)
class CalendarSmokeTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val account = "metrowin-test"
    private var calendarId = 0L

    private fun asSyncAdapter(uri: android.net.Uri) = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()

    @Before fun makeLocalCalendar() {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, account)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "MetroWin test")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "MetroWin test")
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF0050EF.toInt())
        }
        calendarId = ContentUris.parseId(context.contentResolver.insert(asSyncAdapter(CalendarContract.Calendars.CONTENT_URI), values)!!)
    }

    @After fun removeLocalCalendar() {
        if (calendarId != 0L) context.contentResolver.delete(asSyncAdapter(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)), null, null)
    }

    @Test fun writesChangesAndDeletesInTheLocalCalendar() {
        val repo = CalendarRepository(context)
        val start = System.currentTimeMillis() + 3_600_000L
        val id = repo.insert(CalEvent(0, calendarId, "t", start, start + 3_600_000L, false, reminders = listOf(10, 60)))
        assertTrue(id > 0)
        assertEquals(listOf(10, 60), repo.remindersOf(id))
        val seen = repo.events(start - 1000, start + 7_200_000L).single { it.id == id }
        assertEquals(calendarId, seen.calendarId)
        // Changing the reminders replaces them, in one batch.
        assertTrue(repo.update(seen.copy(title = "t2", reminders = listOf(30)), seen))
        assertEquals(listOf(30), repo.remindersOf(id))
        assertEquals("t2", repo.events(start - 1000, start + 7_200_000L).single { it.id == id }.title)
        assertTrue(repo.delete(seen))
        assertTrue(repo.events(start - 1000, start + 7_200_000L).none { it.id == id })
    }

    @Test fun aRepeatingEventCanLoseOneTime() {
        val repo = CalendarRepository(context)
        val start = System.currentTimeMillis() + 3_600_000L
        val id = repo.insert(CalEvent(0, calendarId, "daily", start, start + 3_600_000L, false), "FREQ=DAILY;COUNT=3")
        assertTrue(id > 0)
        val all = repo.events(start - 1000, start + 4 * 86_400_000L).filter { it.id == id }
        assertEquals(3, all.size)
        assertTrue(all.all { it.recurring })
        assertTrue(repo.delete(all[1], all = false))
        assertEquals(2, repo.events(start - 1000, start + 4 * 86_400_000L).count { it.id == id && it.status != CalendarContract.Events.STATUS_CANCELED })
    }

    @Test fun theScreenStaysUp() {
        val intent = Intent().setClassName(context, "com.metrowin.launcher.calendar.CalendarActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<android.app.Activity>(intent).use { scenario ->
            Thread.sleep(2000)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
