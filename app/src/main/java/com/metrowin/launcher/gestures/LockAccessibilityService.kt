package com.metrowin.launcher.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.metrowin.launcher.data.LauncherSettings
import com.metrowin.launcher.data.QuickAction
import com.metrowin.launcher.data.RecentTasks
import com.metrowin.launcher.data.ShizukuShell
import com.metrowin.launcher.power.PowerActivity
import android.content.Intent
import com.metrowin.launcher.data.ShizukuButtons
import com.metrowin.launcher.data.SettingsStore
import com.metrowin.launcher.MainActivity
import com.metrowin.launcher.notifications.NotificationHub
import kotlin.concurrent.thread

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
    private val gameBars by lazy { GameBars(this, navBar, statusBar) }

    // The phone changes from the three buttons to the gestures (or back) while the service runs: the bar follows at once.
    private val navigationMode = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = syncNavBar()
    }

    override fun onServiceConnected() {
        instance = this
        NotificationHub.edgeChanged()
        contentResolver.registerContentObserver(Settings.Secure.getUriFor("navigation_mode"), false, navigationMode)
        safeSettings = SettingsStore.of(this).load()
        syncNavBar()
        syncStatusBar()
        scheduleNudge()
        lockScreen.start()
        plugReceiver = ChargeAnnounce.listen(this)
        syncGameFromUsage()
    }

    /**
     * The service connects (it was restarted, or just turned on) with a game perhaps in front, and no window event comes until something changes:
     * the usage access, when the user gave it, says which app is on screen.
     */
    private fun syncGameFromUsage() {
        thread(name = "game-front") {
            val top = RecentTasks.foreground(this) ?: return@thread
            Handler(Looper.getMainLooper()).post {
                if (NotificationHub.foregroundPackage == null && top != packageName && top !in notApps) syncGame(top)
            }
        }
    }

    private var plugReceiver: android.content.BroadcastReceiver? = null

    private val nudgeHandler = Handler(Looper.getMainLooper())
    private var nudgeTick = 0

    /** Every minute the buttons of the bar move a few pixels with the rest of the screen (see [com.metrowin.launcher.ui.components.BurnIn]). */
    private fun scheduleNudge() {
        nudgeHandler.removeCallbacksAndMessages(null)
        nudgeHandler.postDelayed({
            nudgeTick++
            val (dx, dy) = com.metrowin.launcher.ui.components.BurnIn.at(nudgeTick)
            val on = SettingsStore.of(this).antiBurnInOn()
            val d = resources.displayMetrics.density
            navBar.nudge(if (on) dx * d else 0f, if (on) dy * d else 0f)
            scheduleNudge()
        }, com.metrowin.launcher.ui.components.BurnIn.EVERY_MS)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        nudgeHandler.removeCallbacksAndMessages(null)
        lockScreen.stop()
        plugReceiver?.let { runCatching { unregisterReceiver(it) } }
        plugReceiver = null
        runCatching { contentResolver.unregisterContentObserver(navigationMode) }
        gameBars.set(false)
        navBar.hide()
        statusBar.hide()
        if (instance === this) instance = null
        safe = false
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
        // The windows this service draws (the bars, the strips at the edges, the lock screen) are windows of this package too, and each one that
        // comes or goes sends this event: only a screen of the launcher (an activity) is "back at the launcher", or the bars of a game would be
        // taken away by the strips that call them back.
        if (pkg == packageName && event.className?.toString()?.endsWith("Activity") != true) return
        // Back at the launcher: the app that was in front before is not any more, and what it posts now has not been read.
        if (pkg == packageName) {
            NotificationHub.foregroundPackage = null
            setSafe(false)
            // Only the launcher itself is "back at the launcher": another screen of this app (the call, a message) can come over a game and
            // go again without the game sending an event, and the bars would then stay over it.
            if (event.className?.toString() == MainActivity::class.java.name) syncGame(null)
            return
        }
        if (pkg in notApps) return
        lockScreen.appCameOver()
        // The overlay a phone shows over a game it knows (vivo's Game Cube, Samsung's Game Tools, Google Play Games) comes just after the game and
        // would take its place as the app in front: it says a game is up, and is not an app that came to the front.
        if (pkg in GameApps.HELPERS) {
            syncGame(pkg)
            return
        }
        // A dialog or popup of another app over a game does not take the game's place (and leaves no event of the game behind when it goes).
        if (!(gameBars.active && GameApps.isPopup(event.className?.toString()))) syncGame(pkg)
        // A bank (or an app the user listed) in front: the windows of the launcher over it are taken away until it is left.
        setSafe(SafeMode.isSensitive(pkg, safeSettings))
        if (pkg == NotificationHub.foregroundPackage) return
        NotificationHub.foregroundPackage = pkg
        NotificationHub.appOpened(pkg)
    }

    private val gamePackages = HashMap<String, Boolean>()

    /**
     * The app in front is a game (and the user wants the bars away): the bars slide out. [pkg] is null at the launcher. What an app is
     * is asked of the package manager once, and remembered.
     */
    internal fun syncGame(pkg: String?) {
        val game = pkg != null && SettingsStore.of(this).hideBarsInGamesOn() &&
            GameApps.isGame(pkg, safeSettings.gameAdd, safeSettings.gameSkip) { pkg in GameApps.HELPERS || gamePackages.getOrPut(pkg) { GameApps.isGame(packageManager, pkg) } }
        gameBars.set(game)
        NotificationHub.edgeChanged()
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
            val store = SettingsStore.of(service)
            if (!safe && store.metroNavBarOn() && NavBarOverlay.threeButtons(service) && !NavBarOverlay.onSide(service)) service.navBar.show(store.navHoldMs(), store.navSpacing())
            else service.navBar.hide()
            service.gameBars.refresh()
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
            if (!safe && SettingsStore.of(service).metroStatusBarOverAppsOn()) service.statusBar.show() else service.statusBar.hide()
            service.gameBars.refresh()
        }

        /** Whether a game is in front and the bars are away: the strip at the top edge for the Action Center gives way to the one that calls the status bar back. */
        val gameBarsAway: Boolean get() = instance?.gameBars?.active == true

        /** The switch "hide the bars in games" changed: the app in front is judged again with it (a game being played gets its bars back, or loses them). */
        fun gameSettingChanged() {
            // Later than the change itself: the settings are written after the flow that calls this has run, and the lists are read from them.
            Handler(Looper.getMainLooper()).post { instance?.syncGame(NotificationHub.foregroundPackage) }
        }

        /** The settings changed (the switch, the list of apps): the service decides with the new ones, and leaves the safe mode when it is turned off. */
        fun safeSettingsChanged(settings: LauncherSettings) {
            safeSettings = settings
            if (!settings.bankSafe) setSafe(false)
        }

        @Volatile
        private var safeSettings = LauncherSettings()

        /** True while a bank (see [SafeMode]) is in front or being opened: no window of the launcher is over the apps. */
        @Volatile
        var safe = false
            private set

        /** The launcher is about to open [packageName]: if it is a bank the windows go before it starts, not after the first window of the bank is up. */
        fun opening(packageName: String) {
            if (SafeMode.isSensitive(packageName, safeSettings)) setSafe(true)
            // A game going to start: the bars go with the launcher's own animation, not after the first window of the game is up.
            instance?.syncGame(packageName)
        }

        private fun setSafe(on: Boolean) {
            if (safe == on) return
            safe = on
            val service = instance
            if (service != null) {
                if (on) {
                    service.navBar.hide()
                    service.statusBar.hide()
                    service.lockScreen.hide()
                    NotificationHub.service?.hideOverlays()
                    ChargeAnnounce.hide()
                }
                // Back: each window comes back if its own switch says so.
                else {
                    syncNavBar()
                    syncStatusBar()
                }
            }
            NotificationHub.edgeChanged()
        }

        /** Turns the accessibility service off, for the user who wants no trace of it at the moment (a bank that blocks it): it is turned on again in the settings of Android. */
        fun turnOff(): Boolean {
            val service = instance ?: return false
            service.disableSelf()
            return true
        }

        /** Locks the screen. False when the service is off, or on a version of Android that cannot do it (before 9). */
        fun lock(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        /** Opens the recent apps, at once. False when the service is off. */
        fun recents(): Boolean {
            if (ShizukuButtons.pressIfChosen(QuickAction.Recents)) return true
            val service = instance ?: return false
            // The cards of the launcher when the usage access is given (they list the apps by it); the recent apps of Android otherwise.
            if (RecentTasks.open(service)) return true
            return service.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }

        /** The recent apps of Android itself (what the cards offer as a way out). */
        fun systemRecents(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true

        /**
         * Opens the recent apps from the Quick Settings panel: the panel is closed first (Android 12 and later can do that
         * from a service), then the recent apps open. False when the service is off.
         */
        fun recentsFromPanel(): Boolean {
            if (ShizukuButtons.pressIfChosen(QuickAction.Recents)) return true
            val service = instance ?: return false
            if (RecentTasks.open(service)) return true
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
            if (action == SystemAction.Screenshot && ShizukuButtons.pressIfChosen(QuickAction.Screenshot)) return true
            val service = instance ?: return false
            // The power menu of Windows Phone, when Shizuku can switch the phone off; the one of Android otherwise.
            if (action == SystemAction.PowerMenu && ShizukuShell.ready()) {
                return runCatching { service.startActivity(Intent(service, PowerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            }
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
