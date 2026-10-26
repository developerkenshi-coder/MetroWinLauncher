package com.metrowin.launcher.screens

import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSession
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.DetailLine
import com.metrowin.launcher.data.LockInfo
import com.metrowin.launcher.data.LockStatus
import com.metrowin.launcher.data.QuickBadge
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.data.VolumeHud
import com.metrowin.launcher.data.VolumeRow
import com.metrowin.launcher.data.VolumeStream
import com.metrowin.launcher.notifications.MediaInfo
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.ui.actioncenter.BANNER_MS
import com.metrowin.launcher.ui.actioncenter.ToastBanner
import com.metrowin.launcher.ui.actioncenter.VOLUME_HUD_MS
import com.metrowin.launcher.ui.actioncenter.VolumeHudPanel
import com.metrowin.launcher.ui.lock.LockOptions
import com.metrowin.launcher.ui.lock.MetroLockScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What comes over the launcher: the volume panel, the banner of a notification, and the status and the music of the lock screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class NewOverlaysUiTest : ScreenshotBase() {
    private fun textOf(tag: String, merged: Boolean = true): String =
        compose.onNodeWithTag(tag, useUnmergedTree = !merged).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    // --- the volume panel

    private val music = VolumeRow(VolumeStream.Music, null, level = 7, min = 0, max = 15)
    private val ring = VolumeRow(VolumeStream.Ring, null, level = 3, min = 0, max = 7)
    private val alarm = VolumeRow(VolumeStream.Alarm, null, level = 5, min = 0, max = 7)

    private class HudCalls {
        var toggled = 0
        var dismissed = 0
        val set = mutableListOf<VolumeRow>()
    }

    private fun showHud(hud: VolumeHud, calls: HudCalls = HudCalls(), style: ThemeStyle = ThemeStyle.Classic): HudCalls {
        show(style = style) { VolumeHudPanel(hud, onSet = { row, _ -> calls.set += row }, onToggle = { calls.toggled++ }, onDismiss = { calls.dismissed++ }) }
        advance(400)
        return calls
    }

    @Test
    fun theVolumePanelNamesTheAppThatPlaysAndTheLevel() {
        showHud(VolumeHud(token = 1, main = music, app = "Spotify"))
        capture("hud_music")
        assertTrue(textOf("volume:title:music").contains("Spotify"))
        assertEquals("7", textOf("volume:level:music"))
        compose.onNodeWithTag("volume:slider:music").assertExists()
        // Not opened out: no other volumes, no note.
        compose.onAllNodesWithTag("volume:title:ring").assertCountEquals(0)
        compose.onAllNodesWithTag("volume:note").assertCountEquals(0)
    }

    @Test
    fun theArrowOpensOutTheOtherVolumesAndSaysWhyOneAppCannotBeLowered() {
        val calls = showHud(VolumeHud(token = 1, main = music, app = "Spotify"))
        compose.onNodeWithTag("volume:expand").performClick()
        assertEquals(1, calls.toggled)
    }

    @Test
    fun anOpenedOutPanelListsTheRingAndTheAlarmAndTheNote() {
        showHud(VolumeHud(token = 1, main = music, app = "Spotify", others = listOf(ring, alarm), expanded = true))
        capture("hud_expanded")
        compose.onNodeWithTag("volume:title:ring").assertExists()
        compose.onNodeWithTag("volume:title:alarm").assertExists()
        compose.onNodeWithTag("volume:slider:ring").assertExists()
        compose.onNodeWithTag("volume:note").assertExists()
    }

    @Test
    fun aPlayerWithAVolumeOfItsOwnIsMovedAndNamed() {
        showHud(VolumeHud(token = 1, main = VolumeRow(null, "com.example.cast", level = 4, min = 0, max = 10), app = "Cast"))
        capture("hud_remote")
        assertTrue(textOf("volume:title:remote").contains("Cast"))
        compose.onNodeWithTag("volume:slider:remote").assertExists()
    }

    @Test
    fun theRingerSaysWhenItIsSilentOrOnVibrate() {
        showHud(VolumeHud(token = 1, main = VolumeRow(VolumeStream.Ring, null, 0, 0, 7, ringerMode = AudioManager.RINGER_MODE_VIBRATE)))
        compose.onNodeWithTag("volume:state:ring").assertExists()
    }

    @Test
    fun theVolumePanelGoesByItselfAfterTheLastKey() {
        val calls = showHud(VolumeHud(token = 1, main = music))
        advance(VOLUME_HUD_MS - 800)
        assertEquals(0, calls.dismissed)
        advance(1_200)
        assertEquals(1, calls.dismissed)
    }

    private fun hudIn(style: ThemeStyle) {
        showHud(VolumeHud(token = 1, main = music, app = "Spotify", others = listOf(ring), expanded = true), style = style)
        compose.onNodeWithTag("volume:hud").assertIsDisplayed()
        compose.onNodeWithTag("volume:title:music").assertIsDisplayed()
        capture("hud_${style.name.lowercase()}")
    }

    @Test
    fun theVolumePanelIsDrawnInTheClassicLook() = hudIn(ThemeStyle.Classic)

    @Test
    fun theVolumePanelIsDrawnInTheGlassLook() = hudIn(ThemeStyle.Glass)

    @Test
    fun theVolumePanelIsDrawnInTheDroidLook() = hudIn(ThemeStyle.Droid)

    // --- the banner of a notification

    private val notification = NotificationEntry("k1", "chat", "Giulia", "Ci vediamo alle otto?", System.currentTimeMillis(), null, true)

    @Test
    fun aBannerSaysWhoWroteAndWhatAndOpensOnATouch() {
        var opened = 0
        var dismissed = 0
        show { ToastBanner(notification, app = null, hideText = false, onOpen = { opened++ }, onDismiss = { dismissed++ }) }
        advance(400)
        capture("banner")
        // The banner is one button for a screen reader: its words are looked at one by one in the tree before they are merged.
        assertEquals("Giulia", textOf("banner:title", merged = false))
        assertEquals("Ci vediamo alle otto?", textOf("banner:text", merged = false))
        compose.onNodeWithTag("banner").performClick()
        assertEquals(1, opened)
        assertEquals(0, dismissed)
    }

    @Test
    fun aBannerCanKeepTheWordsOffTheScreen() {
        show { ToastBanner(notification, app = null, hideText = true, onOpen = {}, onDismiss = {}) }
        advance(400)
        compose.onNodeWithTag("banner:title", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("banner:text", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aBannerGoesAwayByItself() {
        var dismissed = 0
        show { ToastBanner(notification, app = null, hideText = false, onOpen = {}, onDismiss = { dismissed++ }) }
        advance(BANNER_MS - 500)
        assertEquals(0, dismissed)
        // Out of the way (200 ms) and then it asks to be taken off.
        advance(1_500)
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    // --- the lock screen

    private val lockInfo = LockInfo(nowMillis = 1_800_000_000_000L, notifications = 3, battery = BatteryInfo(85, BatteryState.Discharging))

    @Test
    fun theQuickStatusShowsTheAppsWithTheirCounters() {
        val status = LockStatus(listOf(QuickBadge("chat", 3), QuickBadge("mail", 0), QuickBadge("news", 120)))
        show { MetroLockScreen(lockInfo, photo = null, options = LockOptions(showStatus = true), status = status, is24Hour = true) }
        advance(400)
        capture("lock_status")
        compose.onNodeWithTag("lock:status").assertExists()
        assertEquals("3", textOf("lock:badge:chat:count"))
        // An app with nothing to count shows its icon alone; a long count is cut at 99.
        compose.onNodeWithTag("lock:badge:mail").assertExists()
        compose.onAllNodesWithTag("lock:badge:mail:count").assertCountEquals(0)
        assertEquals("99+", textOf("lock:badge:news:count"))
    }

    @Test
    fun theDetailedStatusSaysWhoItIsFromAndWhatItSays() {
        val status = LockStatus(detail = DetailLine("chat", "Anna", "See you at 8"))
        show { MetroLockScreen(lockInfo, photo = null, options = LockOptions(showStatus = true), status = status, is24Hour = true) }
        advance(400)
        capture("lock_detail")
        compose.onNodeWithTag("lock:detail").assertExists()
        compose.onNodeWithText("Anna").assertExists()
        compose.onNodeWithText("See you at 8").assertExists()
    }

    @Test
    fun theDetailedStatusKeepsTheWordsOffWhenTheyAreEmpty() {
        val status = LockStatus(detail = DetailLine("chat", "Anna", ""))
        show { MetroLockScreen(lockInfo, photo = null, options = LockOptions(showStatus = true), status = status, is24Hour = true) }
        advance(400)
        compose.onNodeWithText("Anna").assertExists()
        compose.onAllNodesWithText("See you at 8").assertCountEquals(0)
    }

    @Test
    fun theStatusIsLeftOutWhenTheOptionIsOff() {
        val status = LockStatus(listOf(QuickBadge("chat", 3)), DetailLine("chat", "Anna", "See you at 8"))
        show { MetroLockScreen(lockInfo, photo = null, options = LockOptions(showStatus = false), status = status, is24Hour = true) }
        advance(400)
        compose.onAllNodesWithTag("lock:status").assertCountEquals(0)
    }

    private fun mediaInfo(playing: Boolean): MediaInfo {
        val session = MediaSession(context, "test")
        return MediaInfo("player", "Song title", "The artist", null, playing, MediaController(context, session.sessionToken))
    }

    @Test
    fun theMusicCardShowsTheSongAndItsButtons() {
        show { MetroLockScreen(lockInfo, photo = null, options = LockOptions(showMusic = true), media = mediaInfo(playing = true), is24Hour = true) }
        advance(400)
        capture("lock_music")
        assertEquals("Song title", textOf("lock:music:title"))
        compose.onNodeWithTag("lock:music:previous").assertExists()
        compose.onNodeWithTag("lock:music:pause").assertExists()
        compose.onNodeWithTag("lock:music:next").assertExists()
        compose.onAllNodesWithTag("lock:music:play").assertCountEquals(0)
        // The buttons go to the player; with the player of a test there is nothing to answer, and a touch must not fail.
        compose.onNodeWithTag("lock:music:pause").performClick()
        compose.onNodeWithTag("lock:music:next").performClick()
        compose.onNodeWithTag("lock:music:previous").performClick()
    }

    @Test
    fun aPausedSongOffersPlay() {
        show { MetroLockScreen(lockInfo, photo = null, options = LockOptions(showMusic = true), media = mediaInfo(playing = false), is24Hour = true) }
        advance(400)
        compose.onNodeWithTag("lock:music:play").assertExists()
        compose.onAllNodesWithTag("lock:music:pause").assertCountEquals(0)
    }

    @Test
    fun withNothingPlayingOrTheOptionOffThereIsNoMusicCard() {
        show { MetroLockScreen(lockInfo, photo = null, options = LockOptions(showMusic = true), media = null, is24Hour = true) }
        advance(400)
        compose.onAllNodesWithTag("lock:music").assertCountEquals(0)
    }

    @Test
    fun theMusicCardIsLeftOutWhenTheOptionIsOff() {
        show { MetroLockScreen(lockInfo, photo = null, options = LockOptions(showMusic = false), media = mediaInfo(playing = true), is24Hour = true) }
        advance(400)
        compose.onAllNodesWithTag("lock:music").assertCountEquals(0)
    }
}
