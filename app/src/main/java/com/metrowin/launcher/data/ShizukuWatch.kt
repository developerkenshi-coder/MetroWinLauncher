package com.metrowin.launcher.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.metrowin.launcher.R
import com.metrowin.launcher.shizukuterminal.ShizukuTerminalActivity
import kotlin.concurrent.thread

/**
 * Says so when Shizuku is not running while the user has buttons that need it (it is not started again by itself after a restart of the
 * phone). A job of Android looks every 15 minutes, and the launcher looks at once when it sees Shizuku's service end. The notification
 * goes by itself when Shizuku is back; a touch opens Shizuku (or the Terminal when Shizuku is not installed).
 */
object ShizukuWatch {
    const val CHANNEL = "shizuku_watch"
    private const val JOB_ID = 7306
    private const val NOTIFICATION_ID = 0x535A
    private const val ON = "watch"
    private const val SHIZUKU = "moe.shizuku.privileged.api"

    private fun prefs(context: Context) = Prefs.open(context, Prefs.SHIZUKU)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(ON, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(ON, on) }
        schedule(context)
        if (!on) cancelNotice(context)
    }

    /** Worth watching: the user turned it on and has at least one button that goes through Shizuku. */
    fun wanted(context: Context): Boolean = enabled(context) && ShizukuButtons.chosen(context).isNotEmpty()

    /** Plans the job while it is wanted and takes it away when it is not. Asking again when it is planned already changes nothing. */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (!wanted(context)) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        runCatching {
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, ShizukuWatchJob::class.java)).setPeriodic(15 * 60_000L).setPersisted(true).build())
        }.logged("shizuku.watch.schedule")
    }

    /** Looks at Shizuku; blocking, off the main thread. A job that starts the process cold gives Shizuku a few seconds to hand over its connection. */
    fun check(context: Context) {
        if (!wanted(context)) return cancelNotice(context)
        repeat(8) {
            if (ShizukuRadio.running()) return cancelNotice(context)
            Thread.sleep(500)
        }
        notice(context)
    }

    private fun cancelNotice(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun notice(context: Context) {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!allowed || manager == null) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.sw_channel), NotificationManager.IMPORTANCE_LOW))
        val target = context.packageManager.getLaunchIntentForPackage(SHIZUKU) ?: Intent(context, ShizukuTerminalActivity::class.java)
        val open = PendingIntent.getActivity(context, 0, target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_metro)
                .setContentTitle(context.getString(R.string.sw_title))
                .setContentText(context.getString(R.string.sw_text))
                .setContentIntent(open).setAutoCancel(true).build(),
        )
    }

    private var attached = false

    /** Follows Shizuku's service while the launcher lives: a service that ends is looked at again a few seconds later, one that comes back takes the notice away. */
    fun attach(context: Context) {
        if (attached) return
        attached = true
        val app = context.applicationContext ?: context
        runCatching {
            rikka.shizuku.Shizuku.addBinderDeadListener { thread(name = "shizuku-watch") { Thread.sleep(5_000); check(app) } }
            rikka.shizuku.Shizuku.addBinderReceivedListener { cancelNotice(app) }
        }.logged("shizuku.watch.attach")
    }
}

/** Runs [ShizukuWatch.check] when Android calls it. */
class ShizukuWatchJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "shizuku-watch") {
            runCatching { ShizukuWatch.check(applicationContext) }.logged("shizuku.watch.check")
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
