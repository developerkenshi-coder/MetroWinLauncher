package com.metrowin.launcher

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.QuickActionsController
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.Snooze
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** Quiet hours on the spot, snoozing a notification, the settings of its app, and the strength of the flashlight. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionCenterMoreTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val rome = ZoneId.of("Europe/Rome")
    private fun at(iso: String) = Instant.parse(iso)

    // --- snoozing

    @Test
    fun theShortSnoozesAreAFixedTime() {
        val now = at("2026-10-25T10:00:00Z")
        assertEquals(15 * 60_000L, Snooze.millis(Snooze.Option.QuarterHour, now, rome))
        assertEquals(60 * 60_000L, Snooze.millis(Snooze.Option.Hour, now, rome))
        assertEquals(180 * 60_000L, Snooze.millis(Snooze.Option.ThreeHours, now, rome))
    }

    @Test
    fun theMorningIsTheNextEightOClock() {
        val hour = 3_600_000L
        // 07:00 in Rome (UTC+1 on 15 January): an hour to go.
        assertEquals(hour, Snooze.millis(Snooze.Option.Morning, at("2026-01-15T06:00:00Z"), rome))
        // 08:00 sharp: that one is now, the next is tomorrow's.
        assertEquals(24 * hour, Snooze.millis(Snooze.Option.Morning, at("2026-01-15T07:00:00Z"), rome))
        // 23:00: nine hours.
        assertEquals(9 * hour, Snooze.millis(Snooze.Option.Morning, at("2026-01-15T22:00:00Z"), rome))
        // 02:00: six hours, the same day.
        assertEquals(6 * hour, Snooze.millis(Snooze.Option.Morning, at("2026-01-15T01:00:00Z"), rome))
    }

    @Test
    fun theMorningFollowsTheClockWhenItChanges() {
        val hour = 3_600_000L
        // 23:00 on the night the clocks go forward (29 March 2026): the hour that is lost makes it eight hours, not nine.
        assertEquals(8 * hour, Snooze.millis(Snooze.Option.Morning, at("2026-03-28T22:00:00Z"), rome))
        // And the night they go back (25 October 2026): ten hours.
        assertEquals(10 * hour, Snooze.millis(Snooze.Option.Morning, at("2026-10-24T21:00:00Z"), rome))
    }

    @Test
    fun everyOptionHasALabelAndTheyAreDistinct() {
        assertEquals(4, Snooze.Option.entries.size)
        assertEquals(4, Snooze.Option.entries.map { it.label }.toSet().size)
    }

    @Test
    fun snoozingNeedsTheNotificationService() {
        // The service is not running on the test phone: nothing to snooze with.
        assertFalse(NotificationHub.snooze("key", 60_000L))
    }

    // --- quiet hours

    @Test
    fun quietHoursSwitchOnTheSpotWithTheAccess() {
        val manager = app.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        val controller = QuickActionsController(app)
        assertEquals(false, controller.isOn(QuickAction.QuietHours))
        assertTrue("done on the spot", controller.activate(QuickAction.QuietHours))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, manager.currentInterruptionFilter)
        assertEquals(true, controller.isOn(QuickAction.QuietHours))
        assertTrue(controller.activate(QuickAction.QuietHours))
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
        assertEquals(false, controller.isOn(QuickAction.QuietHours))
    }

    @Test
    fun withoutTheAccessQuietHoursOpenTheScreenThatGivesIt() {
        val manager = app.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationPolicyAccessGranted(false)
        val controller = QuickActionsController(app)
        assertFalse(controller.activate(QuickAction.QuietHours))
        assertEquals(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, shadowOf(app).nextStartedActivity.action)
        assertEquals("nothing was switched", NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
    }

    @Test
    fun theOtherButtonsStillOpenTheirScreenAndAnswerFalse() {
        val controller = QuickActionsController(app)
        assertFalse(controller.activate(QuickAction.Wifi))
        assertEquals(Settings.ACTION_WIFI_SETTINGS, shadowOf(app).nextStartedActivity.action)
        assertFalse(controller.activate(QuickAction.LauncherSettings))
        assertFalse(controller.activate(QuickAction.Recents))
    }

    @Test
    fun theViewModelAsksForTheAccessOnlyWhenItIsMissing() {
        val vm = LauncherViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        val manager = app.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        assertTrue(vm.activateQuickAction(QuickAction.QuietHours))
        shadowOf(manager).setNotificationPolicyAccessGranted(false)
        assertFalse(vm.activateQuickAction(QuickAction.Wifi))
    }

    // --- the flashlight

    @Test
    fun aPhoneWithoutAFlashlightHasNoStrengthToSet() {
        val controller = QuickActionsController(app)
        assertNull(controller.torchStrength())
        controller.setTorchStrength(0.5f) // does nothing, and does not fail
        val vm = LauncherViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(vm.torchStrength())
        vm.setTorchStrength(0.3f)
        assertEquals(0.3f, vm.controls.value.torch!!, 0f)
        vm.refreshControls()
        assertNull(vm.controls.value.torch)
    }

    // --- the settings of an app's notifications

    @Test
    fun theNotificationSettingsOfTheAppOpen() {
        val vm = LauncherViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        val entry = NotificationEntry("k", "com.example.chat", "Anna", "hi", 1L, null, false)
        vm.openAppNotificationSettings(entry)
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals("com.example.chat", intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun snoozingWithoutTheServiceSaysItFailedAndDoesNotCrash() {
        val vm = LauncherViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()
        vm.snoozeNotification(NotificationEntry("k", "p", "t", "x", 1L, null, false), Snooze.Option.Hour)
    }
}
