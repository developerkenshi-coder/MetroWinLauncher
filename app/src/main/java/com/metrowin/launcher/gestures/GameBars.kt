package com.metrowin.launcher.gestures

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import com.metrowin.launcher.notifications.overBars

/** Which apps are games. Pure where it can be, so it is tested. */
object GameApps {
    /** An app is a game when it says so with its category (Android 8 and later) or with the older flag that games carry. */
    @Suppress("DEPRECATION")
    fun isGame(category: Int, flags: Int): Boolean =
        category == ApplicationInfo.CATEGORY_GAME || flags and ApplicationInfo.FLAG_IS_GAME != 0

    fun isGame(packages: PackageManager, packageName: String): Boolean =
        runCatching { packages.getApplicationInfo(packageName, 0).let { isGame(it.category, it.flags) } }.getOrDefault(false)
}

/**
 * While a game is in front the status bar and the buttons that the launcher draws over the apps slide away: they are windows of the
 * accessibility service, over the game, and cover what the game draws at its edges. They come back for [RECALL_MS] with a swipe up from
 * the bottom edge (the buttons) or down from the top edge (the status bar). Two thin windows, one at each edge, wait for the swipe: they
 * take the touches that land on them (a few millimetres at the edge of the screen), and only while a game is in front and the bar of that
 * edge is there to be called back.
 */
internal class GameBars(
    private val service: AccessibilityService,
    private val navBar: NavBarOverlay,
    private val statusBar: StatusBarOverlay,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var top: View? = null
    private var bottom: View? = null
    private var probe: View? = null

    /**
     * Tells [onChange] whether the app in front has hidden the status bar of Android (a video or a game in full screen): a one-pixel window
     * of the service is told the state of the system bars like any window. Needs Android 11; before it nothing is reported.
     */
    fun watchSystemBars(onChange: (hidden: Boolean) -> Unit) {
        if (probe != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val view = View(service)
        view.setOnApplyWindowInsetsListener { v, insets ->
            onChange(!insets.isVisible(WindowInsets.Type.statusBars()))
            v.onApplyWindowInsets(insets)
        }
        val params = WindowManager.LayoutParams(
            1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }.overBars()
        probe = view.takeIf { runCatching { service.getSystemService(WindowManager::class.java).addView(it, params) }.isSuccess }
    }

    fun stopWatching() {
        probe = remove(probe)
    }

    /** A game is in front (and the user wants the bars away). */
    var active = false
        private set

    // A bar called back is on screen over its strip, which would cover its buttons: the strip waits until the bar is gone again.
    private var topRecalled = false
    private var bottomRecalled = false

    fun set(on: Boolean) {
        if (on == active) return
        active = on
        if (!on) {
            handler.removeCallbacksAndMessages(null)
            topRecalled = false
            bottomRecalled = false
        }
        navBar.gameHidden(on)
        statusBar.gameHidden(on)
        refresh()
    }

    /** Puts the strips where a bar is waiting to be called, and takes them away where it is not (or when no game is in front). */
    fun refresh() {
        bottom = if (active && !bottomRecalled && navBar.isShown()) bottom ?: strip(Gravity.BOTTOM, ::recallBottom) else remove(bottom)
        top = if (active && !topRecalled && statusBar.isShown()) top ?: strip(Gravity.TOP, ::recallTop) else remove(top)
    }

    private fun recallBottom() {
        bottomRecalled = true
        navBar.gameHidden(false)
        // Later than the touch that asked: the strip is not taken away while it handles an event.
        handler.post { refresh() }
        later(RECALL_MS) { bottomRecalled = false; navBar.gameHidden(true); refresh() }
    }

    private fun recallTop() {
        topRecalled = true
        statusBar.gameHidden(false)
        handler.post { refresh() }
        later(RECALL_MS) { topRecalled = false; statusBar.gameHidden(true); refresh() }
    }

    private fun later(ms: Long, then: () -> Unit) {
        handler.postDelayed({ if (active) then() }, ms)
    }

    private fun remove(view: View?): View? {
        view ?: return null
        runCatching { service.getSystemService(WindowManager::class.java).removeViewImmediate(view) }
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun strip(gravity: Int, onSwipe: () -> Unit): View? {
        val view = View(service)
        val slop = ViewConfiguration.get(service).scaledTouchSlop * 2f
        val towards = if (gravity == Gravity.TOP) 1f else -1f
        var startY = 0f
        var fired = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    fired = false
                }
                MotionEvent.ACTION_MOVE -> if (!fired && (event.rawY - startY) * towards > slop) {
                    fired = true
                    onSwipe()
                }
            }
            true
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, (STRIP_DP * service.resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT,
        ).apply { this.gravity = gravity }.overBars()
        return view.takeIf { runCatching { service.getSystemService(WindowManager::class.java).addView(it, params) }.isSuccess }
    }

    companion object {
        /** How long a bar called back stays. */
        const val RECALL_MS = 4_000L

        /** The height of each strip: a fingertip, more or less. */
        const val STRIP_DP = 24
    }
}
