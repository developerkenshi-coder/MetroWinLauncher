package com.metrowin.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
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
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.settings.collect(::applyDisplaySettings) }
        }
        setContent { LauncherApp(viewModel) }
    }

    private var wantsHighRefresh = true

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
        // The display can change under us (a foldable opened, an external screen).
        requestHighestRefreshRate()
    }

    /**
     * Asks for the fastest refresh rate the screen has (120 Hz and more) at its current resolution.
     * Without the request Android keeps many phones at 60 Hz for apps that do not ask. The tile
     * animations are time based, so they run the same at any rate, only smoother.
     */
    private fun requestHighestRefreshRate() {
        if (!wantsHighRefresh) {
            // No preference: Android picks the rate by what is on screen.
            if (window.attributes.preferredDisplayModeId != 0) {
                window.attributes = window.attributes.also { it.preferredDisplayModeId = 0 }
            }
            if (Build.VERSION.SDK_INT >= 35) window.decorView.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE)
            return
        }
        @Suppress("DEPRECATION")
        val screen = (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
        fun Display.Mode.info() = DisplayModeInfo(modeId, physicalWidth, physicalHeight, refreshRate)
        val best = RefreshRate.best(screen.mode.info(), screen.supportedModes.map { it.info() }) ?: return
        if (window.attributes.preferredDisplayModeId != best.id) {
            window.attributes = window.attributes.also { it.preferredDisplayModeId = best.id }
        }
        if (Build.VERSION.SDK_INT >= 35) {
            // Adaptive refresh rate: keep the rate high while something animates, drop it when idle.
            window.decorView.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
        }
    }

    companion object {
        const val REQUEST_CONFIGURE_WIDGET = 0x4D58
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
