package com.metrowin.launcher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LiveEvent
import com.metrowin.launcher.data.LiveTileData
import com.metrowin.launcher.data.LockInfoSource
import com.metrowin.launcher.data.PowerPolicy
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.data.ThirdPartyTiles
import com.metrowin.launcher.data.keeping
import com.metrowin.launcher.data.updatedWith
import com.metrowin.launcher.model.LiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The smaller costs: what is read again how often, and what is not read at all when nothing changed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryOptimizationsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // --- every kind of live tile has its own age

    @Test
    fun theBirthdaysAreReadFarLessOftenThanTheNextEvent() {
        val event = PowerPolicy.liveMaxAgeMs(LiveKind.Calendar, saving = false)
        assertEquals(2 * 60_000L, event)
        assertTrue(PowerPolicy.liveMaxAgeMs(LiveKind.Photos, false) > event)
        assertTrue(PowerPolicy.liveMaxAgeMs(LiveKind.People, false) > PowerPolicy.liveMaxAgeMs(LiveKind.Photos, false))
        assertEquals("a day is the longest anything waits", 24 * 60 * 60_000L, PowerPolicy.liveMaxAgeMs(LiveKind.Birthdays, saving = true))
    }

    // --- reading some kinds keeps the others

    private val event = LiveEvent("dentist", 1_000L, allDay = false)

    @Test
    fun readingTheCalendarLeavesTheMissedCallsAsTheyWere() {
        val before = LiveTileData(missedCalls = 3, nextAlarm = 9L)
        val after = before.updatedWith(LiveTileData(nextEvent = event, missedCalls = 0), setOf(LiveKind.Calendar))
        assertEquals(event, after.nextEvent)
        assertEquals("not asked for this time", 3, after.missedCalls)
        assertEquals(9L, after.nextAlarm)
    }

    @Test
    fun aTileThatLeftStartLosesItsData() {
        val data = LiveTileData(nextEvent = event, missedCalls = 2, nextAlarm = 5L)
        val kept = data.keeping(setOf(LiveKind.Phone))
        assertNull(kept.nextEvent)
        assertEquals(2, kept.missedCalls)
        assertEquals(0L, kept.nextAlarm)
        assertEquals("nothing wanted, nothing kept", LiveTileData(), data.keeping(emptySet()))
    }

    // --- the lock screen asks the calendar now and then, not every minute

    @Test
    fun theCalendarIsAskedAgainOnlyAfterAWhileOrWhenTheEventHasStarted() {
        var asked = 0
        val upcoming = LiveEvent("meeting", 60 * 60_000L, allDay = false)
        val source = LockInfoSource(context) { _ -> asked++; upcoming }
        val minute = 60_000L

        assertEquals(upcoming, source.read(nowMillis = 0, withEvent = true).nextEvent)
        repeat(4) { source.read(nowMillis = (it + 1) * minute, withEvent = true) }
        assertEquals("four more minutes, one question", 1, asked)

        source.read(nowMillis = LockInfoSource.EVENT_KEPT_MS + 1, withEvent = true)
        assertEquals("five minutes later it asks again", 2, asked)

        source.read(nowMillis = upcoming.startMillis + 1, withEvent = true)
        assertEquals("the event started: the next one takes its place at once", 3, asked)
    }

    @Test
    fun noEventIsAnAnswerToo() {
        var asked = 0
        val source = LockInfoSource(context) { _ -> asked++; null }
        repeat(5) { assertNull(source.read(nowMillis = it * 60_000L, withEvent = true).nextEvent) }
        assertEquals(1, asked)
    }

    @Test
    fun theCalendarIsNotAskedWithoutAskingForIt() {
        var asked = 0
        val source = LockInfoSource(context) { _ -> asked++; null }
        source.read(nowMillis = 0, withEvent = false)
        assertEquals(0, asked)
    }

    // --- an app that says the same thing again changes nothing

    @Test
    fun publishingTheSameWordsAgainIsNotAChange() {
        val first = ThirdPartyTiles.publish(emptyMap(), "app.a", "Inbox", "3 new", 3, now = 1)
        val again = ThirdPartyTiles.publish(first, "app.a", "Inbox", "3 new", 3, now = 99)
        assertSame("the very same map: nothing to save and nothing to draw", first, again)
        assertEquals(1L, again.getValue("app.a").updatedAt)

        val changed = ThirdPartyTiles.publish(first, "app.a", "Inbox", "4 new", 4, now = 100)
        assertEquals(100L, changed.getValue("app.a").updatedAt)
    }

    @Test
    fun theSwitchOfTheAppTilesIsReadWithoutDecodingAllTheSettings() {
        val store = SettingsStore(context)
        assertTrue("on until it is turned off", store.thirdPartyTilesOn())
        store.save(LauncherSettings(thirdPartyTiles = false))
        assertFalse(store.thirdPartyTilesOn())
        store.save(LauncherSettings(thirdPartyTiles = true))
        assertTrue(store.thirdPartyTilesOn())
    }
}
