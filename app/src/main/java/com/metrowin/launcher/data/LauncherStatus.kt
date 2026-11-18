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
class StatusLine(@param:StringRes val label: Int, val ok: Boolean?, val value: String = "")

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
            StatusLine(R.string.status_notif_access, runCatching { NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName) }.getOrDefault(false)),
            StatusLine(R.string.status_accessibility, accessibility),
            StatusLine(R.string.status_overlay, runCatching { Settings.canDrawOverlays(app) }.getOrDefault(false)),
            StatusLine(R.string.status_shizuku, runCatching { ShizukuShell.ready() }.getOrDefault(false)),
            StatusLine(R.string.status_notifications, Build.VERSION.SDK_INT < 33 || granted(app, Manifest.permission.POST_NOTIFICATIONS)),
            StatusLine(R.string.status_phone_state, granted(app, Manifest.permission.READ_PHONE_STATE)),
            StatusLine(R.string.status_contacts, granted(app, Manifest.permission.READ_CONTACTS)),
            StatusLine(R.string.status_calendar, granted(app, Manifest.permission.READ_CALENDAR)),
            StatusLine(R.string.status_battery, runCatching { app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName) }.getOrDefault(false)),
        )
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
