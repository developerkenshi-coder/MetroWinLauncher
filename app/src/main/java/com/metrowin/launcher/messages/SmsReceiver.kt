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
import android.telephony.SmsMessage
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.metrowin.launcher.R
import com.metrowin.launcher.notifications.NotificationHub

/** A text message arriving: it is written among the messages of Android (the role of messaging app) and told in a notification. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Only the system sends this; anything else with the same address is not a message.
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Writing the message, looking the contact up and telling it take the content providers: not on the main thread of the receiver.
        val pending = goAsync()
        Thread {
            try {
                val repository = SmsRepository(context)
                val options = MessagesOptions(context)
                parts.groupBy { it.originatingAddress.orEmpty() }.forEach { (address, pieces) ->
                    val body = pieces.joinToString("") { it.messageBody.orEmpty() }
                    repository.receive(address, body, pieces.first().timestampMillis)
                    if (!options.isBlocked(address)) notify(context, repository, address, body)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val CHANNEL = "sms_incoming"
        const val NOTIFICATION_ID = 0x534D

        /** The conversation was read: its notification goes, and so does the copy the launcher kept in the Action Center (the key ends with the tag, the address). */
        fun cancel(context: Context, address: String) {
            context.getSystemService(NotificationManager::class.java).cancel(address, NOTIFICATION_ID)
            NotificationHub.entries.value
                .filter { it.packageName == context.packageName && it.key.contains("|$NOTIFICATION_ID|$address|") }
                .forEach { NotificationHub.forget(it.key) }
        }

        /**
         * The notification of a conversation: the messages as a chat (what the notification already held stays, the new one is added), a box
         * to reply from, a button to mark it read and, when the option is on, one to copy a one-time code. The text is hidden on the lock screen.
         */
        fun notify(context: Context, repository: SmsRepository, address: String, body: String) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val options = MessagesOptions(context)
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
            val title = repository.nameOf(address) ?: address.ifEmpty { context.getString(R.string.messages_unknown) }
            val sender = Person.Builder().setName(title).build()
            val before = runCatching { manager.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID && it.tag == address }?.notification }.getOrNull()
            val style = before?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
                ?: NotificationCompat.MessagingStyle(Person.Builder().setName(context.getString(R.string.messages_you)).build())
            style.addMessage(body, System.currentTimeMillis(), sender)
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_metro)
                .setContentTitle(title).setContentText(body).setStyle(style)
                .setCategory(Notification.CATEGORY_MESSAGE).setAutoCancel(true).setContentIntent(open).addAction(action)
                .addAction(R.drawable.ic_stat_metro, context.getString(R.string.messages_mark_read), broadcast(context, address, SmsActionReceiver.READ, null))
            if (!options.on(MessagesOptions.Key.Lock)) {
                builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(
                    NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_metro)
                        .setContentTitle(context.getString(R.string.messages_channel)).setContentText(context.getString(R.string.messages_new_message)).build(),
                )
            }
            if (options.on(MessagesOptions.Key.Otp)) {
                MessagesLogic.otpCode(body)?.let { code ->
                    builder.addAction(R.drawable.ic_stat_metro, context.getString(R.string.messages_copy_code, code), broadcast(context, address, SmsActionReceiver.COPY, code))
                }
            }
            manager.notify(address, NOTIFICATION_ID, builder.build())
        }

        private fun broadcast(context: Context, address: String, action: String, code: String?): PendingIntent =
            PendingIntent.getBroadcast(
                context, (address + action).hashCode(),
                Intent(context, SmsActionReceiver::class.java).setAction(action).putExtra(MessagesActivity.EXTRA_ADDRESS, address).putExtra(SmsActionReceiver.CODE, code),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

/** The buttons of a message notification: mark the conversation read, copy the code it carries. */
class SmsActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(MessagesActivity.EXTRA_ADDRESS) ?: return
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    READ -> {
                        runCatching { SmsRepository(context).markRead(android.provider.Telephony.Threads.getOrCreateThreadId(context, address)) }
                        SmsReceiver.cancel(context, address)
                    }
                    COPY -> intent.getStringExtra(CODE)?.let { code ->
                        val clip = android.content.ClipData.newPlainText("code", code)
                        if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                        context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
                        SmsReceiver.cancel(context, address)
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val READ = "com.metrowin.launcher.SMS_READ"
        const val COPY = "com.metrowin.launcher.SMS_COPY"
        const val CODE = "code"
    }
}

/** Sends the text typed in the reply box of a message notification. */
class SmsReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(MessagesActivity.EXTRA_ADDRESS) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        val pending = goAsync()
        Thread {
            try {
                val repository = SmsRepository(context)
                // The box of the notification waits for it to be replaced: gone when sent, the failure told in its place when not.
                if (repository.send(address, text, report = MessagesOptions(context).on(MessagesOptions.Key.Delivery))) SmsReceiver.cancel(context, address)
                else SmsReceiver.notify(context, repository, address, context.getString(R.string.messages_failed))
            } finally {
                pending.finish()
            }
        }.start()
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

/** The delivery report of a message sent (only asked for when the option is on): the status the SMS centre gave. */
class SmsDeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val row = intent.data ?: return
        val pdu = intent.getByteArrayExtra("pdu") ?: return
        val status = runCatching { SmsMessage.createFromPdu(pdu, intent.getStringExtra("format")).status }.getOrNull() ?: return
        SmsRepository(context).markDelivered(row, status)
    }
}
