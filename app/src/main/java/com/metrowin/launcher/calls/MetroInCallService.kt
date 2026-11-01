package com.metrowin.launcher.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import com.metrowin.launcher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * What Android binds to while the launcher is the phone app: it hands over every call, and the launcher shows it on [InCallActivity].
 * A call coming in is a notification that takes the whole screen when the phone is locked and slides in as a banner when it is in use.
 * Android rings the phone itself; this service does not.
 */
class MetroInCallService : InCallService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var announced = HashSet<Int>()
    private var follower: Job? = null

    override fun onBind(intent: Intent?): IBinder? {
        CallManager.bind(this)
        follower?.cancel()
        follower = scope.launch { CallManager.lines.collect(::sync) }
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        follower?.cancel()
        CallManager.bind(null)
        dismiss()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        dismiss()
        super.onDestroy()
    }

    private fun dismiss() {
        announced.clear()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    override fun onCallAdded(call: Call) = CallManager.added(call)

    override fun onCallRemoved(call: Call) = CallManager.removed(call)

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) = CallManager.audioChanged(audioState)

    /** The notification follows the calls; a call that was not seen before opens the screen of calls, unless it is ringing (the notification does that). */
    private fun sync(all: List<CallLine>) {
        val manager = getSystemService(NotificationManager::class.java)
        // A call that was hung up lingers in Android's list for a moment: the notification goes with the hang up, not after.
        val lines = all.filterNot { it.ended }
        if (lines.isEmpty()) {
            dismiss()
            return
        }
        val main = lines.firstOrNull { it.ringing } ?: lines.first()
        val fresh = lines.filter { announced.add(it.id) }
        if (fresh.any { !it.ringing }) runCatching { startActivity(screen().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, getString(R.string.incall_channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) },
        )
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, getString(R.string.incall_channel_ongoing), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, screen(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, if (main.ringing) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_metro)
            .setContentTitle(main.title.ifEmpty { getString(R.string.incall_unknown) })
            .setContentText(getString(if (main.ringing) R.string.incall_incoming else R.string.incall_return))
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
        if (main.ringing) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH).setFullScreenIntent(open, true)
                .addAction(0, getString(R.string.incall_decline), action(InCallActivity.ACTION_DECLINE, 1))
                .addAction(0, getString(R.string.incall_answer), action(InCallActivity.ACTION_ANSWER, 2))
            // Without the permission to post notifications nothing would show: the screen opens by itself then.
            if (!manager.areNotificationsEnabled()) runCatching { startActivity(screen().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else if (main.connectedAt > 0) {
            builder.setUsesChronometer(true).setWhen(main.connectedAt)
        }
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun screen() = Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun action(name: String, code: Int) = PendingIntent.getActivity(
        this, code, screen().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(InCallActivity.EXTRA_ACTION, name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val NOTIFICATION_ID = 0x4D43
        const val CHANNEL_INCOMING = "incall_incoming"
        const val CHANNEL_ONGOING = "incall_ongoing"
    }
}
