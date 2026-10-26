package com.metrowin.launcher

import com.metrowin.launcher.data.Finance
import com.metrowin.launcher.data.FinanceItems
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LockStatusBuilder
import com.metrowin.launcher.data.NewsSources
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.Reminders
import com.metrowin.launcher.data.ScreenTimeouts
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.TileTransition
import com.metrowin.launcher.data.VolumeRoute
import com.metrowin.launcher.data.VolumeRouting
import com.metrowin.launcher.data.VolumeStream
import com.metrowin.launcher.notifications.NotificationControl
import com.metrowin.launcher.notifications.NotificationEntry
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The volume keys, the lock screen status, the choices about notifications, the new buttons and the new settings. */
class MoreFeaturesTest {
    // --- the volume keys

    @Test
    fun aVolumeKeyMovesWhatIsSoundingTheWayAndroidDoes() {
        assertEquals(VolumeRoute.Stream(VolumeStream.Voice), VolumeRouting.route(inCall = true, remotePlaying = true, musicPlaying = true))
        assertEquals(VolumeRoute.Remote, VolumeRouting.route(inCall = false, remotePlaying = true, musicPlaying = true))
        assertEquals(VolumeRoute.Stream(VolumeStream.Music), VolumeRouting.route(inCall = false, remotePlaying = false, musicPlaying = true))
        assertEquals(VolumeRoute.Stream(VolumeStream.Ring), VolumeRouting.route(inCall = false, remotePlaying = false, musicPlaying = false))
    }

    @Test
    fun levelsAndFractionsGoBothWays() {
        assertEquals(0f, VolumeRouting.fraction(0, 0, 15), 0f)
        assertEquals(0.5f, VolumeRouting.fraction(5, 0, 10), 0f)
        assertEquals(1f, VolumeRouting.fraction(15, 0, 15), 0f)
        // A volume that starts at 1 (the call) and one with no room at all.
        assertEquals(0f, VolumeRouting.fraction(1, 1, 7), 0f)
        assertEquals(0f, VolumeRouting.fraction(3, 3, 3), 0f)
        assertEquals(8, VolumeRouting.level(0.5f, 0, 15))
        assertEquals(1, VolumeRouting.level(0f, 1, 7))
        assertEquals(15, VolumeRouting.level(2f, 0, 15))
        assertEquals(0, VolumeRouting.level(-1f, 0, 15))
        assertEquals(6, VolumeRouting.step(5, 0, 15, raise = true))
        assertEquals(4, VolumeRouting.step(5, 0, 15, raise = false))
        assertEquals(15, VolumeRouting.step(15, 0, 15, raise = true))
        assertEquals(0, VolumeRouting.step(0, 0, 15, raise = false))
    }

    // --- the lock screen

    private var clock = 0L
    private fun entry(pkg: String, title: String = "t", text: String = "x") =
        NotificationEntry("k${clock}", pkg, title, text, clock++, null, true)

    @Test
    fun theQuickStatusIsTheAppsWithTheMostNotificationsWhenNoneIsChosen() {
        // The newest first, as the hub gives them.
        val entries = listOf(
            entry("chat"), entry("mail"), entry("chat"), entry("news"), entry("chat"), entry("mail"), entry("a"), entry("b"), entry("c"), entry("com.metrowin.launcher"),
        )
        val status = LockStatusBuilder.build(entries, quickOn = true, quickApps = emptyList(), detailApp = "", hideText = false, own = "com.metrowin.launcher")
        assertEquals(listOf("chat" to 3, "mail" to 2), status.quick.take(2).map { it.packageName to it.count })
        assertEquals(LockStatusBuilder.MAX, status.quick.size)
        // The launcher's own notifications are not one of its apps.
        assertFalse(status.quick.any { it.packageName == "com.metrowin.launcher" })
        // Equal counts: the one that wrote last comes first.
        assertEquals(listOf("news", "a", "b"), status.quick.drop(2).map { it.packageName })
    }

    @Test
    fun theAppsTheUserChoseShowEvenWithNothingToCount() {
        val entries = listOf(entry("chat"), entry("mail"))
        val status = LockStatusBuilder.build(entries, true, listOf("mail", "calendar", "mail"), "", false, "own")
        assertEquals(listOf("mail" to 1, "calendar" to 0), status.quick.map { it.packageName to it.count })
        val many = LockStatusBuilder.build(entries, true, (1..9).map { "app$it" }, "", false, "own")
        assertEquals(LockStatusBuilder.MAX, many.quick.size)
    }

