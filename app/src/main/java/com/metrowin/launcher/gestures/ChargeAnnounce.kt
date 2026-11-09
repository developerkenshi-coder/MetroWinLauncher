package com.metrowin.launcher.gestures

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.metrowin.launcher.data.BatteryInfo
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.OverlayTheme
import com.metrowin.launcher.notifications.OverlayWindowOwner
import com.metrowin.launcher.notifications.overlayTopInset
import com.metrowin.launcher.ui.components.ChargeBanner
import com.metrowin.launcher.ui.theme.AccentScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The banner of the launcher for a cable just plugged in: [id] is new each time, so the banner starts again. */
data class ChargeCue(val id: Int, val battery: BatteryInfo)

/**
 * What happens when the phone is plugged in. The broadcast is heard where something is alive anyway (the accessibility service, the
 * notification listener, the launcher while it is on screen): no service of its own, no wake lock. The same cable is announced once,
 * even if every listener hears it. The banner is in the launcher when it is on screen, over the apps otherwise; the lock screen and
 * the status bar use [plugs] to start their own cue.
 */
object ChargeAnnounce {
    /** A loose cable plugs in and out many times: nothing is announced again within this time. */
    const val DEBOUNCE_MS = 15_000L

    /** How long the banner stays, including the slide in and out. */
    const val SHOW_MS = 2_600L

    /** The phone says "connected" a moment before the battery reading follows (the minutes to full, the state). */
    private const val SETTLE_MS = 600L

    enum class Place { None, Launcher, Overlay }

    fun due(enabled: Boolean, now: Long, last: Long?): Boolean = enabled && (last == null || now - last >= DEBOUNCE_MS)

    /** Nothing over a screen that is off or locked (the lock screen has its own cue) or over a bank; in the launcher when it is in front. */
    fun place(launcherInFront: Boolean, interactive: Boolean, locked: Boolean, safe: Boolean, canOverlay: Boolean): Place = when {
        !interactive || locked || safe -> Place.None
        launcherInFront -> Place.Launcher
        canOverlay -> Place.Overlay
        else -> Place.None
    }

    private val _plugs = MutableStateFlow(0)

    /** Counts each announced cable, for the lock screen and the status bar. */
    val plugs: StateFlow<Int> = _plugs.asStateFlow()

    private val _cue = MutableStateFlow<ChargeCue?>(null)
    val cue: StateFlow<ChargeCue?> = _cue.asStateFlow()

    fun cueDone(id: Int) {
        if (_cue.value?.id == id) _cue.value = null
    }

    private var last: Long? = null
    private var overlay: ChargeOverlay? = null

    /** Called on the main thread by whoever hears the power connected broadcast. */
    fun plugged(context: Context) {
        val app = context.applicationContext
        val now = android.os.SystemClock.elapsedRealtime()
        if (!due(SettingsStore.of(app).chargeAnimationOn(), now, last)) return
        last = now
        _plugs.value++
        Handler(Looper.getMainLooper()).postDelayed({ announce(app) }, SETTLE_MS)
    }

    private fun announce(app: Context) {
        val battery = BatteryInfo.read(app) ?: return
        val where = place(
            launcherInFront = LockAccessibilityService.launcherInFront,
            interactive = app.getSystemService(PowerManager::class.java).isInteractive,
            locked = app.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
            safe = LockAccessibilityService.safe,
            canOverlay = LockAccessibilityService.host() != null || Settings.canDrawOverlays(app),
        )
        when (where) {
            Place.Launcher -> _cue.value = ChargeCue((_cue.value?.id ?: 0) + 1, battery)
            Place.Overlay -> (overlay ?: ChargeOverlay(app) { LockAccessibilityService.host() }.also { overlay = it }).show(battery)
            Place.None -> Unit
        }
    }

    /** Listens for the cable with [context] (a runtime receiver: the broadcast cannot be declared in the manifest); unregister the result with the same context. */
    fun listen(context: Context): BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = plugged(context)
    }.also { ContextCompat.registerReceiver(context, it, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED) }

    /** A bank came to the front: no window of ours stays over it. */
    fun hide() {
        overlay?.hide()
        _cue.value = null
    }
}

/** The banner of [ChargeBanner] in a window over the other apps: it takes no touch and goes by itself (a timer takes it away if the animation never ends). */
internal class ChargeOverlay(private val app: Context, private val host: () -> Context?) {
    private class Shown(val view: ComposeView, val owner: OverlayWindowOwner, val windows: WindowManager)

    private val handler = Handler(Looper.getMainLooper())
    private val icons by lazy { IconLoader(app) }
    private var shown: Shown? = null
    private val hideNow = Runnable { hide() }

    fun show(battery: BatteryInfo): Boolean {
        hide()
        val service = host()
        val windows = (service ?: app).getSystemService(WindowManager::class.java)
        val settings = SettingsStore.of(app).load()
        val topInset = overlayTopInset(app, windows)
        val owner = OverlayWindowOwner()
        val view = ComposeView(service ?: app).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                OverlayTheme(settings, icons) {
                    AccentScope(settings.accentPanel) { ChargeBanner(battery, onDone = { handler.post(hideNow) }, topInset = topInset) }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (service != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        owner.resume()
        if (runCatching { windows.addView(view, params) }.isFailure) {
            owner.destroy()
            return false
        }
        shown = Shown(view, owner, windows)
        handler.postDelayed(hideNow, ChargeAnnounce.SHOW_MS + SAFETY_MS)
        return true
    }

    fun hide() {
        handler.removeCallbacks(hideNow)
        val current = shown ?: return
        shown = null
        runCatching { current.windows.removeViewImmediate(current.view) }
        current.owner.destroy()
    }

    private companion object {
        const val SAFETY_MS = 2_000L
    }
}
