package com.metrowin.launcher.datasense

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
import com.metrowin.launcher.data.StorageInfo
import com.metrowin.launcher.data.UsageAccess
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.concurrent.thread

/**
 * The look at the mobile data used in the cycle of the plan, every hour while the battery is not low, and a notification at four fifths of
 * the limit and again when it is reached. One job of Android; it exists only while a limit is set (see [schedule]).
 */
object DataLimitWatch {
    const val CHANNEL = "data_limit"
    private const val JOB_ID = 7304
    private const val NOTIFICATION_ID = 0x4453

    private fun prefs(context: Context) = context.getSharedPreferences("metrowin_datasense", Context.MODE_PRIVATE)

    /** Plans the job when a limit is set and takes it away when there is none. Asking again when it is planned already changes nothing. */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (prefs(context).getLong("limit_mb", 0L) <= 0L) {
            scheduler.cancel(JOB_ID)
            return
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return
        runCatching {
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, DataLimitJob::class.java))
                    .setPeriodic(60 * 60_000L)
                    .setRequiresBatteryNotLow(true)
                    .setPersisted(true)
                    .build(),
            )
        }
    }

    /** Blocking, off the main thread. */
    fun run(context: Context) {
        val store = prefs(context)
        val limit = store.getLong("limit_mb", 0L) * 1_000_000L
        if (limit <= 0L || !UsageAccess.granted(context)) return
        val now = ZonedDateTime.now()
        val cycle = DataPeriods.cycleStart(now, store.getInt("bill_day", 1)).toInstant().toEpochMilli()
        val announced = if (store.getLong("alert_cycle", 0L) == cycle) store.getInt("alert_level", 0) else 0
        val used = UsageReader(context).device(cycle, now.toInstant().toEpochMilli()).second ?: return
        val level = DataPeriods.alertLevel(used, limit, announced)
        if (level > 0 && notify(context, level, used, limit)) store.edit { putLong("alert_cycle", cycle); putInt("alert_level", level) }
    }

    private fun notify(context: Context, level: Int, used: Long, limit: Long): Boolean {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!allowed || manager == null) return false
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_data), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, DataSenseActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val locale = Locale.getDefault()
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_metro)
                .setContentTitle(context.getString(R.string.ds_alert_title))
                .setContentText(context.getString(if (level >= 100) R.string.ds_alert_100 else R.string.ds_alert_80, StorageInfo.format(used, locale), StorageInfo.format(limit, locale)))
                .setContentIntent(open).setAutoCancel(true).build(),
        )
        return true
    }
}

/** Runs [DataLimitWatch.run] when Android calls it. */
class DataLimitJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "data-limit") {
            runCatching { DataLimitWatch.run(applicationContext) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
