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
        requestHighestRefreshRate()
        setContent { LauncherApp(viewModel) }
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