    @Test
    fun theQuickStatusCanBeSwitchedOff() {
        val status = LockStatusBuilder.build(listOf(entry("chat")), quickOn = false, quickApps = emptyList(), detailApp = "chat", hideText = false, own = "own")
        assertTrue(status.quick.isEmpty())
        // The detailed status is a choice of its own.
        assertNotNull(status.detail)
    }

    @Test
    fun theDetailedStatusIsTheLastNotificationOfThatApp() {
        val entries = listOf(entry("chat", "Anna", "see you at 8"), entry("mail", "Bank", "your statement"), entry("chat", "Luca", "old"))
        val status = LockStatusBuilder.build(entries, true, emptyList(), "chat", hideText = false, own = "own")
        assertEquals("Anna", status.detail?.title)
        assertEquals("see you at 8", status.detail?.text)
        // Only who it is from, when the words are kept private.
        val hidden = LockStatusBuilder.build(entries, true, emptyList(), "chat", hideText = true, own = "own")
        assertEquals("Anna", hidden.detail?.title)
        assertEquals("", hidden.detail?.text)
        assertNull(LockStatusBuilder.build(entries, true, emptyList(), "nothing", false, "own").detail)
        assertNull(LockStatusBuilder.build(entries, true, emptyList(), "", false, "own").detail)
    }

    // --- the choices about notifications, app by app

    @Test
    fun anAppCanBeKeptOutOfTheActionCenterTheLockScreenAndTheBanner() {
        val entries = listOf(entry("chat"), entry("mail"), entry("news"))
        val s = LauncherSettings(notifHideCenter = setOf("chat"), notifHideLock = setOf("mail"), notifNoBanner = setOf("news"))
        assertEquals(listOf("mail", "news"), NotificationControl.forActionCenter(entries, s).map { it.packageName })
        assertEquals(listOf("chat", "news"), NotificationControl.forLockScreen(entries, s).map { it.packageName })
        assertFalse(NotificationControl.showsBanner("news", s))
        assertTrue(NotificationControl.showsBanner("chat", s))
    }

    @Test
    fun withTheMasterSwitchOffNoChoiceIsApplied() {
        val entries = listOf(entry("chat"), entry("mail"))
        val s = LauncherSettings(notifControl = false, notifHideCenter = setOf("chat"), notifHideLock = setOf("chat"), notifNoBanner = setOf("chat"))
        assertEquals(2, NotificationControl.forActionCenter(entries, s).size)
        assertEquals(2, NotificationControl.forLockScreen(entries, s).size)
        assertTrue(NotificationControl.showsBanner("chat", s))
        // The banner has its own switch.
        assertFalse(NotificationControl.showsBanner("mail", LauncherSettings(toastBanner = false)))
        // What was chosen is kept for when the switch comes back.
        assertEquals(setOf("chat"), s.notifHideCenter)
    }

    // --- two more buttons of the Action Center

    @Test
    fun theScreenTimeoutGoesThroughItsLengthsAndStartsAgain() {
        assertEquals(30_000, ScreenTimeouts.next(15_000))
        assertEquals(600_000, ScreenTimeouts.next(300_000))
        assertEquals(15_000, ScreenTimeouts.next(600_000))
        // A length the phone has that is not a step (45 seconds) goes on to the next longer step.
        assertEquals(60_000, ScreenTimeouts.next(45_000))
        assertEquals(15_000, ScreenTimeouts.next(0))
        assertEquals(30 to true, ScreenTimeouts.parts(30_000))
        assertEquals(2 to false, ScreenTimeouts.parts(120_000))
    }

    @Test
    fun theNewButtonsCanBeChosenLikeTheOthers() {
        assertTrue(QuickAction.entries.contains(QuickAction.ScreenTimeout))
        assertTrue(QuickAction.entries.contains(QuickAction.AutoBrightness))
        val slots = QuickAction.slots(listOf("timeout", "autobrightness", "wifi", "flashlight"))
        assertEquals(listOf(QuickAction.ScreenTimeout, QuickAction.AutoBrightness, QuickAction.Wifi, QuickAction.Flashlight), slots)
        assertEquals(QuickAction.entries.size, QuickAction.entries.map { it.id }.toSet().size)
    }

    // --- the new settings

