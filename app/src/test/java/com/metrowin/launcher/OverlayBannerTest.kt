package com.metrowin.launcher

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.SettingsCodec
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.NotificationControl
import com.metrowin.launcher.notifications.NotificationEntry
import com.metrowin.launcher.notifications.OverlayActionCenter
import com.metrowin.launcher.notifications.OverlayBanner
import com.metrowin.launcher.notifications.OverlayRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OverlayBannerTest {
    private val on = LauncherSettings(overlayBanner = true)

    private fun shows(
        settings: LauncherSettings = on,
        packageName: String = "chat",
        canDraw: Boolean = true,
        launcherOnScreen: Boolean = false,
        interactive: Boolean = true,
        locked: Boolean = false,
        importance: Int = NotificationManager.IMPORTANCE_HIGH,
        passesDoNotDisturb: Boolean = true,
    ) = OverlayRules.shows(settings, packageName, canDraw, launcherOnScreen, interactive, locked, importance, passesDoNotDisturb)

    @Test
    fun itIsOffUntilTheUserTurnsItOn() {
        assertFalse(LauncherSettings().overlayBanner)
        assertFalse(shows(settings = LauncherSettings()))
        assertTrue(shows())
    }

    @Test
    fun itNeedsTheAndroidPermissionAndAScreenThatIsOn() {
        assertFalse(shows(canDraw = false))
        assertFalse(shows(interactive = false))
    }

    @Test
    fun theLauncherHasItsOwnBannerWhenItIsOnScreen() {
        assertFalse(shows(launcherOnScreen = true))
    }

    @Test
    fun nothingShowsOverTheLockScreen() {
        assertFalse(shows(locked = true))
    }

    @Test
    fun doNotDisturbAndSilentNotificationsAreRespected() {
        assertFalse(shows(passesDoNotDisturb = false))
        assertFalse(shows(importance = NotificationManager.IMPORTANCE_LOW))
        assertFalse(shows(importance = NotificationManager.IMPORTANCE_MIN))
        assertTrue(shows(importance = NotificationManager.IMPORTANCE_DEFAULT))
    }

    @Test
    fun theBannerSwitchOfAnAppDecidesForBothBanners() {
        val s = on.copy(notifNoBanner = setOf("news"))
        assertFalse(shows(settings = s, packageName = "news"))
        assertTrue(shows(settings = s, packageName = "chat"))
        assertFalse(NotificationControl.showsBanner("news", s))
        // The banner of the launcher has its own switch, and the one over other apps follows it.
        assertFalse(shows(settings = on.copy(toastBanner = false)))
        // The choices of the apps count only while their master switch is on.
        assertTrue(shows(settings = s.copy(notifControl = false), packageName = "news"))
    }

    @Test
    fun theSwitchIsKeptInTheSettingsAndTheStoreReadsItWithoutDecodingAll() {
        assertTrue(SettingsCodec.decode(SettingsCodec.encode(on))!!.overlayBanner)
        assertFalse(SettingsCodec.decode(SettingsCodec.encode(LauncherSettings()))!!.overlayBanner)
        val store = SettingsStore(ApplicationProvider.getApplicationContext<Context>())
        assertFalse(store.overlayBannerOn())
        store.save(on)
        assertTrue(store.overlayBannerOn())
        assertEquals(on.overlayBanner, store.load().overlayBanner)
        store.save(LauncherSettings())
        assertFalse(store.overlayBannerOn())
    }

    @Test
    fun theWindowOfTheBannerIsAddedOverTheAppsAndTakenAway() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val banner = OverlayBanner(context)
        val entry = NotificationEntry("k", "chat", "Anna", "Ciao", System.currentTimeMillis(), null, false)
        val windows = context.getSystemService(WindowManager::class.java)
        val views = { Shadow.extract<ShadowWindowManagerImpl>(windows).getViews() }
        banner.show(entry, on)
        ShadowLooper.idleMainLooper()
        assertEquals(1, views().size)
        assertTrue(views().single() is ComposeView)
        // A newer one replaces the one that is up.
        banner.show(entry.copy(key = "k2"), on)
        assertEquals(1, views().size)
        banner.hide()
        assertEquals(0, views().size)
    }

    @Test
    fun theEdgeIsOffByDefaultAndKeptInTheSettings() {
        assertFalse(LauncherSettings().edgeActionCenter)
        assertTrue(SettingsCodec.decode(SettingsCodec.encode(LauncherSettings(edgeActionCenter = true)))!!.edgeActionCenter)
        val store = SettingsStore(ApplicationProvider.getApplicationContext<Context>())
        assertFalse(store.edgeActionCenterOn())
        store.save(LauncherSettings(edgeActionCenter = true))
        assertTrue(store.edgeActionCenterOn())
        // The two switches are read apart.
        assertFalse(store.overlayBannerOn())
    }

    @Test
    fun onlyASwipeDownOnTheStripOpensThePanelAndItGoesAway() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val windows = context.getSystemService(WindowManager::class.java)
        val views = { Shadow.extract<ShadowWindowManagerImpl>(windows).getViews() }
        lateinit var edge: OverlayActionCenter
        // What the service does when the panel is gone: the strip comes back while it is wanted.
        var wanted = true
        edge = OverlayActionCenter(context, { context }) { if (wanted) edge.showStrip() }
        edge.showStrip()
        edge.showStrip()
        assertEquals("one strip, however often it is asked for", 1, views().size)
        val strip = views().single()
        fun touch(action: Int, y: Float) {
            val now = SystemClock.uptimeMillis()
            strip.dispatchTouchEvent(MotionEvent.obtain(now, now, action, 5f, y, 0))
        }
        // A touch, or a short move, does nothing.
        touch(MotionEvent.ACTION_DOWN, 2f)
        touch(MotionEvent.ACTION_MOVE, 4f)
        touch(MotionEvent.ACTION_UP, 4f)
        assertEquals(1, views().size)
        // A swipe down opens the panel, in a window of its own.
        touch(MotionEvent.ACTION_DOWN, 2f)
        touch(MotionEvent.ACTION_MOVE, 400f)
        assertEquals(2, views().size)
        // It closes, and its window goes (the timer takes it away if the panel itself cannot).
        edge.close()
        ShadowLooper.idleMainLooper(3, TimeUnit.SECONDS)
        assertEquals("the strip is back", 1, views().size)
        wanted = false
        edge.dispose()
        ShadowLooper.idleMainLooper()
        assertEquals(0, views().size)
        wanted = true
        // The service that owns the windows went away with a panel up (the system took the windows): nothing is left of it, and the strip can come back.
        edge.showStrip()
        val again = views().single()
        again.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 5f, 2f, 0))
        again.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 5f, 400f, 0))
        wanted = false
        edge.dispose()
        ShadowLooper.idleMainLooper()
        assertEquals(0, views().size)
        wanted = true
        edge.showStrip()
        assertEquals("the strip comes back after a panel was thrown away", 1, views().size)
        wanted = false
        edge.dispose()
    }
}
