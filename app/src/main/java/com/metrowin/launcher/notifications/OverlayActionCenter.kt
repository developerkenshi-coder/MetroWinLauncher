package com.metrowin.launcher.notifications

import com.metrowin.launcher.data.VolumeProfiles
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.metrowin.launcher.MainActivity
import com.metrowin.launcher.R
import com.metrowin.launcher.data.BrightnessController
import com.metrowin.launcher.data.CONNECTION_DEVICES_EVERY
import com.metrowin.launcher.data.ConnectionAccess
import com.metrowin.launcher.data.ConnectionController
import com.metrowin.launcher.data.ConnectionPanel
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.MetroSound
import com.metrowin.launcher.ui.anim.MetroSoundPlayer
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.QuickActionsController
import com.metrowin.launcher.data.RingerMode
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.data.SoundController
import com.metrowin.launcher.gestures.LockAccessibilityService
import com.metrowin.launcher.gestures.SystemAction
import com.metrowin.launcher.ui.actioncenter.ActionCenter
import com.metrowin.launcher.ui.actioncenter.ActionControls
import com.metrowin.launcher.ui.gesture.PullPanel
import com.metrowin.launcher.ui.theme.AccentScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The Action Center of the launcher over any app. A strip as thin as [STRIP_DP] at the top edge of the screen waits for a swipe down,
 * and only that opens the panel (a touch on the strip does nothing). The panel is the one of the launcher, in a window of its own.
 * The strip is thin but not thinner than a fingertip (a finger rarely lands in the first millimetres), and a swipe that starts below
 * it opens the status bar of Android, which stays reachable from there.
 *
 * The windows are those of the accessibility service of the launcher (the one that locks the screen), the only kind that can be over the
 * status bar, where the swipe has to start: with the windows of an app, Android's own status bar takes the swipe. The strip is a window
 * that draws nothing and wakes nothing: it costs a few KB and the touches that land on it. It is taken away only while the panel is up,
 * and it is shorter than the status bar, so inside the launcher it does not cover the launcher's own pull-down.
 */
