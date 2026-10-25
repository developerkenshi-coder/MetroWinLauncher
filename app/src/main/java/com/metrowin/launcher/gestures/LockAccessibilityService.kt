package com.metrowin.launcher.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Locks the screen, or opens the recent apps, when the launcher asks. Android gives an app no other way to do either.
 * The service reads nothing: it asks for no window content and for no events, and ignores every one. The recent apps come at
 * once, as with the Recents button, without the wait that the swipe of the system's gesture navigation has with a
 * launcher that is not the one that came with the phone.
 */
class LockAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockAccessibilityService? = null

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

/** The things the accessibility service can do for a gesture, with the version of Android that has each. */
enum class SystemAction(val global: Int, val minSdk: Int) {
    Screenshot(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, Build.VERSION_CODES.P),
    PowerMenu(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, Build.VERSION_CODES.LOLLIPOP),
    SplitScreen(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN, Build.VERSION_CODES.N),
    Notifications(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, Build.VERSION_CODES.JELLY_BEAN),
    QuickSettings(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, Build.VERSION_CODES.JELLY_BEAN_MR2),
}
