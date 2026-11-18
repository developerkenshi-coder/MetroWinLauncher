package com.metrowin.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.metrowin.launcher.R

/** One line of the status page: what is checked, and whether it is as it should be ([ok]; null for a plain fact). */
class StatusLine(
    @param:StringRes val label: Int,
    val ok: Boolean?,
    val value: String = "",
    /** What stops working while it is not so, and what to do (0: nothing to say, the line is only a fact). */
    @param:StringRes val hint: Int = 0,
    /** The screen of Android where it is put right. */
    val fix: Fix = Fix.None,
) {
    /** The lines of the "what is wrong?" list: the ones that are not as they should be and that have something to say. */
    val problem: Boolean get() = ok == false && hint != 0
}

/** The screen of Android that puts a line of the status right. */
enum class Fix { None, NotificationAccess, Accessibility, Overlay, AppNotifications, AppPermissions, Battery }

/**
 * The status of the launcher: version, the permissions and services it works with, and the last crash. Read in one place so the page of the
 * settings and the text that is copied for a bug report say the same thing.
 */
object LauncherStatus {
    private fun granted(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun read(context: Context): List<StatusLine> {
        val app = context.applicationContext
        val accessibility = runCatching {
            Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().contains(app.packageName)
        }.getOrDefault(false)
        return listOf(
            StatusLine(R.string.status_safe, !SafeStart.on),
            StatusLine(R.string.status_notif_access, runCatching { NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName) }.getOrDefault(false), hint = R.string.fix_notif_access, fix = Fix.NotificationAccess),
            StatusLine(R.string.status_accessibility, accessibility, hint = R.string.fix_accessibility, fix = Fix.Accessibility),
            StatusLine(R.string.status_overlay, runCatching { Settings.canDrawOverlays(app) }.getOrDefault(false), hint = R.string.fix_overlay, fix = Fix.Overlay),
            StatusLine(R.string.status_shizuku, runCatching { ShizukuShell.ready() }.getOrDefault(false), hint = R.string.fix_shizuku),
            StatusLine(R.string.status_notifications, Build.VERSION.SDK_INT < 33 || granted(app, Manifest.permission.POST_NOTIFICATIONS), hint = R.string.fix_notifications, fix = Fix.AppNotifications),
            StatusLine(R.string.status_phone_state, granted(app, Manifest.permission.READ_PHONE_STATE), hint = R.string.fix_phone_state, fix = Fix.AppPermissions),
            StatusLine(R.string.status_contacts, granted(app, Manifest.permission.READ_CONTACTS), hint = R.string.fix_contacts, fix = Fix.AppPermissions),
            StatusLine(R.string.status_calendar, granted(app, Manifest.permission.READ_CALENDAR), hint = R.string.fix_calendar, fix = Fix.AppPermissions),
            StatusLine(R.string.status_battery, runCatching { app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName) }.getOrDefault(false), hint = R.string.fix_battery, fix = Fix.Battery),
        )
    }

    /** The screen of Android that puts [fix] right, or null for [Fix.None]. */
    fun fixIntent(context: Context, fix: Fix): android.content.Intent? {
        val pkg = android.net.Uri.fromParts("package", context.packageName, null)
        return when (fix) {
            Fix.None -> null
            Fix.NotificationAccess -> android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Fix.Accessibility -> android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            Fix.Overlay -> android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            Fix.AppNotifications -> android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            Fix.AppPermissions -> android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            Fix.Battery -> android.content.Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    /** The first lines of the last crash saved by [CrashLog] (its version and time, then the error), or null when there is none. */
    fun lastError(context: Context): String? = CrashLog.read(context.filesDir)?.lineSequence()?.take(3)?.joinToString("\n")

    /** The plain text to copy: the first line, one `label: value` line for each fact, then the last error. Pure, so it is tested. */
    fun report(header: String, facts: List<Pair<String, String>>, error: String?, errorLabel: String): String = buildString {
        appendLine(header)
        facts.forEach { (label, value) -> appendLine("$label: $value") }
        append("$errorLabel: ").append(error ?: "-")
    }
}
