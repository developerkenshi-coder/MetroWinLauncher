package com.metrowin.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.PowerPolicy
import kotlinx.coroutines.launch
import com.metrowin.launcher.data.DisplayModeInfo
import com.metrowin.launcher.data.RefreshRate

/** The Home screen. Android starts it for the Home button because of the HOME category in the manifest. */
class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        applyDisplaySettings(viewModel.settings.value)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.settings.collect(::applyDisplaySettings) }
                // Saving battery gives the 120 Hz up, and the phone's own rate takes over.
                launch {
                    viewModel.powerSaving.collect {
                        saving = it
                        requestHighestRefreshRate()
                    }
                }
            }
        }
        setContent { LauncherApp(viewModel) }
    }

    private var wantsHighRefresh = true
    private var saving = false

    /** When the screen was last touched, or the launcher came on screen (uptime), for the high refresh rate. */
    private var lastTouchAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val idleCheck = Runnable { requestHighestRefreshRate() }

    /** The manifest starts in portrait; the settings may free the rotation or fix landscape, and turn the 120 Hz request off. */
    private fun applyDisplaySettings(settings: LauncherSettings) {
        if (requestedOrientation != settings.orientation.requested) requestedOrientation = settings.orientation.requested
        wantsHighRefresh = settings.highRefreshRate
        requestHighestRefreshRate()
    }

    /** Called with the result of a widget's own setup screen (see [com.metrowin.launcher.data.WidgetService.configure]). */
    var onWidgetConfigured: ((resultCode: Int) -> Unit)? = null

    @Suppress("OVERRIDE_DEPRECATION") // the widget host still answers through onActivityResult
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) onWidgetConfigured?.invoke(resultCode)
    }

    override fun onResume() {
        super.onResume()
        // Coming on screen is the time of the entry animations: the same as a touch. The display can also change under us
        // (a foldable opened, an external screen).
        touched()
    }

    override fun onStop() {
        handler.removeCallbacks(idleCheck)
        super.onStop()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> touched()
            // The finger lifts: the rate is kept for the fling and the settling after it, then let go.
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastTouchAt = SystemClock.uptimeMillis()
                scheduleIdleCheck()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun touched() {
        val wasIdle = !RefreshRate.wantsHigh(true, false, lastTouchAt, SystemClock.uptimeMillis())
        lastTouchAt = SystemClock.uptimeMillis()
        if (wasIdle) requestHighestRefreshRate()
        scheduleIdleCheck()
    }

    private fun scheduleIdleCheck() {
        handler.removeCallbacks(idleCheck)
        handler.postDelayed(idleCheck, RefreshRate.IDLE_MS + IDLE_SLACK_MS)
    }

    /**
     * Asks for the fastest refresh rate the screen has (120 Hz and more) at its current resolution, while the screen is being
     * used, and takes the request away when it is not (see [RefreshRate.wantsHigh]). Without the request Android keeps many
     * phones at 60 Hz for apps that do not ask. The tile animations are time based, so they run the same at any rate, only
     * smoother. Where the phone cannot go between the rates without a flash, the rate is simply kept as asked.
     */
    private fun requestHighestRefreshRate() {
        val wants = RefreshRate.wantsHigh(wantsHighRefresh, saving, lastTouchAt, SystemClock.uptimeMillis())
        if (!wantsHighRefresh || saving) {
            releaseRefreshRequest()
            return
        }
        @Suppress("DEPRECATION")
        val screen = (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
        fun Display.Mode.info() = DisplayModeInfo(
            modeId, physicalWidth, physicalHeight, refreshRate,
            if (Build.VERSION.SDK_INT >= 31) alternativeRefreshRates.toList() else emptyList(),
        )
        val current = screen.mode.info()
        val best = RefreshRate.best(current, screen.supportedModes.map { it.info() }) ?: return
        if (wants || !RefreshRate.canLeave(current)) {
            if (window.attributes.preferredDisplayModeId != best.id) {
                window.attributes = window.attributes.also { it.preferredDisplayModeId = best.id }
            }
            if (Build.VERSION.SDK_INT >= 35) window.decorView.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
        } else {
            // Nobody is touching it, and the screen can change rate cleanly: the phone decides, and goes as low as the content allows.
            releaseRefreshRequest()
        }
    }

    private fun releaseRefreshRequest() {
        if (window.attributes.preferredDisplayModeId != 0) {
            window.attributes = window.attributes.also { it.preferredDisplayModeId = 0 }
        }
        if (Build.VERSION.SDK_INT >= 35) window.decorView.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE)
    }

    companion object {
        const val REQUEST_CONFIGURE_WIDGET = 0x4D58

        /** The check for an idle screen comes a moment after the rate would run out. */
        private const val IDLE_SLACK_MS = 100L
    }

    /**
     * Home pressed while the launcher is already running (the activity is singleTask). When another app was
     * on screen we are still stopped at this point: onStart comes right after.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed(fromBackground = !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
    }
}
