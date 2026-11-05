package com.metrowin.launcher.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.metrowin.launcher.R
import com.metrowin.launcher.data.VoiceRecorder

/** Keeps a recording alive when the launcher is left or the screen goes off, with a notification to see it and to stop it. */
class RecorderService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.recorder_header), NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, RecorderService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, RecorderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val began = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - VoiceRecorder.startedAt)
        // A service started as a foreground one must say so at once, even when it is only there to be told to stop.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_metro).setContentTitle(getString(R.string.recorder_header))
                .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true)
                .setUsesChronometer(true).setWhen(began).setContentIntent(open)
                .addAction(0, getString(R.string.recorder_stop), stop).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        if (intent?.action == STOP || !VoiceRecorder.active) {
            VoiceRecorder.stopRecording()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        VoiceRecorder.stopRecording()
    }

    private companion object {
        const val CHANNEL = "recording"
        const val NOTIFICATION_ID = 0x5245
        const val STOP = "com.metrowin.launcher.recorder.STOP"
    }
}