    @Test
    fun theNewSettingsHaveTheirOwnDefaults() {
        val s = LauncherSettings()
        assertEquals(TileTransition.Flip, s.tileTransition)
        assertEquals("google", s.newsSource)
        assertEquals(FinanceItems.DEFAULT, s.financeItems)
        assertFalse("the panorama titles are off until asked for", s.panoramaTitles)
        assertTrue(s.volumePanel && s.toastBanner && s.notifControl && s.appBar)
        assertTrue(s.lockQuickStatus && s.lockShowMusic)
        assertEquals("", s.lockDetailApp)
        assertTrue(s.searchAssistant && s.searchSmartDial && s.searchToday)
    }

    @Test
    fun theNewSettingsSurviveTheTripToDisk() {
        val s = LauncherSettings(
            tileTransition = TileTransition.Mixed, newsSource = "google:TECHNOLOGY", newsUrl = "example.com/feed", financeBase = "USD",
            financeItems = listOf("fx:EUR", "crypto:bitcoin"), panoramaTitles = true, appBar = false, volumePanel = false, toastBanner = false,
            notifControl = false, notifHideCenter = setOf("a", "b"), notifHideLock = setOf("c"), notifNoBanner = setOf("d"),
            lockQuickStatus = false, lockQuickApps = listOf("p1", "p2"), lockDetailApp = "p3", lockShowMusic = false,
            searchAssistant = false, searchSmartDial = false, searchToday = false,
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s, portable = true)))
    }

    @Test
    fun anOldFileKeepsTheDefaultsOfTheNewSettings() {
        val decoded = SettingsCodec.decode("""{"themeMode":"light"}""")!!
        assertEquals(LauncherSettings().tileTransition, decoded.tileTransition)
        assertEquals(LauncherSettings().newsSource, decoded.newsSource)
        assertEquals(LauncherSettings().notifControl, decoded.notifControl)
    }

    @Test
    fun badValuesInTheFileAreBroughtBackToWhatIsAllowed() {
        val decoded = SettingsCodec.decode(
            """{"newsSource":"javascript:evil","tileTransition":"spin","financeBase":"XXX","financeItems":["fx:USD","fx:USD","stocks:AAPL","crypto:bitcoin","fx:GBP","fx:JPY","fx:CHF"],
            "lockQuickApps":["a","b","c","d","e","f","g"],"newsUrl":"${"x".repeat(900)}"}""",
        )!!
        assertEquals(NewsSources.GOOGLE_TOP, decoded.newsSource)
        assertEquals(TileTransition.Flip, decoded.tileTransition)
        assertEquals("", decoded.financeBase)
        assertEquals(listOf("fx:USD", "crypto:bitcoin", "fx:GBP", "fx:JPY"), decoded.financeItems)
        assertEquals(LauncherSettings.MAX_QUICK_APPS, decoded.lockQuickApps.size)
        assertEquals(NewsSources.MAX_URL, decoded.newsUrl.length)
    }

    // --- smaller pieces

    @Test
    fun theQuickTimesOfAReminder() {
        val afternoon = LocalDateTime.of(2026, 10, 25, 15, 30)
        assertEquals(LocalDateTime.of(2026, 10, 25, 16, 30), Reminders.quickTime(Reminders.QuickTime.InOneHour, afternoon))
        assertEquals(LocalDateTime.of(2026, 10, 25, 20, 0), Reminders.quickTime(Reminders.QuickTime.ThisEvening, afternoon))
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), Reminders.quickTime(Reminders.QuickTime.TomorrowMorning, afternoon))
        // Nearly eight already (or past it): this evening is tomorrow's.
        assertEquals(LocalDateTime.of(2026, 10, 26, 20, 0), Reminders.quickTime(Reminders.QuickTime.ThisEvening, LocalDateTime.of(2026, 10, 25, 19, 55)))
        assertEquals(LocalDateTime.of(2026, 10, 26, 20, 0), Reminders.quickTime(Reminders.QuickTime.ThisEvening, LocalDateTime.of(2026, 10, 25, 22, 0)))
    }

    @Test
    fun aQuoteOpensItsPageOnGoogleFinance() {
        assertEquals("https://www.google.com/finance/quote/USD-EUR", Finance.quoteUrl("fx:USD", "EUR"))
        assertEquals("https://www.google.com/finance/quote/BTC-EUR", Finance.quoteUrl("crypto:bitcoin", "EUR"))
    }
}
