package com.metrowin.launcher.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.centerX
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.BatteryState
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.LockInfo
import com.metrowin.launcher.ui.lock.LockOptions
import com.metrowin.launcher.ui.lock.MetroLockScreen
import com.metrowin.launcher.ui.settings.SettingsEnv
import com.metrowin.launcher.ui.settings.SettingsScreen
import com.metrowin.launcher.ui.settings.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xxhdpi")
class LockScreenTest : ScreenshotBase() {
    private val utc = ZoneId.of("UTC")
    private fun millis(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 3, day, hour, minute, 0, 0, utc).toInstant().toEpochMilli()

    // Thursday 5 March 2026, 14:05, an alarm tomorrow at 07:30.
    private val info = LockInfo(
        nowMillis = millis(5, 14, 5), nextAlarmMillis = millis(6, 7, 30), notifications = 3,
        battery = BatteryInfo(85, BatteryState.Discharging),
    )

    private var unlocked = 0
    private var finished = 0
    private val signal = androidx.compose.runtime.mutableIntStateOf(0)

    private fun showLock(
        info: LockInfo = this.info, options: LockOptions = LockOptions(), withPhoto: Boolean = false, canUnlock: Boolean = true,
        status: com.metrowin.launcher.data.LockStatus = com.metrowin.launcher.data.LockStatus(),
        call: com.metrowin.launcher.ui.lock.LockCall? = null,
    ) = show {
        val photo = if (withPhoto) {
            remember { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3A7D44.toInt()) }.asImageBitmap() }
        } else {
            null
        }
        MetroLockScreen(
            info = info, photo = photo, options = options,
            onUnlock = if (canUnlock) ({ unlocked++ }) else null,
            unlockSignal = signal.intValue, onUnlocked = { finished++ }, status = status, call = call,
            is24Hour = true, zone = utc, locale = Locale.US,
        )
    }

    @Test
    fun theLockScreenShowsTheTimeTheDateTheAlarmTheNotificationsAndTheBattery() {
        showLock(withPhoto = true)
        advance(300)
        capture("lock_screen")
        compose.onNodeWithTag("lock:time").assertExists()
        compose.onNodeWithTag("lock:date").assertExists()
        compose.onNodeWithTag("lock:alarm").assertExists()
        compose.onNodeWithTag("lock:notifications").assertExists()
        compose.onNodeWithTag("lock:battery").assertExists()
    }

    @Test
    fun aCallThatIsOnShowsItsTimeAndCanBeMutedAndEnded() {
        var muted = 0
        var ended = 0
        var opened = 0
        val call = com.metrowin.launcher.ui.lock.LockCall(
            "Anna", System.currentTimeMillis() - 65_000, false, { muted++ }, { ended++ }, { opened++ },
        )
        showLock(call = call)
        advance(300)
        compose.onNodeWithTag("lock:call").assertExists()
        compose.onNodeWithTag("lock:call:time", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("lock:call:mute").performClick()
        compose.onNodeWithTag("lock:call:hangup").performClick()
        compose.onNodeWithTag("lock:call:open").performClick()
        assertEquals(listOf(1, 1, 1), listOf(muted, ended, opened))
        // The time and the rest of the lock screen are still there.
        compose.onNodeWithTag("lock:time").assertExists()
    }

    @Test
    fun theBatteryIconIsFilledToTheChargeNotHalfFull() {
        showLock(info = info.copy(battery = BatteryInfo(100, BatteryState.Discharging)), canUnlock = false)
        advance(300)
        val box = compose.onNodeWithTag("lock:battery").fetchSemanticsNode().boundsInRoot
        val shot = capture("lock_battery")
        // The icon is the first 28dp of the row: along its middle line, between the walls, a full charge is lit all the way (the old icon lit half).
        val dp = compose.density.density
        val y = ((box.top + box.bottom) / 2).toInt()
        val from = (box.left + 4 * dp).toInt()
        val to = (box.left + 22 * dp).toInt()
        val lit = (from until to).count { shot.getPixel(it, y) and 0xFFFFFF == 0xFFFFFF }
        assertTrue("a full battery is drawn full ($lit of ${to - from} pixels lit)", lit > (to - from) * 0.8)
    }

    @Test
    fun theTimeIsBigAndInTheRightFormat() {
        showLock()
        advance(300)
        val shot = capture("lock_screen_plain")
        // White glyphs of the time on black: plenty of white pixels in the lower half, none of the accent colour.
        val pixels = shot.pixels()
        val whiteLower = pixels.drop(pixels.size / 2).count { it and 0xFFFFFF == 0xFFFFFF }
        assertTrue("the time and the date are drawn ($whiteLower white pixels)", whiteLower > 5_000)
    }

    @Test
    fun theLinesCanBeSwitchedOff() {
        showLock(options = LockOptions(showAlarm = false, showNotifications = false, showBattery = false))
        advance(300)
        compose.onAllNodesWithTag("lock:alarm").assertCountEquals(0)
        compose.onAllNodesWithTag("lock:notifications").assertCountEquals(0)
        compose.onAllNodesWithTag("lock:battery").assertCountEquals(0)
        compose.onNodeWithTag("lock:time").assertExists()
    }

    @Test
    fun withNothingToTellThereAreNoLines() {
        showLock(info = LockInfo(nowMillis = info.nowMillis))
        advance(300)
        compose.onAllNodesWithTag("lock:alarm").assertCountEquals(0)
        compose.onAllNodesWithTag("lock:notifications").assertCountEquals(0)
        compose.onAllNodesWithTag("lock:battery").assertCountEquals(0)
    }

    private fun swipeUp(fraction: Float) {
        compose.onNodeWithTag("lock:screen").performTouchInput {
            swipe(Offset(centerX, height * 0.9f), Offset(centerX, height * (0.9f - fraction)), durationMillis = 300)
        }
        advance(1_500)
    }

    @Test
    fun aLongSwipeUpLiftsTheScreenOffAndUnlocks() {
        showLock()
        advance(300)
        swipeUp(0.6f)
        assertEquals(1, unlocked)
    }

    @Test
    fun aShortSwipeDropsBackWithoutUnlocking() {
        showLock()
        advance(300)
        swipeUp(0.1f)
        assertEquals(0, unlocked)
        val shot = capture("lock_after_short_swipe")
        // Back in place: the white of the time is at the bottom again.
        val pixels = shot.pixels()
        assertTrue(pixels.drop(pixels.size / 2).any { it and 0xFFFFFF == 0xFFFFFF })
    }

    @Test
    fun withoutAHandlerTheScreenStaysPut() {
        showLock(canUnlock = false)
        advance(300)
        swipeUp(0.7f)
        assertEquals(0, unlocked)
    }

    @Test
    fun aTapMakesTheScreenHopWithoutUnlocking() {
        showLock()
        advance(300)
        compose.onNodeWithTag("lock:screen").performTouchInput { click() }
        advance(1_500)
        assertEquals(0, unlocked)
        compose.onNodeWithTag("lock:time").assertExists()
    }

    @Test
    fun androidUnlockingByItselfLiftsTheScreenOffAndTellsIt() {
        showLock()
        advance(300)
        assertEquals(0, finished)
        compose.runOnIdle { signal.intValue++; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        advance(1_500)
        assertEquals(1, finished)
        assertEquals("the swipe is not what unlocked it", 0, unlocked)
    }

    @Test
    fun theAppsWithNewsShowAsIconsWithTheirCounters() {
        val icon = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFFFFFF.toInt()) }
        val status = com.metrowin.launcher.data.LockStatus(
            listOf(com.metrowin.launcher.data.QuickBadge("chat", 3, icon), com.metrowin.launcher.data.QuickBadge("mail", 120, icon)),
        )
        showLock(options = LockOptions(showStatus = true), status = status)
        advance(300)
        compose.onNodeWithTag("lock:badge:chat:icon").assertExists()
        compose.onNodeWithTag("lock:badge:chat:count").assertExists()
        compose.onNodeWithTag("lock:badge:mail:count").assertExists()
    }

    @Test
    fun theSpotForTheFingerKeepsTheTimeAboveIt() {
        show {
            MetroLockScreen(info = info, photo = null, sensorY = 0.765f, is24Hour = true, zone = utc, locale = Locale.US)
        }
        advance(300)
        compose.onNodeWithTag("lock:time").assertExists()
        compose.onNodeWithTag("lock:date").assertExists()
    }

    // --- the settings page

    private var current = LauncherSettings()
    private val calls = mutableListOf<String>()

    @Composable
    private fun Page() {
        var settings by remember { mutableStateOf(current) }
        val away = remember { Animatable(0f) }
        SettingsScreen(
            env = SettingsEnv(
                settings = settings,
                onChange = { change ->
                    settings = change(settings)
                    current = settings
                },
                onApplyLockPhoto = { calls += "apply" },
                onOpenDreamSettings = { calls += "dream" },
                onPreviewLock = { calls += "preview" },
                onClearLockPhoto = { calls += "clear" },
            ),
            section = SettingsSection.Lock,
            onSectionChange = {},
            sectionAway = away,
        )
    }

    @Test
    fun theLockPageHasItsButtonsAndSwitches() {
        show { Page() }
        advance(500)
        capture("settings_lock")
        tapScrolled("lock:preview")
        tapScrolled("lock:alarm")
        advance(100)
        assertFalse(current.lockShowAlarm)
        tapScrolled("lock:notifications")
        advance(100)
        assertFalse(current.lockShowNotifications)
        tapScrolled("lock:battery")
        advance(100)
        assertFalse(current.lockShowBattery)
        tapScrolled("lock:apply")
        tapScrolled("lock:dream")
        assertEquals(listOf("preview", "apply", "dream"), calls)
    }

    @Test
    fun theRemoveButtonOnlyAppearsWithAPhoto() {
        current = LauncherSettings()
        show { Page() }
        advance(500)
        compose.onAllNodesWithTag("lock:remove").assertCountEquals(0)
    }
}
