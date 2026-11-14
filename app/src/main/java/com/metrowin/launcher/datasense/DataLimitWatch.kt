package com.metrowin.launcher.datasense

import com.metrowin.launcher.data.Prefs
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The look at the mobile data used in the cycle of the plan, every hour while the battery is not low, and a notification at the share of
 * the limit the user chose (four fifths unless changed) and again when it is reached. One job of Android; it exists only while a limit is set (see [schedule]).
 */
object DataLimitWatch {
    const val CHANNEL = "data_limit"
    private const val JOB_ID = 7304
    private const val NOTIFICATION_ID = 0x4453

    private fun prefs(context: Context) = Prefs.open(context, Prefs.DATA_SENSE)

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

    /** Whether the launcher may show a notification: from Android 13 the user has to allow it. */
    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** A new limit, billing day or share starts the alerts of the cycle again: a limit raised after it was reached has to warn again. */
    fun resetAlerts(context: Context) = prefs(context).edit { remove("alert_cycle"); remove("alert_level") }

    /** Blocking, off the main thread. */
    fun run(context: Context) {
        val store = prefs(context)
        if (DataHistory.enabled(context) && UsageAccess.granted(context)) recordHistory(context)
        val limit = store.getLong("limit_mb", 0L) * 1_000_000L
        // Without the permission to notify there is nothing to do with the reading: it is not taken, and the next hour asks nothing more.
        if (limit <= 0L || !UsageAccess.granted(context) || !canNotify(context)) return
        val now = ZonedDateTime.now()
        val cycle = DataPeriods.cycleStart(now, store.getInt("bill_day", 1)).toInstant().toEpochMilli()
        val announced = if (store.getLong("alert_cycle", 0L) == cycle) store.getInt("alert_level", 0) else 0
        val used = UsageReader(context).device(cycle, now.toInstant().toEpochMilli()).second ?: return
        val level = DataPeriods.alertLevel(used, limit, announced, store.getInt("alert_pct", DataPeriods.DEFAULT_WARN))
        if (level > 0 && notify(context, level, used, limit)) store.edit { putLong("alert_cycle", cycle); putInt("alert_level", level) }
    }

    /** The use of today and yesterday goes to the history of the app, which keeps it longer than Android does. */
    private fun recordHistory(context: Context) {
        val now = ZonedDateTime.now()
        val reader = UsageReader(context)
        val buckets = DataPeriods.buckets(DataPeriod.Week, now).takeLast(2)
        val fresh = buckets.zip(reader.perBucket(buckets)).associate { (b, v) ->
            java.time.Instant.ofEpochMilli(b.from).atZone(now.zone).toLocalDate().toEpochDay() to (v.first to (v.second ?: 0L))
        }
        DataHistory.recordDays(context, fresh)
    }

    private fun notify(context: Context, level: Int, used: Long, limit: Long): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!canNotify(context) || manager == null) return false
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
                .setContentText(
                    if (level >= 100) context.getString(R.string.ds_alert_100, StorageInfo.format(used, locale), StorageInfo.format(limit, locale))
                    else context.getString(R.string.dsx_alert_pct, level, StorageInfo.format(used, locale), StorageInfo.format(limit, locale)),
                )
                .setContentIntent(open).setAutoCancel(true).build(),
        )
        return true
    }
}

/** Runs [DataLimitWatch.run] when Android calls it. */
class DataLimitJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        running[params.jobId] = scope.launch {
            ensureActive()
            try {
                DataLimitWatch.run(applicationContext)
            } catch (e: Exception) {
                // A failed look is tried again at the next hour.
            }
            running.remove(params.jobId)
            // After onStopJob the job is Android's again: it is not told twice.
            if (isActive) jobFinished(params, false)
        }
        return true
    }

    /** Android stops the job (the conditions are gone): the work is cancelled, and tried again later. */
    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
