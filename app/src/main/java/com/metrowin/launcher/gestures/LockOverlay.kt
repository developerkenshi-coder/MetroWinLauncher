package com.metrowin.launcher.gestures

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import com.metrowin.launcher.ui.lock.SENSOR_R
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableIntState
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.metrowin.launcher.calls.CallManager
import com.metrowin.launcher.calls.InCallActivity
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.OverlayWindowOwner
import com.metrowin.launcher.notifications.overBars
import com.metrowin.launcher.notifications.rememberMediaInfo
import com.metrowin.launcher.ui.lock.LockCall
import com.metrowin.launcher.ui.lock.MetroLockScreen
import com.metrowin.launcher.ui.lock.hiddenOnLock
import com.metrowin.launcher.ui.lock.lockOptions
import com.metrowin.launcher.ui.lock.rememberLockInfo
import com.metrowin.launcher.ui.lock.rememberLockStatus
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.MetroTheme
import java.io.File

/**
 * The Metro lock screen over the lock screen of Android, which a launcher cannot replace: when the screen goes off, a window of the
 * accessibility service with the lock screen of MetroWin (the photo, the big time, the alarm, the notifications) is made ready, so
 * that it is there when the screen comes on. Swiping it up takes it away and leaves the lock screen of Android (with its PIN, if there
 * is one) under it. A call that is on shows in it as a card (the time, mute, end call) when the launcher is the phone app (see CallManager);
 * a call coming in takes it away at once, as the screen of the call must be reachable.
 */
internal class LockOverlay(private val service: AccessibilityService) {
    private class Shown(val view: ComposeView, val owner: OverlayWindowOwner, val windows: WindowManager, val touch: List<View>, val unlock: MutableIntState) {
        var lifting = false
    }

