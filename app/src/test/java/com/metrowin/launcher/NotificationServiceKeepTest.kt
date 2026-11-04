package com.metrowin.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.NotificationService
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationListenerService

/** The service end to end: what it does with a notification posted while nothing of the launcher is looking, and what it does when the app takes it back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationServiceKeepTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var service: NotificationService
    private lateinit var shadow: ShadowNotificationListenerService

    @Before
    fun setUp() {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("test", "test", NotificationManager.IMPORTANCE_DEFAULT))
        repeat(10) { NotificationHub.stopWatching() }
        NotificationHub.clearHeld()
        NotificationHub.foregroundPackage = null
        LockAccessibilityService.launcherFront(false)
        service = Robolectric.setupService(NotificationService::class.java)
        shadow = shadowOf(service)
        service.onListenerConnected()
    }

    @After
    fun tearDown() {
        repeat(10) { NotificationHub.stopWatching() }
        service.onListenerDisconnected()
        NotificationHub.clearHeld()
        NotificationHub.foregroundPackage = null
        LockAccessibilityService.launcherFront(false)
    }

    private fun post(id: Int, pkg: String = "com.chat", group: String? = null, summary: Boolean = false): StatusBarNotification {
        val note = Notification.Builder(context, "test").setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("title $id").setContentText("text $id").apply {
                if (group != null) setGroup(group)
                if (summary) setGroupSummary(true)
            }.build()
        shadow.addActiveNotification(pkg, id, note)
        return service.activeNotifications.last().also { service.onNotificationPosted(it) }
    }

    private fun idleFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun aNotificationPostedWhileNothingIsLookingIsKeptAtOnce() {
        val sbn = post(1)
        assertFalse(NotificationHub.watched)
        assertEquals(setOf(sbn.key), NotificationHub.heldKeys())
    }

    @Test
    fun theAppTakingItBackDoesNotTakeItFromTheList() {
        val sbn = post(1)
        service.onNotificationRemoved(sbn, null, NotificationListenerService.REASON_APP_CANCEL)
        service.onNotificationRemoved(sbn, null, NotificationListenerService.REASON_TIMEOUT)
        assertEquals(setOf(sbn.key), NotificationHub.heldKeys())
    }

    @Test
    fun theUserSwipingItAwayInAndroidDoesTakeItAway() {
        val sbn = post(1)
        service.onNotificationRemoved(sbn, null, NotificationListenerService.REASON_CANCEL)
        assertTrue(NotificationHub.heldKeys().isEmpty())
    }

    @Test
    fun theListenerGoingAwayAndComingBackLosesNothing() {
        val sbn = post(1)
        service.onListenerDisconnected()
        service.onListenerConnected()
        assertEquals(setOf(sbn.key), NotificationHub.heldKeys())
    }

    @Test
    fun theAppThatWasInFrontEarlierIsNotInFrontWhileTheLauncherShows() {
        // Telegram was the last app used; the launcher is on screen now. What Telegram posts has not been read, and must not be cleared as if it had.
        NotificationHub.foregroundPackage = "com.chat"
        LockAccessibilityService.launcherFront(true)
        NotificationHub.startWatching()
        val sbn = post(1)
        idleFor(3_000)
        assertTrue(sbn.key in NotificationHub.heldKeys())
        assertEquals(1, NotificationHub.entries.value.size)
    }

    @Test
    fun anAppReallyInFrontStillClearsItsOwnNotifications() {
        NotificationHub.foregroundPackage = "com.chat"
        NotificationHub.startWatching()
        post(1)
        idleFor(3_000)
        assertTrue(NotificationHub.entries.value.isEmpty())
    }

    @Test
    fun aGroupHeadingThatCameAloneIsKeptAndOneWithChildrenIsNotShownTwice() {
        post(1, group = "g", summary = true)
        NotificationHub.startWatching()
        idleFor(1_000)
        assertEquals(1, NotificationHub.entries.value.size)
        post(2, group = "g")
        idleFor(1_000)
        assertEquals(listOf("title 2"), NotificationHub.entries.value.map { it.title })
    }
}
