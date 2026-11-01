package com.metrowin.launcher.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.notifications.NotificationHub

/**
 * Locks the screen, or opens the recent apps, when the launcher asks. Android gives an app no other way to do either.
 * The service reads nothing: it asks for no window content and for no events, and ignores every one. The recent apps come at
 * once, as with the Recents button, without the wait that the swipe of the system's gesture navigation has with a
 * launcher that is not the one that came with the phone.
 */
class LockAccessibilityService : AccessibilityService() {
    private val navBar by lazy { NavBarOverlay(this) }
    private val lockScreen by lazy { LockOverlay(this) }
    private val statusBar by lazy { StatusBarOverlay(this) }

    // The phone changes from the three buttons to the gestures (or back) while the service runs: the bar follows at once.
    private val navigationMode = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = syncNavBar()
    }

    override fun onServiceConnected() {
        instance = this
        NotificationHub.edgeChanged()
        contentResolver.registerContentObserver(Settings.Secure.getUriFor("navigation_mode"), false, navigationMode)
        syncNavBar()
        syncStatusBar()
        scheduleNudge()
        lockScreen.start()
    }

    private val nudgeHandler = Handler(Looper.getMainLooper())
    private var nudgeTick = 0

    /** Every minute the buttons of the bar move a few pixels with the rest of the screen (see [com.metrowin.launcher.ui.components.BurnIn]). */
    private fun scheduleNudge() {
        nudgeHandler.removeCallbacksAndMessages(null)
        nudgeHandler.postDelayed({
            nudgeTick++
            val (dx, dy) = com.metrowin.launcher.ui.components.BurnIn.at(nudgeTick)
            val on = SettingsStore(this).antiBurnInOn()
            val d = resources.displayMetrics.density
            navBar.nudge(if (on) dx * d else 0f, if (on) dy * d else 0f)
            scheduleNudge()
        }, com.metrowin.launcher.ui.components.BurnIn.EVERY_MS)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        nudgeHandler.removeCallbacksAndMessages(null)
        lockScreen.stop()
        runCatching { contentResolver.unregisterContentObserver(navigationMode) }
        navBar.hide()
        statusBar.hide()
        if (instance === this) instance = null
        NotificationHub.edgeChanged()
        return super.onUnbind(intent)
    }

    /** The packages whose windows are not an app in front (the bars, the keyboard): they do not take the place of the app you are in. */
    private val notApps by lazy {
        val keyboards = getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.enabledInputMethodList.orEmpty().map { it.packageName }
        setOf("com.android.systemui", "android", packageName) + keyboards
    }

    /** Only which app is in front is taken from a window that changes: opened from anywhere, its notifications are taken away at once. */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in notApps || pkg == NotificationHub.foregroundPackage) return
        NotificationHub.foregroundPackage = pkg
        NotificationHub.appOpened(pkg)
    }

    /** The phone turned: the bar of the system has another size now, and ours takes it. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        syncNavBar()
        syncStatusBar()
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockAccessibilityService? = null

        /**
         * The service itself while it is on, to put windows over everything, the status bar included: the windows of an app go under the
         * status bar and cannot take a swipe from the top edge, those of an accessibility service go over it.
         */
        internal fun host(): Context? = instance

        /**
         * Puts the Windows Phone buttons over the navigation bar, or takes them away: they are there when the user turned them on, this
         * service is on and the phone uses the three buttons. Run when the switch changes and when the service connects.
         */
        fun syncNavBar() {
            val service = instance ?: return
            val store = SettingsStore(service)
            if (store.metroNavBarOn() && NavBarOverlay.threeButtons(service)) service.navBar.show(store.navHoldMs())
            else service.navBar.hide()
        }

        /** The Windows Phone status bar over the apps outside the launcher: there when the user turned it on and this service is on. */
        /** Whether the launcher is on screen, said by its activity: the status bar over the apps has no background there. */
        @Volatile
        var launcherInFront = false
            private set

        fun launcherFront(front: Boolean) {
            launcherInFront = front
            instance?.statusBar?.launcher(front)
        }

        fun syncStatusBar() {
            val service = instance ?: return
            if (SettingsStore(service).metroStatusBarOverAppsOn()) service.statusBar.show() else service.statusBar.hide()
        }

        /** Locks the screen. False when the service is off, or on a version of Android that cannot do it (before 9). */
        fun lock(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        /** Opens the recent apps, at once. False when the service is off. */
        fun recents(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }

        /**
         * Opens the recent apps from the Quick Settings panel: the panel is closed first (Android 12 and later can do that
         * from a service), then the recent apps open. False when the service is off.
         */
        fun recentsFromPanel(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return service.performGlobalAction(GLOBAL_ACTION_RECENTS)
            service.performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            Handler(Looper.getMainLooper()).postDelayed({ service.performGlobalAction(GLOBAL_ACTION_RECENTS) }, PANEL_CLOSE_MS)
            return true
        }

        private const val PANEL_CLOSE_MS = 250L

        /**
         * What the service does for a gesture besides locking and the recent apps: the screenshot, the power menu, the split
         * screen and Android's own notification shade and quick settings. False when the service is off or the version of
         * Android does not have it.
         */
        fun perform(action: SystemAction): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < action.minSdk) return false
            return service.performGlobalAction(action.global)
        }

        /** Whether the user has turned the service on. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return isListed(enabled, context.packageName, LockAccessibilityService::class.java.name)
        }

        /**
         * The list Android keeps (services as "package/class", the class possibly abbreviated to ".Class", separated
         * by colons) has the service [className] of [packageName]. Pure, so it can be tested.
         */
        fun isListed(enabledServices: String, packageName: String, className: String): Boolean =
            enabledServices.split(':').any { entry ->
                val slash = entry.indexOf('/')
                if (slash <= 0) return@any false
                val listedPackage = entry.substring(0, slash)
                val listedClass = entry.substring(slash + 1).let { if (it.startsWith('.')) listedPackage + it else it }
                listedPackage == packageName && listedClass == className
            }
    }
}

/**
 * The things the accessibility service can do for a gesture, with the version of Android that has each. The numbers of the
 * actions are copied into the program when it is built; [perform] checks the version before it asks for one.
 */
@android.annotation.SuppressLint("InlinedApi")
enum class SystemAction(val global: Int, val minSdk: Int) {
    Screenshot(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, Build.VERSION_CODES.P),
    PowerMenu(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, Build.VERSION_CODES.LOLLIPOP),
    SplitScreen(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN, Build.VERSION_CODES.N),
    Notifications(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, Build.VERSION_CODES.JELLY_BEAN),
    QuickSettings(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, Build.VERSION_CODES.JELLY_BEAN_MR2),
}
