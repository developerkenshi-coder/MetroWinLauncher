package com.metrowin.launcher.messages

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.metrowin.launcher.R

/** A text message arriving: it is written among the messages of Android (the role of messaging app) and told in a notification. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Only the system sends this; anything else with the same address is not a message.
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val repository = SmsRepository(context)
        parts.groupBy { it.originatingAddress.orEmpty() }.forEach { (address, pieces) ->
            val body = pieces.joinToString("") { it.messageBody.orEmpty() }
            repository.receive(address, body, pieces.first().timestampMillis)
            notify(context, repository, address, body)
        }
    }

    private fun notify(context: Context, repository: SmsRepository, address: String, body: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.messages_channel), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            context, address.hashCode(),
            Intent(context, MessagesActivity::class.java).putExtra(MessagesActivity.EXTRA_ADDRESS, address).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            address, NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_metro)
                .setContentTitle(repository.nameOf(address) ?: address.ifEmpty { context.getString(R.string.messages_unknown) })
                .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_MESSAGE).setAutoCancel(true).setContentIntent(open).build(),
        )
    }

    companion object {
        const val CHANNEL = "sms_incoming"
        const val NOTIFICATION_ID = 0x534D

        fun cancel(context: Context, address: String) =
            context.getSystemService(NotificationManager::class.java).cancel(address, NOTIFICATION_ID)
    }
}
