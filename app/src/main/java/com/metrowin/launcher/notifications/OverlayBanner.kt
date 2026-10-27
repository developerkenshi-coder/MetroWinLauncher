package com.metrowin.launcher.notifications

import android.app.NotificationManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.metrowin.launcher.data.IconLoader
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.MetroSound
import com.metrowin.launcher.ui.anim.MetroSoundPlayer
import com.metrowin.launcher.data.ThemeStyle
import com.metrowin.launcher.model.AppItem
import com.metrowin.launcher.model.Launch
import com.metrowin.launcher.ui.actioncenter.BANNER_MS
import com.metrowin.launcher.ui.actioncenter.ToastBanner
import com.metrowin.launcher.ui.components.LocalIconLoader
import com.metrowin.launcher.ui.theme.AccentScope
import com.metrowin.launcher.ui.theme.AccessibilityPolicy
import com.metrowin.launcher.ui.theme.MetroAccents
import com.metrowin.launcher.ui.theme.MetroTheme
import com.metrowin.launcher.ui.theme.Palettes
import com.metrowin.launcher.ui.theme.rememberIsDark

/**
 * When a notification gets the Metro banner over other apps. Only when the launcher is not on screen (then it has its own banner),
 * the screen is on and not locked, the user allowed drawing over other apps, the notification is one that makes a sound or a
 * pop-up (not a silent one) and Do Not Disturb lets it through.
 */
object OverlayRules {
    fun shows(
        settings: LauncherSettings,
        packageName: String,
        canDraw: Boolean,
        launcherOnScreen: Boolean,
        interactive: Boolean,
        locked: Boolean,
        importance: Int,
        passesDoNotDisturb: Boolean,
    ): Boolean =
        canDraw && !launcherOnScreen && interactive && !locked && passesDoNotDisturb &&
            importance >= NotificationManager.IMPORTANCE_DEFAULT &&
            NotificationControl.showsOverlay(packageName, settings)
}

/**
 * The banner of [ToastBanner] drawn in a window over the other apps. One at a time, the newest replaces the one up. It goes by
 * itself like the banner of the launcher; a second timer takes the window away if the animation never ends, so nothing stays on the screen.
 */
internal class OverlayBanner(context: Context, private val host: () -> Context? = { null }) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val icons by lazy { IconLoader(app) }

    /** Made ready at the first banner that wants its chime, off the main thread, so the banner is not held back. */
    private val sounds by lazy { MetroSoundPlayer(app).also { Thread { it.prepare() }.start() } }
    private var shown: Shown? = null
    private val hideLater = Runnable { hide() }

    private class Shown(val view: ComposeView, val owner: OverlayWindowOwner, val windows: WindowManager)

    fun show(entry: NotificationEntry, settings: LauncherSettings) {
        hide()
        val owner = OverlayWindowOwner()
        // A window of the accessibility service brings no "shown over other apps" notice; one of the app does.
        val service = host()
        val windows = (service ?: app).getSystemService(WindowManager::class.java)
        val view = ComposeView(service ?: app).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                OverlayTheme(settings, icons) {
                    val item = remember(entry.packageName) { overlayAppItem(entry.packageName) }
                    AccentScope(settings.accentPanel) { ToastBanner(
                        entry = entry, app = item, hideText = settings.liveHideText,
                        onOpen = { NotificationHub.open(app, entry); handler.post(hideLater) },
                        onDismiss = { handler.post(hideLater) },
                    ) }
                }
            }
        }
        // Not focusable and not modal: the touches outside the banner go on to the app below it.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (service != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        owner.resume()
        if (runCatching { windows.addView(view, params) }.isFailure) {
            owner.destroy()
            return
        }
        if (settings.metroSounds) sounds.play(MetroSound.Notify)
        shown = Shown(view, owner, windows)
        handler.postDelayed(hideLater, BANNER_MS + SAFETY_MS)
    }

    fun hide() {
        handler.removeCallbacks(hideLater)
        val current = shown ?: return
        shown = null
        runCatching { current.windows.removeViewImmediate(current.view) }
        current.owner.destroy()
    }

    private companion object {
        /** Time past the banner's own that the window is taken away anyway. */
        const val SAFETY_MS = 1_500L
    }
}

/** The look of the launcher, as LauncherApp builds it (without the accent from the photo, which needs the picture), for a window of the service. */
@Composable
internal fun OverlayTheme(settings: LauncherSettings, icons: IconLoader, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val accent = if (settings.customAccent != 0) Color(settings.customAccent) else MetroAccents[settings.accentIndex.coerceIn(MetroAccents.indices)]
    val droid = if (settings.themeStyle == ThemeStyle.Droid) {
        remember(settings.dynamicColor, accent) { (if (settings.dynamicColor) Palettes.fromSystem(context) else null) ?: Palettes.fromSeed(accent) }
    } else {
        null
    }
    MetroTheme(
        accent = accent, dark = rememberIsDark(settings),
        reduceMotion = AccessibilityPolicy.reduceMotion(settings.reduceMotion, AccessibilityPolicy.systemAnimatorScale(context)),
        highContrast = settings.highContrast, style = settings.themeStyle, droid = droid, oled = settings.oledMode,
        textScale = settings.textScale / 100f, boldText = settings.boldText, systemFont = settings.systemFont,
        tilt = settings.tiltStrength / 100f, speed = settings.animationSpeed / 100f,
    ) {
        CompositionLocalProvider(LocalIconLoader provides icons, content = content)
    }
}

/** The entry of an app that has only its package: enough for the icon of the app next to a notification. */
internal fun overlayAppItem(packageName: String) =
    AppItem(id = "overlay:$packageName", label = "", launch = Launch.Link(""), packageName = packageName, pinOnly = true)

/** Puts the window where it says, over the bars of the screen, instead of inside them (the default of a window that is not an activity's). */
internal fun WindowManager.LayoutParams.overBars() = apply {
    if (Build.VERSION.SDK_INT >= 30) {
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    } else if (Build.VERSION.SDK_INT >= 28) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}

/** What Compose needs of a window that is not an activity: a lifecycle, and a place to save state. */
internal class OverlayWindowOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    init {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
