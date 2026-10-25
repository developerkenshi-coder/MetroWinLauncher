package com.metrowin.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.DeferWhileAway
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.NotificationPacer
import com.metrowin.launcher.notifications.NotificationPacer.Step
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

/**
 * What the launcher does while it is not on screen: nothing. The notification service and the list of apps are the two
 * things that Android wakes all day (an app posts a notification, an app is updated), and neither may read anything then.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundWorkTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // --- the pacer, without Android

    @Test
    fun nothingIsReadWhileNoScreenIsOnHoweverManyThingsChange() {
        val pacer = NotificationPacer(minGapMs = 600)
        repeat(1_000) { assertEquals(Step.Idle, pacer.changed(now = it * 10L, watched = false)) }
        assertTrue(pacer.stale)
        assertEquals("one read when a screen comes back", Step.Now, pacer.watched())
        pacer.read(20_000)
        assertFalse(pacer.stale)
        assertEquals("nothing changed since: nothing to read", Step.Idle, pacer.watched())
    }

    @Test
    fun aBurstWhileLookingIsOneReadAndTheNextIsNotTooSoon() {
        val pacer = NotificationPacer(minGapMs = 600)
        assertEquals("the first change is read at once", Step.Now, pacer.changed(now = 1_000, watched = true))
        pacer.read(1_000)
        assertEquals("the next one waits for the gap to pass", Step.After(500), pacer.changed(now = 1_100, watched = true))
        assertEquals("and the ones after it are covered by that read", Step.Idle, pacer.changed(now = 1_200, watched = true))
        assertEquals(Step.Idle, pacer.changed(now = 1_300, watched = true))
        pacer.read(1_600)
        assertEquals("a change after a quiet while is read at once", Step.Now, pacer.changed(now = 5_000, watched = true))
    }

    @Test
    fun aReadThatWasWaitingIsDroppedWhenTheScreenGoesOff() {
        val pacer = NotificationPacer(minGapMs = 600)
        pacer.read(1_000)
        assertEquals(Step.After(400), pacer.changed(now = 1_200, watched = true))
        pacer.away()
        assertEquals("a new wait can be started", Step.After(400), pacer.changed(now = 1_200, watched = true))
    }

    @Test
    fun workAskedForWhileAwayIsDoneOnceOnReturn() {
        val gate = DeferWhileAway()
        assertTrue("on screen it is done now", gate.request(onScreen = true))
        assertFalse(gate.takePending())
        repeat(50) { assertFalse(gate.request(onScreen = false)) }
        assertTrue("once, for all of them", gate.takePending())
        assertFalse(gate.takePending())
    }

    // --- the service, on a JVM

    private lateinit var service: NotificationService
    private lateinit var shadow: ShadowNotificationListenerService

    @Before
    fun setUp() {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("test", "test", NotificationManager.IMPORTANCE_DEFAULT))
        repeat(10) { NotificationHub.stopWatching() }
        NotificationHub.update(emptyList())
        service = Robolectric.setupService(NotificationService::class.java)
        shadow = shadowOf(service)
    }

    @After
    fun tearDown() {
        repeat(10) { NotificationHub.stopWatching() }
        service.onListenerDisconnected()
        NotificationHub.update(emptyList())
    }

    private fun post(id: Int) {
        val note = Notification.Builder(context, "test").setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("title $id").setContentText("text $id").build()
        shadow.addActiveNotification("com.chat", id, note)
        service.onNotificationPosted(service.activeNotifications.last())
    }

    private fun idleFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun theServiceReadsNothingWhileTheLauncherIsAway() {
        service.onListenerConnected()
        repeat(40) { post(it) }
        idleFor(5_000)
        assertTrue("forty notifications later it has still read none", NotificationHub.entries.value.isEmpty())

        NotificationHub.startWatching()
        assertEquals("they are all there when the launcher is looked at", 40, NotificationHub.entries.value.size)
    }

    @Test
    fun aBurstOfNotificationsWhileLookingIsReadOnceAfterTheGap() {
        service.onListenerConnected()
        NotificationHub.startWatching()
        post(1)
        post(2)
        post(3)
        assertTrue("the first is read at once, the others wait", NotificationHub.entries.value.size <= 1)
        idleFor(NotificationPacer.MIN_GAP_MS + 100)
        assertEquals(3, NotificationHub.entries.value.size)
    }

    @Test
    fun leavingStopsTheReadThatWasWaiting() {
        service.onListenerConnected()
        NotificationHub.startWatching()
        post(1)
        post(2)
        NotificationHub.stopWatching()
        val before = NotificationHub.entries.value
        idleFor(5_000)
        assertEquals("the read that was waiting did not happen", before, NotificationHub.entries.value)
        assertFalse(NotificationHub.watched)

        NotificationHub.startWatching()
        assertEquals("it is read when the launcher is back", 2, NotificationHub.entries.value.size)
    }

    @Test
    fun theScreensAreCountedSoTheLastOneToLeaveSilencesTheService() {
        NotificationHub.startWatching()
        NotificationHub.startWatching()
        NotificationHub.stopWatching()
        assertTrue("one screen is still on", NotificationHub.watched)
        NotificationHub.stopWatching()
        assertFalse(NotificationHub.watched)
        NotificationHub.stopWatching()
        assertFalse("an extra stop does not go below zero", NotificationHub.watched)
        NotificationHub.startWatching()
        assertTrue(NotificationHub.watched)
    }
}