    private var shown: Shown? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(keyguardCheck)
                    show()
                }
                // A call coming in, or an alarm going off, wakes the screen: the lock screen would be over it.
                Intent.ACTION_SCREEN_ON -> if (!quiet()) hide() else watchKeyguard()
                Intent.ACTION_USER_PRESENT -> liftAndHide()
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(service, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun stop() {
        if (registered) runCatching { service.unregisterReceiver(receiver) }
        registered = false
        hide()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var unlockedChecks = 0

    /**
     * The lock screen of Android is there while the phone is locked. A phone that unlocks another way (a fingerprint read before the screen
     * came on, a trusted place or device, a lock that waits a minute) sends no "user present" while ours is up, so ours is taken away when
     * Android says it is not locked. Several times in a row (a third of a second), as the lock of Android can lag behind the screen by a moment.
     */
    private val keyguardCheck = object : Runnable {
        override fun run() {
            if (shown == null) return
            val locked = service.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != false
            unlockedChecks = if (locked) 0 else unlockedChecks + 1
            if (unlockedChecks >= UNLOCKED_CHECKS) liftAndHide() else handler.postDelayed(this, CHECK_MS)
        }
    }

    private fun watchKeyguard() {
        if (shown == null) return
        handler.removeCallbacks(keyguardCheck)
        unlockedChecks = 0
        keyguardCheck.run()
    }

    private val hideLater = Runnable { hide() }

    /**
     * Android took its own lock away (a finger was read): the Metro screen is lifted off like a swipe does it, and goes. [hideLater] is
     * for a screen whose animation does not run.
     */
    private fun liftAndHide() {
        val current = shown ?: return
        if (current.lifting) return
        current.lifting = true
        handler.removeCallbacks(keyguardCheck)
        current.unlock.intValue++
        handler.postDelayed(hideLater, LIFT_MS)
    }

    /** No call is ringing, and any that is on is one the launcher handles (the card of the lock screen). Else the phone is in a call of another app: the lock screen stays away. */
    private fun quiet(): Boolean {
        val lines = CallManager.lines.value.filterNot { it.ended }
        if (lines.any { it.ringing }) return false
        return lines.isNotEmpty() || service.getSystemService(AudioManager::class.java)?.mode == AudioManager.MODE_NORMAL
    }

    /** The card of the lock screen opens the screen of the call, which shows over Android's lock as the screen of any phone app does. */
    private fun openCall() {
        runCatching { service.startActivity(Intent(service, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        hide()
    }

    private fun show() {
        if (shown != null || !quiet() || !SettingsStore(service).lockOverlayOn()) return
        val settings = SettingsStore(service).load()
        val photo = loadPhoto(settings.hasLockPhoto, settings.hasStartBackground)
        val owner = OverlayWindowOwner()
        val windows = service.getSystemService(WindowManager::class.java)
        val screen = screenSize(windows)
        // A sensor under the screen reads the finger through the lock screen of Android: a window of ours that takes the touch, or covers the
        // light the sensor needs, stops it. So, upright and with the sensor there, the lock screen has a spot where the finger goes: a hole
        // in the picture (Android's icon shows in it) and no window that takes touches below it. Sideways, or with a sensor on the side or
        // the back, there is no spot.
        val upright = screen.height > screen.width
        val sensorShare = settings.lockSensorY.takeIf { upright && settings.lockSensorUnderScreen }
        val sensorY = screen.height * (sensorShare ?: 0f)
        val sensorR = screen.width * SENSOR_R
        val unlock = mutableIntStateOf(0)
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                MetroTheme(accent = MetroAccents[settings.accentIndex], dark = true) {
                    val lines by CallManager.lines.collectAsState()
                    val audio by CallManager.audio.collectAsState()
                    val line = lines.firstOrNull { !it.ended && !it.ringing }
                    // A call that comes in while this is up: it goes, the answer buttons are on the screen of the call.
                    if (lines.any { it.ringing && !it.ended }) LaunchedEffect(Unit) { handler.post { hide() } }
                    MetroLockScreen(
                        info = rememberLockInfo(settings.hiddenOnLock()),
                        photo = photo,
                        options = settings.lockOptions(),
                        onUnlock = ::unlock,
                        fingerprint = fingerprintEnrolled(),
                        sensorY = sensorShare,
                        ownIcon = settings.lockSensorOwnIcon,
                        unlockSignal = unlock.intValue,
                        onUnlocked = ::hide,
                        status = rememberLockStatus(settings),
                        media = rememberMediaInfo(settings.lockShowMusic),
                        call = line?.let { LockCall(it.title, it.connectedAt, audio.muted, { CallManager.mute(!audio.muted) }, { CallManager.hangUp(it.id) }, ::openCall) },
                    )
                }
            }
        }
        val touchable = if (sensorShare != null) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or touchable,
            // Translucent, not opaque: the screen is lifted off, and what it uncovers is the lock screen of Android, not leftovers of the window.
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }.overBars()
        owner.resume()
        if (runCatching { windows.addView(view, params) }.isFailure) {
            owner.destroy()
            return
        }
        // The swipe is taken above the spot and below it; only the band of the spot goes to Android, whose lock screen reads the finger. A
        // swipe that began under the spot, left to Android, would open its keypad instead of lifting this screen.
        val touch = mutableListOf<View>()
        if (sensorShare != null) {
            val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            val aboveEnd = (sensorY - TOUCH_GAP * sensorR).toInt()
            val belowStart = (sensorY + TOUCH_GAP * sensorR).toInt().coerceAtMost(screen.height)
            for ((top, bottom) in listOf(0 to aboveEnd, belowStart to screen.height)) {
                if (bottom - top <= 0) continue
                val catcher = SwipeCatcher(service, view, top.toFloat())
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, bottom - top, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSPARENT,
                ).apply { gravity = Gravity.TOP; y = top }.overBars()
                if (runCatching { windows.addView(catcher, params) }.isSuccess) touch += catcher
            }
        }
        shown = Shown(view, owner, windows, touch, unlock)
        // The notification service reads the notifications only while a screen shows them.
        NotificationHub.startWatching()
    }

    private fun screenSize(windows: WindowManager): Size =
        if (Build.VERSION.SDK_INT >= 30) {
            windows.currentWindowMetrics.bounds.let { Size(it.width(), it.height()) }
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windows.defaultDisplay.getRealMetrics(metrics)
            Size(metrics.widthPixels, metrics.heightPixels)
        }

    /** Swiped up: Android is asked to take its own lock screen away (that is where the PIN or the fingerprint is read), then ours goes. */
    private fun unlock() {
        runCatching {
            service.startActivity(Intent(service, UnlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        hide()
    }

    /** The phone has a fingerprint (or other biometric) saved: the lock screen says it can be used. */
    @Suppress("DEPRECATION")
    private fun fingerprintEnrolled(): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            service.getSystemService(android.hardware.biometrics.BiometricManager::class.java).canAuthenticate() ==
                android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
        } else {
            service.getSystemService(android.hardware.fingerprint.FingerprintManager::class.java)?.hasEnrolledFingerprints() == true
        }
    }.getOrDefault(false)

    fun hide() {
        handler.removeCallbacks(keyguardCheck)
        handler.removeCallbacks(hideLater)
        val current = shown ?: return
        shown = null
        current.touch.forEach { touch -> runCatching { current.windows.removeViewImmediate(touch) } }
        runCatching { current.windows.removeViewImmediate(current.view) }
        current.owner.destroy()
        NotificationHub.stopWatching()
    }

    private fun loadPhoto(hasLock: Boolean, hasStart: Boolean): ImageBitmap? {
        val names = listOfNotNull("lock_background.jpg".takeIf { hasLock }, "start_background.jpg".takeIf { hasStart })
        return names.firstNotNullOfOrNull { name ->
            runCatching { BitmapFactory.decodeFile(File(service.filesDir, name).path)?.asImageBitmap() }.getOrNull()
        }
    }

    /** Takes the touches of a part of the screen away from the spot and hands them to the lock screen, which is in a window that takes none. [top] is where the part begins. */
    @SuppressLint("ClickableViewAccessibility", "ViewConstructor")
    private class SwipeCatcher(context: Context, private val target: View, private val top: Float) : View(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            // The lock screen's window covers all the screen: the touch is moved to where it is in it.
            target.dispatchTouchEvent(MotionEvent.obtain(event).apply { offsetLocation(0f, top) })
            return true
        }
    }

    private companion object {
        /** The touch of the swipe stops this many radii above the spot. */
        const val TOUCH_GAP = 2.4f

        /** How often, and how many times in a row, Android must say it is not locked for the lock screen of ours to go. */
        const val CHECK_MS = 80L
        const val UNLOCKED_CHECKS = 4

        /** A little more than the lift takes: after it the screen goes whether or not it said so. */
        const val LIFT_MS = 300L
    }
}
