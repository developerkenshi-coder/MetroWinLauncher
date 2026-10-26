package com.metrowin.launcher

import com.metrowin.launcher.data.ContactDirectory
import com.metrowin.launcher.data.DialEntry
import com.metrowin.launcher.data.Places
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.data.ShortcutCodec
import com.metrowin.launcher.data.ShortcutDef
import com.metrowin.launcher.data.ShortcutKind
import com.metrowin.launcher.data.SmartDial
import com.metrowin.launcher.data.TimerFormat
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.model.LiveKind
import com.metrowin.launcher.model.StopwatchState
import com.metrowin.launcher.model.TileData
import com.metrowin.launcher.model.TimerState
import com.metrowin.launcher.model.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The state of the timer and the stopwatch, how they are written, the reminders, the smart dial and the places. */
class TilesStateTest {
    // --- timer

    @Test
    fun aTimerRunsPausesAndFinishesWithNothingRunningInBetween() {
        val fresh = TimerState(60_000)
        assertEquals(TimerStatus.Idle, fresh.status(0))
        assertEquals(60_000, fresh.remaining(123))
        val running = fresh.start(1_000)
        assertTrue(running.running)
        assertEquals(TimerStatus.Running, running.status(30_000))
        assertEquals(31_000, running.remaining(30_000))
        val paused = running.pause(21_000)
        assertFalse(paused.running)
        assertEquals(TimerStatus.Paused, paused.status(99_999_999))
        assertEquals(40_000, paused.remaining(99_999_999))
        // Going on from the pause ends when what was left runs out.
        val again = paused.start(100_000)
        assertEquals(140_000, again.endAt)
        assertEquals(TimerStatus.Finished, again.status(140_000))
        assertEquals(0, again.remaining(150_000))
        assertEquals(TimerStatus.Idle, again.reset().status(0))
    }

    @Test
    fun aFinishedTimerStartsOverFromTheWholeLength() {
        val done = TimerState(60_000).start(0)
        assertEquals(TimerStatus.Finished, done.status(61_000))
        val restarted = done.start(100_000)
        assertEquals(160_000, restarted.endAt)
        assertEquals(TimerStatus.Running, restarted.status(100_001))
    }

    @Test
    fun aClockSetBackNeverShowsMoreThanTheLength() {
        val running = TimerState(60_000).start(1_000_000)
        // The clock goes back an hour: the timer still has at most its whole length.
        assertEquals(60_000, running.remaining(1_000_000 - 3_600_000))
        assertEquals(60_000, running.start(5).remaining(5))
    }

    @Test
    fun aTimerIsWrittenAndReadBack() {
        val state = TimerState(300_000).start(10_000)
        assertEquals(state, TimerState.decode(state.encode()))
        assertEquals(TimerState.Default, TimerState.decode(null))
        assertEquals(TimerState.Default, TimerState.decode("garbage"))
        assertEquals(TimerState.Default, TimerState.decode("1:2"))
        // Lengths out of range are brought into it.
        assertEquals(TimerState.MAX_MS, TimerState.decode("999999999999:0:0").durationMs)
        assertEquals(TimerState.MIN_MS, TimerState.decode("5:0:5").durationMs)
        assertEquals(TimerState.Default.withDuration(90_000), TimerState(90_000))
        assertEquals(TimerState.MIN_MS, TimerState.Default.withDuration(1).durationMs)
    }

    // --- stopwatch

    @Test
    fun aStopwatchCountsOnTheClockThatKeepsGoingWhileThePhoneSleeps() {
        val stopped = StopwatchState()
        assertFalse(stopped.running)
        assertEquals(0, stopped.elapsed(500, 500))
        val running = stopped.start(nowElapsed = 1_000, nowWall = 1_700_000_000_000, boot = 7)
        assertEquals(5_000, running.elapsed(6_000, 1_700_000_005_000, boot = 7))
        val stoppedAt = running.stop(6_000, 1_700_000_005_000, 7)
        assertEquals(5_000, stoppedAt.accumulatedMs)
        assertEquals(5_000, stoppedAt.elapsed(99_999, 99_999_999, 7))
        // Started again, it goes on from there.
        val second = stoppedAt.start(10_000, 1_700_000_100_000, 7)
        assertEquals(8_000, second.elapsed(13_000, 1_700_000_103_000, 7))
    }

    @Test
    fun afterARebootTheWallClockTakesOver() {
        val running = StopwatchState().start(nowElapsed = 600_000, nowWall = 1_000_000, boot = 7)
        // The phone restarted: the elapsed clock is back near zero and the boot count is another.
        assertEquals(60_000, running.elapsed(nowElapsed = 5_000, nowWall = 1_060_000, boot = 8))
        // The same boot but a smaller reading would be a clock that cannot be: the wall clock again.
        assertEquals(30_000, running.elapsed(nowElapsed = 100, nowWall = 1_030_000, boot = 7))
        // And the wall clock set back never gives a negative time.
        assertEquals(0, running.elapsed(nowElapsed = 100, nowWall = 900_000, boot = 9))
    }

