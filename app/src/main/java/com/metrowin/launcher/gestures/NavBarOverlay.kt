package com.metrowin.launcher.gestures

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.metrowin.launcher.MainActivity
import com.metrowin.launcher.notifications.overBars

/**
 * The three buttons of Windows Phone (back, the Windows logo, search) drawn over the navigation bar area.
 * Appears immediately when enabled and stays visible until disabled by the user; the logo held for [holdMs] opens the recent apps
 * without waiting for the finger to lift.
 */
internal class NavBarOverlay(private val service: AccessibilityService) {
    private var bar: Bar? = null
    private var holdMs = DEFAULT_HOLD_MS
    private var attempts = 0
    private val handler = Handler(Looper.getMainLooper())

    private fun heightPx(): Int {
        val fallback = (48 * service.resources.displayMetrics.density).toInt()
        // The height of the bar of the system, not more: a taller one would cover the bottom of the apps (the tabs of Reddit, for one).
        val real = barHeight(service)
        // A little more than the bar, above and below it (see [bleedPx]): a strip of the screen left uncovered next to ours is far worse than
        // a few pixels of an app under it.
        return (if (real <= 0) fallback else real) + 2 * bleedPx()
    }

    private fun bleedPx() = (BLEED_DP * service.resources.displayMetrics.density).toInt()

    fun show(holdMs: Int = DEFAULT_HOLD_MS) {
        val windows = service.getSystemService(WindowManager::class.java)
        bar?.let { view ->
            if (this.holdMs != holdMs) {
                hide()
            } else {
                // Shown already: only the size can have changed (the phone turned, the bar of the system changed).
                view.visibility = View.VISIBLE
                val params = view.layoutParams as? WindowManager.LayoutParams
                val height = heightPx()
                if (params != null && params.height != height) {
                    params.height = height
                    runCatching { windows.updateViewLayout(view, params) }
                }
                return
            }
        }
        this.holdMs = holdMs
        val view = Bar(service, holdMs) { visible -> bar?.let { setShown(it, visible) } }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, heightPx(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            // Pushed down by the bleed, so the part of it below the bar of Android is off the screen (the flags above allow that).
            y = -bleedPx()
        }.overBars()
        if (runCatching { windows.addView(view, params) }.isSuccess) {
            bar = view
            attempts = 0
            view.visibility = View.VISIBLE
        } else if (attempts < MAX_ATTEMPTS) {
            // Right after the service connects Android may not take the window yet: it is tried again a moment later.
            attempts++
            handler.postDelayed({ if (bar == null) show(holdMs) }, RETRY_MS * attempts)
        }
    }

    /**
     * The bar of the system goes away in a video or a game that takes the whole screen, and comes back with a swipe from the edge: ours
     * follows, as a sliver that takes no touch while it is away.
     */
    private fun setShown(view: View, visible: Boolean) {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val height = if (visible) heightPx() else 1
        if (params.height == height) return
        params.height = height
        params.flags = if (visible) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        runCatching { service.getSystemService(WindowManager::class.java).updateViewLayout(view, params) }
    }

    /**
     * Moves the buttons a few pixels, now and then, so that no pixel of the screen shows the same thing for hours (burn-in). Only the
     * buttons move: the whole view moving would leave a strip of the screen uncovered along the edge it came away from.
     */
    fun nudge(dx: Float, dy: Float) {
        bar?.shift(dx, dy)
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        val view = bar ?: return
        bar = null
        runCatching { service.getSystemService(WindowManager::class.java).removeViewImmediate(view) }
    }

    @SuppressLint("ClickableViewAccessibility", "ViewConstructor")
    private class Bar(
        private val service: AccessibilityService,
        private val holdMs: Int,
        private val onSystemBar: (Boolean) -> Unit,
    ) : View(service) {
        private val density = resources.displayMetrics.density
        private val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        private val ink = if (dark) Color.WHITE else Color.rgb(32, 32, 32)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeCap = Paint.Cap.SQUARE
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
        private val slop = ViewConfiguration.get(service).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var heldOpen = false
        private val handler = Handler(Looper.getMainLooper())
        private val openRecents = Runnable {
            heldOpen = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }

        // The bar goes away only after it was seen to be there: if the phone never says so, ours is never taken away.
        private var seenSystemBar = false

        init {
            visibility = VISIBLE
            setBackgroundColor(if (dark) Color.BLACK else Color.rgb(230, 230, 230))
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                setOnApplyWindowInsetsListener { view, insets ->
                    val shown = insets.isVisible(android.view.WindowInsets.Type.navigationBars())
                    if (shown) seenSystemBar = true
                    if (shown || seenSystemBar) onSystemBar(shown)
                    view.onApplyWindowInsets(insets)
                }
            }
        }

        private var shiftX = 0f
        private var shiftY = 0f

        fun shift(dx: Float, dy: Float) {
            shiftX = dx
            shiftY = dy
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.translate(shiftX, shiftY)
            val cell = width / 3f
            val cy = height / 2f
            val s = height * 0.2f
            // back: an arrow to the left
            var cx = cell / 2
            canvas.drawLine(cx + s, cy, cx - s, cy, line)
            val head = Path().apply {
                moveTo(cx - s + 0.75f * s, cy - 0.75f * s)
                lineTo(cx - s, cy)
                lineTo(cx - s + 0.75f * s, cy + 0.75f * s)
            }
            canvas.drawPath(head, line)
            // home: the four panes of the Windows logo
            cx = cell * 1.5f
            val gap = 0.28f * s
            val pane = s - gap / 2
            for (row in 0..1) for (col in 0..1) {
                val left = cx - s + col * (pane + gap)
                val top = cy - s + row * (pane + gap)
                canvas.drawRect(left, top, left + pane, top + pane, fill)
            }
            // search: a lens and its handle
            cx = cell * 2.5f
            canvas.drawCircle(cx - 0.25f * s, cy - 0.25f * s, 0.8f * s, line)
            canvas.drawLine(cx + 0.35f * s, cy + 0.35f * s, cx + s, cy + s, line)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    heldOpen = false
                    // The logo held for the set time opens the recent apps by itself, without waiting for the finger to lift.
                    if (button(downX) == 1) handler.postDelayed(openRecents, holdMs.toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) handler.removeCallbacks(openRecents)
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(openRecents)
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(openRecents)
                    val moved = kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop
                    if (!moved && !heldOpen) press(button(downX))
                }
            }
            return true
        }

        private fun button(x: Float) = (x / (width / 3f)).toInt().coerceIn(0, 2)

        private fun press(button: Int) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            when (button) {
                0 -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                1 -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                else -> runCatching {
                    service.startActivity(
                        android.content.Intent(service, MainActivity::class.java).putExtra(MainActivity.EXTRA_SEARCH, true)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                }
            }
        }
    }

    companion object {
        const val DEFAULT_HOLD_MS = 500
        private const val MAX_ATTEMPTS = 5
        private const val RETRY_MS = 600L
        private const val BLEED_DP = 4

        /** Whether the phone uses the three buttons (0), not the two-button bar (1) or the gestures (2). Where it is not told, the three buttons. */
        fun threeButtons(context: Context): Boolean =
            Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 0

        /** The height of the bar of the system, in pixels; 0 when the phone has none. */
        fun barHeight(context: Context): Int {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val real = context.getSystemService(WindowManager::class.java).currentWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars()).bottom
                if (real > 0) return real
            }
            val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        }
    }
}
