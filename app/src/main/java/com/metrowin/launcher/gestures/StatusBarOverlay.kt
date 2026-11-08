package com.metrowin.launcher.gestures

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.data.StatusItem
import com.metrowin.launcher.notifications.NotificationHub
import com.metrowin.launcher.notifications.OverlayWindowOwner
import com.metrowin.launcher.notifications.overBars
import com.metrowin.launcher.ui.components.MetroStatusBar
import com.metrowin.launcher.ui.components.rememberBroadcast
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.MetroTheme

/**
 * The status bar of Windows Phone drawn over the one of Android in the apps outside the launcher. It takes no touch, so a swipe from the
 * top edge still reaches Android; it covers the top of the app, which no window can push down. It is out of sight in the launcher (which
 * draws its own, see `MetroStatusBar`) and while Android hides its bar (a video, a game).
 */
internal class StatusBarOverlay(private val service: AccessibilityService) {
    private class Shown(val view: View, val owner: OverlayWindowOwner, val params: WindowManager.LayoutParams)

    private var shown: Shown? = null
    private var fullScreen = false
    private var watching = false
    private var barHeight by mutableStateOf(0.dp)
    private var hidden by mutableStateOf(emptySet<String>())

    // Drawn by the launcher over its own page (its own bar is this one, see LauncherApp): then it has no background of its own, because
    // the launcher already took away the bar of Android there.
    private var launcherBar by mutableStateOf(false)
    private var inLauncher by mutableStateOf(LockAccessibilityService.launcherInFront)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val goFullScreen = Runnable { fullScreen = true; sync() }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Only the screen going off stops it: "user present" (the unlock) comes after "screen on" and must not stop it, or the clock,
            // the battery and the notifications of the bar stood still for the whole time the phone was in use.
            watch(shouldWatch(intent.action))
            checkLock()
        }
    }

    // Not over the lock screen: the lock screen has its own bar (Android's, or the Metro one), and this one stays out of sight until it goes.
    private var locked = false
    private val lockCheck = Runnable { checkLock() }

    private fun checkLock() {
        handler.removeCallbacks(lockCheck)
        locked = service.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        sync()
        // Android says "unlocked" to a service some tenths of a second late: asked again until the lock screen is gone.
        if (locked && shown != null) handler.postDelayed(lockCheck, LOCK_CHECK_MS)
    }

    /** The notifications are read only while the screen is on, as for the screens of the launcher. */
    private fun watch(on: Boolean) {
        val current = shown
        if (on == watching || current == null) return
        watching = on
        // The bar's own polls (radio, cell, cutouts) run only while the window is started: with the screen off they stop.
        if (on) current.owner.resume() else current.owner.pause()
        if (on) NotificationHub.startWatching() else NotificationHub.stopWatching()
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun heightPx(): Int {
        val density = service.resources.displayMetrics.density
        val px = if (Build.VERSION.SDK_INT >= 30) {
            service.getSystemService(WindowManager::class.java).currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
        } else {
            // shortcut: Android 8 to 10 give an app no inset outside a window, so the internal resource is asked by name; upgrade when minSdk reaches 30.
            service.resources.getIdentifier("status_bar_height", "dimen", "android").takeIf { it > 0 }?.let(service.resources::getDimensionPixelSize) ?: 0
        }
        return maxOf(px, (MIN_DP * density).toInt())
    }

    fun show() {
        val height = heightPx()
        barHeight = (height / service.resources.displayMetrics.density).dp
        SettingsStore.of(service).load().let { hidden = StatusItem.hiddenIds(it.statusBarHidden); launcherBar = it.metroStatusBar }
        shown?.let {
            if (it.params.height != height) {
                it.params.height = height
                runCatching { service.getSystemService(WindowManager::class.java).updateViewLayout(it.view, it.params) }
            }
            return
        }
        val settings = SettingsStore.of(service).load()
        hidden = StatusItem.hiddenIds(settings.statusBarHidden)
        launcherBar = settings.metroStatusBar
        val dark = service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val owner = OverlayWindowOwner()
        val host = Host(service) { visible -> systemBar(visible) }
        // Compose looks for these on the root view of the window, which here is the host and not the ComposeView.
        host.setViewTreeLifecycleOwner(owner)
        host.setViewTreeSavedStateRegistryOwner(owner)
        host.addView(ComposeView(service).apply {
            setContent {
                MetroTheme(accent = MetroAccents[settings.accentIndex], dark = dark) {
                    // The percentage follows Android's own: the phone says every change, and the first answer comes at once (the broadcast is sticky).
                    val batteryIntent = rememberBroadcast(Intent.ACTION_BATTERY_CHANGED)
                    val battery = remember(batteryIntent) { batteryIntent?.let { BatteryInfo.from(it, service) } ?: BatteryInfo.read(service) }
                    val solid = if (dark) Color.Black else Color(0xFFE6E6E6)
                    MetroStatusBar(
                        battery, Modifier, if (dark) Color.White else Color(0xFF202020), barHeight,
                        if (inLauncher && launcherBar) Color.Transparent else solid, hidden,
                    )
                }
            }
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }.overBars()
        owner.resume()
        if (runCatching { service.getSystemService(WindowManager::class.java).addView(host, params) }.isFailure) {
            owner.destroy()
            return
        }
        shown = Shown(host, owner, params)
        // Created while a game is in front: it never shows.
        if (gameHidden) host.alpha = 0f
        service.registerReceiver(screen, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) })
        watch(true)
        checkLock()
    }

    /**
     * The launcher came on screen or left it (said by its activity, at once: not by an event of the service, which comes later and would
     * leave the bar missing, or doubled, for a moment). It stays on screen all the time; in the launcher it has no background.
     */
    fun launcher(front: Boolean) {
        inLauncher = front
        // Out of the launcher, Android's bar is taken as there until it says otherwise: a bar that comes late is a bar that blinks.
        if (!front) { handler.removeCallbacks(goFullScreen); fullScreen = false }
        sync()
    }

    /** Android shows its bar, or hides it (a video, a game): ours follows, the hiding a moment late so a change of app does not make it blink. */
    private fun systemBar(visible: Boolean) {
        handler.removeCallbacks(goFullScreen)
        if (visible) { fullScreen = false; sync() } else handler.postDelayed(goFullScreen, HIDE_DELAY_MS)
    }

    /** A game is in front: the bar slides up out of the screen (or back down). It never took a touch, so nothing else changes. */
    private var gameHidden = false

    fun isShown() = shown != null

    fun gameHidden(hidden: Boolean) {
        gameHidden = hidden
        val view = shown?.view ?: return
        view.animate().cancel()
        view.animate().translationY(if (hidden) -view.height.toFloat() else 0f).alpha(if (hidden) 0f else 1f).setDuration(SLIDE_MS).start()
    }

    private fun sync() {
        shown?.view?.visibility = if (locked || (fullScreen && !inLauncher)) View.INVISIBLE else View.VISIBLE
    }

    fun hide() {
        val current = shown ?: return
        watch(false)
        handler.removeCallbacks(goFullScreen)
        handler.removeCallbacks(lockCheck)
        shown = null
        runCatching { service.unregisterReceiver(screen) }
        runCatching { service.getSystemService(WindowManager::class.java).removeViewImmediate(current.view) }
        current.owner.destroy()
    }

    /** Tells whether Android shows its status bar; ours follows it, as the buttons of Windows Phone do with the navigation bar. */
    private class Host(context: Context, private val onSystemBar: (Boolean) -> Unit) : FrameLayout(context) {
        // Taken away only after it was seen: if the phone never says so, ours is never hidden.
        private var seen = false

        override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
            if (Build.VERSION.SDK_INT >= 30) {
                val visible = insets.isVisible(WindowInsets.Type.statusBars())
                if (visible) seen = true
                if (visible || seen) onSystemBar(visible)
            }
            return super.onApplyWindowInsets(insets)
        }
    }

    internal companion object {
        /** The bar reads the phone while the screen is on: everything but the screen going off keeps it reading. */
        fun shouldWatch(action: String?): Boolean = action != Intent.ACTION_SCREEN_OFF

        private const val MIN_DP = 24
        private const val HIDE_DELAY_MS = 700L
        private const val LOCK_CHECK_MS = 100L
        private const val SLIDE_MS = 220L
    }
}