internal class OverlayActionCenter(context: Context, private val host: () -> Context?, private val onGone: () -> Unit) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val icons by lazy { IconLoader(app) }

    /** Made ready at the first panel that wants its sounds, off the main thread. */
    private val metroSounds by lazy { MetroSoundPlayer(app).also { Thread { it.prepare() }.start() } }
    private var strip: Pair<View, WindowManager>? = null
    private var panel: Panel? = null
    private val removeNow = Runnable { remove() }

    private class Panel(val root: FrameLayout, val owner: OverlayWindowOwner, val windows: WindowManager, val sounds: Boolean) {
        var closing by mutableStateOf(false)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showStrip() {
        if (strip != null || panel != null) return
        // The window of the service, not of the app: only that one can be over the status bar.
        val service = host() ?: return
        val windows = service.getSystemService(WindowManager::class.java)
        val view = View(service)
        val slop = ViewConfiguration.get(app).scaledTouchSlop * 2f
        var startY = 0f
        var fired = false
        // A window that takes touches takes all of them; the strip is thin so that this costs nothing in use.
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    fired = false
                }
                MotionEvent.ACTION_MOVE -> if (!fired && event.rawY - startY > slop) {
                    fired = true
                    open()
                }
            }
            true
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, (STRIP_DP * app.resources.displayMetrics.density).toInt().coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT,
        ).apply { gravity = Gravity.TOP }.overBars()
        if (runCatching { windows.addView(view, params) }.isSuccess) strip = view to windows
    }

    /** Takes the strip away; later than the touch that may be asking for it, so the view is not removed while it handles an event. */
    fun hideStrip() {
        val (view, windows) = strip ?: return
        strip = null
        handler.post { runCatching { windows.removeViewImmediate(view) } }
    }

    fun open() {
        if (panel != null) return
        val service = host() ?: return
        val windows = service.getSystemService(WindowManager::class.java)
        val settings = SettingsStore(app).load()
        hideStrip()
        // The service reads the notifications once now and keeps them current while the panel is up.
        NotificationHub.startWatching()
        val owner = OverlayWindowOwner()
        val root = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
                if (event.action == KeyEvent.ACTION_UP) this@OverlayActionCenter.close()
                return true
            }
        }
        val current = Panel(root, owner, windows, settings.metroSounds)
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.addView(ComposeView(service).apply { setContent { Host(settings, current) } })
        // Focusable, for the Back key and for the box where an answer is typed.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            @Suppress("DEPRECATION") // still what makes a window that is not an activity's shrink for the keyboard
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }.overBars()
        owner.resume()
        if (runCatching { windows.addView(root, params) }.isFailure) {
            owner.destroy()
            NotificationHub.stopWatching()
            return
        }
        panel = current
        if (current.sounds) metroSounds.play(MetroSound.Open)
        // A panel that does not come up (it did not draw) must not stay over the screen: it goes after a short while unless it opened.
        handler.postDelayed(removeNow, OPEN_WATCHDOG_MS)
    }

    /** Asks the panel to close; it goes up, and then its window goes. The second timer is for a panel that cannot do it. */
    fun close() {
        val current = panel ?: return
        if (current.sounds && !current.closing) metroSounds.play(MetroSound.Close)
        current.closing = true
        handler.removeCallbacks(removeNow)
        handler.postDelayed(removeNow, CLOSE_MS)
    }

    private fun remove() {
        handler.removeCallbacks(removeNow)
        val current = panel ?: return
        panel = null
        runCatching { current.windows.removeViewImmediate(current.root) }
        current.owner.destroy()
        NotificationHub.stopWatching()
        // The strip comes back (if it is still wanted).
        onGone()
    }

    fun dispose() {
        hideStrip()
        remove()
    }

    private fun go(intent: Intent) {
        runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        close()
    }

    private fun goTo(action: String) = go(Intent(action))

    /** What only the accessibility service can do, once the panel is out of the way (so it is not in the picture). */
    private fun system(run: () -> Boolean) {
        close()
        handler.postDelayed({
            if (!run()) {
                Toast.makeText(app, R.string.gest_lock_needs_service, Toast.LENGTH_LONG).show()
                runCatching { app.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }, AFTER_PANEL_MS)
    }

    /** What the sliders and the sound buttons show, read when the panel opens and kept as they are moved. */
    private data class Controls(
        val ringVolume: Float? = null,
        val alarmVolume: Float? = null,
        val volume: Float? = null,
        val ringer: RingerMode? = null,
        val brightness: Float? = null,
        val canSetBrightness: Boolean = false,
        val torch: Float? = null,
    )

    @Composable
    private fun Host(settings: LauncherSettings, current: Panel) {
        OverlayTheme(settings, icons) {
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current.density
            val pull = remember { PullPanel(scope, app.resources.displayMetrics.heightPixels.toFloat(), density) }
            val quick = remember { QuickActionsController(app) }
            val sound = remember { SoundController(app) }
            val brightness = remember { BrightnessController(app) }
            val network = remember { ConnectionController(app) }
            var connections by remember { mutableStateOf(ConnectionPanel()) }
            DisposableEffect(quick) {
                quick.start()
                onDispose { quick.close() }
            }
            val torchOn by quick.torchOn.collectAsState()
            var states by remember { mutableStateOf<Map<QuickAction, Boolean?>>(emptyMap()) }
            var timeout by remember { mutableStateOf<Int?>(null) }
            var controls by remember { mutableStateOf(Controls()) }
            fun readStates() {
                states = QuickAction.entries.associateWith(quick::isOn)
                timeout = quick.screenTimeout()
            }

            LaunchedEffect(pull) {
                controls = Controls(sound.ringVolume(), sound.alarmVolume(), sound.volume(), sound.ringer(), brightness.brightness(), brightness.canWrite(), quick.torchStrength())
                pull.open()
                // The state of the buttons is read again every second while the panel is up, as in the launcher.
                var round = 0
                while (true) {
                    readStates()
                    // Bluetooth devices are read every few seconds.
                    connections = network.read(connections, devices = round % CONNECTION_DEVICES_EVERY == 0)
                    round++
                    delay(1_000)
                }
            }
            LaunchedEffect(pull) {
                snapshotFlow { pull.progress }.first { it >= 1f }
                handler.removeCallbacks(removeNow)
                snapshotFlow { pull.progress }.first { it <= 0f }
                handler.post(removeNow)
            }
            LaunchedEffect(current.closing) { if (current.closing) pull.close() }

            val all by NotificationHub.entries.collectAsState()
            val history by NotificationHub.history.collectAsState()
            val notifications = remember(all, settings) { NotificationControl.forActionCenter(all, settings) }
            val apps = remember(notifications) { notifications.map { it.packageName }.distinct().associateWith(::overlayAppItem) }

            AccentScope(settings.accentPanel) { ActionCenter(
                slots = QuickAction.slots(settings.quickActions),
                states = states,
                timeout = timeout,
                notifications = notifications,
                appsByPackage = apps,
                // The service that feeds the panel is running: the access is given.
                hasNotificationAccess = true,
                panel = pull,
                onAction = { action ->
                    when (action) {
                        // The settings of the launcher are in the launcher: it comes to the front and opens them.
                        QuickAction.LauncherSettings -> go(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true))
                        QuickAction.Screenshot -> system { LockAccessibilityService.perform(SystemAction.Screenshot) }
                        QuickAction.Recents -> system(LockAccessibilityService::recents)
                        else -> {
                            // The flashlight and the quiet hours switch on the spot and the panel stays; anything else opens a screen.
                            if (quick.activate(action, settings.dndMinutes, settings.torchAutoOffMin)) {
                                readStates()
                            } else {
                                if (action == QuickAction.QuietHours) Toast.makeText(app, R.string.qa_quiet_allow, Toast.LENGTH_LONG).show()
                                close()
                            }
                        }
                    }
                },
                connections = connections,
                onConnectionList = { action -> if (action == QuickAction.Wifi) network.scan() },
                // A window over other apps cannot ask for a permission: the app page in the settings of Android can give it.
                onAllowConnections = { action ->
                    if (action == QuickAction.Wifi && connections.wifiAccess == ConnectionAccess.NeedsLocation) {
                        goTo(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    } else {
                        go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)))
                    }
                },
                onAllSettings = { goTo(Settings.ACTION_SETTINGS) },
                onOpenNotification = { entry ->
                    close()
                    NotificationHub.open(app, entry)
                },
                onDismissNotification = { NotificationHub.dismiss(it.key) },
                onReply = if (settings.inlineReply) {
                    { entry, text ->
                        val sent = NotificationHub.reply(app, entry, text)
                        if (sent && entry.autoCancel) NotificationHub.dismiss(entry.key)
                        sent
                    }
                } else {
                    null
                },
                groupConversations = settings.groupConversations,
                controls = if (settings.actionSliders) {
                    ActionControls(
                        volume = controls.volume, ringer = controls.ringer, brightness = controls.brightness,
                        canSetBrightness = controls.canSetBrightness,
                        onVolume = { sound.setVolume(it); controls = controls.copy(volume = it) },
                        onRinger = {
                            // Silent needs the "do not disturb" access, and without it the screen that gives it opens.
                            if (!sound.setRinger(it)) goTo(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            controls = controls.copy(ringer = sound.ringer())
                        },
                        onBrightness = { if (brightness.set(it)) controls = controls.copy(brightness = it) },
                        onAllowBrightness = { go(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.fromParts("package", app.packageName, null))) },
                        torch = controls.torch.takeIf { torchOn },
                        onTorch = { quick.setTorchStrength(it); controls = controls.copy(torch = it) },
                        ringVolume = controls.ringVolume, alarmVolume = controls.alarmVolume,
                        onRingVolume = { sound.setRingVolume(it); controls = controls.copy(ringVolume = it) },
                        onAlarmVolume = { sound.setAlarmVolume(it); controls = controls.copy(alarmVolume = it) },
                        profiles = if (settings.volumeProfilesShow) settings.volumeProfiles.map { it.name } else emptyList(),
                        onProfile = { i ->
                            settings.volumeProfiles.getOrNull(i)?.let { p ->
                                VolumeProfiles.apply(sound, p)
                                controls = controls.copy(volume = p.media / 100f, ringVolume = p.ring / 100f, alarmVolume = p.alarm / 100f)
                            }
                        },
                    )
                } else {
                    null
                },
                onSnooze = { entry, option ->
                    val millis = Snooze.millis(option, java.time.Instant.now(), java.time.ZoneId.systemDefault())
                    if (!NotificationHub.snooze(entry.key, millis)) Toast.makeText(app, R.string.launch_failed, Toast.LENGTH_SHORT).show()
                },
                onAppNotificationSettings = { entry ->
                    go(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, entry.packageName))
                },
                onClearAll = NotificationHub::dismissAll,
                onNotificationAction = { entry, action -> NotificationHub.run(app, entry, action) },
                history = history,
                onClearHistory = NotificationHub::clearHistory,
                onTurnOnNotificationAccess = { goTo(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                onClose = ::close,
                contentPadding = remember { barsPadding(current.windows, density) },
            ) }
        }
    }

    /**
     * The room the bars and the cut-out of the camera take at the edges of the screen. A window of the accessibility service is not
     * given insets, so the panel would draw under the status bar and over the camera hole: it is told how much to leave instead.
     */
    @SuppressLint("DiscouragedApi") // before Android 11 the size of the bars can only be asked by the name of the resource
    private fun barsPadding(windows: WindowManager, density: Float): PaddingValues {
        val edges = if (Build.VERSION.SDK_INT >= 30) {
            val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            windows.maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility(types).let { intArrayOf(it.left, it.top, it.right, it.bottom) }
        } else {
            fun size(name: String) = app.resources.getIdentifier(name, "dimen", "android").takeIf { it > 0 }?.let(app.resources::getDimensionPixelSize) ?: 0
            intArrayOf(0, size("status_bar_height"), 0, size("navigation_bar_height"))
        }
        fun dp(px: Int) = (px / density).dp
        return PaddingValues.Absolute(left = dp(edges[0]), top = dp(edges[1]), right = dp(edges[2]), bottom = dp(edges[3]))
    }

    private companion object {
        /** The height of the strip at the top edge, in dp. */
        const val STRIP_DP = 20

        /** A panel that has not come up by then is taken away. */
        const val OPEN_WATCHDOG_MS = 2_500L

        /** A panel asked to close is taken away by then even if it did not go by itself. */
        const val CLOSE_MS = 1_500L

        /** Time for the panel to be out of the way before the screenshot is taken. */
        const val AFTER_PANEL_MS = 450L
    }
}
