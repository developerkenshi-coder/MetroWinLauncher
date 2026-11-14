package com.metrowin.launcher.messages

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import java.io.File

/**
 * A multimedia message arriving, as the messaging app of the phone. The network pushes a notification (WAP push): it is kept at once among
 * the messages as a message "to download", so it is not lost whatever happens next, and fetched with the phone's own MMS service
 * ([SmsManager.downloadMultimediaMessage]). When the file arrives it is read ([MmsPdu]), written among the messages of Android in place
 * of the notification, and told. If the fetch fails the notification stays: the bubble of the conversation can try again.
 *
 * shortcut: a message to a group is stored in the thread of its sender (the number of this phone is not known to tell the others apart),
 * and no acknowledgement is sent to the network beyond what the phone's service does; sending a multimedia message is not done here.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.provider.Telephony.WAP_PUSH_DELIVER" -> {
                val data = intent.getByteArrayExtra("data") ?: return
                val sub = intent.getIntExtra("subscription", -1)
                async(context) { push(it, data, sub) }
            }
            ACTION_DOWNLOADED -> {
                val row = intent.getLongExtra(EXTRA_ROW, -1)
                val file = intent.getStringExtra(EXTRA_FILE) ?: return
                val ok = resultCode == Activity.RESULT_OK
                async(context) { downloaded(it, row, File(file), ok) }
            }
        }
    }

    private fun async(context: Context, work: (Context) -> Unit) {
        val pending = goAsync()
        Thread {
            try { work(context.applicationContext) } finally { pending.finish() }
        }.start()
    }

    private fun push(context: Context, data: ByteArray, sub: Int) {
        val note = MmsPdu.notification(data) ?: return
        val repository = SmsRepository(context)
        val row = repository.saveMmsNotification(note, sub) ?: return
        download(context, row, note.location, note.transactionId, sub)
    }

    private fun downloaded(context: Context, row: Long, file: File, ok: Boolean) {
        val repository = SmsRepository(context)
        try {
            val message = if (ok && file.exists()) MmsPdu.retrieved(file.readBytes()) else null
            val stored = message?.let { repository.saveMms(it, row) }
            if (stored != null) {
                val from = message.from.orEmpty()
                SmsReceiver.notify(context, repository, from, message.parts.firstOrNull { it.type == "text/plain" }?.let { String(it.data, Charsets.UTF_8) }.orEmpty().ifEmpty { context.getString(com.metrowin.launcher.R.string.messages_mms) })
            }
        } finally {
            file.delete()
        }
    }

    companion object {
        const val ACTION_DOWNLOADED = "com.metrowin.launcher.MMS_DOWNLOADED"
        const val EXTRA_ROW = "row"
        const val EXTRA_FILE = "file"
        private const val PHONE = "com.android.phone"

        /** Asks the phone to fetch the message at [location] into a file of the launcher; the answer comes back to [MmsReceiver]. */
        fun download(context: Context, row: Long, location: String, transactionId: String, sub: Int = -1): Boolean = runCatching {
            val dir = File(context.cacheDir, "shared/mms").apply { mkdirs() }
            val file = File(dir, "dl-${transactionId.filter { it.isLetterOrDigit() }.take(40)}-$row.pdu")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            // The MMS service of the phone writes the file: it must be allowed to (the provider is not exported).
            runCatching { context.grantUriPermission(PHONE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            val done = PendingIntent.getBroadcast(
                context, row.toInt(),
                Intent(context, MmsReceiver::class.java).setAction(ACTION_DOWNLOADED).putExtra(EXTRA_ROW, row).putExtra(EXTRA_FILE, file.path),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val manager = if (sub >= 0) SmsManager.getSmsManagerForSubscriptionId(sub)
            else if (android.os.Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            manager.downloadMultimediaMessage(context, location, uri, null, done)
            true
        }.getOrDefault(false)
    }
}
