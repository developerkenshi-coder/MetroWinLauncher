package com.metrowin.launcher.recorder

import android.app.Notification
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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Recordings
import com.metrowin.launcher.data.VoiceRecorder

/**
 * Keeps a recording alive when the launcher is left or the screen goes off, with a notification to see it, to hold it and to stop it.
 * Every few seconds it looks at the storage: a recording that has no room left is ended (and saved) instead of being lost.
 */
class RecorderService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var shownPaused: Boolean? = null
    private val tick = object : Runnable {
        override fun run() {
            val dir = Recordings(applicationContext).newFile().parentFile
            if (dir != null && VoiceRecorder.guard(dir)) {
                tell(applicationContext, VoiceRecorder.NOTICE_SPACE)
                stopSelf()
                return
            }
            if (!VoiceRecorder.active) { stopSelf(); return }
            // A hold from outside (a call took the microphone) changes the notification too.
            if (shownPaused != VoiceRecorder.paused) show()
            main.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun show() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.recorder_header), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, RecorderService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val hold = PendingIntent.getService(this, 1, Intent(this, RecorderService::class.java).setAction(HOLD), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, RecorderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val paused = VoiceRecorder.paused
        shownPaused = paused
        val began = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - VoiceRecorder.startedAt)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_metro).setContentTitle(getString(R.string.recorder_header))
            .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            // A paused recording shows its time as it stood, not a clock that goes on.
            .apply { if (paused) setContentText(getString(R.string.rec_paused_note)) else setUsesChronometer(true).setWhen(began) }
        if (RecorderPrefs.on(this, Opt.PAUSE)) builder.addAction(0, getString(if (paused) R.string.rec_resume else R.string.rec_pause_action), hold)
        builder.addAction(0, getString(R.string.recorder_stop), stop)
        // A service started as a foreground one must say so at once, even when it is only there to be told to stop.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        show()
        when {
            intent?.action == STOP || !VoiceRecorder.active -> {
                // Closing the file can take a moment on a long recording: not on the main thread.
                Thread {
                    VoiceRecorder.stopRecording()
                    main.post { stopSelf() }
                }.start()
            }
            intent?.action == HOLD -> {
                if (VoiceRecorder.paused) VoiceRecorder.resume() else VoiceRecorder.pause()
                show()
            }
        }
        main.removeCallbacks(tick)
        main.postDelayed(tick, TICK_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        VoiceRecorder.stopRecording()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 0x5245
        private const val NOTICE_ID = 0x5246
        private const val STOP = "com.metrowin.launcher.recorder.STOP"
        private const val HOLD = "com.metrowin.launcher.recorder.HOLD"
        private const val TICK_MS = 2_000L

        /** Says why a recording ended by itself (no room left, the time limit), in a notification that stays until it is seen. */
        fun tell(context: Context, notice: Int) {
            val text = when (notice) {
                VoiceRecorder.NOTICE_SPACE -> R.string.rec_stopped_space
                VoiceRecorder.NOTICE_LIMIT -> R.string.rec_stopped_limit
                else -> return
            }
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.recorder_header), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(context, 0, Intent(context, RecorderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
            runCatching {
                manager.notify(
                    NOTICE_ID,
                    NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_metro).setContentTitle(context.getString(R.string.recorder_header))
                        .setContentText(context.getString(text)).setAutoCancel(true).setContentIntent(open).build(),
                )
            }
        }
    }
}
