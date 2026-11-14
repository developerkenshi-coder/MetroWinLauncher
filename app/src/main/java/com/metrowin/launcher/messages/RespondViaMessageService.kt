package com.metrowin.launcher.messages

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** "Reply with a message" on a call that is declined: the text goes to the number of the call. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.data?.schemeSpecificPart?.substringBefore('?')?.trim()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (number.isNullOrEmpty() || text.isNullOrBlank()) { stopSelf(startId); return START_NOT_STICKY }
        // The write among the messages and the send take the content providers: not on the main thread of the service.
        Thread {
            try { SmsRepository(this).send(number, text, report = MessagesOptions(this).on(MessagesOptions.Key.Delivery)) } finally { stopSelf(startId) }
        }.start()
        return START_NOT_STICKY
    }
}