    @Test
    fun lapsAreTotalsAndTheirLengthsAreTheDifferences() {
        var watch = StopwatchState().start(0, 0, 1)
        watch = watch.lap(10_000, 10_000, 1).lap(25_000, 25_000, 1).lap(60_000, 60_000, 1)
        assertEquals(listOf(10_000L, 25_000L, 60_000L), watch.laps)
        assertEquals(listOf(10_000L, 15_000L, 35_000L), watch.lapLengths())
        // A stopped stopwatch takes no laps.
        assertEquals(watch.stop(60_000, 60_000, 1).laps, watch.stop(60_000, 60_000, 1).lap(70_000, 70_000, 1).laps)
        assertEquals(StopwatchState(), watch.reset())
    }

    @Test
    fun onlyTheLastLapsAreKept() {
        var watch = StopwatchState().start(0, 0, 1)
        repeat(40) { watch = watch.lap((it + 1) * 1_000L, (it + 1) * 1_000L, 1) }
        assertEquals(StopwatchState.MAX_LAPS, watch.laps.size)
        assertEquals(40_000L, watch.laps.last())
    }

    @Test
    fun aStopwatchIsWrittenAndReadBack() {
        val state = StopwatchState(5_000, 123, 456, 7, listOf(1_000, 3_000))
        assertEquals(state, StopwatchState.decode(state.encode()))
        val stopped = StopwatchState(5_000, laps = listOf(2_000))
        assertEquals(stopped, StopwatchState.decode(stopped.encode()))
        assertEquals(StopwatchState(), StopwatchState.decode("nonsense"))
        assertEquals(StopwatchState(), StopwatchState.decode(null))
    }

    // --- how they read

    @Test
    fun timeIsWrittenAsADisplayDoes() {
        assertEquals("00:00", TimerFormat.countdown(0))
        assertEquals("00:01", TimerFormat.countdown(1))
        assertEquals("00:01", TimerFormat.countdown(1_000))
        assertEquals("01:00", TimerFormat.countdown(59_001))
        assertEquals("04:59", TimerFormat.countdown(298_500))
        assertEquals("1:00:00", TimerFormat.countdown(3_600_000))
        assertEquals("00:00", TimerFormat.countdown(-5))
        assertEquals("01:23", TimerFormat.stopwatch(83_456))
        assertEquals("01:23.4", TimerFormat.stopwatch(83_456, tenths = true))
        assertEquals("1:01:23", TimerFormat.stopwatch(3_683_000))
        assertEquals("5 min", TimerFormat.length(300_000))
        assertEquals("1 h 30 min", TimerFormat.length(5_400_000))
        assertEquals("45 s", TimerFormat.length(45_000))
    }

    @Test
    fun theDisplayChangesOnlyWhenItHasSomethingNewToSay() {
        assertEquals(1_000, TimerFormat.untilNextCountdownTick(5_000))
        assertEquals(250, TimerFormat.untilNextCountdownTick(5_250))
        assertEquals(20, TimerFormat.untilNextCountdownTick(5_001))
        assertEquals(1_000, TimerFormat.untilNextStopwatchTick(0, 1_000))
        assertEquals(400, TimerFormat.untilNextStopwatchTick(1_600, 1_000))
        assertEquals(60, TimerFormat.untilNextStopwatchTick(1_640, 100).coerceAtLeast(60))
    }

    @Test
    fun aLengthTypedInThreeFields() {
        assertEquals(300_000L, TimerFormat.parseLength("", "5", ""))
        assertEquals(5_400_000L, TimerFormat.parseLength("1", "30", ""))
        assertEquals(90_000L, TimerFormat.parseLength("0", "", "90"))
        assertNull(TimerFormat.parseLength("", "", ""))
        assertNull(TimerFormat.parseLength("x", "", ""))
        assertNull(TimerFormat.parseLength("25", "", ""))
        assertNull(TimerFormat.parseLength("-1", "", ""))
    }

    // --- reminders

    private val t0 = 1_000_000_000_000L

    @Test
    fun remindersComeInOrderAndTheOnesThatPassedAreMissed() {
        var list = emptyList<Reminder>()
        list = Reminders.add(list, "  Call   Anna ", t0 + 3_600_000)
        list = Reminders.add(list, "Buy milk", t0 + 60_000)
        list = Reminders.add(list, "Old thing", t0 - 3_600_000)
        assertEquals(listOf("Buy milk", "Call Anna"), Reminders.upcoming(list, t0).map { it.text })
        assertEquals(listOf("Old thing"), Reminders.missed(list, t0).map { it.text })
        assertEquals("Buy milk", Reminders.next(list, t0)?.text)
        // One that has just rung is still the next one for a minute.
        assertEquals("Old thing", Reminders.next(listOf(Reminder(1, "Old thing", t0 - 30_000)), t0)?.text)
        assertEquals(listOf(1L, 2L, 3L), list.map { it.id })
    }

