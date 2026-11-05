package com.metrowin.launcher.messages

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
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

    companion object {
        const val CHANNEL = "sms_incoming"
        const val NOTIFICATION_ID = 0x534D

        fun cancel(context: Context, address: String) =
            context.getSystemService(NotificationManager::class.java).cancel(address, NOTIFICATION_ID)

        /** The notification of a conversation, with a box to reply from it. */
        fun notify(context: Context, repository: SmsRepository, address: String, body: String) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.messages_channel), NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(
                context, address.hashCode(),
                Intent(context, MessagesActivity::class.java).putExtra(MessagesActivity.EXTRA_ADDRESS, address).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // The reply fills the intent with the text, so it must be mutable; the intent is explicit, no other app can redirect it.
            val reply = PendingIntent.getBroadcast(
                context, address.hashCode(),
                Intent(context, SmsReplyReceiver::class.java).putExtra(MessagesActivity.EXTRA_ADDRESS, address),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val label = context.getString(R.string.messages_reply)
            val action = NotificationCompat.Action.Builder(R.drawable.ic_stat_metro, label, reply)
                .addRemoteInput(RemoteInput.Builder(SmsReplyReceiver.KEY).setLabel(label).build())
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY).build()
            manager.notify(
                address, NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_metro)
                    .setContentTitle(repository.nameOf(address) ?: address.ifEmpty { context.getString(R.string.messages_unknown) })
                    .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setCategory(Notification.CATEGORY_MESSAGE).setAutoCancel(true).setContentIntent(open).addAction(action).build(),
            )
        }
    }
}

/** Sends the text typed in the reply box of a message notification. */
class SmsReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(MessagesActivity.EXTRA_ADDRESS) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        val repository = SmsRepository(context)
        // The box of the notification waits for it to be replaced: gone when sent, the failure told in its place when not.
        if (repository.send(address, text)) SmsReceiver.cancel(context, address)
        else SmsReceiver.notify(context, repository, address, context.getString(R.string.messages_failed))
    }

    companion object {
        const val KEY = "reply"
    }
}

/** The result of a sent message: when the radio did not take it, it stays among the messages as failed, to try again. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val row = intent.data ?: return
        if (resultCode != Activity.RESULT_OK) SmsRepository(context).markFailed(row)
    }
}
