package com.metrowin.launcher.files

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.metrowin.launcher.R

/**
 * Keeps the long work of the file manager ([FilesJobs]) alive with the screen off or the launcher left: a foreground service with a notification
 * that shows how far the work is and stops it, and a wake lock so the processor does not sleep in the middle of a copy. It ends by itself with the work.
 */
class FilesWorkService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var lock: PowerManager.WakeLock? = null

    private val tick = object : Runnable {
        override fun run() {
            val job = FilesJobs.current
            if (job == null) { stopSelf(); return }
            show(job)
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) FilesJobs.current?.progress?.cancel()
        show(FilesJobs.current)
        if (lock == null) {
            // Held for 30 minutes at most: a work that goes on longer is a loop, not a copy.
            lock = getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "metrowin:files")?.apply { acquire(MAX_HOLD_MS) }
        }
        main.removeCallbacks(tick)
        main.post(tick)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        runCatching { lock?.takeIf { it.isHeld }?.release() }
        super.onDestroy()
    }

    private fun show(job: FileJob?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.files_work_channel), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, FilesWorkService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, FilesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val p = job?.progress
        val percent = if (p != null && p.totalBytes > 0) (p.doneBytes * 100 / p.totalBytes).toInt().coerceIn(0, 100) else 0
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_metro).setContentTitle(job?.title ?: getString(R.string.files_work_channel))
            .setContentText(p?.current.orEmpty()).setCategory(android.app.Notification.CATEGORY_PROGRESS)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .setProgress(100, percent, p == null || p.totalBytes <= 0)
            .addAction(0, getString(android.R.string.cancel), stop)
        // A service started as a foreground one must say so at once, even when the work is already over.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val CHANNEL = "files_work"
        private const val NOTIFICATION_ID = 7310
        private const val STOP = "metrowin.files.STOP"
        private const val TICK_MS = 1_000L
        private const val MAX_HOLD_MS = 30L * 60 * 1000

        /** Called when a work starts; never throws (the system may refuse a service started from the background). */
        fun begin(context: Context) {
            runCatching { ContextCompat.startForegroundService(context.applicationContext, Intent(context.applicationContext, FilesWorkService::class.java)) }
        }
    }
}