    @Test
    fun aReminderIsDoneRemovedOrMovedToAnotherTime() {
        var list = Reminders.add(Reminders.add(emptyList(), "A", t0 + 1000), "B", t0 + 2000)
        list = Reminders.complete(list, 1)
        assertEquals(listOf("B"), Reminders.upcoming(list, t0).map { it.text })
        list = Reminders.reschedule(list, 1, t0 + 5000)
        assertEquals(listOf("B", "A"), Reminders.upcoming(list, t0).map { it.text })
        list = Reminders.remove(list, 2)
        assertEquals(listOf(1L), list.map { it.id })
        assertEquals(emptyList<Reminder>(), Reminders.clearDone(Reminders.complete(list, 1)))
    }

    @Test
    fun aReminderNeedsText() {
        assertEquals(emptyList<Reminder>(), Reminders.add(emptyList(), "   ", t0))
        assertNull(Reminders.clean("  "))
        assertEquals(Reminders.MAX_TEXT, Reminders.clean("x".repeat(500))!!.length)
    }

    @Test
    fun theListNeverGrowsPastItsLimit() {
        var list = emptyList<Reminder>()
        repeat(Reminders.MAX + 10) { list = Reminders.add(list, "R$it", t0 + it) }
        assertEquals(Reminders.MAX, list.size)
        // The oldest go first: the last one is still there.
        assertEquals("R${Reminders.MAX + 9}", list.last().text)
    }

    @Test
    fun remindersAreWrittenAndReadBack() {
        val list = listOf(Reminder(1, "A", 5L), Reminder(2, "B ü", 6L, done = true))
        assertEquals(list, Reminders.decode(Reminders.encode(list)))
        assertEquals(emptyList<Reminder>(), Reminders.decode("garbage"))
        assertEquals(emptyList<Reminder>(), Reminders.decode(null))
    }

    // --- smart dial

    private val entries = listOf(
        DialEntry("Anna Rossi", "+39 333 1234567", "lookup/1"),
        DialEntry("Andrea Bianchi", "+39 347 7654321", "lookup/2"),
        DialEntry("Zoë Verdi", "06 5551234", "lookup/3"),
        DialEntry("Anna Rossi", "+39 02 999999", "lookup/1"),
    )

    @Test
    fun lettersAreTheirKeys() {
        assertEquals("2662 76774", SmartDial.keysOf("Anna Rossi"))
        assertEquals("963 83734", SmartDial.keysOf("Zoë Verdi"))
        assertEquals("2662 76774", SmartDial.keysOf("anna-rossi"))
        assertEquals("", SmartDial.keysOf("!?"))
        assertEquals("12 345", SmartDial.keysOf("12 345"))
    }

    @Test
    fun digitsFindTheNameTheyAreSpelledBy() {
        assertEquals(listOf("Anna Rossi"), SmartDial.search(entries, "266").map { it.name })
        assertEquals(listOf("Anna Rossi", "Andrea Bianchi").sorted(), SmartDial.search(entries, "26").map { it.name }.sorted())
        // The start of the last name, the initials, the number.
        assertEquals(listOf("Anna Rossi"), SmartDial.search(entries, "767").map { it.name })
        assertEquals(listOf("Anna Rossi"), SmartDial.search(entries, "27").map { it.name })
        assertEquals(listOf("Zoë Verdi"), SmartDial.search(entries, "963").map { it.name })
        assertEquals(listOf("Andrea Bianchi"), SmartDial.search(entries, "7654").map { it.name })
        assertEquals(listOf("Zoë Verdi"), SmartDial.search(entries, "5551234").map { it.name })
        assertTrue(SmartDial.search(entries, "0000").isEmpty())
    }

    @Test
    fun aContactIsListedOnceAtItsBestFit() {
        val found = SmartDial.search(entries, "266")
        assertEquals(1, found.size)
        assertEquals("lookup/1", found.single().lookupUri)
    }

    @Test
    fun theNameComesBeforeTheNumberAndWhatIsNotDigitsIsNoQuery() {
        // "333" is a number of Anna's and no name's start.
        assertEquals(listOf("Anna Rossi"), SmartDial.search(entries, "333").map { it.name })
        assertTrue(SmartDial.isQuery("12"))
        assertFalse(SmartDial.isQuery("1"))
        assertFalse(SmartDial.isQuery("12a"))
        assertFalse(SmartDial.isQuery("1".repeat(SmartDial.MAX_DIGITS + 1)))
        assertTrue(SmartDial.search(entries, "ann").isEmpty())
    }

