package com.metrowin.launcher.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.metrowin.launcher.data.AlbumInfo
import com.metrowin.launcher.data.Favourite
import com.metrowin.launcher.data.FinanceData
import com.metrowin.launcher.data.GroupInfo
import com.metrowin.launcher.data.NewsData
import com.metrowin.launcher.data.NewsItem
import com.metrowin.launcher.data.Quote
import com.metrowin.launcher.data.RecentCall
import com.metrowin.launcher.data.Reminder
import com.metrowin.launcher.model.StopwatchState
import com.metrowin.launcher.model.TimerState
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.ui.pages.DialGridPage
import com.metrowin.launcher.ui.pages.FinancePage
import com.metrowin.launcher.ui.pages.NewsPage
import com.metrowin.launcher.ui.pages.PeopleHubPage
import com.metrowin.launcher.ui.pages.RemindersPage
import com.metrowin.launcher.ui.pages.StopwatchPage
import com.metrowin.launcher.ui.pages.TimerPage
import com.metrowin.launcher.ui.start.AddMenu
import com.metrowin.launcher.ui.start.AlbumPicker
import com.metrowin.launcher.ui.start.GroupPicker
import com.metrowin.launcher.ui.start.PlaceDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pages of the tiles of the third round (timer, stopwatch, news, finance, reminders, speed dial, people) and the choices that make a tile. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NewPagesUiTest : ScreenshotBase() {
    private val now = System.currentTimeMillis()
    private val hour = 60L * 60 * 1000

    private fun textOf(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun type(fieldTag: String, text: String) {
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(fieldTag))).performTextInput(text)
        compose.waitForIdle()
    }

    // --- the timer

    private class TimerCalls {
        var started = 0
        var paused = 0
        var reset = 0
        val lengths = mutableListOf<Long>()
        val added = mutableListOf<Long>()
    }

    private fun showTimer(state: TimerState, calls: TimerCalls = TimerCalls()): TimerCalls {
        show {
            TimerPage(
                "My timer", state, onStart = { calls.started++ }, onPause = { calls.paused++ }, onReset = { calls.reset++ },
                onLength = { calls.lengths += it }, onAdd = { calls.added += it },
            )
        }
        advance(300)
        return calls
    }

    @Test
    fun theTimerPageStartsAddsTimeAndResets() {
        val calls = showTimer(TimerState(5 * 60_000L))
        capture("page_timer_idle")
        assertEquals("05:00", textOf("timer:page:time"))
        compose.onNodeWithTag("timer:page:start").performClick()
        compose.onNodeWithTag("timer:page:reset").performClick()
        compose.onNodeWithTag("timer:page:add:1").performClick()
        compose.onNodeWithTag("timer:page:add:5").performClick()
        assertEquals(1, calls.started)
        assertEquals(1, calls.reset)
        assertEquals(listOf(60_000L, 300_000L), calls.added)
        compose.onAllNodesWithTag("timer:page:pause").assertCountEquals(0)
    }

    @Test
    fun theTimerPageChoosesALengthFromThePresetsAndFromTheFields() {
        val calls = showTimer(TimerState(5 * 60_000L))
        tapScrolled("timer:page:preset:10")
        assertEquals(listOf(600_000L), calls.lengths)
        // A length of one's own: two minutes and thirty seconds.
        scrollIntoView("timer:page:minutes")
        type("timer:page:minutes", "2")
        type("timer:page:seconds", "30")
        tapScrolled("timer:page:set")
        assertEquals(listOf(600_000L, 150_000L), calls.lengths)
    }

    @Test
    fun nothingTypedMeansNoLength() {
        val calls = showTimer(TimerState(5 * 60_000L))
        tapScrolled("timer:page:set")
        assertTrue(calls.lengths.isEmpty())
    }

    @Test
    fun aRunningTimerCanOnlyBePausedAndHasNoLengthsToChoose() {
        val calls = showTimer(TimerState(10 * 60_000L, endAt = System.currentTimeMillis() + 4 * 60_000L))
        capture("page_timer_running")
        compose.onAllNodesWithTag("timer:page:start").assertCountEquals(0)
        compose.onAllNodesWithTag("timer:page:preset:5").assertCountEquals(0)
        compose.onNodeWithTag("timer:page:pause").performClick()
        assertEquals(1, calls.paused)
        assertTrue(textOf("timer:page:time").startsWith("03:5") || textOf("timer:page:time").startsWith("04:0"))
    }

    @Test
    fun aTimerThatEndedCanStartAgainButNotGetMoreTime() {
        val calls = showTimer(TimerState(60_000L, endAt = 1L))
        assertEquals("00:00", textOf("timer:page:time"))
        compose.onAllNodesWithTag("timer:page:add:1").assertCountEquals(0)
        compose.onNodeWithTag("timer:page:start").performClick()
        assertEquals(1, calls.started)
    }

    // --- the stopwatch

    private class StopwatchCalls {
        var started = 0
        var stopped = 0
        var laps = 0
        var reset = 0
    }

    private fun showStopwatch(state: StopwatchState): StopwatchCalls {
        val calls = StopwatchCalls()
        show {
            StopwatchPage(
                "My stopwatch", state, onStart = { calls.started++ }, onStop = { calls.stopped++ }, onLap = { calls.laps++ }, onReset = { calls.reset++ },
            )
        }
        advance(300)
        return calls
    }

    @Test
    fun aStoppedStopwatchShowsItsTimeItsLapsAndCanGoOnOrReset() {
        val calls = showStopwatch(StopwatchState(accumulatedMs = 83_400L, laps = listOf(40_000L, 83_400L)))
        capture("page_stopwatch_stopped")
        assertEquals("01:23.4", textOf("stopwatch:page:time"))
        compose.onNodeWithTag("stopwatch:page:lap:0").assertExists()
        compose.onNodeWithTag("stopwatch:page:lap:1").assertExists()
        compose.onNodeWithTag("stopwatch:page:start").performClick()
        compose.onNodeWithTag("stopwatch:page:reset").performClick()
        assertEquals(1, calls.started)
        assertEquals(1, calls.reset)
        compose.onAllNodesWithTag("stopwatch:page:stop").assertCountEquals(0)
    }

    @Test
    fun aStopwatchAtZeroOffersOnlyStart() {
        showStopwatch(StopwatchState())
        assertEquals("00:00.0", textOf("stopwatch:page:time"))
        compose.onNodeWithTag("stopwatch:page:start").assertExists()
        compose.onAllNodesWithTag("stopwatch:page:reset").assertCountEquals(0)
    }

    @Test
    fun aRunningStopwatchCanBeStoppedAndLapped() {
        val calls = showStopwatch(StopwatchState(startedElapsed = android.os.SystemClock.elapsedRealtime(), startedWall = System.currentTimeMillis()))
        compose.onNodeWithTag("stopwatch:page:lap").performClick()
        compose.onNodeWithTag("stopwatch:page:stop").performClick()
        assertEquals(1, calls.laps)
        assertEquals(1, calls.stopped)
        compose.onAllNodesWithTag("stopwatch:page:start").assertCountEquals(0)
    }

    // --- the news

    private val news = NewsData(
        "Top stories - Google News",
        listOf(NewsItem("Headline one", "ANSA", "https://example.com/1", now - hour), NewsItem("Headline two", "Reuters", "https://example.com/2", now - 2 * hour)),
        now, "key",
    )

    @Test
    fun theNewsPageListsTheHeadlinesAndOpensOneOnATouch() {
        val opened = mutableListOf<String>()
        var refreshed = 0
        var chosen = 0
        show { NewsPage(news, googleSource = true, onOpen = { opened += it }, onRefresh = { refreshed++ }, onChooseSource = { chosen++ }) }
        advance(300)
        capture("page_news")
        compose.onNodeWithText("Headline one").assertExists()
        compose.onNodeWithTag("news:item:1").performClick()
        assertEquals(listOf("https://example.com/2"), opened)
        tapScrolled("news:refresh")
        tapScrolled("news:source")
        tapScrolled("news:google")
        assertEquals(1, refreshed)
        assertEquals(1, chosen)
        assertEquals("https://news.google.com/", opened.last())
    }

    @Test
    fun theLinkToGoogleNewsIsOnlyThereForAGoogleSource() {
        show { NewsPage(news, googleSource = false, onOpen = {}, onRefresh = {}, onChooseSource = {}) }
        advance(300)
        compose.onAllNodesWithTag("news:google").assertCountEquals(0)
    }

    @Test
    fun withoutHeadlinesTheNewsPageSaysSoAndStillOffersARefresh() {
        show { NewsPage(null, googleSource = true, onOpen = {}, onRefresh = {}, onChooseSource = {}) }
        advance(300)
        compose.onNodeWithTag("news:empty").assertExists()
        compose.onNodeWithTag("news:refresh").assertExists()
    }

    // --- the finance

    private val finance = FinanceData("EUR", listOf(Quote("fx:USD", 0.92, 0.4), Quote("crypto:bitcoin", 61_000.0, -1.2)), "2026-10-23", now, "key")

    @Test
    fun theFinancePageListsTheQuotesAndOpensGoogleFinanceOnATouch() {
        val opened = mutableListOf<String>()
        show { FinancePage(finance, onOpen = { opened += it }, onRefresh = {}, onChoose = {}) }
        advance(300)
        capture("page_finance")
        compose.onNodeWithTag("finance:item:0").performClick()
        compose.onNodeWithTag("finance:item:1").performClick()
        assertEquals(listOf("https://www.google.com/finance/quote/USD-EUR", "https://www.google.com/finance/quote/BTC-EUR"), opened)
    }

    @Test
    fun withoutQuotesTheFinancePageSaysSo() {
        var refreshed = 0
        var chosen = 0
        show { FinancePage(null, onOpen = {}, onRefresh = { refreshed++ }, onChoose = { chosen++ }) }
        advance(300)
        compose.onNodeWithTag("finance:empty").assertExists()
        tapScrolled("finance:refresh")
        tapScrolled("finance:choose")
        assertEquals(1, refreshed)
        assertEquals(1, chosen)
    }

    // --- the reminders

    private class ReminderCalls {
        val added = mutableListOf<Pair<String, Long>>()
        val done = mutableListOf<Long>()
        val removed = mutableListOf<Long>()
        var cleared = 0
        var allowed = 0
    }

    private fun showReminders(list: List<Reminder> = emptyList(), draft: String = "", canExact: Boolean = true, accept: Boolean = true): ReminderCalls {
        val calls = ReminderCalls()
        show {
            RemindersPage(
                list, draft, canExact,
                onAdd = { text, at ->
                    calls.added += text to at
                    accept
                },
                onDone = { calls.done += it }, onRemove = { calls.removed += it }, onClearDone = { calls.cleared++ }, onAllowExact = { calls.allowed++ },
            )
        }
        advance(300)
        return calls
    }

    @Test
    fun aReminderIsMadeFromTheTextAndAQuickTime() {
        val calls = showReminders()
        capture("page_reminders_empty")
        compose.onNodeWithTag("reminders:empty").assertExists()
        type("reminders:field", "Call Anna")
        val before = System.currentTimeMillis()
        compose.onNodeWithTag("reminders:quick:InOneHour").performClick()
        compose.waitForIdle()
        val (text, at) = calls.added.single()
        assertEquals("Call Anna", text)
        assertTrue("in about an hour: ${at - before}", at - before in 59 * 60_000L - 5_000..60 * 60_000L + 5_000)
    }

    @Test
    fun withNothingWrittenTheQuickTimesDoNothing() {
        val calls = showReminders()
        compose.onNodeWithTag("reminders:quick:InOneHour").performClick()
        compose.onNodeWithTag("reminders:quick:ThisEvening").performClick()
        compose.onNodeWithTag("reminders:quick:TomorrowMorning").performClick()
        assertTrue(calls.added.isEmpty())
    }

    @Test
    fun aDraftFromTheSearchBoxIsWaitingInTheField() {
        val calls = showReminders(draft = "Buy milk")
        compose.onNodeWithTag("reminders:quick:TomorrowMorning").performClick()
        assertEquals("Buy milk", calls.added.single().first)
    }

    @Test
    fun theListShowsWhatComesWhatWasMissedAndWhatIsDone() {
        val list = listOf(
            Reminder(1, "Call Anna", now + hour), Reminder(2, "Missed one", now - 5 * hour), Reminder(3, "Finished", now - hour, done = true),
        )
        val calls = showReminders(list)
        capture("page_reminders_list")
        compose.onNodeWithTag("reminders:item:1").assertExists()
        compose.onNodeWithTag("reminders:item:2").assertExists()
        // A reminder that is done is in the last group, with only the cross.
        compose.onAllNodesWithTag("reminders:item:3").assertCountEquals(0)
        compose.onNodeWithTag("reminders:done:1").performClick()
        compose.onNodeWithTag("reminders:remove:2").performClick()
        tapScrolled("reminders:clear")
        assertEquals(listOf(1L), calls.done)
        assertEquals(listOf(2L), calls.removed)
        assertEquals(1, calls.cleared)
    }

    @Test
    fun withoutExactAlarmsThePageSaysSoAndOffersTheSetting() {
        val calls = showReminders(canExact = false)
        compose.onNodeWithTag("reminders:exact").performClick()
        assertEquals(1, calls.allowed)
    }

    @Test
    fun withExactAlarmsThereIsNoWarning() {
        showReminders(canExact = true)
        compose.onAllNodesWithTag("reminders:exact").assertCountEquals(0)
    }

    // --- the speed dial and the people hub

    private val people = listOf(
        Favourite("Anna", "content://c/1", "111", null), Favourite("Bruno", "content://c/2", "222", null), Favourite("Carla", "content://c/3", "333", null),
        Favourite("Dario", "content://c/4", "444", null), Favourite("Elena", "content://c/5", "555", null),
    )

    @Test
    fun theSpeedDialGridCallsTheNumberOfTheSquareTouched() {
        val called = mutableListOf<String>()
        show { DialGridPage(people, hasPermission = true, onRequestPermission = {}, onCall = { called += it }) }
        advance(300)
        capture("page_dialgrid")
        compose.onNodeWithTag("dialgrid:1").performClick()
        compose.onNodeWithTag("dialgrid:4").performClick()
        assertEquals(listOf("222", "555"), called)
        compose.onNodeWithText("Anna").assertExists()
    }

    @Test
    fun theSpeedDialGridAsksForTheContactsWhenItCannotReadThem() {
        var asked = 0
        show { DialGridPage(null, hasPermission = false, onRequestPermission = { asked++ }, onCall = {}) }
        advance(300)
        compose.onNodeWithTag("dialgrid:allow").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun anEmptySpeedDialSaysSo() {
        show { DialGridPage(emptyList(), hasPermission = true, onRequestPermission = {}, onCall = {}) }
        advance(300)
        compose.onNodeWithTag("dialgrid:empty").assertExists()
    }

    private val calls = listOf(
        RecentCall("Anna", "111", android.provider.CallLog.Calls.INCOMING_TYPE, now - hour),
        RecentCall("Bruno", "222", android.provider.CallLog.Calls.MISSED_TYPE, now - 2 * hour),
        RecentCall("Carla", "333", android.provider.CallLog.Calls.OUTGOING_TYPE, now - 3 * hour),
    )
    private val notification = NotificationEntry("k1", "chat", "Giulia", "Ci vediamo alle otto?", now - 60_000L, null, true)

    @Test
    fun thePeopleHubShowsTheFavouritesTheLatestCallsAndWhatIsNew() {
        val called = mutableListOf<String>()
        val opened = mutableListOf<NotificationEntry>()
        show {
            PeopleHubPage(
                people, calls, listOf(notification), labelOf = { "Chat" }, hasContacts = true, hasCallLog = true,
                onRequestContacts = {}, onRequestCallLog = {}, onCall = { called += it }, onOpenNotification = { opened += it },
            )
        }
        advance(300)
        capture("page_peoplehub")
        compose.onNodeWithTag("peoplehub:fav:content://c/2").performClick()
        compose.onNodeWithTag("peoplehub:call:2").performClick()
        compose.onNodeWithTag("peoplehub:news:0").performClick()
        assertEquals(listOf("222", "333"), called)
        assertEquals(listOf(notification), opened)
        compose.onNodeWithText("Giulia").assertExists()
    }

    @Test
    fun thePeopleHubAsksForEachPermissionItLacks() {
        var contacts = 0
        var callLog = 0
        show {
            PeopleHubPage(
                null, null, emptyList(), labelOf = { "" }, hasContacts = false, hasCallLog = false,
                onRequestContacts = { contacts++ }, onRequestCallLog = { callLog++ }, onCall = {}, onOpenNotification = {},
            )
        }
        advance(300)
        compose.onNodeWithTag("peoplehub:allow:contacts").performClick()
        tapScrolled("peoplehub:allow:calls")
        assertEquals(1, contacts)
        assertEquals(1, callLog)
    }

    // --- the choices that make a tile

    @Test
    fun theAddMenuOffersTheNewTilesAndEachOneDoesItsThing() {
        val done = mutableListOf<String>()
        show {
            AddMenu(
                onContact = {}, onSite = {}, onWidget = {}, onDismiss = {},
                onPlace = { done += "place" }, onAlbum = { done += "album" }, onGroup = { done += "group" }, onTimer = { done += "timer" },
                onStopwatch = { done += "stopwatch" }, onNews = { done += "news" }, onFinance = { done += "finance" },
                onReminders = { done += "reminders" }, onDialGrid = { done += "dialgrid" }, onPeopleHub = { done += "peoplehub" },
            )
        }
        advance(300)
        val names = listOf("place", "album", "group", "timer", "stopwatch", "news", "finance", "reminders", "dialgrid", "peoplehub")
        names.forEach { tapScrolled("add:$it") }
        assertEquals(names, done)
    }

    @Test
    fun aPlaceIsMadeFromANameAndAnAddress() {
        val made = mutableListOf<Pair<String, String>>()
        show { PlaceDialog(onDone = { name, address -> made += name to address; true }, onCancel = {}) }
        advance(300)
        capture("dialog_place")
        compose.onNodeWithTag("place:home").performClick()
        type("place:address", "Via Roma 1, Torino")
        compose.onNodeWithTag("place:ok").performClick()
        assertEquals(listOf("Home" to "Via Roma 1, Torino"), made)
    }

    @Test
    fun aPlaceTheAppRefusesKeepsTheDialogOpen() {
        var tries = 0
        show { PlaceDialog(onDone = { _, _ -> tries++; false }, onCancel = {}) }
        advance(300)
        compose.onNodeWithTag("place:ok").performClick()
        compose.onNodeWithTag("place:ok").assertExists()
        assertEquals(1, tries)
    }

    @Test
    fun theAlbumPickerListsTheAlbumsAndPicksOne() {
        val picked = mutableListOf<AlbumInfo>()
        val albums = listOf(AlbumInfo("1", "Camera", 120), AlbumInfo("2", "Holidays", 1))
        show { AlbumPicker(albums, permissionMissing = false, onAllow = {}, onPick = { picked += it }, onDismiss = {}) }
        advance(300)
        capture("picker_album")
        compose.onNodeWithTag("album:2").performClick()
        assertEquals(listOf(albums[1]), picked)
        compose.onNodeWithText("Camera").assertExists()
    }

    @Test
    fun theAlbumPickerAsksForThePhotosWhenItCannotReadThem() {
        var allowed = 0
        show { AlbumPicker(null, permissionMissing = true, onAllow = { allowed++ }, onPick = {}, onDismiss = {}) }
        advance(300)
        compose.onNodeWithTag("album:allow").performClick()
        assertEquals(1, allowed)
    }

    @Test
    fun theGroupPickerListsTheGroupsAndPicksOne() {
        val picked = mutableListOf<GroupInfo>()
        val groups = listOf(GroupInfo(7L, "Family", 5), GroupInfo(8L, "Work", 12))
        show { GroupPicker(groups, permissionMissing = false, onAllow = {}, onPick = { picked += it }, onDismiss = {}) }
        advance(300)
        capture("picker_group")
        compose.onNodeWithTag("group:8").performClick()
        assertEquals(listOf(groups[1]), picked)
    }

    @Test
    fun theGroupPickerSaysWhenThereAreNoGroupsAndWhenTheContactsCannotBeRead() {
        var allowed = 0
        show { GroupPicker(emptyList(), permissionMissing = false, onAllow = {}, onPick = {}, onDismiss = {}) }
        advance(300)
        compose.onAllNodesWithTag("group:allow").assertCountEquals(0)
        // The same sheet, in another test of its own, for the permission (a screen is shown once in a test).
        assertEquals(0, allowed)
    }

    @Test
    fun theGroupPickerAsksForTheContactsWhenItCannotReadThem() {
        var allowed = 0
        show { GroupPicker(null, permissionMissing = true, onAllow = { allowed++ }, onPick = {}, onDismiss = {}) }
        advance(300)
        compose.onNodeWithTag("group:allow").performClick()
        assertEquals(1, allowed)
    }
}
