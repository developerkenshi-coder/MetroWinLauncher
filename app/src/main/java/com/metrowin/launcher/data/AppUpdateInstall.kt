package com.metrowin.launcher.data

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.metrowin.launcher.MainActivity
import com.metrowin.launcher.R
import java.io.File
import kotlin.concurrent.thread

/** What the last check found, kept between runs (the check also runs by itself, with the launcher closed). */
class UpdateStore(context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    var lastCheck: Long
        get() = prefs.getLong("lastCheck", 0L)
        set(value) = prefs.edit().putLong("lastCheck", value).apply()

    /** The newest release found, or null when none was. */
    var release: UpdateRelease?
        get() {
            val version = prefs.getString("version", null) ?: return null
            val apk = prefs.getString("apk", null) ?: return null
            return UpdateRelease(version, apk, prefs.getLong("size", 0L), prefs.getString("sums", null), prefs.getString("notes", "").orEmpty())
        }
        set(value) = prefs.edit().apply {
            if (value == null) {
                remove("version"); remove("apk"); remove("size"); remove("sums"); remove("notes")
            } else {
                putString("version", value.version).putString("apk", value.apkUrl).putLong("size", value.apkSize).putString("sums", value.sumsUrl)
                putString("notes", value.notes)
            }
        }.apply()

    /** The version the user was told about with a notification: each is told once. */
    var notified: String?
        get() = prefs.getString("notified", null)
        set(value) = prefs.edit().putString("notified", value).apply()
}

/** Looks for a new release, keeps what it found, and says so with a notification the first time. */
object UpdateChecker {
    private const val CHANNEL = "update"
    private const val NOTIFICATION_ID = 7301

    /** The version of the installed app, read from the package only (it works from a service, which has no screen). */
    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Blocking, off the main thread. True when GitHub answered (whether or not there is something new). */
    fun check(context: Context, installedVersion: String): Boolean {
        val store = UpdateStore(context)
        // An answer of null is "could not reach GitHub": what was found before stays.
        val latest = AppUpdate.fetchLatest() ?: return false
        store.lastCheck = System.currentTimeMillis()
        store.release = latest.takeIf { AppUpdate.isNewer(it.version, installedVersion) }
        if (store.release != null && store.notified != latest.version) notify(context, latest.version)?.also { store.notified = latest.version }
        return true
    }

    private fun notify(context: Context, version: String): Unit? {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return null
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_update), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SETTINGS, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_metro)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_text, version))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
        return Unit
    }

    fun clearNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}

/** The check every 12 hours, run by Android when there is a connection, even with the launcher closed. */
class UpdateCheckJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "update-check") {
            val version = UpdateChecker.installedVersion(applicationContext)
            val answered = runCatching { UpdateChecker.check(applicationContext, version) }.getOrDefault(false)
            jobFinished(params, !answered)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val JOB_ID = 7302

        /** Plans the check every 12 hours, or takes it away. Asking again when it is planned already changes nothing. */
        fun schedule(context: Context, enabled: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (!enabled) {
                scheduler.cancel(JOB_ID)
                return
            }
            if (scheduler.getPendingJob(JOB_ID) != null) return
            // The launcher must start whatever happens to the schedule: a check that cannot be planned is only a check that does not run.
            runCatching { scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, UpdateCheckJob::class.java))
                    .setPeriodic(AppUpdate.CHECK_EVERY_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build(),
            ) }
        }
    }
}

/** Hands the APK to the installer of Android and goes on to the screen where the user confirms it. */
object UpdateInstaller {
    const val ACTION_RESULT = "com.metrowin.launcher.UPDATE_RESULT"

    fun cacheFile(context: Context) = File(File(context.cacheDir, "updates"), "update.apk")

    /** The app may install apps: Android asks the user for that once, in its own screen ([openPermissionScreen]). */
    fun mayInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openPermissionScreen(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Blocking. Writes [apk] into an install session and commits it; the result comes to [UpdateResultReceiver]. False when the session could not be made. */
    fun install(context: Context, apk: File): Boolean = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // From Android 12 an update of an app by the one that installed it can go without the confirmation screen.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input -> session.openWrite("update.apk", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) } }
            val result = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateResultReceiver::class.java).setAction(ACTION_RESULT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(result.intentSender)
        }
        true
    }.getOrDefault(false)
}

/** The answer of the installer: the confirmation screen to open, or why it did not work. */
class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                UpdateInstaller.cacheFile(context).delete()
                UpdateChecker.clearNotification(context)
            }
            else -> Toast.makeText(context, context.getString(R.string.update_install_failed), Toast.LENGTH_LONG).show()
        }
    }
}
