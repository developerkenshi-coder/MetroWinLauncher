package com.metrowin.launcher

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.notifications.AppNotification
import com.metrowin.launcher.notifications.LiveCard
import com.metrowin.launcher.notifications.MessageLine
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.NowPlaying
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** From the notifications to the tiles: the settings decide what the live tiles of other apps get. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveFlowsTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val whatsapp = "com.whatsapp"
    private val spotify = "com.spotify.music"

    @Before
    fun clean() = reset()

    @After
    fun after() = reset()

    private fun reset() {
        NotificationHub.useForLiveTiles(false, true)
        NotificationHub.updateApps(emptyList())
        NotificationHub.update(emptyList())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun chat(vararg lines: Pair<String, String>) = AppNotification(
        "k1", whatsapp, 10L, "Giulia", lines.last().second, messages = lines.map { MessageLine(it.first, it.second) },
    )

    private fun entry() = NotificationEntry("k1", whatsapp, "Giulia", "ultimo", 10L, null, true)

    @Test
    fun nothingIsShownUntilTheMainSwitchIsOn() {
        val vm = LauncherViewModel(app)
        idle()
        NotificationHub.updateApps(listOf(chat("Giulia" to "Ciao")))
        NotificationHub.update(listOf(entry()))
        idle()
        assertTrue(vm.liveInfo.value.isEmpty())
        assertTrue(vm.peeks.value.isEmpty())
        assertFalse("the service is not asked to read in detail", NotificationHub.liveContent)
    }

    @Test
    fun withTheSwitchOnAChatBecomesAFaceAndItsMessagesAreCounted() {
        val vm = LauncherViewModel(app)
        vm.updateSettings { it.copy(notificationPeek = true) }
        idle()
        assertTrue("the service is asked to read in detail", NotificationHub.liveContent)
        NotificationHub.updateApps(listOf(chat("Giulia" to "Ciao", "Giulia" to "Ci sei?", "Giulia" to "Ti aspetto")))
        NotificationHub.update(listOf(entry()))
        idle()
        val card = vm.peeks.value[whatsapp]!!.single() as LiveCard.Message
        assertEquals("Giulia", card.sender)
        assertEquals("Ti aspetto", card.text)
        // One notification, three messages.
        assertEquals(3, vm.badges.value[whatsapp])
    }

    @Test
    fun anAppSwitchedOffShowsNothingAndNoNumberFromTheMessages() {
        val vm = LauncherViewModel(app)
        vm.updateSettings { it.copy(notificationPeek = true, liveOffApps = setOf(whatsapp)) }
        NotificationHub.updateApps(listOf(chat("Giulia" to "Ciao", "Giulia" to "Ci sei?")))
        NotificationHub.update(listOf(entry()))
        idle()
        assertNull(vm.peeks.value[whatsapp])
        // Its counter is back to one notification, as before there were live tiles.
        assertEquals(1, vm.badges.value[whatsapp])
    }

    @Test
    fun hidingTheTextLeavesTheNameOnly() {
        val vm = LauncherViewModel(app)
        vm.updateSettings { it.copy(notificationPeek = true, liveHideText = true) }
        NotificationHub.updateApps(listOf(chat("Giulia" to "Segreto")))
        idle()
        val card = vm.peeks.value[whatsapp]!!.single() as LiveCard.Message
        assertEquals("Giulia", card.sender)
        assertEquals("", card.text)
    }

    @Test
    fun theMusicThatPlaysIsForTheMusicTileUnlessItIsSwitchedOff() {
        val vm = LauncherViewModel(app)
        vm.updateSettings { it.copy(notificationPeek = true) }
        NotificationHub.updateApps(listOf(AppNotification("m", spotify, 5L, "Song", "Artist", ongoing = true, playing = NowPlaying("Song", "Artist", null, true))))
        idle()
        assertEquals("Song", vm.nowPlaying.value!!.title)
        vm.updateSettings { it.copy(liveNowPlaying = false) }
        idle()
        assertNull(vm.nowPlaying.value)
        vm.updateSettings { it.copy(liveNowPlaying = true, liveOffApps = setOf(spotify)) }
        idle()
        assertNull("the player is switched off", vm.nowPlaying.value)
    }

    @Test
    fun turningTheSwitchOffClearsWhatWasRead() {
        val vm = LauncherViewModel(app)
        vm.updateSettings { it.copy(notificationPeek = true) }
        idle()
        NotificationHub.updateApps(listOf(chat("Giulia" to "Ciao")))
        idle()
        assertTrue(vm.peeks.value.isNotEmpty())
        vm.updateSettings { it.copy(notificationPeek = false) }
        idle()
        assertFalse(NotificationHub.liveContent)
        assertTrue(NotificationHub.apps.value.isEmpty())
        assertTrue(vm.peeks.value.isEmpty())
    }
}