    // --- places and shortcuts

    @Test
    fun aPlaceStartsTheNavigation() {
        assertEquals("google.navigation:q=Via%20Roma%201%2C%20Milano", Places.navigationUri("Via Roma 1, Milano"))
        assertEquals("geo:0,0?q=Via%20Roma%201%2C%20Milano", Places.geoUri("Via Roma 1, Milano"))
        assertEquals(
            listOf(
                "google.navigation:q=Via%20Roma", "geo:0,0?q=Via%20Roma", "https://www.google.com/maps/dir/?api=1&destination=Via%20Roma",
            ),
            Places.fallbacks("google.navigation:q=Via%20Roma"),
        )
        assertEquals(listOf("https://example.com"), Places.fallbacks("https://example.com"))
        assertEquals("Via Roma 1, Milano", Places.addressOf(Places.navigationUri("Via Roma 1, Milano")))
        assertNull(Places.addressOf("https://example.com"))
        assertEquals("Via Roma 1, Milano", Places.clean("  Via   Roma 1,\nMilano "))
        assertNull(Places.clean("   "))
        assertEquals("Via Roma 1", Places.nameFor("", "Via Roma 1, Milano").let { it })
        assertEquals("Casa", Places.nameFor(" Casa ", "Via Roma 1, Milano"))
    }

    @Test
    fun theNewTilesAreMadeOfTheirShortcuts() {
        val place = ShortcutDef("shortcut:place:1", ShortcutKind.Place, "Casa", "Via Roma 1, Milano").toItem(null)
        assertEquals(Launch.Link("google.navigation:q=Via%20Roma%201%2C%20Milano"), place.launch)
        assertEquals(TileData.Place("Via Roma 1, Milano"), place.data)

        val album = ShortcutDef("shortcut:album:1", ShortcutKind.Album, "Vacanze", "123456").toItem(null)
        assertEquals(LiveKind.Album, album.live)
        assertEquals(TileData.Album("123456"), album.data)

        val group = ShortcutDef("shortcut:group:1", ShortcutKind.Group, "Famiglia", "42").toItem(null)
        assertEquals(LiveKind.Group, group.live)
        assertEquals(TileData.Group(42L), group.data)
        assertEquals(TileData.Group(-1L), ShortcutDef("g", ShortcutKind.Group, "x", "nonsense").toItem(null).data)

        val timer = ShortcutDef("shortcut:timer:1", ShortcutKind.Timer, "Timer", text = TimerState(60_000).encode()).toItem(null)
        assertEquals(TileData.Timer(TimerState(60_000)), timer.data)
        val stopwatch = ShortcutDef("shortcut:stopwatch:1", ShortcutKind.Stopwatch, "Stopwatch").toItem(null)
        assertEquals(TileData.Stopwatch(StopwatchState()), stopwatch.data)

        assertEquals(LiveKind.News, ShortcutDef("n", ShortcutKind.News, "News").toItem(null).live)
        assertEquals(LiveKind.Finance, ShortcutDef("f", ShortcutKind.Finance, "Finance").toItem(null).live)
        assertEquals(TileData.DialGrid, ShortcutDef("d", ShortcutKind.DialGrid, "Dial").toItem(null).data)
        assertEquals(TileData.PeopleHub, ShortcutDef("p", ShortcutKind.PeopleHub, "People").toItem(null).data)
        assertEquals(TileData.Reminders, ShortcutDef("r", ShortcutKind.Reminders, "Reminders").toItem(null).data)
    }

    @Test
    fun theNewShortcutsSurviveTheTripToDisk() {
        val all = listOf(
            ShortcutDef("a", ShortcutKind.Place, "Casa", "Via Roma 1"),
            ShortcutDef("b", ShortcutKind.Album, "Vacanze", "123"),
            ShortcutDef("c", ShortcutKind.Group, "Famiglia", "7"),
            ShortcutDef("d", ShortcutKind.Timer, "Timer", text = TimerState(60_000).start(5).encode()),
            ShortcutDef("e", ShortcutKind.Stopwatch, "Stopwatch", text = StopwatchState(5, laps = listOf(3)).encode()),
            ShortcutDef("f", ShortcutKind.News, "News"),
            ShortcutDef("g", ShortcutKind.Finance, "Finance"),
            ShortcutDef("h", ShortcutKind.DialGrid, "Dial"),
            ShortcutDef("i", ShortcutKind.PeopleHub, "People"),
            ShortcutDef("j", ShortcutKind.Reminders, "Reminders"),
        )
        assertEquals(all, ShortcutCodec.decode(ShortcutCodec.encode(all)))
    }
}
