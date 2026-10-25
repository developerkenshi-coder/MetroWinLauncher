package com.metrowin.launcher.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
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
